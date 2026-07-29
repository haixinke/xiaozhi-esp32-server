package xiaozhi.modules.pdc.nfc.service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;

@Component
public class PdcNfcClaimRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PdcNfcClaimRateLimiter.class);

    private static final int PREVIEW_USER_LIMIT = 30;
    private static final int PREVIEW_ASSET_LIMIT = 20;
    private static final int CONFIRM_USER_LIMIT = 10;
    private static final int CONFIRM_ASSET_LIMIT = 5;
    private static final int INVALID_REF_LIMIT = 10;

    private static final long MINUTE_TTL_SECONDS = 120L;
    private static final long TEN_MINUTE_TTL_SECONDS = 720L;

    private final RedisTemplate<String, Object> redisTemplate;

    public PdcNfcClaimRateLimiter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkPreviewUserRate(Long userId) {
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = RedisKeys.getNfcClaimPreviewUserKey(userId, epochMinute);
        checkRate(key, PREVIEW_USER_LIMIT, MINUTE_TTL_SECONDS);
    }

    public void checkPreviewAssetRate(Long assetId) {
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = RedisKeys.getNfcClaimPreviewAssetKey(assetId, epochMinute);
        checkRate(key, PREVIEW_ASSET_LIMIT, MINUTE_TTL_SECONDS);
    }

    public void checkConfirmUserRate(Long userId) {
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = RedisKeys.getNfcClaimConfirmUserKey(userId, epochMinute);
        checkRate(key, CONFIRM_USER_LIMIT, MINUTE_TTL_SECONDS);
    }

    public void checkConfirmAssetRate(Long assetId) {
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = RedisKeys.getNfcClaimConfirmAssetKey(assetId, epochMinute);
        checkRate(key, CONFIRM_ASSET_LIMIT, MINUTE_TTL_SECONDS);
    }

    public void recordInvalidRef(Long userId) {
        long epochTenMinute = Instant.now().getEpochSecond() / 600;
        String key = RedisKeys.getNfcClaimInvalidRefKey(userId, epochTenMinute);
        checkRate(key, INVALID_REF_LIMIT, TEN_MINUTE_TTL_SECONDS);
    }

    /**
     * Detects contention: if a second different userId hits the same asset within 10 minutes,
     * logs a security audit warning.
     */
    public void detectContention(Long assetId, Long userId) {
        long epochTenMinute = Instant.now().getEpochSecond() / 600;
        String key = RedisKeys.getNfcClaimContentionKey(assetId, epochTenMinute);
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        Long count = ops.increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, TEN_MINUTE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > 1) {
            log.warn("[NFC-SECURITY] Claim contention detected on assetId={}, userId={}, count={} in 10min window",
                    assetId, userId, count);
        }
    }

    private void checkRate(String key, int limit, long ttlSeconds) {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        Long count = ops.increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            throw new RenException(ErrorCode.PDC_NFC_RATE_LIMITED);
        }
    }
}
