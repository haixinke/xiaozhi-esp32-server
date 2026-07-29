package xiaozhi.modules.pdc.nfc.vo;

public record PdcNfcClaimResultVO(String claimStatus, Object pet) {

    public static PdcNfcClaimResultVO claimed(Object pet) {
        return new PdcNfcClaimResultVO("CLAIMED", pet);
    }

    public static PdcNfcClaimResultVO claimedBySelf(Object pet) {
        return new PdcNfcClaimResultVO("CLAIMED_BY_SELF", pet);
    }
}
