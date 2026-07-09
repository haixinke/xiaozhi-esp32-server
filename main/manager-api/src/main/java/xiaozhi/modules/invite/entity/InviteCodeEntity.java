package xiaozhi.modules.invite.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_invite_code")
@Schema(description = "邀请码")
public class InviteCodeEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "邀请码字符串")
    private String code;

    @Schema(description = "1=个人 2=企业")
    private Integer type;

    @Schema(description = "个人码=归属用户id;企业码=NULL")
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

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人")
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createDate;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新人")
    private Long updater;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新时间")
    private Date updateDate;
}
