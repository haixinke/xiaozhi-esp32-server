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
     * 构建 release 环境请求。
     */
    public static WechatNfcSchemeRequest release(String modelId, String wechatSn, String claimRef) {
        String query = "v=1&ref=" + claimRef;
        return new WechatNfcSchemeRequest(
                new JumpWxa("/pages/nfc-claim/nfc-claim", query, "release"),
                modelId,
                wechatSn
        );
    }
}
