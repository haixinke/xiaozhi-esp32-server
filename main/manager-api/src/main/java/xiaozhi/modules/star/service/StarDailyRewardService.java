package xiaozhi.modules.star.service;

import java.time.LocalDate;

import xiaozhi.modules.star.enums.DailyRewardSourceType;
import xiaozhi.modules.star.vo.DailyRewardResult;

/**
 * 每日星星奖励能力：统一负责用户与奖励来源的当天领取资格、并发防重及钱包入账。
 * 钱包（StarService）只承载资金变动，不含连续规则。
 * 日期由后端按 Asia/Shanghai 自然日确定，不接受客户端日期。
 *
 * 金额策略按来源区分：小纸条（DAILY_NOTE）走本接口的连续规则；
 * 游戏（GAME）奖励金额为游戏配置固定值，由 GameStarRewardService 经锁内决策进入，
 * 本接口对游戏来源直接拒绝，防止误用连续规则导致金额随天数漂移。
 */
public interface StarDailyRewardService {

    /**
     * 领取小纸条当日奖励（幂等）。每用户每北京自然日最多一份：
     * 首次成功领取 11 颗，昨天有成功奖励则 min(昨天数量+1, 17)，否则 11。
     * 重复或并发调用返回首次结算的数量与流水，不重复入账。
     *
     * @param userId     用户ID（服务端可信身份）
     * @param sourceType 奖励来源类型，仅支持 DAILY_NOTE（大小写不敏感，落库为规范大写值）；
     *                   游戏奖励请使用 GameStarRewardService
     * @param sourceKey  来源标识（DAILY_NOTE 固定传 DAILY_NOTE，长度≤64，前后空白会被裁剪）
     * @return 结算结果：awarded 区分本次新入账与已结算；已结算返回原金额与流水
     */
    DailyRewardResult claimDailyReward(Long userId, String sourceType, String sourceKey);

    /**
     * 结算决策：在确认当天未领取后被调用一次。award(amount) 按金额入账；
     * skip() 表示本次无需星星变动（如免费/消费型游戏），
     * 不写奖励记录、不调钱包，也不占当天奖励资格。
     * 决策结果不持久化：跳过后当日后续请求仍会重新决策，保证免费游戏改奖励型后可领。
     */
    record ClaimDecision(boolean award, long amount) {
        public static ClaimDecision award(long amount) {
            return new ClaimDecision(true, amount);
        }

        public static ClaimDecision skip() {
            return new ClaimDecision(false, 0);
        }
    }

    /**
     * 结算决策回调：在游戏奖励等按配置定额的入口中读取当前配置并做防御性校验；
     * 已领取分支永远不触发配置读取，保证重试返回原结果。
     */
    @FunctionalInterface
    interface ClaimDecider {
        ClaimDecision decide(Long userId, String sourceType, String sourceKey, LocalDate rewardDate);
    }

    /**
     * 领取核心：先查当天记录（已领取直接返回原快照，不调用决策），
     * 未领取才决策；award 时建户加锁、复查后入账并落奖励记录，skip 直接返回不发生任何写入。
     * 供游戏奖励等按配置定额的入口复用资格与入账机制。
     *
     * @param userId     用户ID（服务端可信身份）
     * @param sourceType 奖励来源类型（如 GAME）
     * @param sourceKey  来源标识（如游戏类型 code，长度≤64）
     * @param decider    结算决策回调，仅在当天未领取时执行一次
     * @return 结算结果：awarded / alreadyClaimed / skipped 三种语义
     */
    DailyRewardResult claimWithDecision(Long userId, DailyRewardSourceType sourceType, String sourceKey,
            ClaimDecider decider);
}
