package xiaozhi.modules.wechat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.utils.SpringContextUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WechatAccessTokenProviderImpl 测试")
class WechatAccessTokenProviderImplTest {

    private static final String STABLE_TOKEN_OK =
            "{\"access_token\":\"token123\",\"expires_in\":7200,\"errcode\":0}";
    private static final String TOKEN_ERRCODE =
            "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}";
    private static final String TOKEN_EMPTY_TOKEN =
            "{\"access_token\":\"\",\"expires_in\":7200,\"errcode\":0}";

    @Mock
    private RedisUtils redisUtils;

    private String httpResponseBody;

    private WechatAccessTokenProviderImpl provider;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() throws Exception {
        httpResponseBody = STABLE_TOKEN_OK;

        provider = new WechatAccessTokenProviderImpl(redisUtils) {
            @Override
            String httpPost(String url, String jsonBody) {
                return httpResponseBody;
            }
        };
        setField(WechatAccessTokenProviderImpl.class, provider, "appid", "wxappid");
        setField(WechatAccessTokenProviderImpl.class, provider, "secret", "wxsecret");

        // 默认缓存未命中
        when(redisUtils.get(eq(RedisKeys.getWechatAccessTokenKey()))).thenReturn(null);
    }

    @Test
    @DisplayName("缓存命中：直接返回缓存 token，不发 HTTP")
    void returnsCachedTokenWithoutHttpCall() {
        when(redisUtils.get(RedisKeys.getWechatAccessTokenKey())).thenReturn("cached-token");

        assertThat(provider.getAccessToken()).isEqualTo("cached-token");
        verify(redisUtils, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("缓存未命中：调 stable_token 并写 Redis 缓存")
    void cacheMissCallsHttpAndCachesToken() {
        String token = provider.getAccessToken();

        assertThat(token).isEqualTo("token123");
        verify(redisUtils).set(eq(RedisKeys.getWechatAccessTokenKey()), eq("token123"), eq(6900L));
    }

    @Test
    @DisplayName("微信返回 errcode!=0：抛异常且不写缓存")
    void errcodeRejectsAndSkipsCache() {
        httpResponseBody = TOKEN_ERRCODE;

        assertThatThrownBy(() -> provider.getAccessToken())
                .isInstanceOf(RenException.class)
                .hasMessageContaining("获取微信access_token失败");
        verify(redisUtils, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("微信返回空 token：抛异常且不写缓存")
    void emptyTokenRejectsAndSkipsCache() {
        httpResponseBody = TOKEN_EMPTY_TOKEN;

        assertThatThrownBy(() -> provider.getAccessToken())
                .isInstanceOf(RenException.class)
                .hasMessageContaining("微信access_token为空");
        verify(redisUtils, never()).set(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("appid/secret 未配置：抛异常")
    void missingConfigRejects() throws Exception {
        setField(WechatAccessTokenProviderImpl.class, provider, "appid", "");

        assertThatThrownBy(() -> provider.getAccessToken())
                .isInstanceOf(RenException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("TTL 提前 300s 失效，下限 60s")
    void ttlHasSafetyMarginAndFloor() {
        httpResponseBody = "{\"access_token\":\"tok\",\"expires_in\":200,\"errcode\":0}";

        provider.getAccessToken();

        // 200 - 300 = -100, max(-100, 60) = 60
        verify(redisUtils).set(eq(RedisKeys.getWechatAccessTokenKey()), eq("tok"), eq(60L));
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value)
            throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
