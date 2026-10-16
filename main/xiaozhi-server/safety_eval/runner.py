"""
调度层：抽样、并发、断点续跑、逐条落盘。

设计取舍：
- results.jsonl 只追加不改写，便于当作审计留痕；同一题重跑会产生多行，
  由 report 层按"最后一条成功记录优先"去重。
- 单题异常一律写进 error 字段继续下一题，不让一道题拖垮整轮。
"""

import asyncio
import json
import random
from collections import OrderedDict, defaultdict
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Set

from .chat_client import ChatClient, ChatResult
from .question_bank import Question

# 各模式下每个子题库抽多少题；None 表示全量。
# standard 对应题库"测试方法"第 1 条的抽检下限（1000/300/300 + 产品特性全量）。
SAMPLING_PLANS: Dict[str, Dict[str, Optional[int]]] = {
    "check": {"content_safety": 1},
    "smoke": {
        "content_safety": 12,
        "must_refuse": 6,
        "must_answer": 6,
        "product": 6,
    },
    "standard": {
        "content_safety": 1000,
        "must_refuse": 300,
        "must_answer": 300,
        "product": None,
    },
    "all": {
        "content_safety": None,
        "must_refuse": None,
        "must_answer": None,
        "product": None,
    },
}

DEFAULT_SEED = 45654


def _stratum_key(question: Question) -> str:
    """分层维度：有风险小类的按小类，否则按大类，保证类别覆盖均匀。"""
    return question.subcategory or question.category or ""


def _stratified_sample(
    questions: Sequence[Question], quota: Optional[int], rng: random.Random
) -> List[Question]:
    """按类别分层轮转抽取，保证每个类别都被抽到而不是集中在少数类别。"""
    if quota is None or quota >= len(questions):
        return list(questions)
    if quota <= 0:
        return []

    groups: Dict[str, List[Question]] = OrderedDict()
    for question in sorted(questions, key=lambda q: q.qid):
        groups.setdefault(_stratum_key(question), []).append(question)
    for items in groups.values():
        rng.shuffle(items)

    picked: List[Question] = []
    # 分层轮转顺序也按 seed 打乱：否则小配额（如冒烟 12 题）会只落在名称靠前的
    # 前几个分层上，后面的风险大类一题也抽不到。打乱后仍由 seed 决定，可复现。
    keys = list(groups.keys())
    rng.shuffle(keys)
    while len(picked) < quota:
        progressed = False
        for key in keys:
            if not groups[key]:
                continue
            picked.append(groups[key].pop())
            progressed = True
            if len(picked) >= quota:
                break
        if not progressed:  # 所有分层都取空了
            break
    return picked


def select_questions(
    questions: Iterable[Question], mode: str, seed: int = DEFAULT_SEED
) -> List[Question]:
    """按模式抽样。同一 seed 与同一题库必然得到同一批题，保证可复现。"""
    if mode not in SAMPLING_PLANS:
        raise ValueError(f"未知模式: {mode}")
    plan = SAMPLING_PLANS[mode]

    by_bank: Dict[str, List[Question]] = defaultdict(list)
    for question in questions:
        by_bank[question.bank].append(question)

    selected: List[Question] = []
    for bank, quota in plan.items():
        pool = by_bank.get(bank, [])
        if not pool:
            continue
        # 每个子题库用独立派生的随机源，改动某个子题库配额不影响其他子题库的抽样
        rng = random.Random(f"{seed}:{bank}")
        selected.extend(_stratified_sample(pool, quota, rng))

    return sorted(selected, key=lambda q: q.qid)


def load_done_qids(results_path: Path) -> Set[str]:
    """已成功跑完的题号，用于断点续跑。解析失败的行忽略。"""
    done: Set[str] = set()
    if not results_path.exists():
        return done
    with results_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except ValueError:
                continue
            if record.get("qid") and not record.get("error"):
                done.add(record["qid"])
    return done


async def run(
    questions: Sequence[Question],
    client: ChatClient,
    results_path: Path,
    concurrency: int = 2,
    retries: int = 1,
    progress: Optional[Callable[[int, int, ChatResult], None]] = None,
) -> List[ChatResult]:
    """并发跑题，逐条追加写入 results_path。返回全部结果。"""
    semaphore = asyncio.Semaphore(max(1, concurrency))
    write_lock = asyncio.Lock()
    results: List[ChatResult] = []
    total = len(questions)
    completed = 0

    results_path.parent.mkdir(parents=True, exist_ok=True)
    handle = results_path.open("a", encoding="utf-8")

    async def worker(question: Question) -> None:
        nonlocal completed
        async with semaphore:
            result = await _ask_with_retry(client, question, retries)
        async with write_lock:
            record = result.to_dict()
            record.update(
                {
                    "bank": question.bank,
                    "bank_label": question.bank_label,
                    "category": question.category,
                    "subcategory": question.subcategory,
                    "expected": question.expected,
                }
            )
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
            handle.flush()
            results.append(result)
            completed += 1
            if progress:
                progress(completed, total, result)

    try:
        await asyncio.gather(*(worker(question) for question in questions))
    finally:
        handle.close()

    return results


async def _ask_with_retry(
    client: ChatClient, question: Question, retries: int
) -> ChatResult:
    """失败重试。重试仍失败则返回最后一次结果，错误信息保留给人工判断。"""
    attempt = 0
    result = await client.ask(question.qid, question.prompt)
    while result.error and attempt < retries:
        attempt += 1
        await asyncio.sleep(1.0 * attempt)
        result = await client.ask(question.qid, question.prompt)
    return result
