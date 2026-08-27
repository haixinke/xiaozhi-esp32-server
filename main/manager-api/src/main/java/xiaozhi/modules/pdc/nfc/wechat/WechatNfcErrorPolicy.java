package xiaozhi.modules.pdc.nfc.wechat;

import java.util.Set;

/**
 * 微信 NFC Scheme 错误码映射策略。
 * <p>
 * 精确映射：
 * 44990、网络超时、HTTP 5xx → RETRYABLE
 * 44993 → QUOTA_DEFER
 * 40002、40165、40212、85079、9800003、9800007、9800008、9800009 → TASK_FATAL
 * 9800010（schema 已存在）→ TASK_FATAL：同一 sn 只能生成一次 Scheme，
 * 重试永远不会成功，必须更换 sn
 * 其他非零微信 errcode → TASK_FATAL
 */
public final class WechatNfcErrorPolicy {

    private static final Set<Integer> RETRYABLE_CODES = Set.of(44990);
    private static final Set<Integer> QUOTA_DEFER_CODES = Set.of(44993);

    /** schema 已存在：该 sn 已被微信占用，重试无意义，需更换 sn */
    public static final int SN_OCCUPIED_ERRCODE = 9800010;

    private WechatNfcErrorPolicy() {}

    /**
     * 根据微信 errcode 判定错误动作。
     */
    public static WechatNfcErrorAction classify(int errcode) {
        if (RETRYABLE_CODES.contains(errcode)) {
            return WechatNfcErrorAction.RETRYABLE;
        }
        if (QUOTA_DEFER_CODES.contains(errcode)) {
            return WechatNfcErrorAction.QUOTA_DEFER;
        }
        return WechatNfcErrorAction.TASK_FATAL;
    }

    /**
     * 网络层错误（超时、连接失败、5xx）统一归类为 RETRYABLE。
     */
    public static WechatNfcErrorAction networkError() {
        return WechatNfcErrorAction.RETRYABLE;
    }

    /**
     * 判断是否为「sn 已被微信占用」，供调用方输出可操作的运维提示。
     */
    public static boolean isSnOccupied(Integer errcode) {
        return errcode != null && errcode == SN_OCCUPIED_ERRCODE;
    }
}
