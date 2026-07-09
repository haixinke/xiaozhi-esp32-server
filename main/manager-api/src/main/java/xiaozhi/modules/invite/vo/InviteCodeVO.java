package xiaozhi.modules.invite.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码视图")
public class InviteCodeVO {

    @Schema(description = "ID")
    private Long id;
    @Schema(description = "邀请码字符串")
    private String code;
    @Schema(description = "1=个人 2=企业")
    private Integer type;
    @Schema(description = "归属用户id")
    private Long ownerUserId;
    @Schema(description = "总配额")
    private Integer quota;
    @Schema(description = "已使用")
    private Integer usedCount;
    @Schema(description = "剩余")
    private Integer remaining;
    @Schema(description = "0=失效 1=有效")
    private Integer status;
    @Schema(description = "过期时间")
    private Date expireTime;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private Date createDate;
}
