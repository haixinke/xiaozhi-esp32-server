package xiaozhi.modules.star.vo;

import java.time.LocalDate;

import lombok.Data;

/**
 * 每日奖励结算结果。明确区分本次新入账与已结算：
 * awarded=true 表示本次新写入奖励并入账；awarded=false 表示当天该来源已领取，
 * 此时 amount/transactionId 返回首次结算的数量与流水标识，本次新增数量为 0。
 */
@Data
public class DailyRewardResult {

    /** 本次是否新入账（false = 当天已领取，命中既有奖励记录） */
    private boolean awarded;

    /** 本次结算归属的奖励数量；已结算时返回首次结算的数量快照 */
    private long amount;

    /** 关联星星入账流水 ID（首次结算与已结算均返回首次的流水） */
    private Long transactionId;

    /** 每日奖励记录 ID */
    private Long rewardId;

    /** 北京时间领取自然日 */
    private LocalDate rewardDate;

    public static DailyRewardResult awarded(long amount, Long transactionId, Long rewardId, LocalDate rewardDate) {
        DailyRewardResult result = new DailyRewardResult();
        result.setAwarded(true);
        result.setAmount(amount);
        result.setTransactionId(transactionId);
        result.setRewardId(rewardId);
        result.setRewardDate(rewardDate);
        return result;
    }

    public static DailyRewardResult alreadyClaimed(long amount, Long transactionId, Long rewardId,
            LocalDate rewardDate) {
        DailyRewardResult result = awarded(amount, transactionId, rewardId, rewardDate);
        result.setAwarded(false);
        return result;
    }
}
