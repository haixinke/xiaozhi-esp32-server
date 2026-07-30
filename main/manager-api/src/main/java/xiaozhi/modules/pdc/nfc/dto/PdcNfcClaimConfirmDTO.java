package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * NFC 领取确认请求 DTO。
 * <p>
 * 用户确认领取 NFC 资产时提交，claimRef 用于定位资产，requestId 用于幂等防重。
 */
@Data
public class PdcNfcClaimConfirmDTO {

    /** 领取引用，22 位 Base64URL 编码，由微信 NFC 碰一碰回调返回 */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{22}")
    private String claimRef;

    /** 幂等请求 ID，客户端生成的唯一标识，防止重复提交 */
    @NotBlank
    private String requestId;
}
