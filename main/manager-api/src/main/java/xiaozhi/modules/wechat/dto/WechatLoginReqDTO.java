package xiaozhi.modules.wechat.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信小程序登录请求")
public class WechatLoginReqDTO implements Serializable {

    @NotBlank(message = "微信code不能为空")
    @Schema(description = "微信小程序wx.login返回的code")
    private String code;
}
