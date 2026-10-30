package xiaozhi.modules.game.service;

import xiaozhi.modules.game.vo.GameStarChargeResult;

/**
 * 游戏星星结算（扣费）服务：消费型游戏按局扣费的标准内部能力。
 * 仅供后端业务内部直调，不暴露 HTTP 接口；费用取游戏管理配置 consumeStars，
 * 客户端不能指定金额。防重复边界 = 用户 + 游戏类型 + 局次业务单号。
 */
public interface GameStarChargeService {

    /**
     * 按局扣费（幂等）。游戏业务在允许用户开始一局消费型游戏前调用；
     * 余额不足抛业务异常，该局不得开始。
     *
     * <p>防重复：同一 (userId, gameType, playSessionId) 重试返回首次扣费的原流水与原金额，
     * 即使此后游戏配置修改、停用也不重新扣款；不同局分别收费。
     *
     * <p>跳过：免费双零游戏或奖励型游戏（earnStars>0）调用本入口时直接返回
     * {@link GameStarChargeResult.Status#NO_CHARGE}，不创建星星账户、不写流水。
     *
     * @param userId        可信用户身份（服务端上下文），必填
     * @param gameType      游戏类型（字典 GAME_TYPE 的 code），必填
     * @param playSessionId 后端生成或验证的局次业务单号，必填；
     *                      局次身份由接入的玩法业务维护，不能用每次请求新生成的随机号冒充同一局
     * @return 扣费结果，区分本次新扣费 / 已扣费 / 无需星星变动
     */
    GameStarChargeResult chargeForPlay(Long userId, String gameType, String playSessionId);
}
