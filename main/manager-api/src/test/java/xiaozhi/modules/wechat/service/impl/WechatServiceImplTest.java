package xiaozhi.modules.wechat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.dto.WechatBindPhoneRespDTO;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WechatServiceImpl - bindPhone")
class WechatServiceImplTest {

    private static final String STABLE_TOKEN_OK =
            "{\"access_token\":\"token123\",\"expires_in\":7200,\"errcode\":0}";
    private static final String PHONE_OK =
            "{\"errcode\":0,\"phone_info\":{\"phoneNumber\":\"13800138000\"}}";
    private static final String PHONE_ERRCODE =
            "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
    private static final String TOKEN_ERRCODE =
            "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}";

    @Mock
    private WechatUserDao wechatUserDao;
    @Mock
    private SysUserDao sysUserDao;
    @Mock
    private SysUserTokenService sysUserTokenService;
    @Mock
    private AgentService agentService;
    @Mock
    private InviteService inviteService;
    @Mock
    private xiaozhi.common.redis.RedisUtils redisUtils;

    private List<String> postedUrls;
    private String stableTokenBody;
    private String phoneBody;

    private WechatServiceImpl service;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any(String.class), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() throws Exception {
        postedUrls = new ArrayList<>();
        stableTokenBody = STABLE_TOKEN_OK;
        phoneBody = PHONE_OK;

        service = new WechatServiceImpl(sysUserDao, sysUserTokenService, agentService,
                inviteService, redisUtils, null) {
            @Override
            String httpPost(String url, String jsonBody) {
                postedUrls.add(url);
                if (url.contains("stable_token")) {
                    return stableTokenBody;
                }
                if (url.contains("getuserphonenumber")) {
                    return phoneBody;
                }
                return "";
            }
        };
        setField(BaseServiceImpl.class, service, "baseDao", wechatUserDao);
        setField(WechatServiceImpl.class, service, "appid", "wxappid");
        setField(WechatServiceImpl.class, service, "secret", "wxsecret");

        when(wechatUserDao.selectOne(any())).thenReturn(wechatUser(7L, "openid-xyz"));
        when(wechatUserDao.update(any(), any())).thenReturn(1);
        // 默认 access_token 缓存未命中
        when(redisUtils.get(eq(RedisKeys.getWechatAccessTokenKey()))).thenReturn(null);
    }

    @Test
    @DisplayName("成功：缓存未命中→取 token→取手机号→按 openid 更新→返回脱敏号")
    void bindPhone_success() {
        WechatBindPhoneRespDTO resp = service.bindPhone(7L, "phone-code");

        assertThat(resp.getPhone()).isEqualTo("138****8000");
        assertThat(postedUrls).hasSize(2);
        assertThat(postedUrls.get(0)).contains("stable_token");
        assertThat(postedUrls.get(1)).contains("getuserphonenumber");
        verify(redisUtils).set(eq(RedisKeys.getWechatAccessTokenKey()), eq("token123"), anyLong());
        verify(wechatUserDao).update(any(), any());
    }

    @Test
    @DisplayName("access_token 缓存命中：不再调 stable_token，仅调手机号接口")
    void bindPhone_tokenCacheHit() {
        when(redisUtils.get(eq(RedisKeys.getWechatAccessTokenKey()))).thenReturn("cached-token");

        WechatBindPhoneRespDTO resp = service.bindPhone(7L, "phone-code");

        assertThat(resp.getPhone()).isEqualTo("138****8000");
        assertThat(postedUrls).hasSize(1);
        assertThat(postedUrls.get(0)).contains("getuserphonenumber");
        verify(redisUtils, never()).set(any(), any(), anyLong());
    }

    @Test
    @DisplayName("当前账号不是微信登录账号：抛异常且不写库")
    void bindPhone_userNotFound() {
        when(wechatUserDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.bindPhone(7L, "phone-code"))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("不是微信登录账号");
        verify(wechatUserDao, never()).update(any(), any());
    }

    @Test
    @DisplayName("phoneCode 为空抛 NOT_NULL")
    void bindPhone_blankPhoneCode() {
        assertThatThrownBy(() -> service.bindPhone(7L, " "))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(10001);
    }

    @Test
    @DisplayName("未登录抛 USER_NOT_LOGIN")
    void bindPhone_notLogin() {
        assertThatThrownBy(() -> service.bindPhone(null, "phone-code"))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(10044);
    }

    @Test
    @DisplayName("appid/secret 未配置抛异常")
    void bindPhone_appidMissing() throws Exception {
        setField(WechatServiceImpl.class, service, "appid", "");

        assertThatThrownBy(() -> service.bindPhone(7L, "phone-code"))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("微信手机号接口 errcode!=0：抛 获取微信手机号失败")
    void bindPhone_phoneErrcode() {
        when(redisUtils.get(eq(RedisKeys.getWechatAccessTokenKey()))).thenReturn("cached-token");
        phoneBody = PHONE_ERRCODE;

        assertThatThrownBy(() -> service.bindPhone(7L, "phone-code"))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("获取微信手机号失败");
        verify(wechatUserDao, never()).update(any(), any());
    }

    @Test
    @DisplayName("微信 access_token 接口 errcode!=0：抛 获取微信access_token失败 且不写缓存")
    void bindPhone_tokenErrcode() {
        stableTokenBody = TOKEN_ERRCODE;

        assertThatThrownBy(() -> service.bindPhone(7L, "phone-code"))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("获取微信access_token失败");
        verify(redisUtils, never()).set(any(), any(), anyLong());
        verify(wechatUserDao, never()).update(any(), any());
    }

    private static WechatUserEntity wechatUser(Long userId, String openid) {
        WechatUserEntity e = new WechatUserEntity();
        e.setId(1L);
        e.setUserId(userId);
        e.setOpenid(openid);
        return e;
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value)
            throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
