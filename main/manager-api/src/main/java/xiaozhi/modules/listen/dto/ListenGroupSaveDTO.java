package xiaozhi.modules.listen.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 题目分组整组保存 DTO（PUT /admin/listen/group/{id}）。
 * 运营在编辑弹窗一次提交组名 + 组内全部题目；后端单事务内 diff：
 * 带 id 的题目更新（id 保持稳定）、无 id 的新增、库内有但 body 缺失的物理删除。
 */
@Data
@Schema(description = "题目分组整组保存")
public class ListenGroupSaveDTO implements Serializable {

    @Schema(description = "题目分组名称，同一大类内唯一")
    @NotBlank(message = "题组名称不能为空")
    private String name;

    @Schema(description = "组内全部题目（含未改动项），缺失的题目将被物理删除")
    @NotEmpty(message = "题组至少包含一道题目")
    @Valid
    private List<Question> questions;

    /**
     * 单道题目；id 为 null 表示新增，非 null 表示更新既有题目
     */
    @Data
    @Schema(description = "题目")
    public static class Question implements Serializable {

        @Schema(description = "题目主键；新增时不传，更新时传既有 id")
        private Long id;

        @Schema(description = "题干，同一分组内唯一")
        @NotBlank(message = "题干不能为空")
        private String text;

        @Schema(description = "题目类型，当前仅 SINGLE(单选)")
        @NotBlank(message = "题目类型不能为空")
        private String type;

        @Schema(description = "组内排序序号，越小越靠前")
        @NotNull(message = "排序序号不能为空")
        private Integer sortOrder;

        @Schema(description = "选项列表，固定 5 项 A-E")
        @NotEmpty(message = "选项不能为空")
        @Valid
        private List<Option> options;
    }

    /**
     * 单个选项（key 为 A-E，text 为选项文本）
     */
    @Data
    @Schema(description = "题目选项")
    public static class Option implements Serializable {

        @Schema(description = "选项键(A-E)")
        @NotBlank(message = "选项键不能为空")
        private String key;

        @Schema(description = "选项文本")
        @NotBlank(message = "选项文本不能为空")
        private String text;
    }
}
