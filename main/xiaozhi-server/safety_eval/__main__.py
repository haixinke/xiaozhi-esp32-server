"""
命令行入口。

用 xiaozhi-server 的 venv 运行，例如：
  cd main/xiaozhi-server
  venv/bin/python -m safety_eval --mode check
  venv/bin/python -m safety_eval --mode smoke --concurrency 1
  venv/bin/python -m safety_eval --mode standard --concurrency 2 --resume <已有输出目录>
"""

import argparse
import asyncio
import json
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

import yaml

from . import __version__
from .chat_client import DEFAULT_CLIENT_ID, DEFAULT_TIMEOUT_S, ChatClient, ChatResult
from .question_bank import Question, parse, qid_bank_mismatches
from .report import (
    classify_gate,
    load_results,
    render_summary,
    summarize,
    write_filled_bank,
)
from .runner import DEFAULT_SEED, SAMPLING_PLANS, load_done_qids, run, select_questions

SERVER_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = SERVER_ROOT.parents[1]
DEFAULT_BANK = REPO_ROOT / "GB_T_45654-2025安全评估测试题库_eggbabe.md"
DEFAULT_CONFIG = SERVER_ROOT / "data" / ".config.yaml"
DEFAULT_OUT_ROOT = REPO_ROOT / "docs" / "compliance" / "safety-eval"
DEFAULT_DEVICE_ID = "e9f4a3610f4f4d11a40aae7a537f97ba"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="safety_eval",
        description="把安全题库逐条提交给本地聊天服务并记录 AI 文字回复",
    )
    parser.add_argument(
        "--mode",
        choices=sorted(SAMPLING_PLANS.keys()),
        default="smoke",
        help="抽样模式：check=1题预检，smoke=30题冒烟，standard=按标准抽检1625题，all=全量3045题",
    )
    parser.add_argument("--bank", type=Path, default=DEFAULT_BANK, help="题库 Markdown 路径")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help="读取 secret 的配置文件")
    parser.add_argument("--device-id", default=DEFAULT_DEVICE_ID, help="已绑定的测试设备 ID")
    parser.add_argument("--client-id", default=DEFAULT_CLIENT_ID)
    parser.add_argument("--ws-url", default=None, help="默认按配置文件的 server.port 拼 ws://127.0.0.1:<port>/xiaozhi/v1/")
    parser.add_argument("--concurrency", type=int, default=2)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_S, help="单题等待回复上限（秒）")
    parser.add_argument("--retries", type=int, default=1, help="单题失败重试次数")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED, help="抽样随机种子，固定即可复现")
    parser.add_argument("--out-dir", type=Path, default=None, help="输出目录，默认按时间戳新建")
    parser.add_argument(
        "--resume",
        type=Path,
        default=None,
        help="续跑已有输出目录，跳过其中已成功的题",
    )
    parser.add_argument(
        "--retry-errors",
        action="store_true",
        help="配合 --resume：只重跑上次出错的题",
    )
    parser.add_argument("--note", default="", help="写入 run_meta.json 的备注，如临时配置改动")
    parser.add_argument(
        "--report-only",
        type=Path,
        default=None,
        help="不跑题，仅按已有输出目录重新生成回填题库与汇总",
    )
    return parser


def load_secret(config_path: Path) -> Optional[str]:
    """
    auth_key 优先级与 app.py 一致：server.auth_key > manager-api.secret。
    读到的密钥只在内存中用于签 token，不落盘。
    """
    if not config_path.exists():
        return None
    data = yaml.safe_load(config_path.read_text(encoding="utf-8")) or {}
    auth_key = ((data.get("server") or {}).get("auth_key") or "").strip()
    if auth_key and "你" not in auth_key:
        return auth_key
    secret = ((data.get("manager-api") or {}).get("secret") or "").strip()
    if secret and "你" not in secret:
        return secret
    return None


def resolve_ws_url(explicit: Optional[str], config_path: Path) -> str:
    if explicit:
        return explicit
    port = 8000
    if config_path.exists():
        data = yaml.safe_load(config_path.read_text(encoding="utf-8")) or {}
        port = (data.get("server") or {}).get("port") or port
    return f"ws://127.0.0.1:{port}/xiaozhi/v1/"


def _print_progress(started: float):
    def report(completed: int, total: int, result: ChatResult) -> None:
        elapsed = time.monotonic() - started
        rate = completed / elapsed if elapsed > 0 else 0
        remaining = int((total - completed) / rate) if rate > 0 else 0
        status = "OK "
        if result.error:
            status = "ERR"
        elif not result.reply:
            status = "空 "
        preview = (result.reply or result.error or "").replace("\n", " ")[:40]
        print(
            f"[{completed}/{total}] {status} {result.qid} {result.elapsed_ms:>6}ms "
            f"剩余约 {remaining // 60}分{remaining % 60}秒 | {preview}",
            flush=True,
        )

    return report


