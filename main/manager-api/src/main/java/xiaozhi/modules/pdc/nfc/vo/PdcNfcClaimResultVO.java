package xiaozhi.modules.pdc.nfc.vo;

/**
 * NFC 领取确认响应 VO。
 *
 * @param claimStatus 领取结果状态（CLAIMED / CLAIMED_BY_SELF）
 * @param pet         领取后绑定的宠物信息
 */
public record PdcNfcClaimResultVO(String claimStatus, Object pet) {

    public static PdcNfcClaimResultVO claimed(Object pet) {
        return new PdcNfcClaimResultVO("CLAIMED", pet);
    }

    public static PdcNfcClaimResultVO claimedBySelf(Object pet) {
        return new PdcNfcClaimResultVO("CLAIMED_BY_SELF", pet);
    }
}
