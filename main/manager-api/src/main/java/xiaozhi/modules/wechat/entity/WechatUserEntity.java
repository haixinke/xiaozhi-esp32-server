package xiaozhi.modules.wechat.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 微信小程序用户绑定实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_wechat_user")
@Schema(description = "微信小程序用户绑定")
public class WechatUserEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "微信openid")
    private String openid;

    @Schema(description = "关联sys_user.id")
    private Long userId;

    @Schema(description = "微信会话密钥")
    private String sessionKey;

    @Schema(description = "微信昵称")
    private String nickname;

    @Schema(description = "微信头像URL")
    private String avatarUrl;

    @Schema(description = "用户授权手机号")
    private String phone;

    @Schema(description = "创建时间")
    private Date createDate;

    @Schema(description = "更新时间")
    private Date updateDate;
}
