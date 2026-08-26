package xiaozhi.modules.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 运营处理反馈请求：更新处理状态与备注
 */
@Data
@Schema(description = "运营处理反馈请求")
public class FeedbackHandleDTO {

    @NotNull(message = "id不能为空")
    @Schema(description = "反馈ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "处理状态不能为空")
    @Schema(description = "处理状态：0-未处理 1-已处理", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Size(max = 500, message = "处理备注最多500字")
    @Schema(description = "运营处理备注")
    private String remark;
}
