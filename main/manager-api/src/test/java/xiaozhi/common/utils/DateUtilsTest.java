package xiaozhi.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * DateUtils 纯单元测试（millsToSecond、format 单参数重载）
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
}
