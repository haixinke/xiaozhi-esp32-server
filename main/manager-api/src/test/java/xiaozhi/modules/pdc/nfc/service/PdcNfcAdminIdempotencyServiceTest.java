package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAdminOperationType;
import xiaozhi.modules.pdc.nfc.crypto.RequestFingerprint;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAdminRequestDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcAdminIdempotencyServiceImpl;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdcNfcAdminIdempotencyService 幂等服务测试")
class PdcNfcAdminIdempotencyServiceTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        lenient().when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(java.util.Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock
    private PdcNfcAdminRequestDao adminRequestDao;

    private RequestFingerprint requestFingerprint;
    private ObjectMapper objectMapper;
    private PdcNfcAdminIdempotencyServiceImpl idempotencyService;

    @BeforeEach
    void setUp() {
        requestFingerprint = new RequestFingerprint();
        objectMapper = new ObjectMapper();
        idempotencyService = new PdcNfcAdminIdempotencyServiceImpl(
                adminRequestDao, requestFingerprint, objectMapper);
    }

    // --- 简单响应类型用于测试 ---

    record TestResponse(String value, int count) {}

    // --- 测试用例 ---

    @Test
    @DisplayName("首次执行：无已有记录，执行业务并存储幂等记录")
    void firstExecution() {
        UUID requestId = UUID.randomUUID();
        when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(adminRequestDao.insert(any(PdcNfcAdminRequestEntity.class))).thenReturn(1);

        TestResponse result = idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                "test-request",
                TestResponse.class,
                () -> new TestResponse("ok", 42)
        );

        assertThat(result.value()).isEqualTo("ok");
        assertThat(result.count()).isEqualTo(42);
        verify(adminRequestDao).insert(any(PdcNfcAdminRequestEntity.class));
    }

    @Test
    @DisplayName("幂等命中：相同 requestId + 相同指纹 → 返回缓存响应")
    void idempotentHit() {
        UUID requestId = UUID.randomUUID();
        String canonical = "test-request";
        String fingerprint = requestFingerprint.sha256Canonical(canonical);

        PdcNfcAdminRequestEntity existing = new PdcNfcAdminRequestEntity();
        existing.setOperationType(PdcNfcAdminOperationType.WRITE_RESULT_IMPORT.name());
        existing.setRequestId(requestId.toString());
        existing.setRequestFingerprint(fingerprint);
        existing.setResponseJson("{\"value\":\"cached\",\"count\":99}");

        when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        TestResponse result = idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                canonical,
                TestResponse.class,
                () -> { throw new AssertionError("Should not be called"); }
        );

        assertThat(result.value()).isEqualTo("cached");
        assertThat(result.count()).isEqualTo(99);
        verify(adminRequestDao, never()).insert(any(PdcNfcAdminRequestEntity.class));
    }

    @Test
    @DisplayName("幂等冲突：相同 requestId + 不同指纹 → 抛出异常")
    void idempotencyConflict() {
        UUID requestId = UUID.randomUUID();
        String fingerprint1 = requestFingerprint.sha256Canonical("request-A");

        PdcNfcAdminRequestEntity existing = new PdcNfcAdminRequestEntity();
        existing.setRequestFingerprint(fingerprint1);
        existing.setResponseJson("{}");

        when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                "request-B",  // different canonical → different fingerprint
                TestResponse.class,
                () -> new TestResponse("x", 0)
        )).isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("并发冲突：首次查找为空，插入时 DuplicateKey，重试查找返回缓存")
    void concurrentConflict() throws Exception {
        UUID requestId = UUID.randomUUID();
        String canonical = "test-concurrent";
        String fingerprint = requestFingerprint.sha256Canonical(canonical);

        // 第一次 selectOne → null (no existing record)
        // insert → DuplicateKeyException
        // 第二次 selectOne → existing record with same fingerprint
        PdcNfcAdminRequestEntity existing = new PdcNfcAdminRequestEntity();
        existing.setRequestFingerprint(fingerprint);
        existing.setResponseJson("{\"value\":\"concurrent\",\"count\":7}");

        AtomicInteger callCount = new AtomicInteger(0);
        when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    int n = callCount.incrementAndGet();
                    return n == 1 ? null : existing;
                });
        when(adminRequestDao.insert(any(PdcNfcAdminRequestEntity.class))).thenThrow(new DuplicateKeyException("unique"));

        TestResponse result = idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                canonical,
                TestResponse.class,
                () -> new TestResponse("from-action", 1)
        );

        assertThat(result.value()).isEqualTo("concurrent");
        assertThat(result.count()).isEqualTo(7);
    }

    @Test
    @DisplayName("存储失败向外抛：幂等记录插入异常必须传播，让调用方事务回滚")
    void storeFailurePropagates() {
        UUID requestId = UUID.randomUUID();
        when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(adminRequestDao.insert(any(PdcNfcAdminRequestEntity.class))).thenThrow(new RuntimeException("DB error"));

        // 业务已执行但幂等记录未保存：若吞掉异常返回结果，客户端用同一 requestId
        // 重试时会再次执行业务（记录缺失），造成重复操作。必须抛出以回滚事务。
        assertThatThrownBy(() -> idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                "test-request",
                TestResponse.class,
                () -> new TestResponse("success", 1)
        )).isInstanceOf(RuntimeException.class).hasMessageContaining("DB error");
    }

    @Test
    @DisplayName("幂等记录必须携带操作人 ID：pdc_nfc_admin_request.operator_user_id 为 NOT NULL")
    void storedRecordCarriesOperatorUserId() {
        // 模拟 Shiro 登录上下文：管理后台操作必经 oauth2 过滤器，SecurityUser 能取到当前用户
        org.apache.shiro.subject.Subject subject = mock(org.apache.shiro.subject.Subject.class);
        xiaozhi.common.user.UserDetail user = new xiaozhi.common.user.UserDetail();
        user.setId(100L);
        when(subject.getPrincipal()).thenReturn(user);
        org.apache.shiro.util.ThreadContext.bind(subject);
        try {
            UUID requestId = UUID.randomUUID();
            when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(adminRequestDao.insert(any(PdcNfcAdminRequestEntity.class))).thenReturn(1);

            idempotencyService.execute(
                    PdcNfcAdminOperationType.STOCK_IN,
                    requestId,
                    "test-request",
                    TestResponse.class,
                    () -> new TestResponse("ok", 1)
            );

            org.mockito.ArgumentCaptor<PdcNfcAdminRequestEntity> captor =
                    org.mockito.ArgumentCaptor.forClass(PdcNfcAdminRequestEntity.class);
            verify(adminRequestDao).insert(captor.capture());
            // 缺该字段时 INSERT 报 Field 'operator_user_id' doesn't have a default value
            assertThat(captor.getValue().getOperatorUserId()).isEqualTo(100L);
        } finally {
            org.apache.shiro.util.ThreadContext.unbindSubject();
        }
    }
}
