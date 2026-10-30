package xiaozhi.modules.star.service.impl;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.DailyRewardSourceType;
import xiaozhi.modules.star.enums.StarEarnBizType;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.service.StarService;
import xiaozhi.modules.star.vo.DailyRewardResult;

/**
 * 每日星星奖励能力实现。
 *
 * 并发与事务设计：
 * 1) 先确保用户星星账户存在，再在同一事务内 SELECT ... FOR UPDATE 锁定账户行，
 *    串行化同一用户的奖励结算；锁内用当前读读奖励记录与流水，
 *    不依赖可重复读事务中锁等待前的旧快照。
 * 2) 北京时间日期在取得锁后确定，避免锁等待跨过日界时按旧日期发奖。
 * 3) 先分配奖励记录雪花 ID 作为钱包业务单号（refId），钱包入账取得流水 ID 后再写奖励记录，
 *    保证奖励记录与流水严格一对一；两者同事务提交，任一步失败整体回滚，重试可重新领取。
 * 4) 每日联合唯一约束为兜底：并发下撞键即另一事务已结算，重查返回原结果。
 *
 * 小纸条连续金额规则：昨天有成功奖励则 min(昨天数量+1, 17)，否则 11；
 * 不需要回溯完整历史，也不存连续天数。
 */
@Slf4j
@Service
public class StarDailyRewardServiceImpl implements StarDailyRewardService {

    /** 首次及中断后的基础奖励 */
    static final long BASE_AMOUNT = 11;
    /** 连续奖励每日递增步长 */
    static final long DAILY_INCREMENT = 1;
    /** 连续奖励封顶值 */
    static final long MAX_AMOUNT = 17;
    /** source_key 长度上限，与表结构 VARCHAR(64) 一致，超长拒绝避免截断形成两份资格 */
    static final int SOURCE_KEY_MAX_LENGTH = 64;

    /** 雪花 ID 由本服务显式分配，作为钱包业务单号，保证奖励记录与流水一对一 */
    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    private final StarDailyRewardDao starDailyRewardDao;
    private final StarAccountDao starAccountDao;
    private final StarService starService;

    /** 可替换时钟：与小纸条共用 dailyNoteClock（Asia/Shanghai），测试替换验证日界 */
    private Clock clock;

    public StarDailyRewardServiceImpl(StarDailyRewardDao starDailyRewardDao, StarAccountDao starAccountDao,
            StarService starService, @Qualifier("dailyNoteClock") Clock clock) {
        this.starDailyRewardDao = starDailyRewardDao;
        this.starAccountDao = starAccountDao;
        this.starService = starService;
        this.clock = clock;
    }

    /** 仅供测试替换时钟（包外通过实现类访问） */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * REQUIRED 传播：可被小纸条打开事务等外层事务编排（打开记录+奖励+流水原子提交），
     * 也可独立成事务供其他游戏直接调用。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DailyRewardResult claimDailyReward(Long userId, String sourceType, String sourceKey) {
        DailyRewardSourceType type = DailyRewardSourceType.parse(sourceType);
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (sourceKey == null || sourceKey.isBlank() || sourceKey.length() > SOURCE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("sourceKey 不能为空且长度不能超过 " + SOURCE_KEY_MAX_LENGTH);
        }
        // 落库使用规范值：来源类型统一大写枚举名，避免大小写差异形成两份每日资格
        String normalizedType = type.name();
        String normalizedKey = sourceKey.trim();

        // 先确保账户存在（并发建户冲突忽略），再锁账户行串行化本用户的奖励结算
        ensureAccount(userId);
        starAccountDao.selectByUserIdForUpdate(userId);

        // 锁内用当前读：当天已领取则返回原结果，不重新计算金额
        LocalDate today = LocalDate.now(clock);
        StarDailyRewardEntity existing = selectReward(userId, normalizedType, normalizedKey, today);
        if (existing != null) {
            return DailyRewardResult.alreadyClaimed(existing.getAmount(), existing.getStarTransactionId(),
                    existing.getId(), today);
        }

        // 连续金额只看昨天同一来源的成功奖励：有则 +1 封顶，无（含中断）回 11
        StarDailyRewardEntity yesterday = selectReward(userId, normalizedType, normalizedKey, today.minusDays(1));
        long amount = yesterday == null ? BASE_AMOUNT
                : Math.min(yesterday.getAmount() + DAILY_INCREMENT, MAX_AMOUNT);

        // 先分配奖励记录 ID 作钱包业务单号；钱包入账成功后再写奖励记录，一对一关联
        long rewardId = snowflake.nextId();
        StarTransactionEntity txn = starService.earn(userId, walletBizType(type), String.valueOf(rewardId), amount,
                "每日奖励-" + normalizedType);

        StarDailyRewardEntity reward = new StarDailyRewardEntity();
        reward.setId(rewardId);
        reward.setUserId(userId);
        reward.setSourceType(normalizedType);
        reward.setSourceKey(normalizedKey);
        reward.setRewardDate(java.sql.Date.valueOf(today));
        reward.setAmount(amount);
        reward.setStarTransactionId(txn.getId());
        try {
            starDailyRewardDao.insert(reward);
        } catch (DuplicateKeyException e) {
            // 兜底分支：账户行锁已串行化同用户结算，正常不会撞键；
            // 万一撞键说明另一事务已提交当天奖励，重查返回原结果，不重复发奖
            StarDailyRewardEntity committed = selectReward(userId, normalizedType, normalizedKey, today);
            if (committed == null) {
                throw new IllegalStateException("每日奖励唯一键冲突但记录不存在: userId=" + userId);
            }
            log.info("每日奖励并发撞键，返回已结算结果: userId={}, source={}/{}", userId, normalizedType, normalizedKey);
            return DailyRewardResult.alreadyClaimed(committed.getAmount(), committed.getStarTransactionId(),
                    committed.getId(), today);
        }
        return DailyRewardResult.awarded(amount, txn.getId(), rewardId, today);
    }

    /**
     * 各来源使用独立钱包业务类型，refId 为每日奖励记录雪花 ID，天然不与签到等旧业务单号碰撞；
     * 游戏奖励的固定金额由游戏业务按配置算出后传入（票据03），本能力只负责资格与入账。
     */
    private StarEarnBizType walletBizType(DailyRewardSourceType type) {
        return type == DailyRewardSourceType.DAILY_NOTE ? StarEarnBizType.DAILY_NOTE
                : StarEarnBizType.GAME_REWARD;
    }

    /** 按用户+来源+北京日期查询奖励记录（锁内当前读或普通查询共用） */
    private StarDailyRewardEntity selectReward(Long userId, String sourceType, String sourceKey, LocalDate date) {
        return starDailyRewardDao.selectOne(new QueryWrapper<StarDailyRewardEntity>()
                .eq("user_id", userId)
                .eq("source_type", sourceType)
                .eq("source_key", sourceKey)
                .eq("reward_date", java.sql.Date.valueOf(date)));
    }

    /** 确保账户行存在；并发插入冲突时忽略（另一事务已建） */
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
}
