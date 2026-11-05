package xiaozhi.modules.drawing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javax.sql.DataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.drawing.dto.DrawingStartDTO;
import xiaozhi.modules.drawing.service.DrawingService;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.star.dao.*;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.service.StarService;
import xiaozhi.modules.star.service.impl.StarDailyRewardServiceImpl;
import xiaozhi.modules.star.service.impl.StarServiceImpl;

/** 独立 MySQL 8 容器验证实际行锁、事务回滚与跨日幂等；不连接开发库或运营游戏配置。 */
@Testcontainers
class DrawingTransactionIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("drawing_isolated_test").withCommand("--log-bin-trust-function-creators=1");
    private static AnnotationConfigApplicationContext context;
    private DrawingService drawing;
    private StarService stars;
    private JdbcTemplate jdbc;
    private GameDao games;
    private MutableClock clock;

    @BeforeAll
    static void open() { context = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll
    static void close() { if (context != null) context.close(); }

    @BeforeEach
    void resetFixtures() {
        MessageSourceMockSupport.install();
        drawing = context.getBean(DrawingService.class); stars = context.getBean(StarService.class);
        games = context.getBean(GameDao.class); clock = context.getBean(MutableClock.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("DROP TRIGGER IF EXISTS drawing_insert_failure");
        jdbc.execute("DROP TRIGGER IF EXISTS drawing_update_failure");
        for (String table : List.of("ai_drawing_session", "ai_star_daily_reward", "ai_star_transaction", "ai_star_account", "sys_user")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO sys_user(id) VALUES (123)");
        jdbc.update("INSERT INTO ai_star_account(user_id,balance) VALUES (123,15)");
        reset(games);
        reset(context.getBean(StarDailyRewardDao.class));
        when(games.selectByGameType(anyString())).thenReturn(game(0, 7, 2, 1));
        clock.current = Instant.parse("2026-11-04T12:00:00Z");
    }

    @Test
    void concurrentDistinctStartsRespectUserLimitWithoutCharging() throws Exception {
        List<Object> results = concurrent(8, index -> drawing.start(123L, request("parallel-" + index)));
        assertThat(results.stream().filter(value -> !(value instanceof Throwable))).hasSize(2);
        assertThat(results.stream().filter(value -> value instanceof RenException)
                .map(value -> ((RenException) value).getCode())).containsOnly(ErrorCode.DRAWING_DAILY_LIMIT);
        assertThat(stars.balance(123L)).isEqualTo(15);
        assertThat(drawing.config(123L, "pet-test").remainingPlays()).isZero();
    }

    @Test
    void concurrentSameRequestCountsOnlyOnceEvenIfPetOrConfigurationLaterChanges() throws Exception {
        List<Object> results = concurrent(8, index -> drawing.start(123L, request("same-request")));
        assertThat(results).noneMatch(value -> value instanceof Throwable);
        assertThat(results.stream().map(value -> ((xiaozhi.modules.drawing.vo.DrawingStartVO) value).sessionId())
                .distinct()).hasSize(1);
        assertThat(stars.balance(123L)).isEqualTo(15);
        assertThat(drawing.config(123L, "pet-test").remainingPlays()).isEqualTo(1);
        when(games.selectByGameType(anyString())).thenReturn(game(0, 99, 2, 0));
        var replay = drawing.start(123L, request("same-request"));
        assertThat(replay.sessionId()).isEqualTo(((xiaozhi.modules.drawing.vo.DrawingStartVO) results.get(0)).sessionId());
        assertThat(stars.balance(123L)).isEqualTo(15);
    }

    @Test
    void databaseInsertFailureDoesNotSpendPlayAllowanceOrTouchWallet() {
        jdbc.execute("CREATE TRIGGER drawing_insert_failure BEFORE INSERT ON ai_drawing_session "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated insert failure'");
        assertThatThrownBy(() -> drawing.start(123L, request("rollback-request"))).isInstanceOf(RuntimeException.class);
        assertThat(stars.balance(123L)).isEqualTo(15);
        assertThat(drawing.config(123L, "pet-test").remainingPlays()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_star_transaction", Integer.class)).isZero();
    }

    @Test
    void insufficientBalanceLeavesSessionIncompleteAndConcurrentCompletionRetriesChargeOnce() throws Exception {
        when(games.selectByGameType(anyString())).thenReturn(game(0, 16, 2, 1));
        var started = drawing.start(123L, request("poor-request"));
        assertThat(stars.balance(123L)).isEqualTo(15);
        assertThatThrownBy(() -> drawing.complete(123L, started.sessionId())).isInstanceOf(RenException.class);
        assertThat(stars.balance(123L)).isEqualTo(15);
        assertThat(drawing.config(123L, "pet-test").remainingPlays()).isEqualTo(1);
        jdbc.update("UPDATE ai_star_account SET balance=20 WHERE user_id=123");
        assertThat(concurrent(8, index -> drawing.complete(123L, started.sessionId())))
                .noneMatch(value -> value instanceof Throwable);
        assertThat(stars.balance(123L)).isEqualTo(4);
    }

    @Test
    void completionWriteFailureRollsBackChargeAndCanRetry() {
        var started = drawing.start(123L, request("update-failure"));
        jdbc.execute("CREATE TRIGGER drawing_update_failure BEFORE UPDATE ON ai_drawing_session "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated update failure'");
        assertThatThrownBy(() -> drawing.complete(123L, started.sessionId())).isInstanceOf(RuntimeException.class);
        assertThat(stars.balance(123L)).isEqualTo(15);
        jdbc.execute("DROP TRIGGER drawing_update_failure");
        assertThat(drawing.complete(123L, started.sessionId()).balance()).isEqualTo(8);
        assertThat(drawing.complete(123L, started.sessionId()).balance()).isEqualTo(8);
    }

    @Test
    void crossMidnightCompletionUsesCurrentAwardAndRetryNextDayNeverAwardsAgain() {
        when(games.selectByGameType(anyString())).thenReturn(game(7, 0, 2, 1));
        clock.current = Instant.parse("2026-11-04T15:59:50Z");
        var first = drawing.start(123L, request("cross-day-start"));
        when(games.selectByGameType(anyString())).thenReturn(game(9, 0, 2, 1));
        clock.current = Instant.parse("2026-11-04T16:00:01Z");
        var result = drawing.complete(123L, first.sessionId());
        assertThat(result.awardedStars()).isEqualTo(9);
        assertThat(stars.balance(123L)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT started_day FROM ai_drawing_session", String.class)).isEqualTo("2026-11-04");
        assertThat(jdbc.queryForObject("SELECT reward_date FROM ai_star_daily_reward", String.class)).isEqualTo("2026-11-05");
        clock.current = Instant.parse("2026-11-05T16:00:01Z");
        stars.earn(123L, xiaozhi.modules.star.enums.StarEarnBizType.ADMIN_GRANT,
                "test-later-grant", 5, "测试后续奖励");
        when(games.selectByGameType(anyString())).thenReturn(null);
        var replay = drawing.complete(123L, first.sessionId());
        assertThat(replay.awardedStars()).isEqualTo(9);
        assertThat(replay.completedAt()).isEqualTo(result.completedAt());
        assertThat(replay.balance()).isEqualTo(29);
        assertThat(stars.balance(123L)).isEqualTo(29);
    }

    @Test
    void concurrentCompletedSessionsRewardOnlyOnceAndFreeSessionDoesNotUseRewardEligibility() throws Exception {
        when(games.selectByGameType(anyString())).thenReturn(game(0, 0, 0, 1));
        var free = drawing.start(123L, request("free-session"));
        clock.current = clock.current.plusSeconds(10);
        assertThat(drawing.complete(123L, free.sessionId()).rewardStatus())
                .isEqualTo("NO_REWARD");
        when(games.selectByGameType(anyString())).thenReturn(game(7, 0, 0, 1));
        var a = drawing.start(123L, request("reward-session-a"));
        var b = drawing.start(123L, request("reward-session-b"));
        clock.current = clock.current.plusSeconds(10);
        List<Object> results = concurrent(2, index -> drawing.complete(123L,
                index == 0 ? a.sessionId() : b.sessionId()));
        assertThat(results).noneMatch(value -> value instanceof Throwable);
        assertThat(results.stream().map(value -> ((xiaozhi.modules.drawing.vo.DrawingCompleteVO) value).awardedStars()))
                .containsExactlyInAnyOrder(7L, 0L);
        assertThat(stars.balance(123L)).isEqualTo(22);
    }

    @Test
    void distinctUsersFirstRewardUnderRepeatableReadDoesNotDeadlockOnMissingRewardGap() throws Exception {
        jdbc.update("INSERT INTO sys_user(id) VALUES (124)");
        jdbc.update("INSERT INTO ai_star_account(user_id,balance) VALUES (124,15)");
        var rewards = context.getBean(StarDailyRewardDao.class);
        var realRewards = context.getBean(SqlSessionTemplate.class).getMapper(StarDailyRewardDao.class);
        var missingReadBarrier = new CyclicBarrier(2);
        var reads = ThreadLocal.withInitial(() -> 0);
        doAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.Wrapper<xiaozhi.modules.star.entity.StarDailyRewardEntity> query = invocation.getArgument(0);
            var result = realRewards.selectOne(query);
            int count = reads.get() + 1; reads.set(count);
            // 两事务各持自己的钱包行锁，并都查到奖励不存在后才允许继续 INSERT。
            if (count == 2 && result == null) missingReadBarrier.await(10, TimeUnit.SECONDS);
            return result;
        }).when(rewards).selectOne(any());
        var daily = context.getBean(StarDailyRewardService.class);
        var results = concurrent(2, index -> {
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            return tx.execute(status -> daily.claimWithDecision(123L + index,
                    xiaozhi.modules.star.enums.DailyRewardSourceType.GAME, "RR_TEST_GAME",
                    (user, type, key, day) -> StarDailyRewardService.ClaimDecision.award(7)));
        });
        assertThat(results).noneMatch(value -> value instanceof Throwable);
        assertThat(stars.balance(123L)).isEqualTo(22);
        assertThat(stars.balance(124L)).isEqualTo(22);
    }

    @Test
    void actualDailyNoteEntryUsesReadCommittedAndConcurrentUsersCanClaimWithoutGapLocks() throws Exception {
        jdbc.update("INSERT INTO sys_user(id) VALUES (124)");
        jdbc.update("INSERT INTO ai_star_account(user_id,balance) VALUES (124,15)");
        var rewards = context.getBean(StarDailyRewardDao.class);
        var realRewards = context.getBean(SqlSessionTemplate.class).getMapper(StarDailyRewardDao.class);
        var missingReadBarrier = new CyclicBarrier(2);
        var reads = ThreadLocal.withInitial(() -> 0);
        doAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.Wrapper<xiaozhi.modules.star.entity.StarDailyRewardEntity> query = invocation.getArgument(0);
            var result = realRewards.selectOne(query);
            var wrapper = (com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>) query;
            wrapper.getSqlSegment();
            boolean todayRead = wrapper.getParamNameValuePairs().containsValue(java.sql.Date.valueOf(java.time.LocalDate.now(clock)));
            int count = reads.get() + (todayRead ? 1 : 0); reads.set(count);
            if (todayRead && count == 2 && result == null) {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                        .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
                missingReadBarrier.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(rewards).selectOne(any());
        var opened = context.getBean(xiaozhi.modules.dailynote.service.impl.DailyNoteOpenedServiceImpl.class);
        var results = concurrent(2, index -> opened.markOpened(123L + index));
        assertThat(results).noneMatch(value -> value instanceof Throwable);
        assertThat(stars.balance(123L)).isEqualTo(26);
        assertThat(stars.balance(124L)).isEqualTo(26);
    }

    private List<Object> concurrent(int count, java.util.function.IntFunction<Object> action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count); var start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> { ready.countDown(); start.await();
                    try { return action.apply(index); } catch (Throwable failure) { return failure; }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    private static DrawingStartDTO request(String requestId) {
        var dto = new DrawingStartDTO(); dto.setPetId("pet-test"); dto.setRequestId(requestId);
        return dto;
    }
    private static GameEntity game(int earn, int consume, int limit, int enabled) {
        var game = new GameEntity(); game.setEarnStars(earn); game.setConsumeStars(consume);
        game.setDailyLimit(limit); game.setStatus(enabled); return game;
    }

    /** 可控上海时钟用于真实事务的跨日测试，不等待真实时间。 */
    static class MutableClock extends Clock {
        volatile Instant current = Instant.parse("2026-11-04T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }

    /** 只加载绘画与钱包真实组件，配置和宠物来自测试桩，数据源仅指向容器。 */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            var sql = new ResourceDatabasePopulator(new ClassPathResource("db/changelog/202610081000.sql"),
                    new ClassPathResource("db/changelog/202610301000.sql"),
                    new ClassPathResource("db/changelog/202611041000.sql"),
                    new ClassPathResource("db/changelog/202611051000.sql"));
            sql.execute(ds); new JdbcTemplate(ds).execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
            return ds;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
            var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            var global = new GlobalConfig(); global.setDbConfig(new GlobalConfig.DbConfig());
            global.setMetaObjectHandler(new MetaObjectHandler() {
                @Override public void insertFill(MetaObject meta) {
                    if (meta.hasSetter("createDate")) setFieldValByName("createDate", new java.util.Date(), meta);
                }
                @Override public void updateFill(MetaObject meta) { }
            });
            factory.setGlobalConfig(global);
            var result = factory.getObject();
            for (Class<?> mapper : List.of(DrawingSessionDao.class, StarAccountDao.class, StarTransactionDao.class, StarDailyRewardDao.class)) {
                result.getConfiguration().addMapper(mapper);
            }
            return result;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean DrawingSessionDao sessions(SqlSessionTemplate sql) { return sql.getMapper(DrawingSessionDao.class); }
        @Bean StarAccountDao accounts(SqlSessionTemplate sql) { return sql.getMapper(StarAccountDao.class); }
        @Bean StarTransactionDao transactions(SqlSessionTemplate sql) { return sql.getMapper(StarTransactionDao.class); }
        @Bean StarDailyRewardDao rewards(SqlSessionTemplate sql) {
            return mock(StarDailyRewardDao.class, org.mockito.AdditionalAnswers.delegatesTo(sql.getMapper(StarDailyRewardDao.class)));
        }
        @Bean GameDao games() { return mock(GameDao.class); }
        @Bean PetDao pets() {
            var pets = mock(PetDao.class); var pet = new PetEntity();
            pet.setUserId(123L); pet.setHatchStatus("HATCHED"); when(pets.selectById("pet-test")).thenReturn(pet);
            return pets;
        }
        @Bean(name = "dailyNoteClock") MutableClock clock() { return new MutableClock(); }
        @Bean StarService stars(StarAccountDao accounts, StarTransactionDao transactions, ApplicationContext app) {
            return new StarServiceImpl(accounts, transactions, app);
        }
        @Bean StarDailyRewardService dailyRewards(StarDailyRewardDao rewards, StarAccountDao accounts,
                StarService stars, MutableClock clock) { return new StarDailyRewardServiceImpl(rewards, accounts, stars, clock); }
        @Bean DrawingService drawing(DrawingSessionDao sessions, GameDao games, PetDao pets, StarDailyRewardDao rewards,
                StarService stars, StarDailyRewardService dailyRewards, MutableClock clock) {
            return new DrawingService(sessions, games, pets, rewards, stars, dailyRewards,
                    new xiaozhi.modules.game.service.impl.GamePlayLimitServiceImpl(games, sessions,
                            mock(xiaozhi.modules.listen.dao.ListenAnswerDao.class), clock), clock);
        }
        @Bean xiaozhi.modules.dailynote.service.impl.DailyNoteOpenedServiceImpl opened(
                StarDailyRewardService dailyRewards, MutableClock clock) {
            var notes = mock(xiaozhi.modules.dailynote.dao.DailyNoteDao.class);
            when(notes.selectCount(any())).thenReturn(1L);
            var opened = mock(xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao.class);
            return new xiaozhi.modules.dailynote.service.impl.DailyNoteOpenedServiceImpl(opened, notes, dailyRewards, clock);
        }
    }
}
