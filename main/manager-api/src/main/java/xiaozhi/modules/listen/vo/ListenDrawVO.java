package xiaozhi.modules.listen.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 听你说·抽题结果 VO（小程序端）。
 * 随机抽到的一个未答过题目分组 + 组内全部题目；不返回分类相关信息。
 */
@Data
@Schema(description = "听你说抽题结果")
public class ListenDrawVO implements Serializable {

    @Schema(description = "题目分组 ID")
    private Long groupId;

    @Schema(description = "题目分组名称")
    private String groupName;

    @Schema(description = "组内题目，按 sort_order 升序")
    private List<Question> questions;

    /**
     * 单道题目（题干 + 选项列表）
     */
    @Data
    @Schema(description = "题目")
    public static class Question implements Serializable {

        @Schema(description = "题目 ID")
        private Long id;

        @Schema(description = "题干")
        private String text;

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
