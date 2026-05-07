package xiaozhi.modules.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "宠物编辑请求")
public class PetUpdateDTO {

    @NotBlank(message = "宠物ID不能为空")
    @Schema(description = "宠物ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "昵称")
    private String nickname;
}
