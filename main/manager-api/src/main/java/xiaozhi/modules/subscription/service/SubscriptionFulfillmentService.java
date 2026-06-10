package xiaozhi.modules.subscription.service;

import xiaozhi.modules.payment.entity.PaymentOrderEntity;

/**
 * 订阅履约服务（支付回调驱动）
 */
public interface SubscriptionFulfillmentService {

    /**
     * 支付成功后履约：创建 user_subscription + 发放 bonus_items
     */
    void fulfill(PaymentOrderEntity order);

    /**
     * 退款时反向处理：作废订阅
     */
    void rollback(PaymentOrderEntity order);
}
