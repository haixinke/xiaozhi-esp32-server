package xiaozhi.modules.invite.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "企业邀请码编辑请求")
public class InviteCodeUpdateDTO {

    @NotNull(message = "id不能为空")
    @Schema(description = "邀请码ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "配额（仅可调增）")
    private Integer quota;

    @Schema(description = "状态 0=失效 1=有效")
    private Integer status;

    @Schema(description = "过期时间")
    private Date expireTime;

    @Schema(description = "备注")
    private String remark;
}
