package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.DailyNoteOpenedService;
import xiaozhi.modules.dailynote.service.impl.DailyNoteOpenedServiceImpl;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.service.impl.StarDailyRewardServiceImpl;

/**
 * 小纸条打开接口的星星奖励链路集成测试（真实 dev 库）。
 * 覆盖：打开即发奖、连续递增/封顶/重置、历史打开记录不参与连续、
 * 无内容不发奖、重复/并发上报不重复发奖。
 * 两个被测 Service 使用同一时钟 Bean（dailyNoteClock），用例拨钟对两侧同时生效。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("小纸条打开奖励集成测试")
class DailyNoteOpenedRewardTest {

    @Autowired
    private DailyNoteOpenedService dailyNoteOpenedService;
    @Autowired
    private DailyNoteOpenedDao dailyNoteOpenedDao;
    @Autowired
    private DailyNoteDao dailyNoteDao;
    /** 奖励记录 DAO 用 spy：在奖励落库点制造失败，验证打开记录+流水整体回滚 */
    @MockitoSpyBean
    private StarDailyRewardDao starDailyRewardDao;
    @Autowired
    private StarTransactionDao starTransactionDao;
    @Autowired
    private StarAccountDao starAccountDao;
    @Autowired
    private StarDailyRewardServiceImpl starDailyRewardServiceImpl;

    private static final long USER_A = 9_999_200_001L;
    private static final long USER_B = 9_999_200_002L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final List<Long> noteIds = new ArrayList<>();

    private void atShanghaiNoon(String date) {
        LocalDate day = LocalDate.parse(date);
        Instant noon = day.atTime(12, 0).atZone(SHANGHAI).toInstant();
        Clock fixed = Clock.fixed(noon, SHANGHAI);
        // 打开记录与每日奖励各持时钟引用（共享 dailyNoteClock Bean），两侧一起拨
        ((DailyNoteOpenedServiceImpl) dailyNoteOpenedService).setClock(fixed);
        starDailyRewardServiceImpl.setClock(fixed);
    }

    @BeforeEach
    void resetClock() {
        atShanghaiNoon("2160-12-30");
    }

    @AfterEach
    void cleanup() {
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
        dailyNoteOpenedDao.delete(new QueryWrapper<DailyNoteOpenedEntity>()
                .in("user_id", USER_A, USER_B)
                .ge("note_date", java.sql.Date.valueOf("2160-01-01")));
        starDailyRewardDao.delete(new QueryWrapper<StarDailyRewardEntity>()
                .in("user_id", USER_A, USER_B));
        starTransactionDao.delete(new QueryWrapper<StarTransactionEntity>()
                .in("user_id", USER_A, USER_B));
        starAccountDao.delete(new QueryWrapper<StarAccountEntity>()
                .in("user_id", USER_A, USER_B));
    }

    private void insertNote(String date, String title) {
        DailyNoteEntity entity = new DailyNoteEntity();
        entity.setNoteDate(LocalDate.parse(date));
        entity.setTitle(title);
        entity.setContent("正文-" + title);
        dailyNoteDao.insert(entity);
        noteIds.add(entity.getId());
    }

    private long balance(long userId) {
        StarAccountEntity account = starAccountDao.selectOne(
                new QueryWrapper<StarAccountEntity>().eq("user_id", userId));
        return account == null ? 0L : account.getBalance();
    }

    private List<StarDailyRewardEntity> rewards(long userId) {
        return starDailyRewardDao.selectList(new QueryWrapper<StarDailyRewardEntity>()
                .eq("user_id", userId).orderByAsc("reward_date"));
    }

    @Test
    @DisplayName("有当日小纸条时打开：写打开记录并发 11 颗星，奖励记录与流水一致")
    void opened_withNote_awardsEleven() {
        atShanghaiNoon("2160-01-10");
        insertNote("2160-01-10", "今日小纸条");

        dailyNoteOpenedService.markOpened(USER_A);

        assertThat(balance(USER_A)).isEqualTo(11);
        List<StarDailyRewardEntity> list = rewards(USER_A);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getAmount()).isEqualTo(11);
        assertThat(list.get(0).getSourceType()).isEqualTo("DAILY_NOTE");

        StarTransactionEntity txn = starTransactionDao.selectById(list.get(0).getStarTransactionId());
        assertThat(txn).isNotNull();
        assertThat(txn.getBizType()).isEqualTo("daily_note");
        assertThat(txn.getBalanceAfter()).isEqualTo(11);
    }

    @Test
    @DisplayName("打开接口连续两天：第二天发 12 颗，连续规则经接口生效")
    void opened_consecutiveDays_streakApplies() {
        atShanghaiNoon("2160-02-01");
        insertNote("2160-02-01", "第一天");
        dailyNoteOpenedService.markOpened(USER_A);

        atShanghaiNoon("2160-02-02");
        insertNote("2160-02-02", "第二天");
        dailyNoteOpenedService.markOpened(USER_A);

        List<StarDailyRewardEntity> list = rewards(USER_A);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getAmount()).isEqualTo(11);
        assertThat(list.get(1).getAmount()).isEqualTo(12);
        assertThat(balance(USER_A)).isEqualTo(23);
    }

    @Test
    @DisplayName("重复与并发上报：打开接口幂等，只发一次奖")
    void opened_repeatAndConcurrent_awardOnce() throws Exception {
        atShanghaiNoon("2160-03-10");
        insertNote("2160-03-10", "标题");

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    dailyNoteOpenedService.markOpened(USER_A);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 并发后再重复上报一次
        dailyNoteOpenedService.markOpened(USER_A);

        assertThat(failures.get()).isZero();
        assertThat(dailyNoteOpenedDao.selectCount(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", USER_A)
                .eq("note_date", java.sql.Date.valueOf("2160-03-10")))).isEqualTo(1);
        assertThat(rewards(USER_A)).hasSize(1);
        assertThat(balance(USER_A)).isEqualTo(11);
    }

    @Test
    @DisplayName("历史打开记录不参与连续：上线前已打开的记录不补发，且不构成昨天的成功奖励")
    void historicalOpened_notCountedNoBackfill() {
        // 模拟上线前的历史打开记录：只有 ai_daily_note_opened，没有每日奖励记录
        atShanghaiNoon("2160-04-09");
        DailyNoteOpenedEntity history = new DailyNoteOpenedEntity();
        history.setUserId(USER_A);
        history.setNoteDate(java.sql.Date.valueOf("2160-04-09"));
        history.setOpenedAt(new java.util.Date());
        dailyNoteOpenedDao.insert(history);

        // 上线当天（04-10）打开：历史记录不构成昨天奖励，应从 11 起
        atShanghaiNoon("2160-04-10");
        insertNote("2160-04-10", "上线日");
        dailyNoteOpenedService.markOpened(USER_A);

        List<StarDailyRewardEntity> list = rewards(USER_A);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getAmount()).isEqualTo(11);
        assertThat(list.get(0).getRewardDate()).isEqualTo(java.sql.Date.valueOf("2160-04-10"));
    }

    @Test
    @DisplayName("上线当天已打开的用户跳过当天奖励：不发奖，次日首次有效打开领 11")
    void openedBeforeLaunch_skipsToday_claimsTomorrow() {
        // 上线当天该用户已按旧语义打开过（只有打开记录，无奖励）
        atShanghaiNoon("2160-05-10");
        insertNote("2160-05-10", "上线日");
        DailyNoteOpenedEntity opened = new DailyNoteOpenedEntity();
        opened.setUserId(USER_A);
        opened.setNoteDate(java.sql.Date.valueOf("2160-05-10"));
        opened.setOpenedAt(new java.util.Date());
        dailyNoteOpenedDao.insert(opened);

        // 重复上报：幂等成功但不补发当天奖励
        dailyNoteOpenedService.markOpened(USER_A);
        assertThat(rewards(USER_A)).isEmpty();
        assertThat(balance(USER_A)).isZero();

        // 次日首次有效打开：从 11 起（昨天无成功奖励记录）
        atShanghaiNoon("2160-05-11");
        insertNote("2160-05-11", "次日");
        dailyNoteOpenedService.markOpened(USER_A);

        List<StarDailyRewardEntity> list = rewards(USER_A);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getAmount()).isEqualTo(11);
        assertThat(list.get(0).getRewardDate()).isEqualTo(java.sql.Date.valueOf("2160-05-11"));
    }

    @Test
    @DisplayName("无当日小纸条：成功空操作，不写打开记录也不发奖")
    void opened_noNote_noReward() {
        atShanghaiNoon("2160-06-10");

        dailyNoteOpenedService.markOpened(USER_A);

        assertThat(dailyNoteOpenedDao.selectCount(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", USER_A)
                .eq("note_date", java.sql.Date.valueOf("2160-06-10")))).isZero();
        assertThat(rewards(USER_A)).isEmpty();
        assertThat(balance(USER_A)).isZero();
    }

    @Test
    @DisplayName("纸条内容覆盖或同日删除重建：不重置已领取资格，重复打开不再发奖")
    void noteOverwriteOrRecreate_doesNotResetReward() {
        atShanghaiNoon("2160-07-10");
        insertNote("2160-07-10", "旧内容");
        dailyNoteOpenedService.markOpened(USER_A);
        assertThat(rewards(USER_A)).hasSize(1);

        // 运营同日删除并重建内容
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
        insertNote("2160-07-10", "重建内容");

        dailyNoteOpenedService.markOpened(USER_A);

        assertThat(rewards(USER_A)).hasSize(1);
        assertThat(balance(USER_A)).isEqualTo(11);
    }

    @Test
    @DisplayName("跨用户隔离：同日 A 领取不影响 B 的打开与发奖")
    void opened_crossUser_isolated() {
        atShanghaiNoon("2160-08-10");
        insertNote("2160-08-10", "标题");

        dailyNoteOpenedService.markOpened(USER_A);
        dailyNoteOpenedService.markOpened(USER_B);

        assertThat(rewards(USER_A)).hasSize(1);
        assertThat(rewards(USER_B)).hasSize(1);
        assertThat(balance(USER_A)).isEqualTo(11);
        assertThat(balance(USER_B)).isEqualTo(11);
    }

    @Test
    @DisplayName("奖励落库失败整体回滚：不留打开记录、流水与余额变动，重试可正常领取")
    void rewardFailure_rollsBackEntirely_retrySucceeds() {
        atShanghaiNoon("2160-09-10");
        insertNote("2160-09-10", "标题");

        // 在奖励落库点制造失败：此时打开记录已插入、钱包已入账，验证整体回滚而非留半成功状态
        Mockito.doThrow(new RuntimeException("模拟奖励落库失败"))
                .when(starDailyRewardDao).insert(Mockito.any(StarDailyRewardEntity.class));
        try {
            dailyNoteOpenedService.markOpened(USER_A);
            org.assertj.core.api.Assertions.fail("应当抛出模拟异常");
        } catch (RuntimeException expected) {
            // 预期失败
        } finally {
            Mockito.reset(starDailyRewardDao);
        }

        // 回滚后：无打开记录、无奖励、无流水、无余额变动
        assertThat(dailyNoteOpenedDao.selectCount(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", USER_A)
                .eq("note_date", java.sql.Date.valueOf("2160-09-10")))).isZero();
        assertThat(rewards(USER_A)).isEmpty();
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", USER_A))).isZero();
        assertThat(balance(USER_A)).isZero();

        // 重试可正常领取（不留已打开但未领奖的状态阻断补偿）
        dailyNoteOpenedService.markOpened(USER_A);
        assertThat(dailyNoteOpenedDao.selectCount(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", USER_A)
                .eq("note_date", java.sql.Date.valueOf("2160-09-10")))).isEqualTo(1);
        assertThat(rewards(USER_A)).hasSize(1);
        assertThat(balance(USER_A)).isEqualTo(11);
    }
}
