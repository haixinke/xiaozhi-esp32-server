package xiaozhi.modules.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "领养蛋请求")
public class PetAdoptDTO {

    @NotBlank(message = "邀请码不能为空")
    @Schema(description = "邀请码(必填,核销裂变邀请码;无效码将拒绝领养)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteCode;
}
