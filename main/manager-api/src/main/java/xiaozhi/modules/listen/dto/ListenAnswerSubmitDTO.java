package xiaozhi.modules.listen.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 听你说·整组答案提交 DTO。
 * 用户答完一组后一次性提交：组 ID + 组内各题答案列表。
 */
@Data
@Schema(description = "听你说整组答案提交")
public class ListenAnswerSubmitDTO {

    @Schema(description = "题目分组 ID")
    @NotNull(message = "题目分组 ID 不能为空")
    private Long groupId;

    @Schema(description = "组内各题答案")
    @NotEmpty(message = "答案列表不能为空")
    @Valid
    private List<AnswerItem> answers;

    /**
     * 单题答案。
     * optionKey 必填（A-E）；customText 可空（选 E「都不是」时可填，最长 200 字）。
     */
    @Data
    @Schema(description = "单题答案")
    public static class AnswerItem {

        @Schema(description = "题目 ID")
        @NotNull(message = "题目 ID 不能为空")
        private Long questionId;

        @Schema(description = "所选选项（A-E）")
        @NotNull(message = "所选选项不能为空")
        private String optionKey;

        @Schema(description = "自定义文本答案（选 E 时可填，最长 200 字）")
        private String customText;
    }
}
