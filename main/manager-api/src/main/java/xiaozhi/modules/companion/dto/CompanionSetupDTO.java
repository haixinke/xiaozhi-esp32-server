package xiaozhi.modules.companion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "伴侣设置聚合请求")
public class CompanionSetupDTO extends CompanionCreateDTO {

    @Schema(description = "已有智能体ID（可选，若提供则跳过智能体创建）")
    private String agentId;
}
