package xiaozhi.modules.pdc.nfc.dto;

/**
 * 工厂写卡结果行 DTO（从工厂回传的写卡结果 CSV 解析）。
 *
 * @param assetNo        资产编号
 * @param wechatSn       微信序列号
 * @param skuCode        SKU 编码
 * @param writeSuccess   写卡是否成功
 * @param verifySuccess  校验是否通过
 * @param uriSha256      URI 记录的 SHA-256 哈希（用于完整性校验）
 * @param ndefRecordCount NDEF 记录数
 * @param aarPackage     Android AAR 包名
 * @param readOnly       标签是否已设为只读
 * @param deviceLocked   设备是否已锁定（写卡失败时标记报废）
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
