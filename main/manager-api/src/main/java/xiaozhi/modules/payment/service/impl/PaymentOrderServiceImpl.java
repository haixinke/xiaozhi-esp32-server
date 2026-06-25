package xiaozhi.modules.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.item.entity.ItemSkuEntity;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.payment.dao.PaymentOrderDao;
import xiaozhi.modules.payment.dto.CreateOrderDTO;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;
import xiaozhi.modules.payment.enums.OrderStatus;
import xiaozhi.modules.payment.enums.PayChannel;
import xiaozhi.modules.payment.enums.ProductType;
import xiaozhi.modules.payment.service.FulfillmentDispatcher;
import xiaozhi.modules.payment.service.PaymentOrderService;
import xiaozhi.modules.payment.vo.OrderVO;
import xiaozhi.modules.payment.vo.PrepayVO;
import xiaozhi.modules.payment.wechat.WechatPayClient;
import xiaozhi.modules.payment.wechat.WechatPayClient.QueryResult;
import xiaozhi.modules.subscription.dao.SubscriptionPlanDao;
import xiaozhi.modules.subscription.dao.UserSubscriptionDao;
import xiaozhi.modules.subscription.entity.SubscriptionPlanEntity;
import xiaozhi.modules.subscription.entity.UserSubscriptionEntity;
import xiaozhi.modules.subscription.enums.SubscriptionStatus;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderServiceImpl implements PaymentOrderService {

    /** 订单超时时间：15 分钟 */
    private static final long ORDER_TTL_MS = 15 * 60 * 1000L;

    /** 单用户同商品防重下单时间窗口：5 秒 */
    private static final long DEDUP_LOCK_SECONDS = 5L;

    /** 下单接口最大数量上限（防止前端篡改造成超大金额） */
    private static final int MAX_QUANTITY = 99;

    private final PaymentOrderDao orderDao;
    private final SubscriptionPlanDao planDao;
    private final UserSubscriptionDao subscriptionDao;
    private final ItemService itemService;
    private final WechatPayClient wechatPayClient;
    private final WechatUserDao wechatUserDao;
    private final StringRedisTemplate stringRedisTemplate;
    private final PlatformTransactionManager transactionManager;
    private final FulfillmentDispatcher fulfillmentDispatcher;

    /** 创建支付订单并调用支付通道预下单。 */
    @Override
    public PrepayVO createOrder(Long userId, CreateOrderDTO dto, String clientIp) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        String type = dto.getProductType();
        Integer quantity = sanitizeQuantity(dto.getQuantity(), type);

        // 1) Redis 防重下单锁：同一用户、同一 productType+refId、5 秒内只允许下一单
        if (!acquireDedupLock(userId, type, dto.getProductRefId())) {
            throw new RenException(ErrorCode.PAY_ORDER_DUPLICATE);
        }

        try {
            // 2) 写订单（仅本地事务，不含外部 IO）
            TransactionTemplate insertTx = new TransactionTemplate(transactionManager);
            PaymentOrderEntity order = insertTx.execute(status -> persistPendingOrder(userId, dto, quantity, type, clientIp));

            // 3) 外部 IO：调用支付通道预下单（事务外）
            String openid = lookupOpenid(userId);
            String description = order.getProductSnapshot();
            // 简短描述：优先用快照中的 name 字段
            try {
                cn.hutool.json.JSONObject snap = JSONUtil.parseObj(order.getProductSnapshot());
                String name = snap.getStr("planName");
                if (StringUtils.isBlank(name)) name = snap.getStr("skuName");
                description = (ProductType.SUBSCRIPTION.equals(type) ? "订阅-" : "道具-")
                        + (StringUtils.isBlank(name) ? type : name)
                        + (quantity > 1 ? "×" + quantity : "");
            } catch (Exception ignore) {
                // 快照解析失败不影响下单
            }

            WechatPayClient.PrepayRequest req = new WechatPayClient.PrepayRequest();
            req.setOutTradeNo(order.getOutTradeNo());
            req.setAmountFen(order.getAmountFen());
            req.setDescription(description);
            req.setOpenid(openid);
            WechatPayClient.PrepayResult prepay;
            try {
                prepay = wechatPayClient.jsapiPrepay(req);
            } catch (Exception e) {
                log.error("调用支付通道下单失败 outTradeNo={}", order.getOutTradeNo(), e);
                // 标记订单失败原因，超时任务后续会关单；此处不强行回退状态以保留审计
                throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE);
            }

            // 4) 单独事务回写 prepay_id
            if (prepay.getPrepayId() != null) {
                TransactionTemplate updateTx = new TransactionTemplate(transactionManager);
                String pid = prepay.getPrepayId();
                updateTx.executeWithoutResult(status -> attachPrepayId(order.getId(), pid));
            }

            PrepayVO vo = new PrepayVO();
            vo.setOutTradeNo(order.getOutTradeNo());
            vo.setAmountFen(order.getAmountFen());
            vo.setPayChannel(order.getPayChannel());
            vo.setPrepayParams(prepay.getJsapiParams());
            return vo;
        } finally {
            // 锁不主动释放，保留 5 秒窗口
        }
    }

    /**
     * 仅 INSERT 订单；由外层 TransactionTemplate 包裹。
     */
    private PaymentOrderEntity persistPendingOrder(Long userId, CreateOrderDTO dto,
                                                   Integer quantity, String type, String clientIp) {
        long amountFen;
        String snapshot;
        if (ProductType.SUBSCRIPTION.equals(type)) {
            SubscriptionPlanEntity plan = planDao.selectById(dto.getProductRefId());
            if (plan == null || plan.getStatus() == null || plan.getStatus() != 1) {
                throw new RenException(ErrorCode.SUBSCRIPTION_PLAN_NOT_FOUND);
            }
            amountFen = plan.getPromoPriceFen() != null ? plan.getPromoPriceFen() : plan.getPriceFen();

            // 升级折算：若用户有生效中低级套餐，剩余时间折算抵扣
            UserSubscriptionEntity activeSub = subscriptionDao.selectOne(new QueryWrapper<UserSubscriptionEntity>()
                    .eq("user_id", userId)
                    .eq("status", SubscriptionStatus.ACTIVE.getValue())
                    .gt("end_at", new Date())
                    .orderByDesc("end_at")
                    .last("LIMIT 1"));
            if (activeSub != null && !activeSub.getPlanId().equals(plan.getId())) {
                SubscriptionPlanEntity oldPlan = planDao.selectById(activeSub.getPlanId());
                if (oldPlan != null && oldPlan.getSort() < plan.getSort()) {
                    long remainingMs = activeSub.getEndAt().getTime() - System.currentTimeMillis();
                    if (remainingMs > 0) {
                        long oldPlanDurationMs = oldPlan.getDurationDays() * 24L * 60 * 60 * 1000;
                        long oldPriceFen = oldPlan.getPromoPriceFen() != null ? oldPlan.getPromoPriceFen() : oldPlan.getPriceFen();
                        long creditFen = remainingMs * oldPriceFen / oldPlanDurationMs;
                        amountFen = Math.max(1, amountFen - creditFen);
                        log.info("升级折算 userId={}, oldPlan={}, newPlan={}, creditFen={}, finalFen={}",
                                userId, oldPlan.getPlanCode(), plan.getPlanCode(), creditFen, amountFen);
                    }
                }
            }

            snapshot = JSONUtil.toJsonStr(plan);
        } else if (ProductType.ITEM.equals(type)) {
            ItemSkuEntity sku = itemService.getBySkuId(dto.getProductRefId());
            if (sku.getStatus() == null || sku.getStatus() != 1) {
                throw new RenException(ErrorCode.ITEM_SKU_NOT_FOUND);
            }
            long unit = sku.getPromoPriceFen() != null ? sku.getPromoPriceFen() : sku.getPriceFen();
            amountFen = Math.multiplyExact(unit, quantity);
            snapshot = JSONUtil.toJsonStr(sku);
        } else {
            throw new RenException(ErrorCode.PAY_PRODUCT_INVALID);
        }

        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setOutTradeNo(generateOutTradeNo());
        order.setUserId(userId);
        order.setProductType(type);
        order.setProductRefId(dto.getProductRefId());
        order.setProductSnapshot(snapshot);
        order.setQuantity(quantity);
        order.setAmountFen(amountFen);
        order.setPayChannel(wechatPayClient.isMockMode() ? PayChannel.MOCK : PayChannel.WECHAT_JSAPI);
        order.setStatus(OrderStatus.PENDING);
        order.setExpireAt(new Date(System.currentTimeMillis() + ORDER_TTL_MS));
        order.setRefundAmountFen(0L);
        order.setClientIp(clientIp);
        orderDao.insert(order);
        return order;
    }

    /** 回写 prepay_id；由外层 TransactionTemplate 包裹。 */
    private void attachPrepayId(Long orderId, String prepayId) {
        PaymentOrderEntity update = new PaymentOrderEntity();
        update.setId(orderId);
        update.setPrepayId(prepayId);
        orderDao.updateById(update);
    }

    /** 根据商户订单号查询订单。 */
    @Override
    public OrderVO queryByOutTradeNo(Long userId, String outTradeNo) {
        PaymentOrderEntity entity = loadByOutTradeNo(outTradeNo);
        if (entity == null) {
            throw new RenException(ErrorCode.PAY_ORDER_NOT_FOUND);
        }
        if (!entity.getUserId().equals(userId)) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }
        return OrderVO.toVO(entity);
    }

    /** 查询用户订单列表（最近100条）。 */
    @Override
    public List<OrderVO> myOrders(Long userId) {
        QueryWrapper<PaymentOrderEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at").last("LIMIT 100");
        List<PaymentOrderEntity> list = orderDao.selectList(wrapper);
        List<OrderVO> result = new ArrayList<>(list.size());
        for (PaymentOrderEntity e : list) {
            result.add(OrderVO.toVO(e));
        }
        return result;
    }

    /** 取消未支付订单。 */
    @Override
    public void cancel(Long userId, String outTradeNo) {
        PaymentOrderEntity entity = loadByOutTradeNo(outTradeNo);
        if (entity == null) {
            throw new RenException(ErrorCode.PAY_ORDER_NOT_FOUND);
        }
        if (!entity.getUserId().equals(userId)) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }
        // 原子条件更新：仅允许从 PENDING → CANCELLED，避免与回调并发覆盖 PAID
        int affected = orderDao.markCancelled(entity.getId());
        if (affected <= 0) {
            throw new RenException(ErrorCode.PAY_ORDER_STATUS_INVALID);
        }
        try {
            wechatPayClient.closeOrder(outTradeNo);
        } catch (Exception e) {
            log.warn("关闭微信订单失败 outTradeNo={}: {}", outTradeNo, e.getMessage());
        }
    }

    /** 根据商户订单号加载订单实体。 */
    @Override
    public PaymentOrderEntity loadByOutTradeNo(String outTradeNo) {
        if (StringUtils.isBlank(outTradeNo)) {
            return null;
        }
        return orderDao.selectOne(new QueryWrapper<PaymentOrderEntity>().eq("out_trade_no", outTradeNo));
    }

    /**
     * 主动查询微信支付订单状态并触发履约。
     * 用于回调失败时的补偿：小程序前端触发或定时任务兜底。
     */
    @Override
    public void queryAndFulfill(String outTradeNo) {
        PaymentOrderEntity order = loadOrderForQuery(outTradeNo);
        QueryResult queryResult = queryWechatPay(outTradeNo);
        if (!queryResult.isPaid()) {
            log.info("主动查单确认订单未支付 outTradeNo={}", outTradeNo);
            return;
        }
        validateQueryAmount(queryResult, order.getAmountFen());
        advanceToPaidIfNeeded(order, queryResult);
        fulfillIfNeeded(order);
        log.info("主动查单履约成功 outTradeNo={}", outTradeNo);
    }

    /**
     * 加载订单并校验是否允许查单履约。
     */
    private PaymentOrderEntity loadOrderForQuery(String outTradeNo) {
        PaymentOrderEntity order = loadByOutTradeNo(outTradeNo);
        if (order == null) {
            log.warn("主动查单失败，订单不存在 outTradeNo={}", outTradeNo);
            throw new RenException(ErrorCode.PAY_ORDER_NOT_FOUND);
        }

        Integer status = order.getStatus();
        if (status != null && status == OrderStatus.FULFILLED) {
            log.info("主动查单跳过，订单已履约 outTradeNo={}", outTradeNo);
            return order;
        }
        if (status == null || (status != OrderStatus.PENDING && status != OrderStatus.PAID)) {
            log.warn("主动查单跳过，订单状态不允许 outTradeNo={}, status={}", outTradeNo, status);
            throw new RenException(ErrorCode.PAY_ORDER_STATUS_INVALID);
        }
        return order;
    }

    /**
     * 调用微信支付查单接口。
     */
    private QueryResult queryWechatPay(String outTradeNo) {
        QueryResult queryResult = wechatPayClient.queryOrder(outTradeNo);
        if (!queryResult.isSuccess()) {
            log.error("主动查单调用微信支付失败 outTradeNo={}, message={}", outTradeNo, queryResult.getMessage());
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE);
        }
        return queryResult;
    }

    /**
     * 校验微信支付返回金额与订单金额一致。
     */
    private void validateQueryAmount(QueryResult queryResult, long expectedAmountFen) {
        if (queryResult.getAmountFen() != expectedAmountFen) {
            log.error("主动查单金额不一致 wxAmount={}, orderAmount={}",
                    queryResult.getAmountFen(), expectedAmountFen);
            throw new RenException(ErrorCode.PAY_ORDER_STATUS_INVALID);
        }
    }

    /**
     * 如果订单还是 PENDING，原子地推进到 PAID；已是 PAID 则跳过。
     * 处理并发：markPaid 失败时重查状态，确认是 FULFILLED 则直接返回，是 PAID 则继续。
     */
    private void advanceToPaidIfNeeded(PaymentOrderEntity order, QueryResult queryResult) {
        Integer status = order.getStatus();
        if (status != null && status == OrderStatus.PAID) {
            return;
        }

        int updated = orderDao.markPaid(order.getId(), new Date(), queryResult.getTransactionId());
        if (updated <= 0) {
            PaymentOrderEntity refreshed = orderDao.selectById(order.getId());
            Integer refreshedStatus = refreshed != null ? refreshed.getStatus() : null;
            if (refreshedStatus != null && refreshedStatus == OrderStatus.FULFILLED) {
                log.info("主动查单跳过，并发线程已完成履约 outTradeNo={}", order.getOutTradeNo());
                return;
            }
            if (refreshedStatus == null || refreshedStatus != OrderStatus.PAID) {
                log.warn("主动查单 markPaid 失败，订单状态异常 outTradeNo={}", order.getOutTradeNo());
                throw new RenException(ErrorCode.PAY_ORDER_STATUS_INVALID);
            }
        }
    }

    /**
     * 在事务内执行履约，并原子地推进到 FULFILLED。
     */
    private void fulfillIfNeeded(PaymentOrderEntity order) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            PaymentOrderEntity refreshed = orderDao.selectById(order.getId());
            Integer refreshedStatus = refreshed != null ? refreshed.getStatus() : null;
            if (refreshedStatus != null && refreshedStatus == OrderStatus.FULFILLED) {
                return;
            }
            fulfillmentDispatcher.dispatch(refreshed);
            orderDao.markFulfilled(refreshed.getId(), new Date());
        });
    }

    /**
     * 生成商户单号：PG + 时间戳 + UUID 简短版（22位无歧义字符）。
     */
    private String generateOutTradeNo() {
        // 商户单号长度上限 32，这里固定 30 字符以内：PG + 14 位时间 + 14 位 UUID
        String time = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String rand = IdUtil.fastSimpleUUID().substring(0, 14);
        return "PG" + time + rand;
    }

    private Integer sanitizeQuantity(Integer raw, String type) {
        int q = raw == null || raw <= 0 ? 1 : raw;
        if (ProductType.SUBSCRIPTION.equals(type)) {
            return 1;
        }
        if (q > MAX_QUANTITY) {
            q = MAX_QUANTITY;
        }
        return q;
    }

    /**
     * SETNX 实现的同用户同商品 5 秒下单防重锁。
     */
    private boolean acquireDedupLock(Long userId, String productType, Long refId) {
        String key = "pay:lock:" + userId + ":" + productType + ":" + refId;
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", DEDUP_LOCK_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 查询用户在 ai_wechat_user 中的 openid。
     * Mock 模式下若未绑定则返回占位值，便于本地联调。
     */
    private String lookupOpenid(Long userId) {
        WechatUserEntity wechatUser = wechatUserDao.selectOne(
                new QueryWrapper<WechatUserEntity>().eq("user_id", userId));
        if (wechatUser == null || StringUtils.isBlank(wechatUser.getOpenid())) {
            if (wechatPayClient.isMockMode()) {
                return "MOCK_OPENID_" + userId;
            }
            throw new RenException(ErrorCode.PAY_OPENID_REQUIRED);
        }
        return wechatUser.getOpenid();
    }
}
