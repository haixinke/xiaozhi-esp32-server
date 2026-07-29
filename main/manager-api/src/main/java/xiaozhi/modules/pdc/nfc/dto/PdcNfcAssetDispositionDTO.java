package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 资产处置 DTO（作废 / 停用等单资产操作）。
 */
@Data
public class PdcNfcAssetDispositionDTO {

    /** 资产 ID */
    private Long assetId;

    /** 业务单号 */
    @NotBlank(message = "businessNo不能为空")
    private String businessNo;

    /** 作废原因 */
    private String reason;
}