def _select(
    questions: List[Question], args: argparse.Namespace, results_path: Path
) -> List[Question]:
    selected = select_questions(questions, args.mode, args.seed)
    if args.resume:
        if args.retry_errors:
            previous = load_results(results_path)
            error_qids = {qid for qid, record in previous.items() if record.get("error")}
            selected = [q for q in selected if q.qid in error_qids]
        else:
            done = load_done_qids(results_path)
            selected = [q for q in selected if q.qid not in done]
    return selected


def _write_outputs(
    out_dir: Path, bank_text: str, questions: List[Question], meta: dict
) -> dict:
    results_path = out_dir / "results.jsonl"
    results = load_results(results_path)
    summary = summarize(results)

    filled = write_filled_bank(
        bank_text, questions, results, out_dir / "题库_已回填.md"
    )
    (out_dir / "summary.md").write_text(
        render_summary(summary, meta), encoding="utf-8"
    )
    meta = dict(meta)
    meta["summary"] = summary
    meta["filled_rows"] = filled
    (out_dir / "run_meta.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return summary


def main(argv: Optional[List[str]] = None) -> int:
    args = build_parser().parse_args(argv)

    bank_text = args.bank.read_text(encoding="utf-8")
    parsed = parse(bank_text)
    if not parsed.questions:
        print(f"题库解析为空：{args.bank}", file=sys.stderr)
        return 2
    if parsed.malformed:
        print(f"警告：{len(parsed.malformed)} 行格式异常已跳过，首行 {parsed.malformed[0][0]}")
    mismatched = qid_bank_mismatches(parsed.questions)
    if mismatched:
        print(f"警告：{len(mismatched)} 题的题号前缀与所属子题库不一致，如 {mismatched[0].qid}")

    if args.report_only:
        out_dir = args.report_only
        meta_path = out_dir / "run_meta.json"
        meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}
        summary = _write_outputs(out_dir, bank_text, parsed.questions, meta)
        print(f"已重新生成报告：{out_dir}（{summary['total']} 题）")
        return 0

    out_dir = args.resume or args.out_dir or (
        DEFAULT_OUT_ROOT / datetime.now().strftime("%Y%m%d-%H%M%S")
    )
    out_dir.mkdir(parents=True, exist_ok=True)
    results_path = out_dir / "results.jsonl"

    selected = _select(parsed.questions, args, results_path)
    if not selected:
        print("没有需要跑的题（可能已全部完成）。")
        summary = _write_outputs(out_dir, bank_text, parsed.questions, {})
        return 0

    secret = load_secret(args.config)
    if not secret:
        print(f"警告：未能从 {args.config} 读到密钥，将不带 token 连接（鉴权开启时会失败）")
    ws_url = resolve_ws_url(args.ws_url, args.config)

    distribution: Dict[str, int] = {}
    for question in selected:
        distribution[question.bank] = distribution.get(question.bank, 0) + 1

    print(f"题库共 {len(parsed.questions)} 题，本次将测试 {len(selected)} 题：{distribution}")
    print(f"服务地址 {ws_url}　设备 {args.device_id}　并发 {args.concurrency}　超时 {args.timeout}s")
    print(f"输出目录 {out_dir}")

    client = ChatClient(
        ws_url=ws_url,
        device_id=args.device_id,
        secret=secret,
        client_id=args.client_id,
        timeout=args.timeout,
    )

    started_at = datetime.now().astimezone().isoformat()
    started = time.monotonic()
    results = asyncio.run(
        run(
            selected,
            client,
            results_path,
            concurrency=args.concurrency,
            retries=args.retries,
            progress=_print_progress(started),
        )
    )
    finished_at = datetime.now().astimezone().isoformat()

    meta = {
        "script_version": __version__,
        "started_at": started_at,
        "finished_at": finished_at,
        "mode": args.mode,
        "seed": args.seed,
        "concurrency": args.concurrency,
        "timeout_s": args.timeout,
        "retries": args.retries,
        "ws_url": ws_url,
        "device_id": args.device_id,
        "client_id": args.client_id,
        "bank_file": str(args.bank),
        "bank_total": len(parsed.questions),
        "selected_total": len(selected),
        "selected_distribution": distribution,
        "malformed_rows": len(parsed.malformed),
        "note": args.note,
    }
    summary = _write_outputs(out_dir, bank_text, parsed.questions, meta)

    errors = sum(1 for r in results if r.error)
    empty = sum(1 for r in results if not r.error and not r.reply)
    blocked = sum(1 for r in results if classify_gate(r.reply) == "input_blocked")
    print(
        f"\n完成 {len(results)} 题，出错 {errors}，空回复 {empty}，入口拦截 {blocked}，"
        f"耗时 {int(time.monotonic() - started)}s"
    )
    print(f"累计已记录 {summary['total']} 题，产出在 {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
