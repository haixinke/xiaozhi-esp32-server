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
import xiaozhi.modules.agent.service.AgentService;
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
    private final AgentService agentService;

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

        // 1) 计算 startAt：区分升级与续费
        Date now = new Date();
        Date startAt = now;
        // 查询用户所有生效中的订阅（含堆叠续费产生的多条记录）
        List<UserSubscriptionEntity> allActive = subscriptionDao.selectList(
                new QueryWrapper<UserSubscriptionEntity>()
                        .eq("user_id", userId)
                        .eq("status", SubscriptionStatus.ACTIVE.getValue())
                        .gt("end_at", now)
                        .orderByDesc("end_at"));
        if (!allActive.isEmpty()) {
            UserSubscriptionEntity latest = allActive.get(0); // end_at 最远的那条
            if (latest.getPlanCode().equals(plan.getPlanCode())) {
                // 同档位续费（含不同周期）→ 堆叠到最远到期时间之后
                startAt = latest.getEndAt();
            } else {
                // 跨档位升级 → 立即过期【所有】旧订阅，新订阅从 now 开始
                log.info("套餐升级 userId={}, oldPlanCode={}, newPlanCode={}, 过期旧订阅{}条",
                        userId, latest.getPlanCode(), plan.getPlanCode(), allActive.size());
                for (UserSubscriptionEntity old : allActive) {
                    old.setStatus(SubscriptionStatus.EXPIRED.getValue());
                    old.setEndAt(now);
                    subscriptionDao.updateById(old);
                }
            }
        }
        Date endAt = new Date(startAt.getTime() + plan.getDurationDays() * 24L * 60 * 60 * 1000);

        // 升级折抵赠送天数：从订单快照中读取
        int bonusDays = 0;
        try {
            cn.hutool.json.JSONObject snap = cn.hutool.json.JSONUtil.parseObj(order.getProductSnapshot());
            bonusDays = snap.getInt("_bonusDays", 0);
        } catch (Exception ignore) {
        }
        if (bonusDays > 0) {
            endAt = new Date(endAt.getTime() + bonusDays * 24L * 60 * 60 * 1000);
            log.info("升级赠送天数 userId={}, bonusDays={}, newEndAt={}", userId, bonusDays, endAt);
        }

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

        // 4) 按套餐更新 agent 配置（silver/gold 开启文字+语音记录；gold 额外开启 function_call 意图）
        agentService.applySubscriptionConfig(userId, plan.getPlanCode());
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
