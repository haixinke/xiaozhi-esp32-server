package xiaozhi.modules.star.service;

import xiaozhi.modules.star.vo.DailyRewardResult;

/**
 * 每日星星奖励能力：统一负责用户与奖励来源的当天领取资格、并发防重及钱包入账。
 * 小纸条与游戏共用本资格与入账机制；金额策略由奖励记录推导（昨天成功奖励 +1，封顶17，中断回11），
 * 钱包（StarService）只承载资金变动，不含连续规则。
 * 日期由后端按 Asia/Shanghai 自然日确定，不接受客户端日期。
 */
public interface StarDailyRewardService {

    /**
     * 领取某来源当日奖励（幂等）。每用户每来源每北京自然日最多一份：
     * 首次成功领取 11 颗，昨天有成功奖励则 min(昨天数量+1, 17)，否则 11。
     * 重复或并发调用返回首次结算的数量与流水，不重复入账。
     *
     * @param userId     用户ID（服务端可信身份）
     * @param sourceType 奖励来源类型（DAILY_NOTE/GAME，大小写不敏感，落库为规范大写值）
     * @param sourceKey  来源标识（DAILY_NOTE 固定传 DAILY_NOTE；GAME 传游戏类型 code，长度≤64，前后空白会被裁剪）
     * @return 结算结果：awarded 区分本次新入账与已结算；已结算返回原金额与流水
     */
    DailyRewardResult claimDailyReward(Long userId, String sourceType, String sourceKey);
}
