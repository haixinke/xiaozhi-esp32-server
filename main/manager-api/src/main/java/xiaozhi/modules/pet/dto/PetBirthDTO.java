package xiaozhi.modules.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "宠物出生请求")
public class PetBirthDTO {

    @NotBlank(message = "设备ID不能为空")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;
}
