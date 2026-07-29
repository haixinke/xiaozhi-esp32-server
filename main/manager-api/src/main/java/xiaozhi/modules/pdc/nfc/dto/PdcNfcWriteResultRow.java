package xiaozhi.modules.pdc.nfc.dto;

/**
 * 工厂写卡结果行 DTO（从结果 CSV 解析）。
 */
public record PdcNfcWriteResultRow(
        String assetNo,
        String wechatSn,
        String skuCode,
        boolean writeSuccess,
        boolean verifySuccess,
        String uriSha256,
        int ndefRecordCount,
        String aarPackage,
        boolean readOnly,
        boolean deviceLocked
) {}
