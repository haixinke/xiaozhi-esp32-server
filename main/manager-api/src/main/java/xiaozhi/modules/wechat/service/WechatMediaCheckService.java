package xiaozhi.modules.wechat.service;

/**
 * 微信内容安全检测服务。
 *
 * <p>封装 security.mediaCheckAsync（2.0 异步图片审核）：提交后结果由微信在 30 分钟内
 * 通过消息推送回调返回，提交成功仅代表受理。
 */
public interface WechatMediaCheckService {

    /**
     * 提交图片异步审核。
     *
     * @param mediaUrl 图片公网 URL（微信服务器需可下载，jpg/png 等，≤10M）
     * @param openid   用户 openid（要求近 2 小时访问过小程序）
     * @return 微信返回的 trace_id，用于回调时关联任务
     * @throws xiaozhi.common.exception.RenException 提交失败（errcode 非 0 或网络异常）
     */
    String mediaCheckAsync(String mediaUrl, String openid);
}
