package xiaozhi.modules.payment.enums;

/**
 * 支付渠道
 */
public final class PayChannel {
    /** 微信JSAPI支付 */
    public static final String WECHAT_JSAPI = "WECHAT_JSAPI";
    /** 模拟支付（仅本地联调） */
    public static final String MOCK = "MOCK";

    private PayChannel() {
    }
}
