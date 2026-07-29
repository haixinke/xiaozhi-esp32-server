package xiaozhi.modules.pdc.nfc.wechat;

/**
 * 微信 NFC Scheme 错误动作分类。
 */
public enum WechatNfcErrorAction {

    /** 网络瞬时故障或频率限制，可指数退避重试 */
    RETRYABLE,

    /** 配额耗尽（44993），延后到次日 */
    QUOTA_DEFER,

    /** 参数或权限错误，不可重试，标记任务失败 */
    TASK_FATAL
}
