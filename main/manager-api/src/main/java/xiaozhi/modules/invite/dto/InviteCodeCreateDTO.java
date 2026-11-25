package xiaozhi.modules.invite.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "企业邀请码创建请求")
public class InviteCodeCreateDTO {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "企业或活动名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "配额不能为空")
    @Min(value = 1, message = "配额必须大于0")
    @Schema(description = "总配额", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quota;

    @Min(0)
    @Max(1)
    @Schema(description = "状态 0=失效 1=有效，默认1")
    private Integer status;

    @Schema(description = "过期时间，可空")
    private Date expireTime;

    @Size(max = 255)
    @Schema(description = "备注")
    private String remark;
}
