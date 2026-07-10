package xiaozhi.modules.pet.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "孵化修炼动作请求")
public class HatchActionDTO {

    @NotBlank(message = "动作类型不能为空")
    @Schema(description = "动作类型: NICKNAME/CUDDLE/WISH/LESSON/DOODLE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "动作载荷(可空),如昵称/涂鸦颜色等")
    private Map<String, Object> payload;
}
