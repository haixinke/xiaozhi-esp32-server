package xiaozhi.modules.pdc.nfc.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import xiaozhi.common.redis.RedisUtils;

/**
 * 基于 Redis 秒级 bucket 的分布式限速器。
 * <p>
 * Key: pdc:nfc:scheme:rate:{epochSecond}，TTL 2 秒。
 * 当某秒内计数超过 80 时，调用线程阻塞至下一秒。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pdc.nfc", name = "enabled", havingValue = "true")
public class PdcNfcSchemeRateLimiter {

    /** 每秒最大请求数 */
    public static final int MAX_RATE_PER_SECOND = 80;

    /** Redis key 前缀 */
    private static final String RATE_KEY_PREFIX = "pdc:nfc:scheme:rate:";

    /** key TTL（秒） */
    private static final long KEY_TTL_SECONDS = 2L;

    private final RedisUtils redisUtils;

    public PdcNfcSchemeRateLimiter(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    /**
     * 获取一个限速令牌。如果当前秒已满 80，则阻塞至下一秒。
     */
    public void acquire() {
        while (true) {
            long now = System.currentTimeMillis();
            long epochSecond = now / 1000L;
            String key = RATE_KEY_PREFIX + epochSecond;
            Long count = redisUtils.increment(key, KEY_TTL_SECONDS);
            if (count == null || count <= MAX_RATE_PER_SECOND) {
                return;
            }
            // 已超限，等待到下一秒
            long sleepMs = 1000L - (now % 1000L);
            if (sleepMs <= 0) {
                sleepMs = 1L;
            }
            log.debug("Scheme rate limit reached ({}), sleeping {}ms", count, sleepMs);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
