package xiaozhi.modules.wechat.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信授权手机号绑定请求")
public class WechatBindPhoneReqDTO implements Serializable {

    @NotBlank(message = "手机号授权code不能为空")
    @Schema(description = "getPhoneNumber 回调返回的动态 code（e.detail.code）")
    private String phoneCode;
}
