package xiaozhi.modules.wechat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import xiaozhi.common.page.TokenDTO;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.Result;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.dto.WechatLoginRespDTO;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WechatServiceImpl - login")
class WechatServiceImplLoginTest {

    private static final String JSCODE_OK =
            "{\"openid\":\"openid-new\",\"session_key\":\"session-key\",\"errcode\":0}";

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
        service = new WechatServiceImpl(sysUserDao, sysUserTokenService, agentService,
                inviteService, redisUtils, null) {
            @Override
            JSONObject jscode2session(String code) {
                return JSONUtil.parseObj(JSCODE_OK);
            }
        };
        setField(BaseServiceImpl.class, service, "baseDao", wechatUserDao);
        setField(WechatServiceImpl.class, service, "appid", "wxappid");
        setField(WechatServiceImpl.class, service, "secret", "wxsecret");

        when(sysUserDao.selectCount(any())).thenReturn(0L);
        when(sysUserDao.insert(any(SysUserEntity.class))).thenAnswer(invocation -> {
            SysUserEntity user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        });
        when(wechatUserDao.insert(any(WechatUserEntity.class))).thenReturn(1);
        when(agentService.getUserAgents(anyLong(), any(), any())).thenReturn(null);
        TokenDTO tokenDTO = new TokenDTO();
        tokenDTO.setToken("token-42");
        tokenDTO.setExpire(43200);
        when(sysUserTokenService.createToken(anyLong())).thenReturn(new Result<TokenDTO>().ok(tokenDTO));
    }

    @Test
    @DisplayName("新用户静默登录时写入默认头像")
    void login_newUser_setsDefaultAvatar() {
        when(wechatUserDao.selectOne(any())).thenReturn(null);

        WechatLoginRespDTO resp = service.login("wx-code");

        assertThat(resp.getUserId()).isEqualTo(42L);
        assertThat(resp.getIsNewUser()).isTrue();
        verify(wechatUserDao).insert(any(WechatUserEntity.class));
        WechatUserEntity saved = captureInsertedEntity();
        assertThat(saved.getAvatarUrl())
                .isEqualTo("https://oss.eggbabe.com/default-avatar/user/user-avatar.png");
    }

    @Test
    @DisplayName("老用户静默登录不覆盖已有头像")
    void login_existingUser_keepsExistingAvatar() {
        WechatUserEntity existing = new WechatUserEntity();
        existing.setOpenid("openid-existing");
        existing.setUserId(7L);
        existing.setAvatarUrl("https://example.com/old.png");
        when(wechatUserDao.selectOne(any())).thenReturn(existing);
        when(wechatUserDao.updateById(any(WechatUserEntity.class))).thenReturn(1);

        WechatLoginRespDTO resp = service.login("wx-code");

        assertThat(resp.getUserId()).isEqualTo(7L);
        assertThat(resp.getIsNewUser()).isFalse();
        verify(wechatUserDao, never()).insert(any(WechatUserEntity.class));
        assertThat(existing.getAvatarUrl()).isEqualTo("https://example.com/old.png");
    }

    private WechatUserEntity captureInsertedEntity() {
        ArgumentCaptor<WechatUserEntity> captor = forClass(WechatUserEntity.class);
        verify(wechatUserDao).insert(captor.capture());
        return captor.getValue();
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value)
            throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
