package xiaozhi.modules.invite.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码使用记录视图")
public class InviteUsageVO {

    @Schema(description = "ID")
    private Long id;
    @Schema(description = "关联邀请码ID")
    private Long codeId;
    @Schema(description = "被邀请人user_id")
    private Long inviteeUserId;
    @Schema(description = "消耗时间")
    private Date createDate;
}
