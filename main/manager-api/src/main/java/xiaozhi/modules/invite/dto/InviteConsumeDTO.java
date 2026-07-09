package xiaozhi.modules.invite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "邀请码消耗请求")
public class InviteConsumeDTO {

    @NotBlank(message = "邀请码不能为空")
    @Schema(description = "邀请码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
