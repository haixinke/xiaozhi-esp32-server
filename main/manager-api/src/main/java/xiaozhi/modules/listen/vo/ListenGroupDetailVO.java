package xiaozhi.modules.listen.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 题目分组详情 VO，含组内全部题目与解析后的选项。
 * 选项在库中以 options_json JSON 列存储，下发前解析为 key/text 列表，前端无需理解 JSON 包装。
 */
@Data
@Schema(description = "题目分组详情VO(含组内题目)")
public class ListenGroupDetailVO implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "题目大类(字典 LISTEN_CATEGORY 的 dict_value)")
    private String categoryCode;

    @Schema(description = "题目大类名称(字典 dict_label，翻译不到时为原始 dict_value)")
    private String categoryName;

    @Schema(description = "题目分组名称")
    private String name;

    @Schema(description = "排序序号，越小越靠前")
    private Integer sortOrder;

    @Schema(description = "组内题目，按 sort_order 升序")
    private List<Question> questions;

    /**
     * 单道题目（题干 + 类型 + 选项列表）
     */
    @Data
    @Schema(description = "题目")
    public static class Question implements Serializable {

        @Schema(description = "主键")
        private Long id;

        @Schema(description = "题干")
        private String text;

        @Schema(description = "题目类型，当前仅 SINGLE(单选)")
        private String type;

        @Schema(description = "排序序号，越小越靠前")
        private Integer sortOrder;

        @Schema(description = "选项列表，固定 5 项 A-E")
        private List<Option> options;
    }

    /**
     * 单个选项（key 为 A-E，text 为选项文本）
     */
    @Data
    @Schema(description = "题目选项")
    public static class Option implements Serializable {

        @Schema(description = "选项键(A-E)")
        private String key;

        @Schema(description = "选项文本")
        private String text;
    }
}
