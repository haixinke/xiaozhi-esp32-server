package xiaozhi.modules.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户反馈提交请求
 */
@Data
@Schema(description = "用户反馈提交请求")
public class FeedbackSubmitDTO {

    @NotBlank(message = "诉求类型不能为空")
    @Schema(description = "诉求类型（字典 EGG_FEEDBACK_TYPE 的 dict_value）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 500, message = "反馈内容最多500字")
    @Schema(description = "反馈内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @AssertTrue(message = "请先阅读并同意隐私条款")
    @Schema(description = "是否同意隐私条款", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean consent;
}
