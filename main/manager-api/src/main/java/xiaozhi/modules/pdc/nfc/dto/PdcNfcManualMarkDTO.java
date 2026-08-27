package xiaozhi.modules.pdc.nfc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcManualMarkAction;

/**
 * 手动写卡模式的单资产标记请求（ADR 0003）。
 */
@Data
@Schema(description = "手动写卡单资产标记请求")
public class PdcNfcManualMarkDTO {

    @NotNull
    @Schema(description = "标记动作", requiredMode = Schema.RequiredMode.REQUIRED)
    private PdcNfcManualMarkAction action;
}
