package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;
import java.util.Map;

/**
 * 资产视图：仅暴露非敏感字段。
 * <p>
 * 不包含 claimRefHash、claimRefCiphertext、schemeCiphertext、tagUid 等敏感数据。
 *
 * @param id             资产 ID
 * @param assetNo        资产编号
 * @param batchNo        批次编号
 * @param itemNo         批次内序号
 * @param skuCode        SKU 编码
 * @param prototype      原型标识
 * @param wechatSn       微信序列号
 * @param status         资产状态
 * @param schemeSha256   scheme 明文的 SHA-256 哈希
 * @param statusTimeline 状态时间线（状态名 → 进入该状态的时间）
 */
public record PdcNfcAssetVO(
        Long id,
        String assetNo,
        String batchNo,
        String itemNo,
        String skuCode,
        String prototype,
        String wechatSn,
        String status,
        String schemeSha256,
        Map<String, Date> statusTimeline
) {}
