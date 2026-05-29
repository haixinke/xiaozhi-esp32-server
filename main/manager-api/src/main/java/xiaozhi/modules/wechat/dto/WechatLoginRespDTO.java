package xiaozhi.modules.wechat.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "微信小程序登录响应")
public class WechatLoginRespDTO implements Serializable {

    @Schema(description = "Bearer Token")
    private String token;

    @Schema(description = "Token有效期(秒)")
    private Integer expire;

    @Schema(description = "微信openid")
    private String openid;

    @Schema(description = "是否为新自动创建的用户")
    private Boolean isNewUser;

    @Schema(description = "智能体ID")
    private String agentId;
}
