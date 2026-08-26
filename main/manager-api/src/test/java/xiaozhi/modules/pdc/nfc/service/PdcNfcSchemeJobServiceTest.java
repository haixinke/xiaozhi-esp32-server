package xiaozhi.modules.pdc.nfc.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcSchemeJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcSchemeJobDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcSchemeJobServiceImpl;

import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdcNfcSchemeJobService 测试")
class PdcNfcSchemeJobServiceTest {

    private static final Long BATCH_ID = 100L;
    private static final Long OPERATOR_ID = 7L;

    @Mock private PdcNfcSchemeJobDao jobDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcBatchDao batchDao;
    @Mock private PdcNfcReadinessService readiness;

    private PdcNfcBatchStateMachine batchStateMachine;
    private PdcNfcSchemeJobServiceImpl service;

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
    void setUp() {
        batchStateMachine = new PdcNfcBatchStateMachine();
        service = new PdcNfcSchemeJobServiceImpl(
                jobDao, assetDao, batchDao, readiness, batchStateMachine);
    }

    @Test
    @DisplayName("重试 - 游标与计数归零，失败资产从头重扫")
    void retry_resetsCursorAndCounters() {
        PdcNfcSchemeJobEntity previous = previousJob(
                PdcNfcSchemeJobStatus.PARTIAL_SUCCESS.name(), 420L, 380, 20);
        when(jobDao.selectLatestByBatchId(BATCH_ID)).thenReturn(previous);

        service.retry(BATCH_ID, OPERATOR_ID);

        ArgumentCaptor<PdcNfcSchemeJobEntity> captor =
                ArgumentCaptor.forClass(PdcNfcSchemeJobEntity.class);
        verify(jobDao).insert(captor.capture());
        PdcNfcSchemeJobEntity created = captor.getValue();

        // 核心断言：游标必须归零，否则 id <= 旧游标的失败资产会被永久跳过
        assertThat(created.getCursorAssetId()).isZero();
        assertThat(created.getSuccessCount()).isZero();
        assertThat(created.getFailureCount()).isZero();
        // totalCount 沿用上一轮的总量
        assertThat(created.getTotalCount()).isEqualTo(previous.getTotalCount());
        assertThat(created.getStatus()).isEqualTo(PdcNfcSchemeJobStatus.PENDING.name());

        // 失败资产仍为 CREATED，重新绑定到新 job
        verify(assetDao).assignJobToCreatedAssets(BATCH_ID, created.getId());
    }

    @Test
    @DisplayName("重试 - 上一轮状态非 FAILED/PARTIAL_SUCCESS 时拒绝")
    void retry_rejectsWhenPreviousNotRetryable() {
        when(jobDao.selectLatestByBatchId(BATCH_ID))
                .thenReturn(previousJob(PdcNfcSchemeJobStatus.SUCCEEDED.name(), 420L, 400, 0));

        assertThatThrownBy(() -> service.retry(BATCH_ID, OPERATOR_ID))
                .isInstanceOf(RenException.class);

        verify(jobDao, never()).insert(any(PdcNfcSchemeJobEntity.class));
    }

    @Test
    @DisplayName("重试 - 无历史任务时抛 JOB_NOT_FOUND")
    void retry_throwsWhenNoPreviousJob() {
        when(jobDao.selectLatestByBatchId(BATCH_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.retry(BATCH_ID, OPERATOR_ID))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("启动 - 批次无 CREATED 资产时抛无可用资产错误码")
    void start_throwsWhenNoCreatedAssets() {
        when(batchDao.selectById(BATCH_ID)).thenReturn(draftBatch());
        when(assetDao.countCreatedAssets(BATCH_ID)).thenReturn(0);

        // 历史误用 RELEASE_NOT_READY，会误导运维去查发布配置；真实原因是批次无可用资产
        assertThatThrownBy(() -> service.start(BATCH_ID, OPERATOR_ID))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PDC_NFC_NO_AVAILABLE_ASSETS);

        verify(jobDao, never()).insert(any(PdcNfcSchemeJobEntity.class));
    }

    private PdcNfcBatchEntity draftBatch() {
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(BATCH_ID);
        batch.setBatchNo("B001");
        batch.setStatus(PdcNfcBatchStatus.DRAFT.name());
        return batch;
    }

    private PdcNfcSchemeJobEntity previousJob(String status, long cursor, int success, int failure) {
        PdcNfcSchemeJobEntity job = new PdcNfcSchemeJobEntity();
        job.setId(50L);
        job.setBatchId(BATCH_ID);
        job.setStatus(status);
        job.setTotalCount(400);
        job.setSuccessCount(success);
        job.setFailureCount(failure);
        job.setCursorAssetId(cursor);
        job.setCreateDate(new Date());
        return job;
    }
}
