package xiaozhi.modules.invite.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_invite_usage")
@Schema(description = "邀请码使用记录")
public class InviteUsageEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "关联ai_invite_code.id")
    private Long codeId;

    @Schema(description = "被邀请人user_id")
    private Long inviteeUserId;

    @Schema(description = "消耗时间")
    private Date createDate;
}
