package xiaozhi.modules.payment.wechat;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付客户端抽象。
 * <p>线上接入完整 V3 SDK 时替换 Mock 实现即可。</p>
 */
public interface WechatPayClient {

    /** 是否处于 mock 模式 */
    boolean isMockMode();

    /** 获取小程序 appid，便于回写到回调返回参数 */
    String getAppid();

    /**
     * 发起 JSAPI 预下单
     */
    PrepayResult jsapiPrepay(PrepayRequest request);

    /**
     * 关闭订单
     */
    void closeOrder(String outTradeNo);

    /**
     * 发起退款
     */
    RefundResult refund(RefundRequest request);

    /**
     * 解析回调请求；mock 模式下走简化 JSON 协议；真实模式下做验签 + 解密。
     */
    NotifyResult parseNotify(Map<String, String> headers, String body);

    @Data
    class PrepayRequest {
        private String outTradeNo;
        private long amountFen;
        private String description;
        private String openid;
    }

    @Data
    class PrepayResult {
        private String prepayId;
        private Map<String, String> jsapiParams;
    }

    @Data
    class RefundRequest {
        private String outTradeNo;
        private String outRefundNo;
        private long refundFen;
        private long totalFen;
        private String reason;
    }

    @Data
    class RefundResult {
        private boolean success;
        private String refundId;
        private String message;
    }

    @Data
    class NotifyResult {
        /** 是否签名/解密通过 */
        private boolean valid;
        /** 是否实际是支付成功事件 */
        private boolean paySuccess;
        private String outTradeNo;
        private String transactionId;
        private long amountFen;
        private String message;
    }
}
