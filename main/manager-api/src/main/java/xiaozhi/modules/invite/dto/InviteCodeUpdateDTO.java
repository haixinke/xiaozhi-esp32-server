package xiaozhi.modules.invite.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
@Schema(description = "企业邀请码编辑请求")
public class InviteCodeUpdateDTO {

    @NotNull(message = "id不能为空")
    @Schema(description = "邀请码ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Size(max = 100)
    @Schema(description = "企业或活动名称")
    private String name;

    @Min(1)
    @Schema(description = "配额（仅可调增）")
    private Integer quota;

    @Min(0)
    @Max(1)
    @Schema(description = "状态 0=失效 1=有效")
    private Integer status;

    @Schema(description = "过期时间，显式传 null 清除")
    private Date expireTime;

    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean expireTimeSpecified;

    @Size(max = 255)
    @Schema(description = "备注")
    private String remark;

    @JsonSetter(nulls = Nulls.SET)
    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
        this.expireTimeSpecified = true;
    }
}
