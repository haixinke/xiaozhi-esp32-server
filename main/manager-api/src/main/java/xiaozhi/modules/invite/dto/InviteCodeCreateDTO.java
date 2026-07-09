package xiaozhi.modules.invite.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "企业邀请码创建请求")
public class InviteCodeCreateDTO {

    @NotNull(message = "配额不能为空")
    @Min(value = 1, message = "配额必须大于0")
    @Schema(description = "总配额", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quota;

    @Schema(description = "状态 0=失效 1=有效，默认1")
    private Integer status;

    @Schema(description = "过期时间，可空")
    private Date expireTime;

    @Schema(description = "备注")
    private String remark;
}
