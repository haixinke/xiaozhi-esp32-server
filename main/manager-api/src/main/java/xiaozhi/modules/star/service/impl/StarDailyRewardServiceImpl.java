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
 * 4) 每日联合唯一约束为兜底：账户行锁已串行化同用户结算，正常不会撞键；
 *    万一撞键说明并发下另一事务已提交，直接失败回滚，由调用方重试。
 *
 * 金额策略按来源区分（spec 标准内部接口职责）：
 * 小纸条（DAILY_NOTE）按连续规则计算：昨天有成功奖励则 min(昨天数量+1, 17)，否则 11；
 * 游戏（GAME）奖励金额由游戏奖励入口按当前有效配置在决策回调（锁前）算出，
 * 本能力不参与游戏金额推导，连续规则不适用于游戏。
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
     * REQUIRED 传播：可被小纸条打开事务等外层事务编排（打开记录+奖励+流水原子提交）。
     *
     * 本入口仅承载连续金额策略（小纸条）：游戏奖励的固定金额在决策回调中由配置决定，
     * 游戏业务请走 GameStarRewardService，不得走本入口套用连续规则。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DailyRewardResult claimDailyReward(Long userId, String sourceType, String sourceKey) {
        DailyRewardSourceType type = DailyRewardSourceType.parse(sourceType);
        String normalizedKey = normalizeSourceKey(sourceKey);
        if (type != DailyRewardSourceType.DAILY_NOTE) {
            // 连续规则只属于小纸条；游戏来源的金额在锁内由配置决定，防止误用导致金额随天数漂移
            throw new IllegalArgumentException("游戏奖励请通过 GameStarRewardService 领取");
        }
        return claimWithDecision(userId, type, normalizedKey,
                (uid, normalizedType, key, today) -> ClaimDecision.award(
                        streakAmount(uid, normalizedType, key, today)));
    }

    /**
     * {@inheritDoc}
     *
     * 并发与事务顺序：决策回调在取得账户行锁之前执行（读取配置的普通读），
     * 拿锁后对当天奖励记录做复查，最终以 (user_id, source_type, source_key, reward_date)
     * 唯一约束兜底；撞键即并发下另一事务已提交，直接失败回滚，不返回伪成功。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DailyRewardResult claimWithDecision(Long userId, DailyRewardSourceType type, String sourceKey,
            ClaimDecider decider) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        // 落库使用规范值：来源类型统一大写枚举名，避免大小写差异形成两份每日资格
        String normalizedType = type.name();

        // 锁内用当前读：当天已领取则返回原结果，不重新决策
        LocalDate today = LocalDate.now(clock);
        StarDailyRewardEntity existing = selectReward(userId, normalizedType, sourceKey, today);
        if (existing != null) {
            return DailyRewardResult.alreadyClaimed(existing.getAmount(), existing.getStarTransactionId(),
                    existing.getId(), today);
        }

        // 当天未领取：结算决策（游戏奖励在此读配置并校验）
        ClaimDecision decision = decider.decide(userId, normalizedType, sourceKey, today);
        if (!decision.award()) {
            // 跳过场景：不写奖励记录、不调钱包、不占资格，账户也不必创建
            return DailyRewardResult.skipped(today);
        }
        long amount = decision.amount();
        if (amount <= 0) {
            // 每日奖励表 amount 必须大于 0；零金额应使用 skip 决策表达
            throw new IllegalArgumentException("奖励金额必须大于 0");
        }

        // 确认发奖后才建户并加锁：免费/消费型游戏不产生任何星星业务数据
        ensureAccount(userId);
        starAccountDao.selectByUserIdForUpdate(userId);
        // 拿锁后复查：等待锁期间另一事务可能已完成当天发奖，按已领取返回原结果
        existing = selectReward(userId, normalizedType, sourceKey, today);
        if (existing != null) {
            return DailyRewardResult.alreadyClaimed(existing.getAmount(), existing.getStarTransactionId(),
                    existing.getId(), today);
        }

        // 先分配奖励记录 ID 作钱包业务单号；钱包入账成功后再写奖励记录，一对一关联
        long rewardId = snowflake.nextId();
        StarTransactionEntity txn = starService.earn(userId, walletBizType(type), String.valueOf(rewardId), amount,
                "每日奖励-" + normalizedType);

        StarDailyRewardEntity reward = new StarDailyRewardEntity();
        reward.setId(rewardId);
        reward.setUserId(userId);
        reward.setSourceType(normalizedType);
        reward.setSourceKey(sourceKey);
        reward.setRewardDate(java.sql.Date.valueOf(today));
        reward.setAmount(amount);
        reward.setStarTransactionId(txn.getId());
        try {
            starDailyRewardDao.insert(reward);
        } catch (DuplicateKeyException e) {
            // 兜底分支：账户行锁已串行化同用户结算，正常不会撞键；
            // 万一撞键说明唯一约束兜底生效，此时钱包流水已写入，
            // 不能返回"已领取"伪成功（那会让调用方拿到未实际入账的原流水），
            // 直接失败并回滚整个事务，由调用方重试拿真实结果
            throw e;
        }
        return DailyRewardResult.awarded(amount, txn.getId(), rewardId, today);
    }

    /** 参数规范化：来源 key 去空白并校验长度上限 */
    private String normalizeSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank() || sourceKey.length() > SOURCE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("sourceKey 不能为空且长度不能超过 " + SOURCE_KEY_MAX_LENGTH);
        }
        return sourceKey.trim();
    }

    /** 小纸条连续金额：只看昨天同一来源的成功奖励，有则 +1 封顶，无（含中断）回 11 */
    private long streakAmount(Long userId, String sourceType, String sourceKey, LocalDate today) {
        StarDailyRewardEntity yesterday = selectReward(userId, sourceType, sourceKey, today.minusDays(1));
        return yesterday == null ? BASE_AMOUNT
                : Math.min(yesterday.getAmount() + DAILY_INCREMENT, MAX_AMOUNT);
    }

    /**
     * 各来源使用独立钱包业务类型，refId 为每日奖励记录雪花 ID，天然不与签到等旧业务单号碰撞。
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
