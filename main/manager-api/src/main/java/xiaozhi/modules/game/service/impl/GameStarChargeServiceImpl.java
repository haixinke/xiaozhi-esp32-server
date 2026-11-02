package xiaozhi.modules.game.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameStarChargeService;
import xiaozhi.modules.game.vo.GameStarChargeResult;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.StarConsumeBizType;
import xiaozhi.modules.star.service.StarService;
import xiaozhi.modules.star.vo.StarConsumeResult;

/**
 * 游戏按局扣费实现。
 *
 * <p>幂等边界 = (user_id, biz_type=game_play, ref_id)，ref_id 为
 * SHA-256("game-play-charge|{gameType}|{playSessionId}") 的 64 位十六进制串：
 * 游戏类型与局次单号长度可变，直接拼接既可能超过 ref_id VARCHAR(64)，
 * 也可能因分隔符歧义碰撞；定长哈希天然规避两者。
 *
 * <p>执行顺序刻意为「先查原流水，再校验配置」：已扣费的同一局重试必须返回原结果，
 * 不受此后配置修改或停用影响（spec 数据与事务边界）。
 *
 * <p>资金写入委托 {@link StarService#consumeIndependent}：账户创建、流水写入、原子
 * 条件扣减（balance >= amount，杜绝负余额）与余额快照回填由星星罐模块在独立事务
 * （REQUIRES_NEW）内完成；并发同局经唯一索引兜底幂等。本类只保留游戏侧编排：
 * 参数校验、refId 推导、配置读取与免费/奖励短路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameStarChargeServiceImpl implements GameStarChargeService {

    private final GameSettlementValidator gameSettlementValidator;
    private final StarService starService;
    /** 仅用于"已扣费重试先于配置校验"的快速回放查询，不做资金写入 */
    private final StarTransactionDao starTransactionDao;

    @Override
    public GameStarChargeResult chargeForPlay(Long userId, String gameType, String playSessionId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (StringUtils.isBlank(gameType)) {
            throw new RenException(ErrorCode.GAME_NOT_FOUND);
        }
        // 局次单号是防重复边界的组成部分，缺失会让每次请求都当新局扣费
        if (StringUtils.isBlank(playSessionId)) {
            throw new RenException(ErrorCode.GAME_CHARGE_SESSION_REQUIRED);
        }

        String refId = deriveChargeRefId(gameType, playSessionId);

        // 防重复查询先于配置校验：已扣费同局重试返回原流水与原金额，
        // 不因配置修改、停用而重新扣款或报错
        StarTransactionEntity existing = starTransactionDao.selectOne(
                new QueryWrapper<StarTransactionEntity>()
                        .eq("user_id", userId)
                        .eq("biz_type", StarConsumeBizType.GAME_PLAY.getCode())
                        .eq("ref_id", refId));
        if (existing != null) {
            // 流水 amount 为负数，取相反数还原首次扣费的原金额
            return GameStarChargeResult.alreadyCharged(-existing.getAmount(), existing.getId());
        }

        // 游戏必须存在、启用且奖扣配置不冲突
        GameEntity game = gameSettlementValidator.requireSettleableGame(gameType);
        int consume = gameSettlementValidator.consumeStars(game);
        // 免费双零或奖励型游戏调用扣费入口：直接跳过，不建账户、不写流水
        if (consume == 0) {
            return GameStarChargeResult.noCharge();
        }

        StarConsumeResult result = starService.consumeIndependent(userId, StarConsumeBizType.GAME_PLAY, refId,
                consume, "游戏按局扣费:" + gameType);
        long charged = -result.transaction().getAmount();
        return result.newCharge()
                ? GameStarChargeResult.newCharge(charged, result.transaction().getId())
                : GameStarChargeResult.alreadyCharged(charged, result.transaction().getId());
    }

    /** 确定性扣费单号：SHA-256 十六进制 64 位，见类注释 */
    private String deriveChargeRefId(String gameType, String playSessionId) {
        String canonical = "game-play-charge|" + gameType + "|" + playSessionId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必含 SHA-256，此处为防御性兜底
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
