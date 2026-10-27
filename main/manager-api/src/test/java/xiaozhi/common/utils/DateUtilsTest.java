package xiaozhi.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * DateUtils 纯单元测试（millsToSecond、format 单参数重载、parse、getDateTimeNow、getShortTime）
 * 不依赖 Spring 上下文、数据库或 Redis
 */
public class DateUtilsTest {

    @Test
    public void millsToSecond_zero() {
        assertEquals("0.000", DateUtils.millsToSecond(0));
    }

    @Test
    public void millsToSecond_1500ms() {
        assertEquals("1.500", DateUtils.millsToSecond(1500));
    }

    @Test
    public void millsToSecond_123456ms() {
        assertEquals("123.456", DateUtils.millsToSecond(123456));
    }

    @Test
    public void format_withEpochDate_returnsDatePattern() {
        // 只断格式不断具体日期值，避免不同时区下 CI 漂移
        assertTrue(DateUtils.format(new Date(0L)).matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void format_withNullDate_returnsNull() {
        assertNull(DateUtils.format(null));
    }

    @Test
    public void parse_validDateTime_roundTrip() {
        // round-trip 只依赖 pattern 本身，与系统时区无关
        String text = "2026-10-23 14:05:06";
        Date parsed = DateUtils.parse(text, DateUtils.DATE_TIME_PATTERN);
        assertEquals(text, DateUtils.format(parsed, DateUtils.DATE_TIME_PATTERN));
    }

    @Test
    public void parse_invalidDate_returnsNull() {
        assertNull(DateUtils.parse("not-a-date", DateUtils.DATE_PATTERN));
    }

    @Test
    public void parse_withExplicitOffsetAndMillis_returnsExactInstant() {
        // 输入字符串自带 +08:00 显式时区偏移，解析结果与本机默认时区无关；
        // 期望值用独立的 UTC 绝对时间，同时验证偏移换算正确和 .789 毫秒被保留
        Date parsed = DateUtils.parse("2026-10-26T12:34:56.789+08:00", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        assertEquals(Date.from(Instant.parse("2026-10-26T04:34:56.789Z")), parsed);
    }

    @Test
    public void getDateTimeNow_defaultPattern_returnsDateTimeFormat() {
        // 断格式不断具体时间值，避免不同时区下断言漂移
        assertTrue(DateUtils.getDateTimeNow().matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"));
    }

    @Test
    public void getDateTimeNow_datePattern_returnsDateFormat() {
        // 断格式不断具体日期值，避免不同时区下断言漂移
        assertTrue(DateUtils.getDateTimeNow(DateUtils.DATE_PATTERN).matches("^\\d{4}-\\d{2}-\\d{2}$"));
    }

    @Test
    public void parseAndFormat_millisPattern_roundTrip() {
        // round-trip 只依赖 pattern 本身，与本机时区无关，验证毫秒位被保留
        String text = "2026-10-23 14:05:06.789";
        Date parsed = DateUtils.parse(text, DateUtils.DATE_TIME_MILLIS_PATTERN);
        assertEquals(text, DateUtils.format(parsed, DateUtils.DATE_TIME_MILLIS_PATTERN));
    }

    @Test
    public void getShortTime_withNullDate_returnsNull() {
        assertNull(DateUtils.getShortTime(null));
    }

    @Test
    public void getShortTime_nowWithin10Seconds_returnsJustNow() {
        // 传入当前时间，差值 ≤ 10 秒，返回“刚刚”
        assertEquals("刚刚", DateUtils.getShortTime(new Date()));
    }

    @Test
    public void getShortTime_30SecondsAgo_returnsSecondsAgo() {
        // 容忍 1 秒时钟漂移：30 秒前允许 29 或 30
        Date thatDate = Date.from(Instant.now().minus(30, ChronoUnit.SECONDS));
        assertTrue(DateUtils.getShortTime(thatDate).matches("^(29|30)秒前$"));
    }

    @Test
    public void getShortTime_5MinutesAgo_returnsMinutesAgo() {
        Date thatDate = Date.from(Instant.now().minus(5 * 60, ChronoUnit.SECONDS));
        assertTrue(DateUtils.getShortTime(thatDate).matches("^[45]分钟前$"));
    }

    @Test
    public void getShortTime_2HoursAgo_returnsHoursAgo() {
        Date thatDate = Date.from(Instant.now().minus(2 * 3600, ChronoUnit.SECONDS));
        assertTrue(DateUtils.getShortTime(thatDate).matches("^[12]小时前$"));
    }

    @Test
    public void getShortTime_3DaysAgo_returnsDaysAgo() {
        Date thatDate = Date.from(Instant.now().minus(3L * 86400, ChronoUnit.SECONDS));
        assertTrue(DateUtils.getShortTime(thatDate).matches("^[23]天前$"));
    }

    @Test
    public void getShortTime_10DaysAgo_returnsFormattedDateTime() {
        // 超过一周走 format 分支，结果与 DATE_TIME_PATTERN 格式化一致
        Date thatDate = Date.from(Instant.now().minus(10L * 86400, ChronoUnit.SECONDS));
        assertEquals(DateUtils.format(thatDate, DateUtils.DATE_TIME_PATTERN), DateUtils.getShortTime(thatDate));
    }
}
