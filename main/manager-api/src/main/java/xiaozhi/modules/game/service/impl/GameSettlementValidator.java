package xiaozhi.modules.game.service.impl;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;

/**
 * 游戏星星结算共用的配置校验：奖励（GameStarRewardServiceImpl）与扣费
 * （GameStarChargeServiceImpl）两条结算路径在确认"需要结算"后，都按同一规则
 * 读取并校验游戏配置——游戏必须存在且启用，奖扣配置不得同时为正（存量脏数据兜底）。
 *
 * 注意：本校验只用于"新结算"路径；防重复查询（当日已领奖/同局已扣费）先于校验，
 * 已结算的重试不受此后配置修改、停用或删除影响。
 */
@Component
@RequiredArgsConstructor
class GameSettlementValidator {

    private final GameDao gameDao;

    /**
     * 加载并按结算前提校验游戏配置。
     *
     * @param gameType 游戏类型 code
     * @return 校验通过的游戏配置行
     * @throws RenException 游戏不存在(10350)、已停用(10369)或奖扣配置冲突(10375)
     */
    GameEntity requireSettleableGame(String gameType) {
        GameEntity game = gameDao.selectByGameType(gameType);
        if (game == null) {
            throw new RenException(ErrorCode.GAME_NOT_FOUND);
        }
        if (game.getStatus() == null || game.getStatus() != 1) {
            throw new RenException(ErrorCode.GAME_DISABLED);
        }
        int earn = game.getEarnStars() == null ? 0 : game.getEarnStars();
        int consume = game.getConsumeStars() == null ? 0 : game.getConsumeStars();
        // 存量冲突配置（奖扣同时为正）防御性拒绝：不静默采用其中一方金额。
        // 保存期互斥校验由游戏管理侧负责，这里兜底历史脏数据
        if (earn > 0 && consume > 0) {
            throw new RenException(ErrorCode.GAME_STAR_CONFIG_CONFLICT);
        }
        return game;
    }

    /** 奖励星星数，null 按 0 处理 */
    int earnStars(GameEntity game) {
        return game.getEarnStars() == null ? 0 : game.getEarnStars();
    }

    /** 消费星星数，null 按 0 处理 */
    int consumeStars(GameEntity game) {
        return game.getConsumeStars() == null ? 0 : game.getConsumeStars();
    }
}
