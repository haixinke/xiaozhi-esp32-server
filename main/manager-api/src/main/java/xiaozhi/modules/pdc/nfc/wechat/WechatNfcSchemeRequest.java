package xiaozhi.modules.pdc.nfc.wechat;

/**
 * 微信 generatenfcscheme 请求体。
 */
public record WechatNfcSchemeRequest(
        JumpWxa jumpWxa,
        String modelId,
        String sn
) {

    public record JumpWxa(
            String path,
            String query,
            String envVersion
    ) {}

    /**
     * 构建请求，env_version 由调用方传入（release/trial/develop）。
     */
    public static WechatNfcSchemeRequest of(String modelId, String wechatSn, String claimRef, String envVersion) {
        String query = "v=1&ref=" + claimRef;
        return new WechatNfcSchemeRequest(
                new JumpWxa("/pages/nfc-claim/nfc-claim", query, envVersion),
                modelId,
                wechatSn
        );
    }
}
