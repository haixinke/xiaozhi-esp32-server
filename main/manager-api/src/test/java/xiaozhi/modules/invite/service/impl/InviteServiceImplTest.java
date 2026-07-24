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
import xiaozhi.modules.invite.entity.InviteUsageEntity;
import xiaozhi.modules.invite.vo.InviteCodeVO;
import xiaozhi.modules.invite.vo.InviteConsumeVO;

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

    @Test
    @DisplayName("consume - 正常消耗：扣减并写使用记录")
    void consume_normal() {
        InviteCodeEntity entity = codeEntity(1L, "CCCC4444", 200L, 5, 5, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate("CCCC4444")).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(0L);
        when(inviteCodeDao.decrementRemaining(1L)).thenReturn(1);

        InviteConsumeVO vo = service.consume("CCCC4444", 300L);

        assertThat(vo.getMessage()).isEqualTo("success");
        assertThat(vo.getRemaining()).isEqualTo(4);
        verify(inviteCodeDao).decrementRemaining(1L);
        verify(inviteUsageDao).insert(any(InviteUsageEntity.class));
    }

    @Test
    @DisplayName("consume - 码不存在抛 邀请码无效")
    void consume_notFound() {
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(null);
        try {
            service.consume("NOPE", 1L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("无效");
        }
        verify(inviteCodeDao, never()).decrementRemaining(any());
    }

    @Test
    @DisplayName("consume - status=0 抛 已失效")
    void consume_disabled() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 3, 0, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("失效");
        }
    }

    @Test
    @DisplayName("consume - 已过期抛 已过期")
    void consume_expired() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 3, 1,
                Date.from(Instant.parse("2026-07-01T00:00:00Z")));
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("过期");
        }
    }

    @Test
    @DisplayName("consume - remaining=0 抛 已无剩余")
    void consume_noRemaining() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 0, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("无剩余");
        }
    }

    @Test
    @DisplayName("consume - 自邀拦截：owner==invitee 抛异常")
    void consume_selfInvite() {
        InviteCodeEntity entity = codeEntity(1L, "X", 300L, 5, 5, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("自己的邀请码");
        }
        verify(inviteCodeDao, never()).decrementRemaining(any());
    }

    @Test
    @DisplayName("consume - 幂等：同被邀请人重复消耗不扣减，返回 已使用过")
    void consume_idempotent() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 4, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(1L);

        InviteConsumeVO vo = service.consume("X", 300L);

        assertThat(vo.getMessage()).contains("已使用");
        assertThat(vo.getRemaining()).isEqualTo(4);
        verify(inviteCodeDao, never()).decrementRemaining(any());
        verify(inviteUsageDao, never()).insert(any(InviteUsageEntity.class));
    }

    @Test
    @DisplayName("consume - 并发抢空：decrementRemaining 返回 0 抛 已无剩余")
    void consume_raceEmpty() {
        InviteCodeEntity entity = codeEntity(1L, "X", 200L, 5, 1, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(0L);
        when(inviteCodeDao.decrementRemaining(1L)).thenReturn(0);
        try {
            service.consume("X", 300L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("无剩余");
        }
    }

    @Test
    @DisplayName("consume - 企业码 owner=NULL 不受自邀限制")
    void consume_enterpriseNoOwner() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 10, 1, null);
        when(inviteCodeDao.selectByCodeForUpdate(any())).thenReturn(entity);
        when(inviteUsageDao.selectCount(any())).thenReturn(0L);
        when(inviteCodeDao.decrementRemaining(1L)).thenReturn(1);

        InviteConsumeVO vo = service.consume("X", 300L);
        assertThat(vo.getMessage()).isEqualTo("success");
    }

    @Test
    @DisplayName("createEnterprise - 生成企业码 quota 来自入参 owner=NULL")
    void createEnterprise() {
        xiaozhi.modules.invite.dto.InviteCodeCreateDTO dto =
                new xiaozhi.modules.invite.dto.InviteCodeCreateDTO();
        dto.setQuota(100);
        dto.setStatus(1);
        dto.setRemark("展会A");

        xiaozhi.modules.invite.vo.InviteCodeVO vo = service.createEnterprise(dto);

        assertThat(vo.getType()).isEqualTo(InviteCodeType.ENTERPRISE);
        assertThat(vo.getOwnerUserId()).isNull();
        assertThat(vo.getQuota()).isEqualTo(100);
        assertThat(vo.getRemaining()).isEqualTo(100);
        assertThat(vo.getUsedCount()).isZero();
        assertThat(vo.getRemark()).isEqualTo("展会A");
        verify(inviteCodeDao).insert(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("update - quota 调增允许并重算 remaining")
    void update_increaseQuota() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 7, 1, null); // used=3
        when(inviteCodeDao.selectByIdForUpdate(1L)).thenReturn(entity);

        xiaozhi.modules.invite.dto.InviteCodeUpdateDTO dto =
                new xiaozhi.modules.invite.dto.InviteCodeUpdateDTO();
        dto.setId(1L);
        dto.setQuota(20);
        service.update(dto);

        assertThat(entity.getQuota()).isEqualTo(20);
        assertThat(entity.getRemaining()).isEqualTo(17); // 20-3
        verify(inviteCodeDao).updateById(entity);
    }

    @Test
    @DisplayName("update - quota 调减被拒绝（仅允许调增）")
    void update_decreaseQuota_rejected() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 7, 1, null); // used=3, quota=10
        when(inviteCodeDao.selectByIdForUpdate(1L)).thenReturn(entity);

        xiaozhi.modules.invite.dto.InviteCodeUpdateDTO dto =
                new xiaozhi.modules.invite.dto.InviteCodeUpdateDTO();
        dto.setId(1L);
        dto.setQuota(8); // 8 < current 10 → rejected; 8 >= used(3) so old guard would have allowed
        try {
            service.update(dto);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("调增");
        }
        verify(inviteCodeDao, never()).updateById(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("update - quota 小于 used_count 抛异常")
    void update_quotaBelowUsed() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 2, 1, null); // used=8
        when(inviteCodeDao.selectByIdForUpdate(1L)).thenReturn(entity);

        xiaozhi.modules.invite.dto.InviteCodeUpdateDTO dto =
                new xiaozhi.modules.invite.dto.InviteCodeUpdateDTO();
        dto.setId(1L);
        dto.setQuota(5); // < used(8)
        try {
            service.update(dto);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("调增");
        }
        verify(inviteCodeDao, never()).updateById(any(InviteCodeEntity.class));
    }

    @Test
    @DisplayName("delete - used_count=0 可删")
    void delete_unused() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 10, 1, null); // used=0
        when(inviteCodeDao.selectByIdForUpdate(1L)).thenReturn(entity);
        service.delete(1L);
        verify(inviteCodeDao).deleteById((java.io.Serializable) 1L);
    }

    @Test
    @DisplayName("delete - used_count>0 拒绝")
    void delete_used() {
        InviteCodeEntity entity = codeEntity(1L, "X", null, 10, 7, 1, null); // used=3
        when(inviteCodeDao.selectByIdForUpdate(1L)).thenReturn(entity);
        try {
            service.delete(1L);
            assertThat(false).as("应抛异常").isTrue();
        } catch (RenException e) {
            assertThat(e.getMsg()).contains("已被使用");
        }
        verify(inviteCodeDao, never()).deleteById((java.io.Serializable) any());
    }

    private static InviteCodeEntity codeEntity(Long id, String code, Long owner,
            int quota, int remaining, int status, Date expire) {
        InviteCodeEntity e = new InviteCodeEntity();
        e.setId(id);
        e.setCode(code);
        e.setType(owner == null ? InviteCodeType.ENTERPRISE : InviteCodeType.PERSONAL);
        e.setOwnerUserId(owner);
        e.setQuota(quota);
        e.setUsedCount(quota - remaining);
        e.setRemaining(remaining);
        e.setStatus(status);
        e.setExpireTime(expire);
        return e;
    }

    private static void setField(Class<?> clazz, Object target, String name, Object value)
            throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}