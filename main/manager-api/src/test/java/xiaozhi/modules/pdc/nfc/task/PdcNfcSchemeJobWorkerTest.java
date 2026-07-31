package xiaozhi.modules.pdc.nfc.task;

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
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.crypto.ProtectedClaimRef;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcSchemeJobDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchStateMachine;
import xiaozhi.modules.pdc.nfc.wechat.WechatNfcErrorAction;
import xiaozhi.modules.pdc.nfc.wechat.WechatNfcSchemeClient;
import xiaozhi.modules.pdc.nfc.wechat.WechatNfcSchemeResult;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PdcNfcSchemeJobWorker 测试")
class PdcNfcSchemeJobWorkerTest {

    private static final Long JOB_ID = 1L;
    private static final Long BATCH_ID = 100L;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        xiaozhi.common.utils.SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PdcNfcSchemeJobDao jobDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcBatchDao batchDao;
    @Mock private WechatNfcSchemeClient schemeClient;
    @Mock private PdcNfcSchemeRateLimiter rateLimiter;

    private ClaimRefProtection claimRefProtection;
    private PdcNfcBatchStateMachine batchStateMachine;
    private PdcNfcSchemeJobWorker worker;

    @BeforeEach
    void setUp() {
        PdcNfcProperties properties = new PdcNfcProperties();
        properties.setEnabled(true);
        PdcNfcProperties.ClaimRef cr = new PdcNfcProperties.ClaimRef();
        cr.setActiveVersion("v1");
        cr.setActiveHmacKeyBase64("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=");
        cr.setActiveAesKeyBase64("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        properties.setClaimRef(cr);

        claimRefProtection = new ClaimRefProtection(properties);
        batchStateMachine = new PdcNfcBatchStateMachine();

        worker = spy(new PdcNfcSchemeJobWorker(
                jobDao, assetDao, batchDao, claimRefProtection, schemeClient,
                rateLimiter, batchStateMachine));
        doNothing().when(worker).sleep(anyLong());

        // 默认 stub：job 始终 RUNNING
        when(jobDao.selectById(JOB_ID)).thenReturn(runningJob());
        // 默认 stub：DAO 写操作返回成功
        when(assetDao.markSchemeGenerated(anyLong(), anyString(), any(), any(), anyString(), anyLong(), any(Date.class)))
                .thenReturn(1);
        when(jobDao.updateProgress(anyLong(), anyLong(), anyInt(), anyInt(), any(Date.class)))
                .thenReturn(1);
        when(jobDao.completeJob(anyLong(), anyString(), any(), any(), any(Date.class)))
                .thenReturn(1);
        when(jobDao.heartbeat(anyLong(), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(jobDao.releaseLease(anyLong(), anyString(), any(Date.class)))
                .thenReturn(1);
        when(assetDao.releaseAssetsForJob(anyLong(), anyLong())).thenReturn(1);
        // batch 转换 stub
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(BATCH_ID);
        batch.setStatus(PdcNfcBatchStatus.SCHEME_GENERATING.name());
        when(batchDao.selectById(BATCH_ID)).thenReturn(batch);
        when(batchDao.updateById(any(PdcNfcBatchEntity.class))).thenReturn(1);
    }

    // ===== Test: Scheme 加密并完成任务 =====

    @Test
    @DisplayName("成功生成 - 加密 Scheme 并推进资产为 SCHEME_GENERATED")
    void successEncryptsSchemeAndCompletesJob() {
        PdcNfcAssetEntity asset = createAsset(10L, "AbCdEfGhIjKlMnOpQrStUv");
        when(assetDao.selectCreatedAssetsAfterCursor(eq(BATCH_ID), anyLong(), anyInt()))
                .thenReturn(List.of(asset))
                .thenReturn(List.of());
        when(schemeClient.generate(eq("SN10"), anyString()))
                .thenReturn(WechatNfcSchemeResult.ok("weixin://nfc/test10"));

        worker.run(JOB_ID, "test-instance");

        ArgumentCaptor<byte[]> nonceCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> cipherCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> shaCaptor = ArgumentCaptor.forClass(String.class);

        verify(assetDao).markSchemeGenerated(
                eq(10L), eq("v1"), nonceCaptor.capture(), cipherCaptor.capture(),
                shaCaptor.capture(), eq(JOB_ID), any(Date.class));

        assertThat(nonceCaptor.getValue()).isNotEmpty();
        assertThat(cipherCaptor.getValue()).isNotEmpty();
        assertThat(shaCaptor.getValue()).hasSize(64); // SHA-256 hex = 64 chars

        verify(jobDao).completeJob(eq(JOB_ID), eq("SUCCEEDED"), any(), any(), any(Date.class));
        verify(jobDao).updateProgress(eq(JOB_ID), eq(10L), eq(1), eq(0), any(Date.class));
    }

    // ===== Test: 游标推进 =====

    @Test
    @DisplayName("多个资产 - 游标依次推进")
    void multipleAssetsAdvanceCursorInOrder() {
        PdcNfcAssetEntity asset1 = createAsset(10L, "AbCdEfGhIjKlMnOpQrStUv");
        PdcNfcAssetEntity asset2 = createAsset(20L, "BbCdEfGhIjKlMnOpQrStUv");

        when(assetDao.selectCreatedAssetsAfterCursor(eq(BATCH_ID), anyLong(), anyInt()))
                .thenReturn(List.of(asset1, asset2))
                .thenReturn(List.of());

        when(schemeClient.generate(eq("SN10"), anyString()))
                .thenReturn(WechatNfcSchemeResult.ok("weixin://nfc/a"));
        when(schemeClient.generate(eq("SN20"), anyString()))
                .thenReturn(WechatNfcSchemeResult.ok("weixin://nfc/b"));

        worker.run(JOB_ID, "test-instance");

        verify(assetDao, times(2)).markSchemeGenerated(
                anyLong(), eq("v1"), any(), any(), anyString(), eq(JOB_ID), any(Date.class));
        verify(jobDao).updateProgress(eq(JOB_ID), eq(10L), eq(1), eq(0), any(Date.class));
        verify(jobDao).updateProgress(eq(JOB_ID), eq(20L), eq(2), eq(0), any(Date.class));
        verify(jobDao).completeJob(eq(JOB_ID), eq("SUCCEEDED"), any(), any(), any(Date.class));
    }

    // ===== Test: RETRYABLE 退避后成功 =====

    @Test
    @DisplayName("RETRYABLE - 指数退避后重试成功")
    void retryableBackoffThenSuccess() {
        PdcNfcAssetEntity asset = createAsset(10L, "AbCdEfGhIjKlMnOpQrStUv");
        when(assetDao.selectCreatedAssetsAfterCursor(eq(BATCH_ID), anyLong(), anyInt()))
                .thenReturn(List.of(asset))
                .thenReturn(List.of());

        when(schemeClient.generate(eq("SN10"), anyString()))
                .thenReturn(WechatNfcSchemeResult.fail(-1, "timeout", WechatNfcErrorAction.RETRYABLE))
                .thenReturn(WechatNfcSchemeResult.ok("weixin://nfc/test10"));

        worker.run(JOB_ID, "test-instance");

        // 第一次失败 + 第二次成功 = 2 次调用
        verify(schemeClient, times(2)).generate(eq("SN10"), anyString());
        // 退避 sleep 一次
        verify(worker, times(1)).sleep(anyLong());
        // 最终成功加密
        verify(assetDao).markSchemeGenerated(
                eq(10L), eq("v1"), any(), any(), anyString(), eq(JOB_ID), any(Date.class));
        verify(jobDao).completeJob(eq(JOB_ID), eq("SUCCEEDED"), any(), any(), any(Date.class));
    }

    // ===== Test: TASK_FATAL 停止任务 =====

    @Test
    @DisplayName("TASK_FATAL - 标记任务 FAILED 并释放资产")
    void taskFatalStopsJobAndReleasesAssets() {
        PdcNfcAssetEntity asset = createAsset(10L, "AbCdEfGhIjKlMnOpQrStUv");
        when(assetDao.selectCreatedAssetsAfterCursor(eq(BATCH_ID), anyLong(), anyInt()))
                .thenReturn(List.of(asset));

        when(schemeClient.generate(eq("SN10"), anyString()))
                .thenReturn(WechatNfcSchemeResult.fail(9800003, "invalid param", WechatNfcErrorAction.TASK_FATAL));

        worker.run(JOB_ID, "test-instance");

        verify(schemeClient, times(1)).generate(eq("SN10"), anyString());
        verify(assetDao, never()).markSchemeGenerated(
                anyLong(), anyString(), any(), any(), anyString(), anyLong(), any(Date.class));
        verify(assetDao).releaseAssetsForJob(BATCH_ID, JOB_ID);
        verify(jobDao).completeJob(eq(JOB_ID), eq("FAILED"), any(), any(), any(Date.class));
    }

    // ===== Test: 单件失败后继续 =====

    @Test
    @DisplayName("单件失败继续 - 首资产耗尽重试后继续处理次资产")
    void singleAssetFailureContinuesToNext() {
        PdcNfcAssetEntity asset1 = createAsset(10L, "AbCdEfGhIjKlMnOpQrStUv");
        PdcNfcAssetEntity asset2 = createAsset(20L, "BbCdEfGhIjKlMnOpQrStUv");

        when(assetDao.selectCreatedAssetsAfterCursor(eq(BATCH_ID), anyLong(), anyInt()))
                .thenReturn(List.of(asset1, asset2))
                .thenReturn(List.of());

        // asset1: 6 次 RETRYABLE（1 次初始 + 5 次重试）
        WechatNfcSchemeResult retryable =
                WechatNfcSchemeResult.fail(-1, "timeout", WechatNfcErrorAction.RETRYABLE);
        when(schemeClient.generate(eq("SN10"), anyString()))
                .thenReturn(retryable, retryable, retryable, retryable, retryable, retryable);
        // asset2: 成功
        when(schemeClient.generate(eq("SN20"), anyString()))
                .thenReturn(WechatNfcSchemeResult.ok("weixin://nfc/test20"));

        worker.run(JOB_ID, "test-instance");

        // asset1 耗尽 6 次调用
        verify(schemeClient, times(6)).generate(eq("SN10"), anyString());
        // asset2 成功 1 次
        verify(schemeClient, times(1)).generate(eq("SN20"), anyString());
        // asset1 未加密，asset2 已加密
        verify(assetDao).markSchemeGenerated(
                eq(20L), eq("v1"), any(), any(), anyString(), eq(JOB_ID), any(Date.class));
        // 进度：success=1, failure=1
        verify(jobDao).updateProgress(eq(JOB_ID), eq(10L), eq(0), eq(1), any(Date.class));
        verify(jobDao).updateProgress(eq(JOB_ID), eq(20L), eq(1), eq(1), any(Date.class));
        // 部分成功
        verify(jobDao).completeJob(eq(JOB_ID), eq("PARTIAL_SUCCESS"), any(), any(), any(Date.class));
    }

    // ===== Test: QUOTA_DEFER 延后 =====

    @Test
    @DisplayName("QUOTA_DEFER - 任务保持 RUNNING 并设 next_retry_at")
    void quotaDeferKeepsJobRunningWithNextRetry() {
        PdcNfcAssetEntity asset = createAsset(10L, "AbCdEfGhIjKlMnOpQrStUv");
        when(assetDao.selectCreatedAssetsAfterCursor(eq(BATCH_ID), anyLong(), anyInt()))
                .thenReturn(List.of(asset));

        when(schemeClient.generate(eq("SN10"), anyString()))
                .thenReturn(WechatNfcSchemeResult.fail(44993, "quota exceeded", WechatNfcErrorAction.QUOTA_DEFER));

        worker.run(JOB_ID, "test-instance");

        verify(schemeClient, times(1)).generate(eq("SN10"), anyString());
        verify(assetDao, never()).markSchemeGenerated(
                anyLong(), anyString(), any(), any(), anyString(), anyLong(), any(Date.class));

        ArgumentCaptor<Date> retryCaptor = ArgumentCaptor.forClass(Date.class);
        verify(jobDao).completeJob(eq(JOB_ID), eq("RUNNING"), retryCaptor.capture(), any(), any(Date.class));
        assertThat(retryCaptor.getValue()).isNotNull();
        assertThat(retryCaptor.getValue().after(new Date())).isTrue();
    }

    // ===== Test: 租约丢失 fencing =====

    @Test
    @DisplayName("租约丢失 - 心跳返回0时 worker 停止处理后续资产")
    void leaseLostStopsProcessing() {
        // 两个资产：处理 asset1 后时间越过心跳间隔触发心跳，
        // 心跳返回 0 表示租约已被其他实例接管，worker 必须停止，
        // 不得再处理 asset2（避免重复调用微信）。
        PdcNfcAssetEntity asset1 = createAsset(10L, "AbCdEfGhIjKlMnOpQrStUv");
        PdcNfcAssetEntity asset2 = createAsset(20L, "BbCdEfGhIjKlMnOpQrStUv");
        when(assetDao.selectCreatedAssetsAfterCursor(eq(BATCH_ID), anyLong(), anyInt()))
                .thenReturn(List.of(asset1, asset2))
                .thenReturn(List.of());

        when(schemeClient.generate(eq("SN10"), anyString()))
                .thenReturn(WechatNfcSchemeResult.ok("weixin://nfc/a"));
        when(schemeClient.generate(eq("SN20"), anyString()))
                .thenReturn(WechatNfcSchemeResult.ok("weixin://nfc/b"));

        // 租约已丢失：持有者不匹配 → heartbeat 影响 0 行
        when(jobDao.heartbeat(anyLong(), anyString(), any(Date.class), any(Date.class)))
                .thenReturn(0);
        // 模拟时间流逝：第二次取时间时越过心跳间隔，触发心跳
        when(worker.currentTimeMs())
                .thenReturn(0L)                          // run() 起始 lastHeartbeatMs
                .thenAnswer(inv -> 25_000L);             // 之后均越过 20s 心跳间隔

        worker.run(JOB_ID, "test-instance");

        // asset2 绝不应被处理
        verify(schemeClient, never()).generate(eq("SN20"), anyString());
        // 不应标记任务完成
        verify(jobDao, never()).completeJob(eq(JOB_ID), eq("SUCCEEDED"), any(), any(), any(Date.class));
    }

    // ===== Helper =====

    private PdcNfcSchemeJobEntity runningJob() {
        PdcNfcSchemeJobEntity job = new PdcNfcSchemeJobEntity();
        job.setId(JOB_ID);
        job.setBatchId(BATCH_ID);
        job.setStatus("RUNNING");
        job.setCursorAssetId(0L);
        job.setSuccessCount(0);
        job.setFailureCount(0);
        job.setTotalCount(1);
        return job;
    }

    private PdcNfcAssetEntity createAsset(long id, String claimRef) {
        ProtectedClaimRef prot = claimRefProtection.protect(id, claimRef);
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(id);
        asset.setBatchId(BATCH_ID);
        asset.setWechatSn("SN" + id);
        asset.setClaimRefKeyVersion(prot.encrypted().keyVersion());
        asset.setClaimRefNonce(prot.encrypted().nonce());
        asset.setClaimRefCiphertext(prot.encrypted().ciphertext());
        asset.setStatus("CREATED");
        asset.setVersion(0);
        return asset;
    }
}
