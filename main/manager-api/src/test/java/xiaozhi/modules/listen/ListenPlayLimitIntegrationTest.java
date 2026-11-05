package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GamePlayLimitService;
import xiaozhi.modules.game.service.GameStarRewardService;
import xiaozhi.modules.game.service.impl.GamePlayLimitServiceImpl;
import xiaozhi.modules.game.vo.GameStarRewardResult;
import xiaozhi.modules.listen.dao.ListenAnswerDao;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.service.ListenAnswerService;
import xiaozhi.modules.listen.service.impl.ListenAnswerServiceImpl;
import xiaozhi.modules.listen.vo.ListenSubmitResultVO;

/** 隔离 MySQL 验证真实用户行锁与整组计局，不连接运营库或修改业务字典。 */
@Testcontainers
class ListenPlayLimitIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("listen_play_limit_test");
    private static AnnotationConfigApplicationContext context;
    private ListenAnswerService listen;
    private GameDao games;

    @BeforeAll
    static void open() { context = new AnnotationConfigApplicationContext(Config.class); }

    @AfterAll
    static void close() { if (context != null) context.close(); }

    @BeforeEach
    void prepare() {
        MessageSourceMockSupport.install();
        listen = context.getBean(ListenAnswerService.class);
        games = context.getBean(GameDao.class);
        var game = new GameEntity();
        game.setDailyLimit(2);
        when(games.selectByGameType("LISTEN_TO_YOU")).thenReturn(game);
        var jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("DELETE FROM ai_listen_answer");
        jdbc.update("DELETE FROM ai_listen_question");
        jdbc.update("DELETE FROM ai_listen_group");
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("INSERT INTO sys_user(id) VALUES (123)");
        // 每组两道题，确保一局产生多行答案而不是一题一局。
        for (long group = 1; group <= 8; group++) {
            jdbc.update("INSERT INTO ai_listen_group(id, category_code, name) VALUES (?, 'TEST_ONLY', ?)",
                    group, "test-group-" + group);
            for (int index = 0; index < 2; index++) {
                jdbc.update("INSERT INTO ai_listen_question(id, group_id, text, options_json) VALUES (?, ?, ?, ?)",
                        group * 10 + index, group, "test-question-" + index,
                        "[{\"key\":\"A\",\"text\":\"test-option\"}]");
            }
        }
    }

    @Test
    void concurrentGroupsCannotExceedConfiguredDailyPlays() throws Exception {
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (long group = 1; group <= 8; group++) {
                long id = group;
                futures.add(executor.submit(() -> {
                    ready.countDown(); start.await();
                    try { return listen.submitAnswers(123L, request(id)); }
                    catch (RenException error) { return error; }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));

            assertThat(results.stream().filter(ListenSubmitResultVO.class::isInstance).count()).isEqualTo(2);
            assertThat(results.stream().filter(RenException.class::isInstance).map(RenException.class::cast)
                    .map(RenException::getCode)).containsOnly(ErrorCode.LISTEN_DAILY_LIMIT_REACHED).hasSize(6);
            assertThatThrownBy(() -> listen.draw(123L)).isInstanceOf(RenException.class)
                    .extracting("code").isEqualTo(ErrorCode.LISTEN_DAILY_LIMIT_REACHED);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void wholeGroupAndRetriesUseOnePlayAndCurrentConfigurationTakesEffect() {
        assertThat(listen.submitAnswers(123L, request(1)).getRewardStatus()).isEqualTo("NO_REWARD");
        assertThat(listen.submitAnswers(123L, request(1)).getRewardStatus()).isEqualTo("NO_REWARD");
        assertThat(listen.submitAnswers(123L, request(2)).getRewardStatus()).isEqualTo("NO_REWARD");
        assertThatThrownBy(() -> listen.submitAnswers(123L, request(3))).isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.LISTEN_DAILY_LIMIT_REACHED);

        var changed = new GameEntity(); changed.setDailyLimit(3);
        when(games.selectByGameType("LISTEN_TO_YOU")).thenReturn(changed);
        assertThat(listen.submitAnswers(123L, request(3)).getRewardStatus()).isEqualTo("NO_REWARD");
        assertThatThrownBy(() -> listen.draw(123L)).isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.LISTEN_DAILY_LIMIT_REACHED);
    }

    @Test
    void recordsBeforeTodayAndAtTomorrowDoNotConsumeTodaysQuota() {
        var jdbc = context.getBean(JdbcTemplate.class);
        var day = java.time.LocalDate.of(2026, 11, 5);
        var zone = ZoneId.of("Asia/Shanghai");
        jdbc.update("INSERT INTO ai_listen_answer(id,user_id,group_id,question_id,option_key,create_date) "
                        + "VALUES (1,123,1,10,'A',?), (2,123,2,20,'A',?)",
                Date.from(day.atStartOfDay(zone).toInstant().minusSeconds(1)),
                Date.from(day.plusDays(1).atStartOfDay(zone).toInstant()));
        var game = new GameEntity(); game.setDailyLimit(1);
        when(games.selectByGameType("LISTEN_TO_YOU")).thenReturn(game);

        assertThat(listen.draw(123L)).isNotNull();
        assertThat(listen.submitAnswers(123L, request(3)).getRewardStatus()).isEqualTo("NO_REWARD");
        assertThatThrownBy(() -> listen.draw(123L)).isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.LISTEN_DAILY_LIMIT_REACHED);
    }

    private static ListenAnswerSubmitDTO request(long group) {
        var dto = new ListenAnswerSubmitDTO();
        dto.setGroupId(group);
        List<ListenAnswerSubmitDTO.AnswerItem> items = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            var item = new ListenAnswerSubmitDTO.AnswerItem();
            item.setQuestionId(group * 10 + index); item.setOptionKey("A"); items.add(item);
        }
        dto.setAnswers(items);
        return dto;
    }

    /** 仅装载答题真实持久化与事务；运营配置和既有奖励入口为边界桩。 */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(name = "dailyNoteClock") Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-05T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
        @Bean DataSource dataSource() throws Exception {
            var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            String questionSql = new ClassPathResource("db/changelog/202610281000.sql")
                    .getContentAsString(StandardCharsets.UTF_8).split("-- 题目大类字典大类")[0];
            new ResourceDatabasePopulator(new ByteArrayResource(questionSql.getBytes(StandardCharsets.UTF_8)),
                    new ClassPathResource("db/changelog/202610281200.sql"),
                    new ClassPathResource("db/changelog/202610281300.sql")).execute(ds);
            new JdbcTemplate(ds).execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory factory(DataSource ds, Clock clock) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            var global = new GlobalConfig(); global.setDbConfig(new GlobalConfig.DbConfig());
            global.setMetaObjectHandler(new MetaObjectHandler() {
                @Override public void insertFill(MetaObject meta) {
                    setFieldValByName("createDate", Date.from(clock.instant()), meta);
                }
                @Override public void updateFill(MetaObject meta) { }
            });
            factory.setGlobalConfig(global);
            var result = factory.getObject();
            for (var mapper : List.of(ListenAnswerDao.class, ListenGroupDao.class, ListenQuestionDao.class)) {
                result.getConfiguration().addMapper(mapper);
            }
            return result;
        }
        @Bean SqlSessionTemplate sql(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean ListenAnswerDao answers(SqlSessionTemplate sql) { return sql.getMapper(ListenAnswerDao.class); }
        @Bean ListenGroupDao groups(SqlSessionTemplate sql) { return sql.getMapper(ListenGroupDao.class); }
        @Bean ListenQuestionDao questions(SqlSessionTemplate sql) { return sql.getMapper(ListenQuestionDao.class); }
        @Bean GameDao games() { return mock(GameDao.class); }
        @Bean GamePlayLimitService limits(GameDao games, ListenAnswerDao answers, Clock clock) {
            return new GamePlayLimitServiceImpl(games, mock(DrawingSessionDao.class), answers, clock);
        }
        @Bean GameStarRewardService rewards() {
            var reward = mock(GameStarRewardService.class);
            when(reward.claimGameReward(any(), any())).thenReturn(GameStarRewardResult.noReward());
            return reward;
        }
        @Bean ListenAnswerService listen(ListenGroupDao groups, ListenQuestionDao questions, ListenAnswerDao answers,
                GameStarRewardService rewards, GamePlayLimitService limits) {
            return new ListenAnswerServiceImpl(groups, questions, answers, rewards, mock(ApplicationEventPublisher.class), limits);
        }
    }
}
