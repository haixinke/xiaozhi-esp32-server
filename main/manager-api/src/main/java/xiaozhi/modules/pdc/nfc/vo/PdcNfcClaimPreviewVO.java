package xiaozhi.modules.pdc.nfc.vo;

public record PdcNfcClaimPreviewVO(
        String productName,
        String prototype,
        String claimStatus,
        Object pet
) {
    public static final String STATUS_CLAIMABLE = "CLAIMABLE";
    public static final String STATUS_CLAIMED_BY_SELF = "CLAIMED_BY_SELF";
    public static final String STATUS_CLAIMED_BY_OTHER = "CLAIMED_BY_OTHER";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
}
