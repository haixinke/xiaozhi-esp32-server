package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

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
import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.CompanionDaysService;
import xiaozhi.modules.dailynote.service.impl.CompanionDaysServiceImpl;

/**
 * 连续陪伴天数集成测试（真实 dev 库）。
 * 通过可替换的 Clock 固定"当天"为 2160 年的专用日期段，用户 ID 使用不可能存在的超大值，
 * 避免与真实用户数据冲突；每个用例自造打开记录（绕过 markOpened 的当日内容校验），
 * AfterEach 按专用用户+日期段物理清理。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("连续陪伴天数集成测试")
class CompanionDaysServiceTest {

    @Autowired
    private CompanionDaysService companionDaysService;
    @Autowired
    private DailyNoteOpenedDao dailyNoteOpenedDao;

    /** 本用例使用的专用用户 ID（远离真实用户区间），统一清理 */
    private static final long USER_A = 9_999_000_101L;
    private static final long USER_B = 9_999_000_102L;

    /** 把被测 Service 的可替换时钟拨到指定日期的正午（Asia/Shanghai），远离日界 */
    private void atShanghaiNoon(String date) {
        LocalDate day = LocalDate.parse(date);
        Instant noon = day.atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        ((CompanionDaysServiceImpl) companionDaysService)
                .setClock(Clock.fixed(noon, ZoneId.of("Asia/Shanghai")));
    }

    @BeforeEach
    void resetClock() {
        atShanghaiNoon("2160-06-15");
    }

    @AfterEach
    void cleanup() {
        dailyNoteOpenedDao.delete(new QueryWrapper<DailyNoteOpenedEntity>()
                .in("user_id", USER_A, USER_B)
                .ge("note_date", java.sql.Date.valueOf("2160-01-01")));
    }

    /** 直接落库一条打开记录（聚焦连续计算，不经过 markOpened 的当日内容校验） */
    private void insertOpened(long userId, String date) {
        DailyNoteOpenedEntity entity = new DailyNoteOpenedEntity();
        entity.setUserId(userId);
        entity.setNoteDate(java.sql.Date.valueOf(date));
        entity.setOpenedAt(new Date());
        dailyNoteOpenedDao.insert(entity);
    }

    @Test
    @DisplayName("无任何打开记录：连续陪伴天数为 0")
    void noRecordIsZero() {
        assertThat(companionDaysService.companionDays(USER_A)).isZero();
    }

    @Test
    @DisplayName("仅今天打开：连续陪伴天数为 1")
    void onlyTodayIsOne() {
        atShanghaiNoon("2160-06-15");
        insertOpened(USER_A, "2160-06-15");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("昨天和今天连续打开：连续陪伴天数为 2")
    void yesterdayAndTodayIsTwo() {
        atShanghaiNoon("2160-06-15");
        insertOpened(USER_A, "2160-06-14");
        insertOpened(USER_A, "2160-06-15");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(2);
    }

    @Test
    @DisplayName("今天未打开、昨天往前连续 3 天：断档当天仍显示昨天的连续数 3")
    void notOpenedTodayKeepsYesterdayStreak() {
        atShanghaiNoon("2160-06-15");
        insertOpened(USER_A, "2160-06-12");
        insertOpened(USER_A, "2160-06-13");
        insertOpened(USER_A, "2160-06-14");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(3);
    }

    @Test
    @DisplayName("今天未打开、昨天无记录、前天有记录：断档已生效，为 0")
    void gapAtYesterdayIsZero() {
        atShanghaiNoon("2160-06-15");
        insertOpened(USER_A, "2160-06-13");

        assertThat(companionDaysService.companionDays(USER_A)).isZero();
    }

    @Test
    @DisplayName("历史连续段断开后今天重新打开：只算最近连续段，为 1")
    void oldStreakBrokenRestartIsOne() {
        atShanghaiNoon("2160-06-15");
        // 历史连续 5 天，断 06-14，今天重新打开
        insertOpened(USER_A, "2160-06-08");
        insertOpened(USER_A, "2160-06-09");
        insertOpened(USER_A, "2160-06-10");
        insertOpened(USER_A, "2160-06-11");
        insertOpened(USER_A, "2160-06-12");
        insertOpened(USER_A, "2160-06-15");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("跨月连续：月末到月初日期正确累计")
    void crossMonthStreak() {
        atShanghaiNoon("2160-07-01");
        insertOpened(USER_A, "2160-06-29");
        insertOpened(USER_A, "2160-06-30");
        insertOpened(USER_A, "2160-07-01");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(3);
    }

    @Test
    @DisplayName("跨年连续：年末到年初日期正确累计")
    void crossYearStreak() {
        atShanghaiNoon("2161-01-01");
        insertOpened(USER_A, "2160-12-30");
        insertOpened(USER_A, "2160-12-31");
        insertOpened(USER_A, "2161-01-01");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(3);
    }

    @Test
    @DisplayName("日界附近：23:59 与次日 00:01 按 Asia/Shanghai 日界判定")
    void dayBoundaryShanghai() {
        // 23:59 仍属 06-15：今天未打开，锚昨天
        LocalDate day = LocalDate.parse("2160-06-15");
        Instant beforeMidnight = day.atTime(23, 59).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        ((CompanionDaysServiceImpl) companionDaysService)
                .setClock(Clock.fixed(beforeMidnight, ZoneId.of("Asia/Shanghai")));
        insertOpened(USER_A, "2160-06-14");
        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(1);

        // 拨到次日 00:01：昨天（06-15）无记录，断档生效
        Instant afterMidnight = day.plusDays(1).atTime(0, 1).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        ((CompanionDaysServiceImpl) companionDaysService)
                .setClock(Clock.fixed(afterMidnight, ZoneId.of("Asia/Shanghai")));
        assertThat(companionDaysService.companionDays(USER_A)).isZero();
    }

    @Test
    @DisplayName("跨用户隔离：A 的连续段不影响 B")
    void crossUserIsolation() {
        atShanghaiNoon("2160-06-15");
        insertOpened(USER_A, "2160-06-14");
        insertOpened(USER_A, "2160-06-15");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(2);
        assertThat(companionDaysService.companionDays(USER_B)).isZero();
    }

    @Test
    @DisplayName("乱序插入不依赖物理顺序：uk+ORDER BY 保证日期序正确")
    void outOfOrderInsertStillCorrect() {
        atShanghaiNoon("2160-06-15");
        // 故意倒序/乱序插入：06-15, 06-12, 06-14, 06-13（缺 06-11 以前）
        insertOpened(USER_A, "2160-06-15");
        insertOpened(USER_A, "2160-06-12");
        insertOpened(USER_A, "2160-06-14");
        insertOpened(USER_A, "2160-06-13");

        assertThat(companionDaysService.companionDays(USER_A)).isEqualTo(4);
    }
}
