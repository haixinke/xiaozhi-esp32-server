package xiaozhi.modules.game.vo;

import lombok.Data;

/**
 * 游戏按局扣费结果。仅供服务端内部方法返回，不对客户端暴露。
 * 明确区分三种语义：本次新扣费 / 同局重复请求命中已扣费 / 无需星星变动（免费或奖励型游戏）。
 */
@Data
public class GameStarChargeResult {

    /**
     * 扣费结果语义
     */
    public enum Status {
        /** 本次新扣费 */
        NEW_CHARGE,
        /** 同局重复请求：命中首次扣费流水，chargedStars 与 transactionId 为首次结算原值 */
        ALREADY_CHARGED,
        /** 无需星星变动：免费双零游戏或奖励型游戏调用扣费入口 */
        NO_CHARGE
    }

    /** 扣费结果语义 */
    private Status status;

    /** 实际扣除的星星数；ALREADY_CHARGED 时为首次扣费的原金额，NO_CHARGE 时为 0 */
    private long chargedStars;

    /** 关联的星星流水 ID；NO_CHARGE 时为 null */
    private Long transactionId;

    public static GameStarChargeResult newCharge(long chargedStars, Long transactionId) {
        GameStarChargeResult r = new GameStarChargeResult();
        r.status = Status.NEW_CHARGE;
        r.chargedStars = chargedStars;
        r.transactionId = transactionId;
        return r;
    }

    public static GameStarChargeResult alreadyCharged(long chargedStars, Long transactionId) {
        GameStarChargeResult r = new GameStarChargeResult();
        r.status = Status.ALREADY_CHARGED;
        r.chargedStars = chargedStars;
        r.transactionId = transactionId;
        return r;
    }

    public static GameStarChargeResult noCharge() {
        GameStarChargeResult r = new GameStarChargeResult();
        r.status = Status.NO_CHARGE;
        r.chargedStars = 0;
        r.transactionId = null;
        return r;
    }
}
