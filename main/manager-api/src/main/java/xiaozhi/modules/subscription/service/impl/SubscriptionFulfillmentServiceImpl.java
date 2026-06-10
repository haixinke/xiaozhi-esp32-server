package xiaozhi.modules.subscription.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.item.enums.ItemGrantSource;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;
import xiaozhi.modules.subscription.dao.SubscriptionPlanDao;
import xiaozhi.modules.subscription.dao.UserSubscriptionDao;
import xiaozhi.modules.subscription.entity.SubscriptionPlanEntity;
import xiaozhi.modules.subscription.entity.UserSubscriptionEntity;
import xiaozhi.modules.subscription.enums.SubscriptionStatus;
import xiaozhi.modules.subscription.service.SubscriptionFulfillmentService;
import xiaozhi.modules.subscription.vo.SubscriptionPlanVO;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionFulfillmentServiceImpl implements SubscriptionFulfillmentService {

    private final SubscriptionPlanDao planDao;
    private final UserSubscriptionDao subscriptionDao;
    private final ItemService itemService;

    /** 订阅履约：创建订阅记录、堆叠生效时间、发放附赠道具 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fulfill(PaymentOrderEntity order) {
        Long userId = order.getUserId();
        Long planId = order.getProductRefId();
        SubscriptionPlanEntity plan = planDao.selectById(planId);
        if (plan == null) {
            throw new RenException(ErrorCode.SUBSCRIPTION_PLAN_NOT_FOUND);
        }

        // 1) 计算 startAt：若已有生效中订阅则拼接到旧 endAt 之后
        Date now = new Date();
        Date startAt = now;
        UserSubscriptionEntity active = subscriptionDao.selectOne(new QueryWrapper<UserSubscriptionEntity>()
                .eq("user_id", userId)
                .eq("status", SubscriptionStatus.ACTIVE.getValue())
                .gt("end_at", now)
                .orderByDesc("end_at")
                .last("LIMIT 1"));
        if (active != null) {
            startAt = active.getEndAt();
        }
        Date endAt = new Date(startAt.getTime() + plan.getDurationDays() * 24L * 60 * 60 * 1000);

        // 2) 写订阅记录（基于 uk_order 实现幂等）
        UserSubscriptionEntity entity = new UserSubscriptionEntity();
        entity.setUserId(userId);
        entity.setPlanId(planId);
        entity.setPlanCode(plan.getPlanCode());
        entity.setOrderId(order.getId());
        entity.setFeaturesSnapshot(plan.getFeatures());
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        entity.setStatus(SubscriptionStatus.ACTIVE.getValue());
        try {
            subscriptionDao.insert(entity);
        } catch (DuplicateKeyException dup) {
            log.info("订阅已履约过，跳过: orderId={}", order.getId());
            return;
        }

        // 3) 发放 bonus_items
        List<SubscriptionPlanVO.BonusItem> bonus = SubscriptionServiceImpl.parseBonusItems(plan.getBonusItems());
        for (SubscriptionPlanVO.BonusItem b : bonus) {
            try {
                itemService.grant(userId, b.getSkuCode(), b.getCount(),
                        ItemGrantSource.SUBSCRIPTION_BONUS, order.getOutTradeNo());
            } catch (Exception e) {
                log.warn("赠送道具失败: userId={}, sku={}, count={}, err={}",
                        userId, b.getSkuCode(), b.getCount(), e.getMessage());
            }
        }
        log.info("订阅履约完成 userId={}, planCode={}, startAt={}, endAt={}",
                userId, plan.getPlanCode(), startAt, endAt);
    }

    /** 订阅退款回滚：将订阅状态置为已退款 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(PaymentOrderEntity order) {
        UserSubscriptionEntity entity = subscriptionDao.selectOne(
                new QueryWrapper<UserSubscriptionEntity>().eq("order_id", order.getId()));
        if (entity == null) {
            log.info("退款回滚未找到订阅: orderId={}", order.getId());
            return;
        }
        entity.setStatus(SubscriptionStatus.REFUNDED.getValue());
        subscriptionDao.updateById(entity);
        log.info("订阅已作废(退款): orderId={}, subId={}", order.getId(), entity.getId());
    }
}
