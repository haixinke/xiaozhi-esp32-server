package xiaozhi.modules.wechat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.dto.WechatProfileUpdateDTO;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.wechat.vo.WechatProfileVO;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WechatServiceImpl - profile/avatar")
class WechatServiceImplProfileTest {

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
    @Mock
    private OssService ossService;
    @Mock
    private AliyunOssProperties ossProperties;

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
                inviteService, redisUtils, ossService, ossProperties);
        setField(BaseServiceImpl.class, service, "baseDao", wechatUserDao);
    }

    @Test
    @DisplayName("getProfile：返回脱敏手机号和星座")
    void getProfile_success_returnsMaskedPhoneAndZodiac() {
        WechatUserEntity entity = fullEntity();
        when(wechatUserDao.selectOne(any())).thenReturn(entity);

        WechatProfileVO vo = service.getProfile(7L);

        assertThat(vo.getNickname()).isEqualTo("蛋友");
        assertThat(vo.getPhone()).isEqualTo("138****8000");
        assertThat(vo.getZodiac()).isEqualTo("摩羯座");
        assertThat(vo.getBirthday()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    @DisplayName("getProfile：未登录抛 USER_NOT_LOGIN")
    void getProfile_notLogin_throwsUserNotLogin() {
        assertThatThrownBy(() -> service.getProfile(null))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.USER_NOT_LOGIN);
    }

    @Test
    @DisplayName("updateProfile：仅更新传入字段")
    void updateProfile_success_updatesOnlyProvidedFields() {
        WechatUserEntity entity = fullEntity();
        when(wechatUserDao.selectOne(any())).thenReturn(entity);
        when(wechatUserDao.update(any(), any())).thenReturn(1);

        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setCity("上海");
        service.updateProfile(7L, dto);

        verify(wechatUserDao).update(eq(null), any());
    }

    @Test
    @DisplayName("updateProfile：昵称超长抛 NICKNAME_TOO_LONG")
    void updateProfile_nicknameTooLong_throws() {
        WechatUserEntity entity = fullEntity();
        when(wechatUserDao.selectOne(any())).thenReturn(entity);

        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setNickname("这是一段超过十六个字符的昵称测试内容");

        assertThatThrownBy(() -> service.updateProfile(7L, dto))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.NICKNAME_TOO_LONG);
        verify(wechatUserDao, never()).update(any(), any());
    }

    @Test
    @DisplayName("updateProfile：无效 MBTI 抛 INVALID_MBTI")
    void updateProfile_invalidMbti_throws() {
        WechatUserEntity entity = fullEntity();
        when(wechatUserDao.selectOne(any())).thenReturn(entity);

        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setMbti("XXXX");

        assertThatThrownBy(() -> service.updateProfile(7L, dto))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_MBTI);
    }

    @Test
    @DisplayName("uploadAvatar：OSS 可用时返回公开 URL")
    void uploadAvatar_success_returnsPublicUrl() throws Exception {
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.upload(any(), any())).thenReturn("avatar/7/uuid.png");
        when(ossProperties.getBucketName()).thenReturn("test-bucket");
        when(ossProperties.getEndpoint()).thenReturn("https://oss-cn-shanghai.aliyuncs.com");

        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());
        String url = service.uploadAvatar(7L, file);

        assertThat(url).startsWith("https://test-bucket.oss-cn-shanghai.aliyuncs.com/avatar/7/");
        assertThat(url).endsWith(".png");
    }

    @Test
    @DisplayName("uploadAvatar：OSS 未配置抛 OSS_UPLOAD_FILE_ERROR")
    void uploadAvatar_ossDisabled_throws() {
        when(ossService.isEnabled()).thenReturn(false);

        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());
        assertThatThrownBy(() -> service.uploadAvatar(7L, file))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.OSS_UPLOAD_FILE_ERROR);
    }

    @Test
    @DisplayName("uploadAvatar：非法文件类型抛 AVATAR_FILE_TYPE_ERROR")
    void uploadAvatar_invalidFileType_throws() {
        MultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", "x".getBytes());
        assertThatThrownBy(() -> service.uploadAvatar(7L, file))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.AVATAR_FILE_TYPE_ERROR);
    }

    private static WechatUserEntity fullEntity() {
        WechatUserEntity e = new WechatUserEntity();
        e.setId(1L);
        e.setUserId(7L);
        e.setOpenid("openid-xyz");
        e.setNickname("蛋友");
        e.setAvatarUrl("https://example.com/a.png");
        e.setPhone("13800138000");
        e.setGender("MALE");
        e.setBirthday(LocalDate.of(2000, 1, 1));
        e.setCity("北京");
        e.setMbti("INFP");
        return e;
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value)
            throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
