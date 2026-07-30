package xiaozhi.modules.pdc.nfc.dto;

import java.time.LocalDateTime;

/**
 * 工厂回传的 14 列写卡结果。验证证据字段允许为空，
 * 是否必填由 write_result/verify_result 组合决定。
 */
public record PdcNfcWriteResultRow(
        String formatVersion,
        String jobNo,
        String assetNo,
        String wechatSn,
        String writeResult,
        String verifyResult,
        String tagUid,
        Integer ndefRecordCount,
        String uriSha256,
        String aarPackage,
        Boolean isReadOnly,
        LocalDateTime writtenAt,
        String errorCode,
        String errorMessage
) {
}
