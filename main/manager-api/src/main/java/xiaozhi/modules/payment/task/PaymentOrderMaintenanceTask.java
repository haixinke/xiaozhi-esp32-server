//package xiaozhi.modules.payment.task;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.PlatformTransactionManager;
//import org.springframework.transaction.support.TransactionTemplate;
//import xiaozhi.modules.payment.dao.PaymentOrderDao;
//import xiaozhi.modules.payment.entity.PaymentOrderEntity;
//import xiaozhi.modules.payment.service.FulfillmentDispatcher;
//import xiaozhi.modules.payment.service.PaymentOrderService;
//
//import java.util.Date;
//import java.util.List;
//
///**
// * 支付订单维护定时任务：
// * <ol>
// *   <li>重试已支付但履约超时的订单（PAID 状态且 paid_at 超过 5 分钟）</li>
// *   <li>主动查询已创建一段时间的 PENDING 订单，若已支付则推进履约（回调丢失兜底）</li>
// *   <li>关闭已过期的待支付订单（PENDING 状态且 expire_at 已过期）</li>
// * </ol>
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class PaymentOrderMaintenanceTask {
//
//    /** PAID 状态超过此时间（毫秒）未履约则重试：5 分钟 */
//    private static final long FULFILL_RETRY_THRESHOLD_MS = 5 * 60 * 1000L;
//
//    /** 每次扫描最大处理订单数 */
//    private static final int BATCH_LIMIT = 50;
//
//    /** 订单创建多久后才开始主动查单（毫秒）：3 分钟 */
//    private static final long RECONCILE_MIN_AGE_MS = 3 * 60 * 1000L;
//
//    private final PaymentOrderDao orderDao;
//    private final FulfillmentDispatcher fulfillmentDispatcher;
//    private final PaymentOrderService paymentOrderService;
//    private final PlatformTransactionManager transactionManager;
//
//    /**
//     * 每 5 分钟执行一次，扫描 PAID 状态超时的订单并重试履约。
//     * 采用 fixedDelay，确保上一次执行完毕后再等待 5 分钟。
//     */
//    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
//    public void retryPaidOrders() {
//        Date threshold = new Date(System.currentTimeMillis() - FULFILL_RETRY_THRESHOLD_MS);
//        List<PaymentOrderEntity> orders = orderDao.findPaidBefore(threshold, BATCH_LIMIT);
//        if (orders.isEmpty()) {
//            return;
//        }
//        log.info("扫描到 {} 笔 PAID 超时订单，开始重试履约", orders.size());
//        int success = 0;
//        int fail = 0;
//        for (PaymentOrderEntity order : orders) {
//            try {
//                TransactionTemplate tx = new TransactionTemplate(transactionManager);
//                tx.executeWithoutResult(status -> {
//                    PaymentOrderEntity refreshed = orderDao.selectById(order.getId());
//                    if (refreshed == null || refreshed.getStatus() == null || refreshed.getStatus() != 1) {
//                        return;
//                    }
//                    fulfillmentDispatcher.dispatch(refreshed);
//                    orderDao.markFulfilled(refreshed.getId(), new Date());
//                });
//                success++;
//            } catch (Exception e) {
//                fail++;
//                log.error("履约重试失败 orderId={}, outTradeNo={}: {}",
//                        order.getId(), order.getOutTradeNo(), e.getMessage());
//            }
//        }
//        log.info("PAID 订单履约重试完成：成功 {}，失败 {}", success, fail);
//    }
//
//    /**
//     * 每 5 分钟执行一次，主动查询已创建一段时间的 PENDING 订单。
//     * 若微信支付状态为已支付，则推进到 PAID 并履约，防止回调丢失导致订单错误超时。
//     */
//    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 45 * 1000)
//    public void reconcilePendingOrders() {
//        Date now = new Date();
//        Date threshold = new Date(now.getTime() - RECONCILE_MIN_AGE_MS);
//        List<PaymentOrderEntity> orders = orderDao.findPendingForReconcile(threshold, now, BATCH_LIMIT);
//        if (orders.isEmpty()) {
//            return;
//        }
//        log.info("扫描到 {} 笔待查单 PENDING 订单，开始主动查询微信支付", orders.size());
//        int success = 0;
//        int fail = 0;
//        for (PaymentOrderEntity order : orders) {
//            try {
//                paymentOrderService.queryAndFulfill(order.getOutTradeNo());
//                success++;
//            } catch (Exception e) {
//                // queryAndFulfill 内部已记录详细日志；
//                // 这里只统计失败数量，单个失败不阻断批量处理
//                fail++;
//                log.warn("主动查单失败 orderId={}, outTradeNo={}: {}",
//                        order.getId(), order.getOutTradeNo(), e.getMessage());
//            }
//        }
//        log.info("PENDING 订单主动查单完成：成功 {}，失败 {}", success, fail);
//    }
//
//    /**
//     * 每 5 分钟执行一次，关闭过期的待支付订单。
//     */
//    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 30 * 1000)
//    public void expirePendingOrders() {
//        List<PaymentOrderEntity> orders = orderDao.findExpiredPending(new Date(), BATCH_LIMIT);
//        if (orders.isEmpty()) {
//            return;
//        }
//        log.info("扫描到 {} 笔过期 PENDING 订单，开始关闭", orders.size());
//        int count = 0;
//        for (PaymentOrderEntity order : orders) {
//            try {
//                int affected = orderDao.markExpired(order.getId());
//                if (affected > 0) {
//                    count++;
//                }
//            } catch (Exception e) {
//                log.warn("关闭过期订单失败 orderId={}: {}", order.getId(), e.getMessage());
//            }
//        }
//        if (count > 0) {
//            log.info("已关闭 {} 笔过期 PENDING 订单", count);
//        }
//    }
//}
