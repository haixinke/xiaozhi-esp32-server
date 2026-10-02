"""对话旁白（Dialogue Aside）过滤测试。

覆盖两层：
- DialogueAsideFilter 流式状态机（三种定界符、跨 chunk、50 字安全阀）
- MarkdownCleaner.clean_markdown 的无状态兜底正则（闭合段删除、星号规则顺序）
"""

from core.utils.tts import DialogueAsideFilter, MarkdownCleaner


class TestDialogueAsideFilter:
    def test_plain_text_passes_through(self):
        f = DialogueAsideFilter()
        assert f.feed("你好，世界") == "你好，世界"

    def test_fullwidth_parentheses_removed(self):
        f = DialogueAsideFilter()
        assert f.feed("你好（微笑）世界") == "你好世界"

    def test_halfwidth_parentheses_removed(self):
        f = DialogueAsideFilter()
        assert f.feed("你好(laughs)世界") == "你好世界"

    def test_star_removed(self):
        f = DialogueAsideFilter()
        assert f.feed("你好*sighs*世界") == "你好世界"

    def test_multiple_asides_in_one_chunk(self):
        f = DialogueAsideFilter()
        assert f.feed("（笑）你好（哭）世界*点头*！") == "你好世界！"

    def test_aside_split_across_chunks(self):
        f = DialogueAsideFilter()
        assert f.feed("你好（微") == "你好"
        assert f.feed("笑）世界") == "世界"

    def test_open_delimiter_at_chunk_end(self):
        f = DialogueAsideFilter()
        assert f.feed("你好（") == "你好"
        assert f.feed("微笑）世界") == "世界"

    def test_unclosed_aside_stays_swallowed(self):
        f = DialogueAsideFilter()
        assert f.feed("你好（微笑") == "你好"

    def test_safety_valve_flushes_after_50_chars(self):
        f = DialogueAsideFilter()
        # 前 50 字仍在吞食状态，无输出
        assert f.feed("（" + "啊" * 50) == ""
        # 第 51 字触发安全阀：开符与已吞内容原样吐出并复位
        assert f.feed("吗") == "（" + "啊" * 50 + "吗"

    def test_filter_works_again_after_safety_valve(self):
        f = DialogueAsideFilter()
        f.feed("（" + "啊" * 50)
        f.feed("吗")  # 触发安全阀并复位
        assert f.feed("（笑）你好") == "你好"

    def test_aside_after_safety_valve_in_same_chunk(self):
        f = DialogueAsideFilter()
        out = f.feed("（" + "啊" * 51 + "正常（笑）结束")
        assert out == "（" + "啊" * 51 + "正常结束"

    def test_mismatched_close_does_not_exit(self):
        # 全角括号吞食期间，半角闭括号不解除吞食
        f = DialogueAsideFilter()
        assert f.feed("（笑)哭）好") == "好"

    def test_reset(self):
        f = DialogueAsideFilter()
        f.feed("你好（微")
        f.reset()
        assert f.feed("笑）世界") == "笑）世界"


class TestMarkdownCleanerAside:
    def test_fullwidth_parentheses_removed(self):
        assert MarkdownCleaner.clean_markdown("你好（微笑）世界") == "你好世界"

    def test_halfwidth_parentheses_removed(self):
        assert MarkdownCleaner.clean_markdown("你好(laughs)世界") == "你好世界"

    def test_star_aside_removed_not_kept_as_italic(self):
        # 旁白规则必须先于斜体规则执行，否则 *微笑* 会被保留为「微笑」
        assert MarkdownCleaner.clean_markdown("你好*微笑*世界") == "你好世界"

    def test_unclosed_parenthesis_kept(self):
        # 无状态正则只删闭合段，未闭合原文保留
        assert MarkdownCleaner.clean_markdown("你好（微笑") == "你好（微笑"

    def test_bold_content_still_kept(self):
        # 回归：粗体仍按既有规则保留文字内容
        assert MarkdownCleaner.clean_markdown("这很**重要**") == "这很重要"
