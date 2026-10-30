package xiaozhi.modules.game.service;

import xiaozhi.modules.game.vo.GameStarRewardResult;

/**
 * 游戏星星结算（奖励）服务：奖励型游戏每日首次成功完成发奖的标准内部能力。
 * 仅供后端业务内部直调，不暴露 HTTP 接口；金额取游戏管理配置 earnStars（固定值，
 * 不随连续天数递增），客户端不能指定金额。
 * 防重复边界 = 用户 + 游戏类型 + 北京时间自然日，由统一每日奖励能力执行。
 */
public interface GameStarRewardService {

    /**
     * 领取游戏当日首次完成奖励（幂等）。游戏业务在验证用户成功完成一局奖励型游戏后调用；
     * 中途退出或失败不调用本方法，不占当天奖励机会。
     *
     * <p>防重复：同一 (userId, gameType) 每个北京自然日最多一份；当日重复或并发调用
     * 返回首次结算的原金额与原流水，不重复入账，即使此后游戏配置修改、停用或删除。
     *
     * <p>跳过：免费双零游戏或消费型游戏（consumeStars&gt;0）调用本入口时直接返回
     * {@link GameStarRewardResult.Status#NO_REWARD}，不创建星星账户、奖励记录或流水，
     * 也不占当天奖励资格。
     *
     * <p>拒绝：游戏不存在（10350）、已停用（10369）、存量奖扣冲突配置（10375）时
     * 抛业务异常，不发生任何星星写入；已领取后的重试先于上述校验返回原结果。
     *
     * @param userId   可信用户身份（服务端上下文），必填
     * @param gameType 游戏类型（字典 GAME_TYPE 的 code），必填
     * @return 奖励结果，区分本次新发奖 / 当日已领取 / 无需星星变动
     */
    GameStarRewardResult claimGameReward(Long userId, String gameType);
}
