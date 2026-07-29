package xiaozhi.modules.pdc.nfc.wechat;

/**
 * 微信 generatenfcscheme 调用结果。
 */
public record WechatNfcSchemeResult(
        boolean success,
        String scheme,
        Integer errcode,
        String errmsg,
        WechatNfcErrorAction action
) {

    public static WechatNfcSchemeResult ok(String scheme) {
        return new WechatNfcSchemeResult(true, scheme, 0, null, null);
    }

    public static WechatNfcSchemeResult fail(Integer errcode, String errmsg, WechatNfcErrorAction action) {
        return new WechatNfcSchemeResult(false, null, errcode, errmsg, action);
    }

    public static WechatNfcSchemeResult networkError(String errmsg) {
        return new WechatNfcSchemeResult(false, null, -1, errmsg, WechatNfcErrorAction.RETRYABLE);
    }
}
