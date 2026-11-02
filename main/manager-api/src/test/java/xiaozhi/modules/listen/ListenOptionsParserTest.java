package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import xiaozhi.modules.listen.util.ListenOptionsParser;

/**
 * 听你说·选项 JSON 解析器单测（纯函数，不依赖 Spring/DB）。
 * 覆盖：正常解析、空/非数组/坏 JSON 兜底、缺字段容忍。
 */
class ListenOptionsParserTest {

    @Test
    @DisplayName("正常解析：返回 key/text 选项列表")
    void parse_normalJson_returnsOptions() {
        var result = ListenOptionsParser.parse("[{\"key\":\"A\",\"text\":\"刷手机\"},{\"key\":\"E\",\"text\":\"都不是\"}]");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).key()).isEqualTo("A");
        assertThat(result.get(0).text()).isEqualTo("刷手机");
        assertThat(result.get(1).key()).isEqualTo("E");
    }

    @Test
    @DisplayName("空输入：blank/null 返回空 List")
    void parse_blank_returnsEmpty() {
        assertThat(ListenOptionsParser.parse(null)).isEmpty();
        assertThat(ListenOptionsParser.parse("")).isEmpty();
        assertThat(ListenOptionsParser.parse("  ")).isEmpty();
    }

    @Test
    @DisplayName("非数组 JSON：返回空 List 不抛异常")
    void parse_notArray_returnsEmpty() {
        assertThat(ListenOptionsParser.parse("{\"key\":\"A\"}")).isEmpty();
    }

    @Test
    @DisplayName("坏 JSON：返回空 List 不抛异常")
    void parse_brokenJson_returnsEmpty() {
        assertThat(ListenOptionsParser.parse("[{key:broken")).isEmpty();
    }

    @Test
    @DisplayName("节点缺 key/text 字段：容忍为 null 字段的 Option（不丢整条）")
    void parse_missingFields_toleratedAsNullFields() {
        var result = ListenOptionsParser.parse("[{\"key\":\"A\"},{\"text\":\"乙\"}]");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isNull();
        assertThat(result.get(1).key()).isNull();
    }
}
