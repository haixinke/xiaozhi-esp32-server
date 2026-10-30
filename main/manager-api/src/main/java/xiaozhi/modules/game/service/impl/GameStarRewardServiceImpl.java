package xiaozhi.modules.game.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameStarRewardService;
import xiaozhi.modules.game.vo.GameStarRewardResult;
import xiaozhi.modules.star.enums.DailyRewardSourceType;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.vo.DailyRewardResult;

/**
 * 游戏每日首次完成奖励实现。
 *
 * <p>执行顺序刻意为「先查当日领取记录，再校验配置」：已领奖的当日重试必须返回原结果，
 * 不受此后配置修改、停用或删除影响（spec 数据与事务边界）。
 * 领取记录的查重、配置校验与入账都在统一每日奖励能力的同一事务内串行完成。
 *
 * <p>金额规则：奖励金额为配置的固定 earnStars，不随连续天数递增；
 * 连续规则只属于小纸条来源，游戏来源经固定决策进入每日奖励能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameStarRewardServiceImpl implements GameStarRewardService {

    private final StarDailyRewardService starDailyRewardService;
    private final GameSettlementValidator gameSettlementValidator;

    @Override
    public GameStarRewardResult claimGameReward(Long userId, String gameType) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (StringUtils.isBlank(gameType)) {
            throw new RenException(ErrorCode.GAME_NOT_FOUND);
        }
        // 游戏类型 code 为后端规范值，裁剪后作为每日奖励的 source_key，避免空白差异形成两份资格
        String normalizedGameType = gameType.trim();

        // 决策回调只在"当天未领取"时被调用：配置校验与金额确定严格发生在查重之后，
        // 保证已领取重试返回原结果，未领取时按当前有效配置结算
        DailyRewardResult result = starDailyRewardService.claimWithDecision(
                userId, DailyRewardSourceType.GAME, normalizedGameType,
                (uid, type, key, date) -> decideAward(normalizedGameType));

        if (result.isSkipped()) {
            return GameStarRewardResult.noReward();
        }
        if (!result.isAwarded()) {
            return GameStarRewardResult.alreadyRewarded(result.getAmount(), result.getTransactionId());
        }
        return GameStarRewardResult.newReward(result.getAmount(), result.getTransactionId());
    }

    /**
     * 结算决策（在每日奖励能力的账户行锁之前执行）：按当前配置决定发奖金额或跳过。
     * 并发正确性不依赖本决策在锁内——锁内会复查当天奖励记录，
     * (user_id, source_type, source_key, reward_date) 唯一约束兜底并发撞键。
     * 免费双零与消费型游戏返回 skip（无星星变动、不占资格）；
     * 游戏不存在、停用、奖扣冲突配置抛业务异常，整天不发生任何星星写入。
     */
    private StarDailyRewardService.ClaimDecision decideAward(String gameType) {
        GameEntity game = gameSettlementValidator.requireSettleableGame(gameType);
        int earn = gameSettlementValidator.earnStars(game);
        // 免费双零或消费型游戏调用奖励入口：跳过，不建账户、不写奖励记录与流水、不占资格
        if (earn == 0) {
            return StarDailyRewardService.ClaimDecision.skip();
        }
        return StarDailyRewardService.ClaimDecision.award(earn);
    }
}
