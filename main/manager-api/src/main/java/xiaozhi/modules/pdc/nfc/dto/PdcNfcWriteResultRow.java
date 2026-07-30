package xiaozhi.modules.pdc.nfc.dto;

import java.time.LocalDateTime;

/**
 * 工厂回传的 14 列写卡结果。
 */
public record PdcNfcWriteResultRow(
        String formatVersion,
        String jobNo,
        String assetNo,
        String wechatSn,
        String writeResult,
        String verifyResult,
        String tagUid,
        int ndefRecordCount,
        String uriSha256,
        String aarPackage,
        boolean isReadOnly,
        LocalDateTime writtenAt,
        String errorCode,
        String errorMessage
) {
}
