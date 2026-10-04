package xiaozhi.modules.pdc.nfc.vo;

/**
 * NFC 领取确认响应 VO。
 *
 * @param claimStatus        领取结果状态（CLAIMED / CLAIMED_BY_SELF）
 * @param pet                领取后绑定的宠物信息
 * @param replacedInvitePet 本次领取是否静默替换（逻辑删除）了邀请码宠物，
 *                          供前端成功页轻提示（渠道互斥，ADR 0007）
 */
public record PdcNfcClaimResultVO(String claimStatus, Object pet, boolean replacedInvitePet) {

    public static PdcNfcClaimResultVO claimed(Object pet, boolean replacedInvitePet) {
        return new PdcNfcClaimResultVO("CLAIMED", pet, replacedInvitePet);
    }

    public static PdcNfcClaimResultVO claimedBySelf(Object pet) {
        // 碰自己的卡不发生任何创建或替换
        return new PdcNfcClaimResultVO("CLAIMED_BY_SELF", pet, false);
    }
}
