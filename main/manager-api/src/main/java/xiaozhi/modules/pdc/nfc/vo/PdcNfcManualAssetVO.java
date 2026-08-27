package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 手动写卡模式的资产视图（ADR 0003）。
 * 不含 Scheme 明文；Scheme 需单条解密接口按需获取并记审计。
 *
 * @param assetId        资产 ID
 * @param assetNo        资产编号
 * @param wechatSn       微信一机一码编号
 * @param prototype      原型标识
 * @param status         资产状态
 * @param verifySource   验证来源（TOUCH / MANUAL，未验证为空）
 * @param writtenAt      标记已写入时间
 * @param verifiedAt     验证通过时间
 * @param lockedAt       锁卡确认时间
 * @param lockVerifiedAt 锁后触碰复验时间
 */
public record PdcNfcManualAssetVO(
        Long assetId,
        String assetNo,
        String wechatSn,
        String prototype,
        String status,
        String verifySource,
        Date writtenAt,
        Date verifiedAt,
        Date lockedAt,
        Date lockVerifiedAt
) {}
