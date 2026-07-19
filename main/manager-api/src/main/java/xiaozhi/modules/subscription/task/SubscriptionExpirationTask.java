package xiaozhi.modules.subscription.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.subscription.dao.UserSubscriptionDao;
import xiaozhi.modules.subscription.entity.UserSubscriptionEntity;
import xiaozhi.modules.subscription.enums.SubscriptionStatus;

import java.util.Date;
import java.util.List;

/**
 * 订阅到期维护定时任务：
 * <ol>
 *   <li>扫描 status=ACTIVE 且 end_at<=now 的订阅，置为 EXPIRED</li>
 *   <li>若用户此时已无任何生效订阅，把对应 agent 重置为初始态
 *       （chat_history_conf=0 + intent_model_id=Intent_nointent）</li>
 * </ol>
 * 续费会产生重叠的 ACTIVE 记录（旧记录不置 EXPIRED，新记录 startAt=旧endAt），
 * 故"用户已无生效订阅"校验必须存在，避免误清已续费用户的权益。
 * agent 配置由 xiaozhi-server 在建连时拉取，重置在用户下次召唤重连后生效。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpirationTask {

    /** 每次扫描最大处理条数 */
    private static final int BATCH_LIMIT = 100;

    private final UserSubscriptionDao subscriptionDao;
    private final AgentService agentService;
    private final PlatformTransactionManager transactionManager;

    /**
     * 每小时执行一次。采用 fixedDelay，确保上一次执行完毕后再等待 1 小时。
     * 订阅为天级周期，到期无需秒级精度；扫描间隔即"到期后仍可能拉到旧 premium 配置"的窗口上限（≤1 小时），
     * 对伴侣产品可接受，且 agent 配置在用户下次召唤重连时才生效，空闲会话另有不活动超时兜底。
     */
    // @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void expireSubscriptions() {
        List<UserSubscriptionEntity> expired = subscriptionDao.selectList(
                new QueryWrapper<UserSubscriptionEntity>()
                        .eq("status", SubscriptionStatus.ACTIVE.getValue())
                        .le("end_at", new Date())
                        .last("LIMIT " + BATCH_LIMIT));
        if (expired.isEmpty()) {
            return;
        }
        log.info("扫描到 {} 笔到期订阅，开始处理", expired.size());

        for (UserSubscriptionEntity sub : expired) {
            try {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.executeWithoutResult(status -> processOne(sub.getId()));
            } catch (Exception e) {
                log.error("处理到期订阅失败 subId={}, userId={}: {}",
                        sub.getId(), sub.getUserId(), e.getMessage());
            }
        }
        log.info("到期订阅处理完成：本轮扫描 {} 笔", expired.size());
    }

    /**
     * 单条事务处理：重查状态 → 置 EXPIRED → 校验无其他生效订阅 → 重置 agent。
     */
    private void processOne(Long subId) {
        UserSubscriptionEntity refreshed = subscriptionDao.selectById(subId);
        if (refreshed == null
                || refreshed.getStatus() == null
                || refreshed.getStatus() != SubscriptionStatus.ACTIVE.getValue()) {
            // 已被其它流程处理（如升级时置为 EXPIRED，或上一轮已处理）
            return;
        }

        // 1) 置为到期
        refreshed.setStatus(SubscriptionStatus.EXPIRED.getValue());
        subscriptionDao.updateById(refreshed);

        // 2) 校验用户是否还有其他生效订阅（续费重叠 ACTIVE 记录场景）
        Long remaining = subscriptionDao.selectCount(
                new QueryWrapper<UserSubscriptionEntity>()
                        .eq("user_id", refreshed.getUserId())
                        .eq("status", SubscriptionStatus.ACTIVE.getValue())
                        .gt("end_at", new Date()));
        if (remaining != null && remaining > 0) {
            log.info("订阅到期但用户仍有生效订阅，跳过 agent 重置 userId={}, subId={}",
                    refreshed.getUserId(), refreshed.getId());
            return;
        }

        // 3) 无生效订阅 → 重置 agent 为初始态
        agentService.resetToInitial(refreshed.getUserId());
    }
}
