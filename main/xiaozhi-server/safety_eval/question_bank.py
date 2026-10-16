"""
题库解析与回填。

只负责 Markdown 表格 <-> Question 对象的双向转换，不做网络、不做文件读写，
调用方传入文本、拿到文本，方便单测。

题库形态：4 个 "## 子题库" 标题，每个标题下跟一张 Markdown 表。
生成内容安全题库 8 列（多了风险大类/风险小类），其余 3 张表 7 列，
因此列下标一律按表头列名定位，不写死数字。
"""

import re
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

# 子题库标题关键字 -> bank 键。"非拒答" 必须排在 "应拒答" 之前，否则子串误判
BANK_PATTERNS: List[Tuple[str, str]] = [
    ("非拒答", "must_answer"),
    ("应拒答", "must_refuse"),
    ("生成内容安全", "content_safety"),
    ("产品特性", "product"),
]

# 题号前缀 -> bank 键，用于校验解析结果是否落在预期子题库
QID_PREFIX_BANK: Dict[str, str] = {
    "SC": "content_safety",
    "RJ": "must_refuse",
    "NR": "must_answer",
    "FT": "product",
}

BANK_LABELS: Dict[str, str] = {
    "content_safety": "生成内容安全题库",
    "must_refuse": "应拒答题库",
    "must_answer": "非拒答题库",
    "product": "产品特性题库",
}

_SEPARATOR_CELL = re.compile(r"^:?-{2,}:?$")
_QID = re.compile(r"^[A-Z]{2,}-\d+$")
# 单元格分隔符：转义过的 \| 属于内容，不能当分隔符，否则回填含竖线的回复后自己读不回来
_CELL_DELIMITER = re.compile(r"(?<!\\)\|")


@dataclass(frozen=True)
class Question:
    """一道题。line_no 为 1 起的原文行号，回填时据此定位。"""

    qid: str
    bank: str
    category: str
    subcategory: str
    prompt: str
    expected: str
    line_no: int
    reply_col: int

    @property
    def bank_label(self) -> str:
        return BANK_LABELS.get(self.bank, self.bank)

    @property
    def full_category(self) -> str:
        if self.subcategory:
            return f"{self.category} / {self.subcategory}"
        return self.category


@dataclass
class ParseResult:
    questions: List[Question]
    # 单元格数与表头不一致的行：(行号, 原始行)，不静默丢弃，交给调用方决定是否报错
    malformed: List[Tuple[int, str]]


def _split_row(line: str) -> Optional[List[str]]:
    """把 Markdown 表格行拆成单元格；非表格行返回 None。"""
    stripped = line.strip()
    if not stripped.startswith("|"):
        return None
    body = stripped[1:]
    if body.endswith("|") and not body.endswith("\\|"):
        body = body[:-1]
    return [cell.strip() for cell in _CELL_DELIMITER.split(body)]


def _is_separator(cells: List[str]) -> bool:
    return bool(cells) and all(_SEPARATOR_CELL.match(cell or "") for cell in cells)


def _detect_bank(heading: str) -> Optional[str]:
    for keyword, bank in BANK_PATTERNS:
        if keyword in heading:
            return bank
    return None


def _locate_columns(header: List[str]) -> Optional[Dict[str, int]]:
    """按列名前缀定位关键列下标。缺少必需列时返回 None。"""
    index: Dict[str, int] = {}
    for pos, name in enumerate(header):
        if name.startswith("编号"):
            index["qid"] = pos
        elif name.startswith("风险大类"):
            index["category"] = pos
        elif name.startswith("风险小类"):
            index["subcategory"] = pos
        elif name.startswith("风险/场景类别"):
            index["category"] = pos
        elif name.startswith("测试提示词"):
            index["prompt"] = pos
        elif name.startswith("期望行为"):
            index["expected"] = pos
        elif name.startswith("实际回复"):
            index["reply"] = pos
    required = ("qid", "prompt", "reply")
    if any(key not in index for key in required):
        return None
    return index


def parse(text: str) -> ParseResult:
    """解析题库全文。"""
    questions: List[Question] = []
    malformed: List[Tuple[int, str]] = []

    bank: Optional[str] = None
    header: Optional[List[str]] = None
    columns: Optional[Dict[str, int]] = None

    for line_no, line in enumerate(text.splitlines(), start=1):
        if line.startswith("## "):
            bank = _detect_bank(line[3:])
            header = None
            columns = None
            continue

        cells = _split_row(line)
        if cells is None:
            # 表格结束，重置表头状态
            header = None
            columns = None
            continue

        if bank is None:
            continue
        if _is_separator(cells):
            continue

        if header is None:
            # 第一行表格行即表头。缺关键列说明不是题目表，columns 留空整张表跳过
            header = cells
            columns = _locate_columns(cells)
            continue

        if columns is None:
            continue
        if len(cells) != len(header):
            malformed.append((line_no, line))
            continue

        qid = cells[columns["qid"]]
        if not _QID.match(qid):
            malformed.append((line_no, line))
            continue

        questions.append(
            Question(
                qid=qid,
                bank=bank,
                category=cells[columns["category"]] if "category" in columns else "",
                subcategory=(
                    cells[columns["subcategory"]] if "subcategory" in columns else ""
                ),
                prompt=cells[columns["prompt"]],
                expected=cells[columns["expected"]] if "expected" in columns else "",
                line_no=line_no,
                reply_col=columns["reply"],
            )
        )

    return ParseResult(questions=questions, malformed=malformed)


def qid_bank_mismatches(questions: List[Question]) -> List[Question]:
    """题号前缀与所属子题库不一致的题，用于发现题库自身错位。"""
    mismatched = []
    for question in questions:
        prefix = question.qid.split("-", 1)[0]
        expected_bank = QID_PREFIX_BANK.get(prefix)
        if expected_bank and expected_bank != question.bank:
            mismatched.append(question)
    return mismatched


def escape_cell(value: str) -> str:
    """把任意文本变成可安全放进 Markdown 单元格的内容。"""
    if not value:
        return ""
    return (
        value.replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("|", "\\|")
        .replace("\n", "<br>")
        .strip()
    )


def fill_replies(
    text: str, questions: List[Question], replies: Dict[str, str]
) -> Tuple[str, int]:
    """
    把回复回填进"实际回复"列，返回 (新全文, 实际回填条数)。

    只改动有回复的行，其他行原样保留；重复执行结果一致（幂等）。
    """
    lines = text.splitlines()
    filled = 0

    by_line = {q.line_no: q for q in questions if q.qid in replies}
    for line_no, question in by_line.items():
        reply = replies.get(question.qid)
        if reply is None:
            continue
        index = line_no - 1
        if index < 0 or index >= len(lines):
            continue
        cells = _split_row(lines[index])
        if cells is None or question.reply_col >= len(cells):
            continue
        # 行号漂移保护：确认这一行仍是同一道题
        if cells[0] != question.qid:
            continue
        cells[question.reply_col] = escape_cell(reply)
        lines[index] = "| " + " | ".join(cells) + " |"
        filled += 1

    trailing_newline = "\n" if text.endswith("\n") else ""
    return "\n".join(lines) + trailing_newline, filled
