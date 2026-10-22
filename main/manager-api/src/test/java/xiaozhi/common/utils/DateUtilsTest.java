package xiaozhi.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * DateUtils.millsToSecond 纯单元测试
 * 不依赖 Spring 上下文、数据库或 Redis
 */
public class DateUtilsTest {

    @Test
    public void millsToSecond_zero_returnsZeroPointZeroZeroZero() {
        // 0 毫秒 → "0.000"
        assertEquals("0.000", DateUtils.millsToSecond(0));
    }

    @Test
    public void millsToSecond_1500_returnsOnePointFiveZeroZero() {
        // 1500 毫秒 → "1.500"
        assertEquals("1.500", DateUtils.millsToSecond(1500));
    }

    @Test
    public void millsToSecond_123456_returns123PointFiveFiveSix() {
        // 123456 毫秒 → "123.456"
        assertEquals("123.456", DateUtils.millsToSecond(123456));
    }
}
