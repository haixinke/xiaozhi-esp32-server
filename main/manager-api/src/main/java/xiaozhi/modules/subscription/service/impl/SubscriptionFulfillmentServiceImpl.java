package xiaozhi.modules.subscription.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.companion.dao.CompanionDao;
import xiaozhi.modules.companion.entity.CompanionEntity;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;
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
    private final CompanionDao companionDao;
    private final DeviceService deviceService;
    private final AgentService agentService;

    /** silver/gold 档对应的意图模型，需配合支持 function calling 的 LLM */
    private static final String INTENT_FUNCTION_CALL = "Intent_function_call";

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
        UserSubscriptionEntity active = subscriptionDao.selectOne(new QueryWrapper<UserSubscriptionEntity>()
                .eq("user_id", userId)
                .eq("status", SubscriptionStatus.ACTIVE.getValue())
                .gt("end_at", now)
                .orderByDesc("end_at")
                .last("LIMIT 1"));
        if (active != null) {
            if (active.getPlanId().equals(planId)) {
                // 同套餐续费 → 堆叠到旧订阅到期之后
                startAt = active.getEndAt();
            } else {
                // 升级 → 立即过期旧订阅，新订阅从 now 开始
                log.info("套餐升级 userId={}, oldPlanId={}, oldPlanCode={}, newPlanId={}, newPlanCode={}",
                        userId, active.getPlanId(), active.getPlanCode(), planId, plan.getPlanCode());
                active.setStatus(SubscriptionStatus.EXPIRED.getValue());
                active.setEndAt(now);
                subscriptionDao.updateById(active);
            }
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

        // 4) 按套餐更新 agent 配置（silver/gold 开启文字+语音记录；gold 额外开启 function_call 意图）
        applyPlanAgentConfig(userId, plan.getPlanCode());
    }

    /**
     * silver → chat_history_conf=2；gold → chat_history_conf=2 + intent_model_id=function_call。
     * 通过 userId → 伴侣 → 设备(mac) → agent 定位，单列更新，幂等。
     * 聊天服务在建连时拉取 agent 配置，故客户端需在档位变更后断开重连才能生效。
     */
    private void applyPlanAgentConfig(Long userId, String planCode) {
        if (!"silver".equals(planCode) && !"gold".equals(planCode)) {
            return;
        }
        // 用户可能有多个伴侣（按 device_id 唯一），取最近创建的一个并 LIMIT 1，避免 selectOne 多行抛异常导致整笔履约回滚
        CompanionEntity companion = companionDao.selectOne(
                new QueryWrapper<CompanionEntity>().eq("user_id", userId)
                        .orderByDesc("created_at").last("LIMIT 1"));
        if (companion == null || companion.getDeviceId() == null) {
            log.info("套餐 agent 配置：未找到伴侣，跳过 userId={}", userId);
            return;
        }
        DeviceEntity device = deviceService.getDeviceByMacAddress(companion.getDeviceId());
        if (device == null || device.getAgentId() == null) {
            log.info("套餐 agent 配置：未找到设备/agent，跳过 userId={}, mac={}", userId, companion.getDeviceId());
            return;
        }

        UpdateWrapper<AgentEntity> wrapper = new UpdateWrapper<AgentEntity>()
                .eq("id", device.getAgentId())
                .set("chat_history_conf", Constant.ChatHistoryConfEnum.RECORD_TEXT_AUDIO.getCode());
        if ("gold".equals(planCode)) {
            wrapper.set("intent_model_id", INTENT_FUNCTION_CALL);
        }
        agentService.update(null, wrapper);
        log.info("套餐 agent 配置已更新 userId={}, planCode={}, agentId={}", userId, planCode, device.getAgentId());
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
