package xiaozhi.modules.pet.constant;

/**
 * AI生图任务状态机。
 *
 * <p>迁移路径：PENDING(照片内容审核中) → RUNNING(生成中) → REVIEWING(结果图审核中) → SUCCEEDED/FAILED。
 * 两个审核态由微信消息推送回调驱动推进；照片或结果图任一驳回即 FAILED，且不计入每日配额。
 */
public enum ImageGenTaskStatus {

    /** 照片内容审核中（等待 mediaCheckAsync 推送） */
    PENDING,

    /** 生成中（@Async 调用 Seedream） */
    RUNNING,

    /** 结果图内容审核中（等待 mediaCheckAsync 推送） */
    REVIEWING,

    /** 成功，result_url 可用 */
    SUCCEEDED,

    /** 失败（审核驳回/生成异常/超时兜底），原因见 fail_reason */
    FAILED
}
