package xiaozhi.modules.game.vo;

import lombok.Data;

/**
 * 游戏每日首次完成奖励结果。仅供服务端内部方法返回，不对客户端暴露。
 * 明确区分三种语义：本次新发奖 / 当日已领取 / 无需星星变动（免费或消费型游戏）。
 * rewardedStars 与 newRewardedStars 分离，使"原奖励数量"与"本次新增数量（可为 0）"
 * 在结果中可被明确区分（spec：必须区分原奖励数量与本次新增数量）。
 */
@Data
public class GameStarRewardResult {

    /**
     * 奖励结果语义
     */
    public enum Status {
        /** 本次新发奖 */
        NEW_REWARD,
        /** 当日已领取：rewardedStars 与 transactionId 为首次结算原值，本次新增数量为零 */
        ALREADY_REWARDED,
        /** 无需星星变动：免费双零游戏或消费型游戏调用奖励入口 */
        NO_REWARD
    }

    /** 奖励结果语义 */
    private Status status;

    /** 首次结算的原奖励星星数；NO_REWARD 时为 0 */
    private long rewardedStars;

    /** 本次新增的奖励星星数；仅 NEW_REWARD 时为奖励金额，其余为 0 */
    private long newRewardedStars;

    /** 关联的星星入账流水 ID；NO_REWARD 时为 null */
    private Long transactionId;

    public static GameStarRewardResult newReward(long rewardedStars, Long transactionId) {
        GameStarRewardResult r = new GameStarRewardResult();
        r.status = Status.NEW_REWARD;
        r.rewardedStars = rewardedStars;
        r.newRewardedStars = rewardedStars;
        r.transactionId = transactionId;
        return r;
    }

    public static GameStarRewardResult alreadyRewarded(long rewardedStars, Long transactionId) {
        GameStarRewardResult r = new GameStarRewardResult();
        r.status = Status.ALREADY_REWARDED;
        r.rewardedStars = rewardedStars;
        r.newRewardedStars = 0;
        r.transactionId = transactionId;
        return r;
    }

    public static GameStarRewardResult noReward() {
        GameStarRewardResult r = new GameStarRewardResult();
        r.status = Status.NO_REWARD;
        r.rewardedStars = 0;
        r.newRewardedStars = 0;
        r.transactionId = null;
        return r;
    }
}
