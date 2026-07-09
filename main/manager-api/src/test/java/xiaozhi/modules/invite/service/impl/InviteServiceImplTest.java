package xiaozhi.modules.invite.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
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
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.invite.constant.InviteCodeType;
import xiaozhi.modules.invite.dao.InviteCodeDao;
import xiaozhi.modules.invite.dao.InviteUsageDao;
import xiaozhi.modules.invite.entity.InviteCodeEntity;
import xiaozhi.modules.invite.vo.InviteCodeVO;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InviteServiceImpl")
class InviteServiceImplTest {

    @Mock
    private InviteCodeDao inviteCodeDao;
    @Mock
    private InviteUsageDao inviteUsageDao;

    private InviteServiceImpl service;

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
        service = new InviteServiceImpl();
        setField(BaseServiceImpl.class, service, "baseDao", inviteCodeDao);
        setField(InviteServiceImpl.class, service, "inviteUsageDao", inviteUsageDao);
        setField(InviteServiceImpl.class, service, "personalQuota", 5);
        service.setClock(Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneId.systemDefault()));
        when(inviteCodeDao.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("createPersonalCode - 新用户生成个人码 quota=5 remaining=5")
    void createPersonalCode_newUser() {
        when(inviteCodeDao.selectOne(any())).thenReturn(null);

        InviteCodeVO vo = service.createPersonalCode(100L);

        assertThat(vo).isNotNull();
        assertThat(vo.getType()).isEqualTo(InviteCodeType.PERSONAL);
        assertThat(vo.getOwnerUserId()).isEqualTo(100L);
        assertThat(vo.getQuota()).isEqualTo(5);
        assertThat(vo.getRemaining()).isEqualTo(5);
        assertThat(vo.getUsedCount()).isZero();
        assertThat(vo.getStatus()).isEqualTo(1);
        assertThat(vo.getCode()).hasSize(8);
        verify(inviteCodeDao).insert(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("createPersonalCode - 已有个人码时幂等返回已有记录，不重复插入")
    void createPersonalCode_idempotent() {
        InviteCodeEntity existing = new InviteCodeEntity();
        existing.setId(9L);
        existing.setCode("AAAA2222");
        existing.setType(InviteCodeType.PERSONAL);
        existing.setOwnerUserId(100L);
        existing.setQuota(5);
        existing.setRemaining(3);
        when(inviteCodeDao.selectOne(any())).thenReturn(existing);

        InviteCodeVO vo = service.createPersonalCode(100L);

        assertThat(vo.getCode()).isEqualTo("AAAA2222");
        assertThat(vo.getRemaining()).isEqualTo(3);
        verify(inviteCodeDao, never()).insert(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("createPersonalCode - userId 为空抛 NOT_NULL")
    void createPersonalCode_nullUserId() {
        try {
            service.createPersonalCode(null);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getCode()).isEqualTo(10001);
        }
    }

    @Test
    @DisplayName("getMine - 找到返回；未找到抛异常")
    void getMine() {
        InviteCodeEntity e = new InviteCodeEntity();
        e.setId(1L);
        e.setCode("BBBB3333");
        e.setType(InviteCodeType.PERSONAL);
        when(inviteCodeDao.selectOne(any())).thenReturn(e);
        assertThat(service.getMine(7L).getCode()).isEqualTo("BBBB3333");

        when(inviteCodeDao.selectOne(any())).thenReturn(null);
        try {
            service.getMine(7L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException ex) {
            assertThat(ex.getMsg()).contains("个人邀请码");
        }
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value)
            throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
