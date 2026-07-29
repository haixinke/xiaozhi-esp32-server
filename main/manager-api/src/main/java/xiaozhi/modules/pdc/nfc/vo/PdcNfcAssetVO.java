package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;
import java.util.Map;

/**
 * 资产视图：仅暴露非敏感字段。
 * <p>
 * 不包含 claimRefHash、claimRefCiphertext、schemeCiphertext、tagUid 等敏感数据。
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
