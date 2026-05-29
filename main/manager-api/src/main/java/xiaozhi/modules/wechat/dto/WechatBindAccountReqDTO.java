package xiaozhi.modules.wechat.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信小程序绑定已有账号请求")
public class WechatBindAccountReqDTO implements Serializable {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "已有账号用户名(手机号)")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "已有账号密码")
    private String password;
}
