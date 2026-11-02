package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenFeedbackPromptBuilder;

/**
 * 听你说·反馈提示词组装器单测。
 * 不依赖 Spring/DB，纯函数行为验证。
 */
class ListenFeedbackPromptBuilderTest {

    private final ListenFeedbackPromptBuilder builder = new ListenFeedbackPromptBuilder();

    private ListenQuestionEntity question(long id, String text) {
        ListenQuestionEntity q = new ListenQuestionEntity();
        q.setId(id);
        q.setText(text);
        return q;
    }

    @Test
    @DisplayName("组装：分组名、题干、选项文本进入 prompt")
    void build_includesGroupAndAnswers() {
        var questions = List.of(question(1L, "睡前最后那一会儿，你通常在做什么？"));
        var optionText = Map.of(1L, Map.of("A", "刷手机"));
        var optionKey = Map.of(1L, "A");

        String prompt = builder.build("睡前最后那一会儿", questions, optionText, Map.of(), optionKey);

        assertThat(prompt).contains("小红书风格", "80 个字", "睡前最后那一会儿", "刷手机");
    }

    @Test
    @DisplayName("用户补充文本与选项不同则附加「补充：」")
    void build_appendsCustomTextWhenDifferent() {
        var questions = List.of(question(1L, "Q1"));
        var optionText = Map.of(1L, Map.of("A", "刷手机"));
        var custom = Map.of(1L, "其实我也想早点睡");
        var optionKey = Map.of(1L, "A");

        String prompt = builder.build("G", questions, optionText, custom, optionKey);

        assertThat(prompt).contains("刷手机；补充：其实我也想早点睡");
    }

    @Test
    @DisplayName("选项文本与补充相同（A-D 冗余进 custom_text）时不重复拼接")
    void build_dedupesRedundantCustomText() {
        var questions = List.of(question(1L, "Q1"));
        var optionText = Map.of(1L, Map.of("A", "刷手机"));
        // 选 A 时后端把选项文本冗余进 custom_text，组装 prompt 时不能再拼一次
        var custom = Map.of(1L, "刷手机");
        var optionKey = Map.of(1L, "A");

        String prompt = builder.build("G", questions, optionText, custom, optionKey);

        assertThat(prompt).contains("回答：刷手机").doesNotContain("；补充：");
    }

    @Test
    @DisplayName("选 E「都不是」时选项文本来自用户补充输入")
    void build_optionE_withCustomTextOnly() {
        var questions = List.of(question(1L, "Q1"));
        var optionText = Map.of(1L, Map.of("E", "都不是"));
        var custom = Map.of(1L, "都不想");
        var optionKey = Map.of(1L, "E");

        String prompt = builder.build("G", questions, optionText, custom, optionKey);

        assertThat(prompt).contains("都不是；补充：都不想");
    }

    @Test
    @DisplayName("坏数据防御：选项 key 不在选项 Map 中时回落为 key 本身，不出现 \"null\"")
    void build_unknownOptionKey_fallsBackToKey() {
        var questions = List.of(question(1L, "Q1"));
        var optionKey = Map.of(1L, "Z");

        String prompt = builder.build("G", questions, Map.of(), Map.of(), optionKey);

        assertThat(prompt).contains("回答：Z").doesNotContain("null");
    }

    @Test
    @DisplayName("prompt 含不当内容软兜底指令")
    void build_containsSafetyNet() {
        var questions = List.of(question(1L, "Q1"));
        var optionKey = Map.of(1L, "A");

        String prompt = builder.build("G", questions, Map.of(1L, Map.of("A", "甲")), Map.of(), optionKey);

        assertThat(prompt).contains("忽略这些内容");
    }
}
