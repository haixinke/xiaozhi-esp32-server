package xiaozhi.modules.listen.vo;

import lombok.Data;

/**
 * 听你说·整组提交答案的响应。
 * 答案落库之外，携带本次提交的星星结算结果，供小程序播放收星动效或显示兜底文案。
 * awardedStars 为本次实际新增的星星数（当日已领奖或无奖励时为 0），
 * rewardStatus 区分三种语义：本次新发奖 / 当日已领取 / 无需星星变动。
 */
@Data
public class ListenSubmitResultVO {

    /** 本次新增的奖励星星数；当日已领取或无奖励时为 0 */
    private long awardedStars;

    /** 奖励结果语义：NEW_REWARD 本次新发奖 / ALREADY_REWARDED 当日已领取 / NO_REWARD 无需星星变动 */
    private String rewardStatus;
}
