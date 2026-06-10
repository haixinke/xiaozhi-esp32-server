package xiaozhi.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.item.service.ItemFulfillmentService;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;
import xiaozhi.modules.payment.enums.ProductType;
import xiaozhi.modules.payment.service.FulfillmentDispatcher;
import xiaozhi.modules.subscription.service.SubscriptionFulfillmentService;

@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentDispatcherImpl implements FulfillmentDispatcher {

    private final SubscriptionFulfillmentService subscriptionFulfillment;
    private final ItemFulfillmentService itemFulfillment;

    /** 按 productType 路由到订阅或道具履约。 */
    @Override
    public void dispatch(PaymentOrderEntity order) {
        String type = order.getProductType();
        if (ProductType.SUBSCRIPTION.equals(type)) {
            subscriptionFulfillment.fulfill(order);
        } else if (ProductType.ITEM.equals(type)) {
            itemFulfillment.fulfill(order);
        } else {
            throw new RenException(ErrorCode.PAY_PRODUCT_INVALID);
        }
    }

    /** 按 productType 路由到订阅或道具退款回滚。 */
    @Override
    public void rollback(PaymentOrderEntity order) {
        String type = order.getProductType();
        if (ProductType.SUBSCRIPTION.equals(type)) {
            subscriptionFulfillment.rollback(order);
        } else if (ProductType.ITEM.equals(type)) {
            itemFulfillment.rollback(order);
        } else {
            log.warn("未知 productType, 无法回滚: orderId={}, type={}", order.getId(), type);
        }
    }
}
