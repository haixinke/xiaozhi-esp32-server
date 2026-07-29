package xiaozhi.modules.pdc.nfc.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.MessageUtils;

@ExtendWith(MockitoExtension.class)
class PdcNfcClaimRateLimiterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private PdcNfcClaimRateLimiter rateLimiter;

    @BeforeAll
    static void initMessageSource() throws Exception {
        MessageSource mockSource = mock(MessageSource.class);
        lenient().when(mockSource.getMessage(anyString(), any(), any(), any(Locale.class)))
                .thenReturn("mock message");
        Field field = MessageUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockSource);
    }

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        rateLimiter = new PdcNfcClaimRateLimiter(redisTemplate);
    }

    @Test
    void previewUserRateLimit() {
        AtomicLong counter = new AtomicLong(0);
        when(valueOperations.increment(anyString())).thenAnswer(inv -> counter.incrementAndGet());

        for (int i = 0; i < 30; i++) {
            assertThatCode(() -> rateLimiter.checkPreviewUserRate(1L))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.checkPreviewUserRate(1L))
                .isInstanceOf(RenException.class);
    }

    @Test
    void previewAssetRateLimit() {
        AtomicLong counter = new AtomicLong(0);
        when(valueOperations.increment(anyString())).thenAnswer(inv -> counter.incrementAndGet());

        for (int i = 0; i < 20; i++) {
            assertThatCode(() -> rateLimiter.checkPreviewAssetRate(1L))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.checkPreviewAssetRate(1L))
                .isInstanceOf(RenException.class);
    }

    @Test
    void confirmUserRateLimit() {
        AtomicLong counter = new AtomicLong(0);
        when(valueOperations.increment(anyString())).thenAnswer(inv -> counter.incrementAndGet());

        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> rateLimiter.checkConfirmUserRate(1L))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.checkConfirmUserRate(1L))
                .isInstanceOf(RenException.class);
    }

    @Test
    void invalidRefRateLimit() {
        AtomicLong counter = new AtomicLong(0);
        when(valueOperations.increment(anyString())).thenAnswer(inv -> counter.incrementAndGet());

        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> rateLimiter.recordInvalidRef(1L))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.recordInvalidRef(1L))
                .isInstanceOf(RenException.class);
    }

    @Test
    void contentionDetection() {
        AtomicLong counter = new AtomicLong(0);
        when(valueOperations.increment(anyString())).thenAnswer(inv -> counter.incrementAndGet());

        rateLimiter.detectContention(1L, 100L);

        assertThatCode(() -> rateLimiter.detectContention(1L, 200L))
                .doesNotThrowAnyException();

        verify(valueOperations, times(2)).increment(anyString());
    }
}
