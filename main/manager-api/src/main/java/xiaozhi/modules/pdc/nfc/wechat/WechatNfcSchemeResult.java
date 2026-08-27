package xiaozhi.modules.pdc.nfc.wechat;

/**
 * 微信 generatenfcscheme 调用结果。
 */
public record WechatNfcSchemeResult(
        boolean success,
        /** NFC Scheme 链接，取自微信响应的 openlink 字段 */
        String scheme,
        Integer errcode,
        String errmsg,
        WechatNfcErrorAction action
) {

    /** 响应缺少 openlink 的伪错误码，与网络错误 -1 区分 */
    public static final int MISSING_OPENLINK_ERRCODE = -2;

    public static WechatNfcSchemeResult ok(String scheme) {
        return new WechatNfcSchemeResult(true, scheme, 0, null, null);
    }

    public static WechatNfcSchemeResult fail(Integer errcode, String errmsg, WechatNfcErrorAction action) {
        return new WechatNfcSchemeResult(false, null, errcode, errmsg, action);
    }

    public static WechatNfcSchemeResult networkError(String errmsg) {
        return new WechatNfcSchemeResult(false, null, -1, errmsg, WechatNfcErrorAction.RETRYABLE);
    }

    /**
     * 微信返回 errcode=0 但无 openlink：该 sn 已被微信消耗（重试必得 9800010），
     * 归为 TASK_FATAL 停掉任务，避免空值流到加密环节抛 NPE 后继续烧毁后续 sn。
     */
    public static WechatNfcSchemeResult missingOpenlink() {
        return new WechatNfcSchemeResult(false, null, MISSING_OPENLINK_ERRCODE,
                "微信响应缺少 openlink 字段，该 sn 可能已被消耗，需排查后更换 sn 重试",
                WechatNfcErrorAction.TASK_FATAL);
    }
}
