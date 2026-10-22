package xiaozhi.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * DateUtils.millsToSecond 纯单元测试
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
}
