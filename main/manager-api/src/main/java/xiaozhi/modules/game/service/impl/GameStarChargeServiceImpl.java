package xiaozhi.modules.game.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameStarChargeService;
import xiaozhi.modules.game.vo.GameStarChargeResult;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.StarConsumeBizType;

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
 * <p>并发安全：两个并发线程同时未命中流水时，第二个在流水插入处撞唯一索引 →
 * 幂等返回；新扣费路径在同事务内完成账户创建、流水写入与原子条件扣减
 * （balance >= amount，杜绝负余额），任一步失败整体回滚，不留残留流水，
 * 重试不会被误判为已扣款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameStarChargeServiceImpl implements GameStarChargeService {

    private final GameDao gameDao;
    private final StarAccountDao starAccountDao;
    private final StarTransactionDao starTransactionDao;

    /** 自引用代理，保证 doCharge 的 REQUIRES_NEW 事务经代理生效 */
    private final org.springframework.context.ApplicationContext applicationContext;

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

        // 以下为新扣费路径：游戏必须存在且启用
        GameEntity game = gameDao.selectByGameType(gameType);
        if (game == null) {
            throw new RenException(ErrorCode.GAME_NOT_FOUND);
        }
        if (game.getStatus() == null || game.getStatus() != 1) {
            throw new RenException(ErrorCode.GAME_DISABLED);
        }
        int earn = game.getEarnStars() == null ? 0 : game.getEarnStars();
        int consume = game.getConsumeStars() == null ? 0 : game.getConsumeStars();
        // 存量冲突配置（奖扣同时为正）防御性拒绝：不静默采用消费金额。
        // 保存期互斥校验由游戏管理侧负责，这里兜底历史脏数据
        if (earn > 0 && consume > 0) {
            throw new RenException(ErrorCode.GAME_STAR_CONFIG_CONFLICT);
        }
        // 免费双零或奖励型游戏调用扣费入口：直接跳过，不建账户、不写流水
        if (consume == 0) {
            return GameStarChargeResult.noCharge();
        }

        // 自调用走代理，使内层 REQUIRES_NEW 事务生效
        return self().doCharge(userId, gameType, refId, consume);
    }

    /**
     * 内层独立事务（REQUIRES_NEW）：供外层游戏业务事务调用时，扣费失败只回滚扣费自身，
     * 不回滚调用方已完成的其他业务写入；直接内部调用（无外层事务）时与普通事务等价。
     * 账户创建、流水写入、原子扣减在同一事务提交；余额不足抛异常整体回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public GameStarChargeResult doCharge(Long userId, String gameType, String refId, int fee) {
        ensureAccount(userId);

        StarTransactionEntity txn = new StarTransactionEntity();
        txn.setUserId(userId);
        txn.setType("consume");
        txn.setBizType(StarConsumeBizType.GAME_PLAY.getCode());
        txn.setRefId(refId);
        txn.setAmount((long) -fee);
        txn.setBalanceAfter(0L); // 占位，扣减成功后回填
        txn.setRemark("游戏按局扣费:" + gameType);
        try {
            starTransactionDao.insert(txn);
        } catch (DuplicateKeyException dup) {
            // 并发同局：外层查重与本次插入之间另一事务已提交同 ref_id 流水，按幂等命中处理
            log.info("游戏扣费流水已存在，幂等返回：userId={}, gameType={}, ref={}", userId, gameType, refId);
            StarTransactionEntity hit = starTransactionDao.selectOne(
                    new QueryWrapper<StarTransactionEntity>()
                            .eq("user_id", userId)
                            .eq("biz_type", StarConsumeBizType.GAME_PLAY.getCode())
                            .eq("ref_id", refId));
            if (hit == null) {
                throw new IllegalStateException("幂等命中但流水不存在: userId=" + userId + ", ref=" + refId);
            }
            return GameStarChargeResult.alreadyCharged(-hit.getAmount(), hit.getId());
        }

        // 原子条件扣减：balance >= fee 才更新，并发不同局也不会扣成负余额；
        // 影响行数 0 = 余额不足，抛统一业务异常，事务回滚连带流水，重试不算已扣款
        int affected = starAccountDao.deductBalance(userId, fee);
        if (affected <= 0) {
            throw new RenException(ErrorCode.STAR_BALANCE_INSUFFICIENT);
        }

        // 回填操作后余额快照
        long after = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", userId)).getBalance();
        txn.setBalanceAfter(after);
        StarTransactionEntity update = new StarTransactionEntity();
        update.setId(txn.getId());
        update.setBalanceAfter(after);
        starTransactionDao.updateById(update);

        return GameStarChargeResult.newCharge(fee, txn.getId());
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

    /** 确保账户行存在；并发插入冲突时忽略（另一事务已建），与 StarServiceImpl 同款模式 */
    private void ensureAccount(Long userId) {
        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", userId));
        if (account != null) {
            return;
        }
        try {
            StarAccountEntity created = new StarAccountEntity();
            created.setUserId(userId);
            created.setBalance(0L);
            starAccountDao.insert(created);
        } catch (DuplicateKeyException e) {
            log.info("星星账户并发插入冲突，忽略：userId={}", userId);
        }
    }

    private GameStarChargeServiceImpl self() {
        return applicationContext.getBean(GameStarChargeServiceImpl.class);
    }
}
