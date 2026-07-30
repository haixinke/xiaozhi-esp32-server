package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcWriteResultRow;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;

/**
 * Importer 完整预检后的不可变写入参数。
 */
public record ValidatedWriteResult(
        PdcNfcWriteResultRow row,
        PdcNfcWriteJobItemEntity item,
        PdcNfcAssetEntity asset,
        PdcNfcAssetStatus targetStatus,
        boolean fullyVerified
) {
}
