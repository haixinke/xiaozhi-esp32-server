package xiaozhi.modules.pdc.nfc.vo;

/**
 * NFC 领取预览响应 VO。
 * <p>
 * 返回领取前的状态信息，不产生副作用。
 *
 * @param productName 商品类型名称
 * @param prototype   原型标识
 * @param claimStatus 领取状态（CLAIMABLE / CLAIMED_BY_SELF / CLAIMED_BY_OTHER / ALREADY_OWNED / UNAVAILABLE）
 * @param pet         已绑定的宠物信息（已领取时返回）
 */
public record PdcNfcClaimPreviewVO(
        String productName,
        String prototype,
        String claimStatus,
        Object pet
) {
    public static final String STATUS_CLAIMABLE = "CLAIMABLE";
    public static final String STATUS_CLAIMED_BY_SELF = "CLAIMED_BY_SELF";
    public static final String STATUS_CLAIMED_BY_OTHER = "CLAIMED_BY_OTHER";
    /** 一人一宠约束：用户已领养过蛋宝宝，不可再领取 */
    public static final String STATUS_ALREADY_OWNED = "ALREADY_OWNED";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
}
