package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登记发布证据请求 DTO。
 * <p>
 * 用于为商品类型追加不可变的发布审核证据（append-only）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdcNfcReleaseEvidenceDTO {

    /** 发布版本（必须与当前小程序领取页发布版本一致） */
    @NotBlank(message = "发布版本不能为空")
    private String releaseVersion;

    /** 发布时间（ISO-8601 格式） */
    @NotBlank(message = "发布时间不能为空")
    private String publishedAt;

    /** 冒烟验证证据 */
    @NotBlank(message = "冒烟验证证据不能为空")
    private String smokeEvidence;
}
