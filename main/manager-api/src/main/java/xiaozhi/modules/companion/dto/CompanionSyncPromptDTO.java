package xiaozhi.modules.companion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "同步伴侣提示词请求")
public class CompanionSyncPromptDTO {

    @NotBlank(message = "智能体ID不能为空")
    @Schema(description = "智能体ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String agentId;

    @NotNull(message = "伴侣ID不能为空")
    @Schema(description = "伴侣ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long companionId;
}
