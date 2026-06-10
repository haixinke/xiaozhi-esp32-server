package xiaozhi.modules.payment.service;

import java.util.Map;

/**
 * 支付回调处理器：解析 + 验签 + 去重 + 状态机推进 + 履约
 */
public interface PaymentNotifyService {

    /**
     * @param channel WECHAT_JSAPI / MOCK
     * @param headers HTTP headers map（lower-case key）
     * @param body    raw body
     * @return 处理结果文本：SUCCESS / DUPLICATE / SIGN_FAIL / PROCESS_FAIL
     */
    String handleNotify(String channel, Map<String, String> headers, String body);
}
