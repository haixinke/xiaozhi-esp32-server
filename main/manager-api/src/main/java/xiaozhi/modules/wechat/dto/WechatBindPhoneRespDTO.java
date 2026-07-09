package xiaozhi.modules.wechat.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "微信授权手机号绑定响应")
public class WechatBindPhoneRespDTO implements Serializable {

    @Schema(description = "脱敏后的手机号（如 138****1234）")
    private String phone;
}
