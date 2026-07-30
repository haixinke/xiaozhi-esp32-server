package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    /** 关联商品类型 ID（对应 pdc_nfc_product_type.id） */
    @NotNull(message = "商品类型ID不能为空")
    private Long productTypeId;

    /** 证据类型（如 FIRMWARE_VERSION / PRODUCTION_VERIFY / QUALITY_AUDIT） */
    @NotBlank(message = "证据类型不能为空")
    private String evidenceType;

    /** 证据内容，自由文本，最长 500 字符 */
    @NotBlank(message = "证据内容不能为空")
    private String evidenceContent;
}
