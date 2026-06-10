package xiaozhi.modules.payment.service;

import xiaozhi.modules.payment.entity.PaymentOrderEntity;

/**
 * 履约调度：根据订单 product_type 路由到对应履约服务。
 */
public interface FulfillmentDispatcher {
    void dispatch(PaymentOrderEntity order);

    void rollback(PaymentOrderEntity order);
}
