package xiaozhi.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "伴侣设置聚合响应")
public class CompanionSetupVO {

    @Schema(description = "伴侣信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private CompanionVO companion;

    @Schema(description = "智能体ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String agentId;

    @Schema(description = "设备是否绑定成功", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean deviceBound;

    @Schema(description = "WebSocket URL")
    private String wsUrl;

    @Schema(description = "WebSocket Token")
    private String wsToken;
}
