package xiaozhi.modules.pdc.nfc.service;

/**
 * 写卡 CSV 的内存行。
 *
 * <p>完整 Scheme 仅在导出期间存在于此不可变对象，不映射到数据库实体。
 */
public record PdcNfcWriteCsvRow(
        Integer sequenceNo,
        String assetNo,
        String wechatSn,
        String skuCode,
        String prototype,
        String uriPayload
) {
}
