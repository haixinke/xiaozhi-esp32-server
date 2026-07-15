package xiaozhi.modules.payment.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xiaozhi.modules.payment.dao.PaymentCallbackLogDao;
import xiaozhi.modules.payment.dao.PaymentOrderDao;
import xiaozhi.modules.payment.entity.PaymentCallbackLogEntity;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;
import xiaozhi.modules.payment.enums.OrderStatus;
import xiaozhi.modules.payment.service.FulfillmentDispatcher;
import xiaozhi.modules.payment.service.PaymentNotifyService;
import xiaozhi.modules.payment.service.PaymentOrderService;
import xiaozhi.modules.payment.wechat.WechatPayClient;

import java.util.Date;
import java.util.Map;

/**
 * 支付回调统一处理器：负责验签 → 解密 → 幂等 → 状态推进 → 履约调度。
 */
@Slf4j
// [暂时屏蔽微信支付功能] 取消注释以下注解即可恢复
// @Service
@RequiredArgsConstructor
public class PaymentNotifyServiceImpl implements PaymentNotifyService {

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_DUPLICATE = "DUPLICATE";
    private static final String RESULT_SIGN_FAIL = "SIGN_FAIL";
    private static final String RESULT_PROCESS_FAIL = "PROCESS_FAIL";

    private final WechatPayClient wechatPayClient;
    private final PaymentOrderService paymentOrderService;
    private final PaymentOrderDao orderDao;
    private final PaymentCallbackLogDao callbackLogDao;
    private final FulfillmentDispatcher fulfillmentDispatcher;
    private final PlatformTransactionManager transactionManager;

    /** 处理支付回调：验签、解密、幂等、状态推进、履约调度。 */
    @Override
    public String handleNotify(String channel, Map<String, String> headers, String body) {
        WechatPayClient.NotifyResult result;
        try {
            result = wechatPayClient.parseNotify(headers, body);
        } catch (Exception e) {
            log.error("回调解析异常 channel={}, body={}", channel, body, e);
            recordCallback(channel, null, null, headers, body, false, RESULT_SIGN_FAIL, e.getMessage());
            return RESULT_SIGN_FAIL;
        }
        if (!result.isValid()) {
            recordCallback(channel, result.getOutTradeNo(), result.getTransactionId(), headers, body,
                    false, RESULT_SIGN_FAIL, result.getMessage());
            return RESULT_SIGN_FAIL;
        }
        if (!result.isPaySuccess()) {
            recordCallback(channel, result.getOutTradeNo(), result.getTransactionId(), headers, body,
                    true, RESULT_PROCESS_FAIL, "not pay success");
            return RESULT_PROCESS_FAIL;
        }

        PaymentOrderEntity order = paymentOrderService.loadByOutTradeNo(result.getOutTradeNo());
        if (order == null) {
            recordCallback(channel, result.getOutTradeNo(), result.getTransactionId(), headers, body,
                    true, RESULT_PROCESS_FAIL, "order not found");
            return RESULT_PROCESS_FAIL;
        }

        // 已是 FULFILLED 才算真正完成；PAID 意味着上次履约异常中断，需要重试履约。
        if (order.getStatus() != null && order.getStatus() == OrderStatus.FULFILLED) {
            recordCallback(channel, order.getOutTradeNo(), result.getTransactionId(), headers, body,
                    true, RESULT_DUPLICATE, "order already fulfilled");
            return RESULT_SUCCESS;
        }

        // PAID 状态跳过微信重推的重复 markPaid，直接走履约重试分支
        boolean alreadyPaid = order.getStatus() != null && order.getStatus() == OrderStatus.PAID;

        if (!alreadyPaid && order.getStatus() != OrderStatus.PENDING) {
            recordCallback(channel, order.getOutTradeNo(), result.getTransactionId(), headers, body,
                    true, RESULT_PROCESS_FAIL, "invalid order status: " + order.getStatus());
            return RESULT_PROCESS_FAIL;
        }

        if (result.getAmountFen() != order.getAmountFen()) {
            recordCallback(channel, order.getOutTradeNo(), result.getTransactionId(), headers, body,
                    true, RESULT_PROCESS_FAIL, "amount mismatch: " + result.getAmountFen() + " vs " + order.getAmountFen());
            return RESULT_PROCESS_FAIL;
        }

        if (!alreadyPaid) {
            // 推进 PENDING -> PAID（行级原子）
            int updated = orderDao.markPaid(order.getId(), new Date(), result.getTransactionId());
            if (updated <= 0) {
                // 并发场景：另一个回调先成功了，重查一次状态决定下一步
                PaymentOrderEntity refresh = orderDao.selectById(order.getId());
                if (refresh != null && refresh.getStatus() != null && refresh.getStatus() == OrderStatus.FULFILLED) {
                    recordCallback(channel, order.getOutTradeNo(), result.getTransactionId(), headers, body,
                            true, RESULT_DUPLICATE, "race condition, already fulfilled");
                    return RESULT_SUCCESS;
                }
                // 其他并发只走到 PAID，继续尝试履约
            }
        }

        // 履约（事务内）
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                PaymentOrderEntity refreshed = orderDao.selectById(order.getId());
                fulfillmentDispatcher.dispatch(refreshed);
                orderDao.markFulfilled(refreshed.getId(), new Date());
            });
            recordCallback(channel, order.getOutTradeNo(), result.getTransactionId(), headers, body,
                    true, RESULT_SUCCESS, "fulfilled");
            return RESULT_SUCCESS;
        } catch (Exception e) {
            log.error("履约失败 outTradeNo={}", order.getOutTradeNo(), e);
            recordCallback(channel, order.getOutTradeNo(), result.getTransactionId(), headers, body,
                    true, RESULT_PROCESS_FAIL, e.getMessage());
            return RESULT_PROCESS_FAIL;
        }
    }

    private void recordCallback(String channel, String outTradeNo, String transactionId,
                                Map<String, String> headers, String body,
                                boolean signValid, String result, String remark) {
        try {
            PaymentCallbackLogEntity entry = new PaymentCallbackLogEntity();
            entry.setChannel(channel);
            entry.setOutTradeNo(outTradeNo);
            entry.setTransactionId(transactionId);
            entry.setRawHeaders(headers != null ? headers.toString() : null);
            entry.setRawBody(body);
            entry.setSignatureValid(signValid ? 1 : 0);
            entry.setProcessResult(result);
            entry.setRemark(remark);
            callbackLogDao.insert(entry);
        } catch (Exception e) {
            log.warn("写回调日志失败: {}", e.getMessage());
        }
    }
}
