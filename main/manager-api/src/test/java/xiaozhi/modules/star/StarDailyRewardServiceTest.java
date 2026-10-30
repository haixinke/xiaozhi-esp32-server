package xiaozhi.modules.star;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.service.impl.StarDailyRewardServiceImpl;
import xiaozhi.modules.star.vo.DailyRewardResult;

/**
 * 每日奖励能力集成测试（真实 dev 库，OceanBase MySQL 模式）。
 * 通过可替换的 Clock 固定"当天"为 2160 年的专用日期段验证北京时间日界；
 * 用户 ID 使用不可能存在的超大值，AfterEach 物理清理本用例产生的奖励/流水/账户行。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("每日星星奖励服务集成测试")
class StarDailyRewardServiceTest {

    @Autowired
    private StarDailyRewardService starDailyRewardService;
    @Autowired
    private StarDailyRewardDao starDailyRewardDao;
    @Autowired
    private StarTransactionDao starTransactionDao;
    @Autowired
    private StarAccountDao starAccountDao;

    private static final long USER_A = 9_999_100_001L;
    private static final long USER_B = 9_999_100_002L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 测试专用 GAME 来源 key：与真实游戏字典 code 错开，避免依赖运营数据；
     *  奖励记录按 user_id 在 AfterEach 物理清理 */
    private static final String REWARD_TEST_A = "REWARD_TEST_A";
    private static final String REWARD_TEST_B = "REWARD_TEST_B";

    /** 把被测 Service 的可替换时钟拨到指定日期的正午（Asia/Shanghai），远离日界 */
    private void atShanghaiNoon(String date) {
        LocalDate day = LocalDate.parse(date);
        Instant noon = day.atTime(12, 0).atZone(SHANGHAI).toInstant();
        ((StarDailyRewardServiceImpl) starDailyRewardService)
                .setClock(Clock.fixed(noon, SHANGHAI));
    }

    /** 把时钟拨到指定北京时间时刻（用于日界验证） */
    private void atShanghaiTime(String date, int hour, int minute) {
        Instant instant = LocalDate.parse(date).atTime(hour, minute).atZone(SHANGHAI).toInstant();
        ((StarDailyRewardServiceImpl) starDailyRewardService)
                .setClock(Clock.fixed(instant, SHANGHAI));
    }

    @BeforeEach
    void resetClock() {
        atShanghaiNoon("2160-12-30");
    }

    @AfterEach
    void cleanup() {
        starDailyRewardDao.delete(new QueryWrapper<StarDailyRewardEntity>()
                .in("user_id", USER_A, USER_B));
        starTransactionDao.delete(new QueryWrapper<StarTransactionEntity>()
                .in("user_id", USER_A, USER_B));
        starAccountDao.delete(new QueryWrapper<StarAccountEntity>()
                .in("user_id", USER_A, USER_B));
    }

    private List<StarDailyRewardEntity> rewardsOf(long userId) {
        return starDailyRewardDao.selectList(new QueryWrapper<StarDailyRewardEntity>()
                .eq("user_id", userId).orderByAsc("reward_date"));
    }

    // ---- 小纸条连续金额规则 ----

    @Test
    @DisplayName("首次领取：11 颗，余额增加 11，流水与奖励记录一对一")
    void firstClaim_awardsEleven() {
        atShanghaiNoon("2160-01-10");

        DailyRewardResult result = starDailyRewardService.claimDailyReward(
                USER_A, "DAILY_NOTE", "DAILY_NOTE");

        assertThat(result.isAwarded()).isTrue();
        assertThat(result.getAmount()).isEqualTo(11);
        assertThat(result.getTransactionId()).isNotNull();

        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", USER_A));
        assertThat(account.getBalance()).isEqualTo(11);

        List<StarDailyRewardEntity> rewards = rewardsOf(USER_A);
        assertThat(rewards).hasSize(1);
        assertThat(rewards.get(0).getAmount()).isEqualTo(11);
        assertThat(rewards.get(0).getStarTransactionId()).isEqualTo(result.getTransactionId());
        assertThat(rewards.get(0).getSourceType()).isEqualTo("DAILY_NOTE");
        assertThat(rewards.get(0).getSourceKey()).isEqualTo("DAILY_NOTE");

        StarTransactionEntity txn = starTransactionDao.selectById(result.getTransactionId());
        assertThat(txn.getType()).isEqualTo("earn");
        assertThat(txn.getBizType()).isEqualTo("daily_note");
        assertThat(txn.getRefId()).isEqualTo(rewards.get(0).getId().toString());
        assertThat(txn.getAmount()).isEqualTo(11);
        assertThat(txn.getBalanceAfter()).isEqualTo(11);
    }

    @Test
    @DisplayName("连续第1至第8天：11、12、13、14、15、16、17、17，第9天仍17封顶")
    void streak_incrementsAndCaps() {
        long[] expected = {11, 12, 13, 14, 15, 16, 17, 17, 17};
        for (int i = 0; i < expected.length; i++) {
            String date = String.format("2160-02-%02d", i + 1);
            atShanghaiNoon(date);
            DailyRewardResult result = starDailyRewardService.claimDailyReward(
                    USER_A, "DAILY_NOTE", "DAILY_NOTE");
            assertThat(result.isAwarded()).as("day %s awarded", i + 1).isTrue();
            assertThat(result.getAmount()).as("day %s amount", i + 1).isEqualTo(expected[i]);
        }

        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", USER_A));
        long total = java.util.Arrays.stream(expected).sum();
        assertThat(account.getBalance()).isEqualTo(total);
        assertThat(rewardsOf(USER_A)).hasSize(expected.length);
    }

    @Test
    @DisplayName("中断一天后再次领取：恢复 11 颗，不沿用中断前金额")
    void gap_resetsToEleven() {
        atShanghaiNoon("2160-03-01");
        starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        atShanghaiNoon("2160-03-02");
        DailyRewardResult day2 = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(day2.getAmount()).isEqualTo(12);

        // 03-03 漏领，03-04 中断后重置
        atShanghaiNoon("2160-03-04");
        DailyRewardResult day4 = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(day4.isAwarded()).isTrue();
        assertThat(day4.getAmount()).isEqualTo(11);
    }

    @Test
    @DisplayName("昨天非本来源奖励不参与连续：游戏来源昨天的奖励不影响小纸条从 11 起")
    void otherSource_doesNotCountTowardStreak() {
        atShanghaiNoon("2160-04-01");
        DailyRewardResult game = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, REWARD_TEST_A,
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(5));
        assertThat(game.getAmount()).isEqualTo(5);

        atShanghaiNoon("2160-04-02");
        // 小纸条来源昨天没有成功奖励，应从 11 起；游戏来源固定金额不随天数变
        DailyRewardResult note = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(note.getAmount()).isEqualTo(11);
        DailyRewardResult gameDay2 = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, REWARD_TEST_A,
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(5));
        assertThat(gameDay2.getAmount()).isEqualTo(5);

        // 两来源各一条奖励记录，互不占资格
        assertThat(rewardsOf(USER_A)).hasSize(3);
    }

    @Test
    @DisplayName("不同 source_key 各自独立计算每日资格")
    void distinctSourceKeys_independent() {
        atShanghaiNoon("2160-05-01");
        DailyRewardResult g1 = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, REWARD_TEST_A,
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(5));
        DailyRewardResult g2 = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, REWARD_TEST_B,
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(8));
        assertThat(g1.isAwarded()).isTrue();
        assertThat(g2.isAwarded()).isTrue();
        assertThat(rewardsOf(USER_A)).hasSize(2);
    }

    // ---- 幂等与并发 ----

    @Test
    @DisplayName("同日重复领取：返回原金额与流水，新增数量为 0，余额不重复增加")
    void sameDay_repeat_returnsExisting() {
        atShanghaiNoon("2160-06-10");
        DailyRewardResult first = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");

        DailyRewardResult second = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        DailyRewardResult third = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");

        assertThat(second.isAwarded()).isFalse();
        assertThat(third.isAwarded()).isFalse();
        // 已结算返回首次结算的数量与流水标识，不重新计算
        assertThat(second.getAmount()).isEqualTo(first.getAmount());
        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());

        assertThat(rewardsOf(USER_A)).hasSize(1);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", USER_A))).isEqualTo(1);
        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", USER_A));
        assertThat(account.getBalance()).isEqualTo(11);
    }

    @Test
    @DisplayName("并发领取：多个线程同时请求，只入账一次且全部拿到结果")
    void concurrent_claims_awardOnce() throws Exception {
        atShanghaiNoon("2160-07-10");

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger awarded = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        List<DailyRewardResult> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    DailyRewardResult r = starDailyRewardService.claimDailyReward(
                            USER_A, "DAILY_NOTE", "DAILY_NOTE");
                    synchronized (results) {
                        results.add(r);
                    }
                    if (r.isAwarded()) {
                        awarded.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 并发请求均不得抛错；只有一笔实际发奖
        assertThat(errors.get()).isZero();
        assertThat(awarded.get()).isEqualTo(1);
        assertThat(results).hasSize(threads);

        assertThat(rewardsOf(USER_A)).hasSize(1);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", USER_A))).isEqualTo(1);
        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", USER_A));
        assertThat(account.getBalance()).isEqualTo(11);
    }

    // ---- 北京时间日界 ----

    @Test
    @DisplayName("北京时间零点跨日：23:59 领取属当天，00:01 属次日且连续 +1")
    void beijingMidnight_dayRollover() {
        atShanghaiTime("2160-08-10", 23, 59);
        DailyRewardResult before = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(before.getAmount()).isEqualTo(11);

        atShanghaiTime("2160-08-11", 0, 1);
        DailyRewardResult after = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(after.isAwarded()).isTrue();
        assertThat(after.getAmount()).isEqualTo(12);

        List<StarDailyRewardEntity> rewards = rewardsOf(USER_A);
        assertThat(rewards).hasSize(2);
        assertThat(rewards.get(0).getRewardDate()).isEqualTo(java.sql.Date.valueOf("2160-08-10"));
        assertThat(rewards.get(1).getRewardDate()).isEqualTo(java.sql.Date.valueOf("2160-08-11"));
    }

    @Test
    @DisplayName("同一 UTC 时刻可跨北京日界：UTC 16:00 前后分属两个北京自然日")
    void utcBoundary_mapsToBeijingDays() {
        // UTC 2160-09-10 15:30 = 北京 2160-09-10 23:30
        Instant utcEvening = LocalDate.parse("2160-09-10").atTime(15, 30)
                .atZone(ZoneId.of("UTC")).toInstant();
        ((StarDailyRewardServiceImpl) starDailyRewardService)
                .setClock(Clock.fixed(utcEvening, SHANGHAI));
        DailyRewardResult day1 = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(day1.getAmount()).isEqualTo(11);

        // UTC 2160-09-10 16:30 = 北京 2160-09-11 00:30
        Instant utcNext = LocalDate.parse("2160-09-10").atTime(16, 30)
                .atZone(ZoneId.of("UTC")).toInstant();
        ((StarDailyRewardServiceImpl) starDailyRewardService)
                .setClock(Clock.fixed(utcNext, SHANGHAI));
        DailyRewardResult day2 = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(day2.getAmount()).isEqualTo(12);
    }

    // ---- 用户隔离 ----

    @Test
    @DisplayName("用户隔离：A 的领取与连续不影响 B，各自独立从 11 起")
    void crossUser_isolation() {
        atShanghaiNoon("2160-10-01");
        starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");

        DailyRewardResult bFirst = starDailyRewardService.claimDailyReward(USER_B, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(bFirst.getAmount()).isEqualTo(11);

        atShanghaiNoon("2160-10-02");
        DailyRewardResult aDay2 = starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", "DAILY_NOTE");
        DailyRewardResult bDay2 = starDailyRewardService.claimDailyReward(USER_B, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(aDay2.getAmount()).isEqualTo(12);
        assertThat(bDay2.getAmount()).isEqualTo(12);
    }

    // ---- 参数校验 ----

    @Test
    @DisplayName("参数非法：userId 为空、sourceKey 空白均拒绝，零写入")
    void invalidInput_rejected() {
        assertThatThrownBy(() -> starDailyRewardService.claimDailyReward(null, "DAILY_NOTE", "DAILY_NOTE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> starDailyRewardService.claimDailyReward(USER_A, "DAILY_NOTE", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> starDailyRewardService.claimDailyReward(USER_A, "BAD_TYPE", "k"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(rewardsOf(USER_A)).isEmpty();
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", USER_A))).isZero();
    }

    @Test
    @DisplayName("sourceKey 长度上限 64：超长拒绝，避免截断后形成两份资格")
    void sourceKey_tooLong_rejected() {
        String tooLong = "k".repeat(65);
        assertThatThrownBy(() -> starDailyRewardService.claimDailyReward(USER_A, "GAME", tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rewardsOf(USER_A)).isEmpty();
    }

    // ---- 锁内决策重载（游戏奖励金额策略，ticket 03） ----

    @Test
    @DisplayName("固定金额决策：按回调金额入账，不套用连续规则，隔日仍发同一固定金额")
    void decision_claimsExactly_noStreak() {
        atShanghaiNoon("2161-01-10");
        DailyRewardResult day1 = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(7));
        assertThat(day1.isAwarded()).isTrue();
        assertThat(day1.getAmount()).isEqualTo(7);

        // 隔日再领：固定金额不随连续天数 +1
        atShanghaiNoon("2161-01-11");
        DailyRewardResult day2 = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(7));
        assertThat(day2.isAwarded()).isTrue();
        assertThat(day2.getAmount()).isEqualTo(7);

        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", USER_A));
        assertThat(account.getBalance()).isEqualTo(14);
        assertThat(rewardsOf(USER_A)).hasSize(2);

        // 钱包流水业务类型为 game_reward，区别于小纸条
        StarTransactionEntity txn = starTransactionDao.selectById(day1.getTransactionId());
        assertThat(txn.getBizType()).isEqualTo("game_reward");
        assertThat(txn.getAmount()).isEqualTo(7);
    }

    @Test
    @DisplayName("固定金额当日重领：返回原金额快照，决策回调不再执行")
    void decision_sameDay_returnsOriginalSnapshot() {
        atShanghaiNoon("2161-02-10");
        DailyRewardResult first = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(7));

        // 模拟配置上调后重试：回调若执行会抛错，验证已领取分支不触发决策
        DailyRewardResult retry = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> {
                    throw new IllegalStateException("已领取后不应再决策");
                });
        assertThat(retry.isAwarded()).isFalse();
        assertThat(retry.getAmount()).isEqualTo(7);
        assertThat(retry.getTransactionId()).isEqualTo(first.getTransactionId());

        assertThat(rewardsOf(USER_A)).hasSize(1);
        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", USER_A));
        assertThat(account.getBalance()).isEqualTo(7);
    }

    @Test
    @DisplayName("决策金额非法：零或负数拒绝（零金额应使用 skip 决策表达）")
    void decision_nonPositiveAmount_rejected() {
        assertThatThrownBy(() -> starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(-3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rewardsOf(USER_A)).isEmpty();
    }

    @Test
    @DisplayName("跳过决策：无奖励记录、无流水、不建账户，当日后续发奖决策仍可领取")
    void decision_skip_writesNothing_keepsEligibility() {
        atShanghaiNoon("2161-03-10");
        DailyRewardResult skipped = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.skip());
        assertThat(skipped.isSkipped()).isTrue();
        assertThat(skipped.isAwarded()).isFalse();

        assertThat(rewardsOf(USER_A)).isEmpty();
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", USER_A))).isZero();
        assertThat(starAccountDao.selectCount(new QueryWrapper<StarAccountEntity>()
                .eq("user_id", USER_A))).isZero();

        // 跳过不占资格：同一来源当日后续决策为发奖时正常入账
        DailyRewardResult awarded = starDailyRewardService.claimWithDecision(USER_A,
                xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "fixed_game",
                (uid, type, key, date) -> StarDailyRewardService.ClaimDecision.award(5));
        assertThat(awarded.isAwarded()).isTrue();
        assertThat(awarded.getAmount()).isEqualTo(5);
    }

    @Test
    @DisplayName("连续入口拒绝游戏来源：GAME 必须走锁内决策，防止金额随天数漂移")
    void streakEntry_rejectsGameSource() {
        assertThatThrownBy(() -> starDailyRewardService.claimDailyReward(USER_A, "GAME", REWARD_TEST_A))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rewardsOf(USER_A)).isEmpty();
    }
}
