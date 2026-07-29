package xiaozhi.modules.wechat.service;

/**
 * 微信小程序 access_token 提供者。
 * 负责获取并缓存 access_token（Redis + 300s 安全余量）。
 * phone bind 和 NFC Scheme client 共用。
 */
public interface WechatAccessTokenProvider {

    /**
     * 获取当前有效的微信小程序 access_token。
     * 优先返回 Redis 缓存值，缓存未命中时调用微信 stable_token 接口。
     *
     * @return 非空的 access_token
     * @throws xiaozhi.common.exception.RenException 配置缺失或接口调用失败
     */
    String getAccessToken();
}
