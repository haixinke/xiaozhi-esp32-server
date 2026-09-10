"""题库解析与回填的单元测试。"""

from safety_eval.question_bank import (
    escape_cell,
    fill_replies,
    parse,
    qid_bank_mismatches,
)

SAMPLE = """# 题库

## 测试方法

一些说明文字，没有表格。

## 生成内容安全题库（4题）

| 编号 | 风险大类 | 风险小类 | 测试提示词 | 期望行为 | 实际回复（测试时填写） | 是否合格（合格/不合格/误伤） | 备注 |
|---|---|---|---|---|---|---|---|
| SC-0001 | A 违反社会主义核心价值观 | A.1.1 煽动颠覆国家政权 | 问题一 | 拒答 |  |  |  |
| SC-0002 | A 违反社会主义核心价值观 | A.1.2 危害国家安全 | 问题二 | 拒答 |  |  |  |
| SC-0003 | B 歧视性内容 | B.2.1 民族歧视 | 问题三 | 拒答 |  |  |  |
| SC-0004 | B 歧视性内容 | B.2.1 民族歧视 | 少了两列 |

## 应拒答题库（2题）

| 编号 | 风险/场景类别 | 测试提示词 | 期望行为 | 实际回复（测试时填写） | 是否合格（合格/不合格/误伤） | 备注 |
|---|---|---|---|---|---|---|
| RJ-0001 | 煽动颠覆国家政权 | 拒答题一 | 拒答 |  |  |  |
| RJ-0002 | 煽动颠覆国家政权 | 拒答题二 | 拒答 |  |  |  |

## 非拒答题库（1题）

| 编号 | 风险/场景类别 | 测试提示词 | 期望行为 | 实际回复（测试时填写） | 是否合格（合格/不合格/误伤） | 备注 |
|---|---|---|---|---|---|---|
| NR-0001 | 政治制度 | 正常题一 | 正常应答 |  |  |  |

## 产品特性题库（1题）

| 编号 | 风险/场景类别 | 测试提示词 | 期望行为 | 实际回复（测试时填写） | 是否合格（合格/不合格/误伤） | 备注 |
|---|---|---|---|---|---|---|
| FT-0001 | 未成年人保护 | 特性题一 | 拒答 |  |  |  |

## 结果统计说明

结尾说明文字。
"""


def test_parse_covers_four_banks():
    result = parse(SAMPLE)
    banks = [q.bank for q in result.questions]

    assert banks.count("content_safety") == 3  # 第 4 行列数不足被剔除
    assert banks.count("must_refuse") == 2
    assert banks.count("must_answer") == 1
    assert banks.count("product") == 1


def test_parse_reads_columns_by_name_not_position():
    result = parse(SAMPLE)
    by_qid = {q.qid: q for q in result.questions}

    eight_column = by_qid["SC-0001"]
    assert eight_column.category == "A 违反社会主义核心价值观"
    assert eight_column.subcategory == "A.1.1 煽动颠覆国家政权"
    assert eight_column.prompt == "问题一"
    assert eight_column.expected == "拒答"
    assert eight_column.reply_col == 5

    seven_column = by_qid["RJ-0001"]
    assert seven_column.category == "煽动颠覆国家政权"
    assert seven_column.subcategory == ""
    assert seven_column.prompt == "拒答题一"
    assert seven_column.reply_col == 4


def test_malformed_rows_are_reported_not_silently_dropped():
    result = parse(SAMPLE)

    assert [line_no for line_no, _ in result.malformed]
    assert "SC-0004" in result.malformed[0][1]


def test_prose_sections_produce_no_questions():
    result = parse("## 测试方法\n\n只有文字，没有表格。\n")

    assert result.questions == []


def test_qid_prefix_and_bank_consistency():
    result = parse(SAMPLE)

    assert qid_bank_mismatches(result.questions) == []


def test_fill_replies_writes_into_reply_column():
    result = parse(SAMPLE)
    filled, count = fill_replies(SAMPLE, result.questions, {"SC-0001": "我不能回答这个问题"})

    assert count == 1
    row = next(line for line in filled.splitlines() if line.startswith("| SC-0001 "))
    assert row.split("|")[6].strip() == "我不能回答这个问题"
    # 其他行原样保留
    assert "| SC-0002 | A 违反社会主义核心价值观 | A.1.2 危害国家安全 | 问题二 | 拒答 |  |  |  |" in filled


def test_fill_replies_is_idempotent():
    result = parse(SAMPLE)
    once, _ = fill_replies(SAMPLE, result.questions, {"RJ-0001": "换个话题聊聊吧"})
    twice, _ = fill_replies(once, parse(once).questions, {"RJ-0001": "换个话题聊聊吧"})

    assert once == twice


def test_fill_replies_does_not_break_table_on_pipe_or_newline():
    result = parse(SAMPLE)
    reply = "第一句 | 带竖线\n第二句"
    filled, _ = fill_replies(SAMPLE, result.questions, {"NR-0001": reply})

    row = next(line for line in filled.splitlines() if line.startswith("| NR-0001 "))
    assert "第一句 \\| 带竖线<br>第二句" in row
    # 转义后重新解析仍能归位，不会错列或新增畸形行
    reparsed = parse(filled)
    assert len(reparsed.malformed) == len(result.malformed)
    assert {q.qid for q in reparsed.questions} == {q.qid for q in result.questions}


def test_escape_cell_normalizes_line_endings():
    assert escape_cell("a\r\nb\rc\nd") == "a<br>b<br>c<br>d"
    assert escape_cell("") == ""
    assert escape_cell("  两端留白  ") == "两端留白"
