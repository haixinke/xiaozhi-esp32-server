package xiaozhi.modules.listen.util;

import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 听你说·题目选项 JSON 的统一解析器。
 * <p>
 * 库内 options_json 格式为裸数组 {@code [{"key":"A","text":"..."}]}；
 * 解析失败一律返回空 List 并记 warn 日志——坏数据由导入期校验兜底，
 * 消费端（抽题/详情展示/反馈 prompt 组装）不应因坏数据整体 500。
 */
@Slf4j
public final class ListenOptionsParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ListenOptionsParser() {
    }

    /** 一个选项：key（A-E）+ 展示文本 */
    public record Option(String key, String text) {
    }

    /**
     * 解析 options_json 为选项列表；解析失败/非数组/空输入均返回空 List。
     */
    public static List<Option> parse(String optionsJson) {
        if (StringUtils.isBlank(optionsJson)) {
            return List.of();
        }
        try {
            JsonNode array = OBJECT_MAPPER.readTree(optionsJson);
            if (!array.isArray()) {
                log.warn("options_json 不是数组: {}", optionsJson);
                return List.of();
            }
            return StreamSupport.stream(array.spliterator(), false)
                    .map(node -> new Option(node.path("key").asText(null), node.path("text").asText(null)))
                    .toList();
        } catch (Exception e) {
            log.warn("options_json 解析失败: {}", optionsJson, e);
            return List.of();
        }
    }
}
