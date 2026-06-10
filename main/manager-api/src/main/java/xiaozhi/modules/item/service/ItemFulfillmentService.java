package xiaozhi.modules.item.service;

import xiaozhi.modules.payment.entity.PaymentOrderEntity;

/**
 * 道具履约服务（支付回调驱动）
 */
public interface ItemFulfillmentService {

    /** 支付成功后履约：发放道具 */
    void fulfill(PaymentOrderEntity order);

    /** 退款时反向处理：扣减剩余库存（不足则记审计） */
    void rollback(PaymentOrderEntity order);
}
