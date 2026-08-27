package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcManualMarkAction;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcVerifySource;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobMode;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.crypto.EncryptedField;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcManualWriteServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcManualAssetVO;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 手动写卡模式服务测试（ADR 0003）。
 */
@ExtendWith(MockitoExtension.class)
class PdcNfcManualWriteServiceTest {

    private static final String SCHEME = "weixin://dl/business/?t=manual";

    @Mock private PdcNfcWriteJobDao jobDao;
    @Mock private PdcNfcWriteJobItemDao jobItemDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcBatchDao batchDao;
    @Mock private PdcNfcOperationLogDao operationLogDao;
    @Mock private ClaimRefProtection claimRefProtection;

    private PdcNfcManualWriteServiceImpl service;

    @BeforeAll
    static void initializeMyBatisTableMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "pdc-nfc-manual-write-test");
        TableInfoHelper.initTableInfo(assistant, PdcNfcWriteJobEntity.class);
        TableInfoHelper.initTableInfo(assistant, PdcNfcAssetEntity.class);
        TableInfoHelper.initTableInfo(assistant, PdcNfcWriteJobItemEntity.class);

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        service = new PdcNfcManualWriteServiceImpl(
                jobDao, jobItemDao, assetDao, batchDao, operationLogDao,
                claimRefProtection, new PdcNfcWriteJobStateMachine(), new PdcNfcBatchStateMachine());
    }

    // --- listAssets ---

    @Test
    @DisplayName("列出手动任务资产，按快照序号排序，不含 Scheme 明文")
    void listAssetsReturnsOrderedAssetsWithoutScheme() {
        when(jobDao.selectById(100L)).thenReturn(manualJob());
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item(1), item(2)));
        PdcNfcAssetEntity a1 = asset(10001L, PdcNfcAssetStatus.SCHEME_GENERATED);
        PdcNfcAssetEntity a2 = asset(10002L, PdcNfcAssetStatus.WRITTEN);
        when(assetDao.selectBatchIds(any())).thenReturn(List.of(a1, a2));

        List<PdcNfcManualAssetVO> result = service.listAssets(100L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).assetId()).isEqualTo(10001L);
        assertThat(result.get(1).status()).isEqualTo(PdcNfcAssetStatus.WRITTEN.name());
    }

    @Test
    @DisplayName("工厂 CSV 任务走手动通道被拒绝")
    void listAssetsRejectsFactoryJob() {
        when(jobDao.selectById(100L)).thenReturn(factoryJob());

        assertThatThrownBy(() -> service.listAssets(100L))
                .isInstanceOf(RenException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PDC_NFC_JOB_MODE_MISMATCH);
    }

    // --- revealScheme ---

    @Test
    @DisplayName("单条解密 Scheme 并记审计日志")
    void revealSchemeDecryptsAndAudits() {
        when(jobDao.selectById(100L)).thenReturn(manualJob());
        when(jobItemDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(assetDao.selectById(501L)).thenReturn(encryptedAsset());
        when(claimRefProtection.decrypt(eq(501L), any(EncryptedField.class))).thenReturn(SCHEME);

        String scheme = service.revealScheme(100L, 501L, 99L);

        assertThat(scheme).isEqualTo(SCHEME);
        verify(operationLogDao).insert(any(PdcNfcOperationLogEntity.class));
    }

    @Test
    @DisplayName("资产不属于该任务时拒绝解密")
    void revealSchemeRejectsAssetOutsideJob() {
        when(jobDao.selectById(100L)).thenReturn(manualJob());
        when(jobItemDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.revealScheme(100L, 501L, 99L))
                .isInstanceOf(RenException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        verify(claimRefProtection, never()).decrypt(any(), any(EncryptedField.class));
    }

    @Test
    @DisplayName("任务已取消时不允许再取 Scheme")
    void revealSchemeRejectsCancelledJob() {
        PdcNfcWriteJobEntity job = manualJob();
        job.setStatus(PdcNfcWriteJobStatus.CANCELLED.name());
        when(jobDao.selectById(100L)).thenReturn(job);

        assertThatThrownBy(() -> service.revealScheme(100L, 501L, 99L))
                .isInstanceOf(RenException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PDC_NFC_INVALID_STATE);
    }

    // --- mark ---

    @Test
    @DisplayName("标记已写入：SCHEME_GENERATED → WRITTEN")
    void markWrittenTransitionsAsset() {
        stubMarkableJob();
        when(assetDao.selectById(501L))
                .thenReturn(asset(501L, PdcNfcAssetStatus.SCHEME_GENERATED),
                        asset(501L, PdcNfcAssetStatus.WRITTEN));
        when(assetDao.markWritten(eq(501L), eq(100L), eq(99L), any())).thenReturn(1);

        PdcNfcManualAssetVO vo = service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_WRITTEN, 99L);

        assertThat(vo.status()).isEqualTo(PdcNfcAssetStatus.WRITTEN.name());
        verify(operationLogDao).insert(any(PdcNfcOperationLogEntity.class));
    }

    @Test
    @DisplayName("标记已写入：CAS 影响 0 行说明状态不符")
    void markWrittenFailsWhenCasMisses() {
        stubMarkableJob();
        when(assetDao.selectById(501L)).thenReturn(asset(501L, PdcNfcAssetStatus.WRITTEN));
        when(assetDao.markWritten(eq(501L), eq(100L), eq(99L), any())).thenReturn(0);

        assertThatThrownBy(() -> service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_WRITTEN, 99L))
                .isInstanceOf(RenException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PDC_NFC_INVALID_STATE);
    }

    @Test
    @DisplayName("写坏回退：WRITTEN → SCHEME_GENERATED，留任务内重写")
    void markWriteFailedRevertsToSchemeGenerated() {
        stubMarkableJob();
        when(assetDao.selectById(501L))
                .thenReturn(asset(501L, PdcNfcAssetStatus.WRITTEN),
                        asset(501L, PdcNfcAssetStatus.SCHEME_GENERATED));
        when(assetDao.revertWrittenToSchemeGenerated(eq(501L), eq(100L), eq(99L), any())).thenReturn(1);

        PdcNfcManualAssetVO vo = service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_WRITE_FAILED, 99L);

        assertThat(vo.status()).isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
    }

    @Test
    @DisplayName("人工验证通过：WRITTEN → VERIFIED，verify_source=MANUAL")
    void markVerifiedTransitionsWithManualSource() {
        stubMarkableJob();
        when(assetDao.selectById(501L))
                .thenReturn(asset(501L, PdcNfcAssetStatus.WRITTEN),
                        asset(501L, PdcNfcAssetStatus.VERIFIED));
        when(assetDao.markVerified(eq(501L), eq(100L),
                eq(PdcNfcVerifySource.MANUAL.name()), eq(99L), any())).thenReturn(1);
        // maybeComplete：仍有未验证资产，任务不完成
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item(1), item(2)));
        when(assetDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        PdcNfcManualAssetVO vo = service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_VERIFIED, 99L);

        assertThat(vo.status()).isEqualTo(PdcNfcAssetStatus.VERIFIED.name());
    }

    @Test
    @DisplayName("全部验证通过后任务自动完成并推进批次")
    void markVerifiedCompletesJobWhenAllVerified() {
        stubMarkableJob();
        when(assetDao.selectById(501L))
                .thenReturn(asset(501L, PdcNfcAssetStatus.WRITTEN),
                        asset(501L, PdcNfcAssetStatus.VERIFIED));
        when(assetDao.markVerified(eq(501L), eq(100L),
                eq(PdcNfcVerifySource.MANUAL.name()), eq(99L), any())).thenReturn(1);
        // maybeComplete：任务内唯一资产已验证
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item(1)));
        when(assetDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setStatus(PdcNfcBatchStatus.WRITING.name());
        when(batchDao.selectById(10L)).thenReturn(batch);
        when(batchDao.transitionStatus(eq(10L),
                eq(PdcNfcBatchStatus.WRITING.name()),
                eq(PdcNfcBatchStatus.READY_FOR_STOCK.name()), eq(99L), any())).thenReturn(1);
        when(jobDao.updateById(any(PdcNfcWriteJobEntity.class))).thenReturn(1);

        service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_VERIFIED, 99L);

        org.mockito.ArgumentCaptor<PdcNfcWriteJobEntity> jobCaptor =
                org.mockito.ArgumentCaptor.forClass(PdcNfcWriteJobEntity.class);
        verify(jobDao).updateById(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(PdcNfcWriteJobStatus.COMPLETED.name());
        verify(batchDao).transitionStatus(eq(10L),
                eq(PdcNfcBatchStatus.WRITING.name()),
                eq(PdcNfcBatchStatus.READY_FOR_STOCK.name()), eq(99L), any());
    }

    @Test
    @DisplayName("锁卡确认：仅 VERIFIED 后可操作，CAS 保护")
    void markLockedOnlyAfterVerified() {
        stubMarkableJob();
        when(assetDao.selectById(501L))
                .thenReturn(asset(501L, PdcNfcAssetStatus.VERIFIED),
                        asset(501L, PdcNfcAssetStatus.VERIFIED));
        when(assetDao.markLocked(eq(501L), eq(99L), any())).thenReturn(1);

        PdcNfcManualAssetVO vo = service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_LOCKED, 99L);

        assertThat(vo.status()).isEqualTo(PdcNfcAssetStatus.VERIFIED.name());
        verify(assetDao).markLocked(eq(501L), eq(99L), any());
    }

    // --- touchVerify ---

    @Test
    @DisplayName("任务完成后仍允许锁卡确认（锁卡必然发生在全部验证之后）")
    void markLockedAllowedOnCompletedJob() {
        // 回归：maybeComplete 把任务置 COMPLETED 后，MARK_LOCKED 不得被拒
        PdcNfcWriteJobEntity job = manualJob();
        job.setStatus(PdcNfcWriteJobStatus.COMPLETED.name());
        when(jobDao.selectById(100L)).thenReturn(job);
        when(jobItemDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(assetDao.selectById(501L))
                .thenReturn(asset(501L, PdcNfcAssetStatus.VERIFIED),
                        asset(501L, PdcNfcAssetStatus.VERIFIED));
        when(assetDao.markLocked(eq(501L), eq(99L), any())).thenReturn(1);

        PdcNfcManualAssetVO vo = service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_LOCKED, 99L);

        assertThat(vo.status()).isEqualTo(PdcNfcAssetStatus.VERIFIED.name());
    }

    @Test
    @DisplayName("任务完成后其他标记动作仍被拒绝")
    void otherMarksRejectedOnCompletedJob() {
        PdcNfcWriteJobEntity job = manualJob();
        job.setStatus(PdcNfcWriteJobStatus.COMPLETED.name());
        when(jobDao.selectById(100L)).thenReturn(job);

        assertThatThrownBy(() -> service.mark(100L, 501L, PdcNfcManualMarkAction.MARK_WRITTEN, 99L))
                .isInstanceOf(RenException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PDC_NFC_INVALID_STATE);
    }

    @Test
    @DisplayName("触碰自验证：手动任务中 WRITTEN 资产推进 VERIFIED（TOUCH）")
    void touchVerifyAdvancesWrittenAssetInManualJob() {
        PdcNfcAssetEntity asset = asset(501L, PdcNfcAssetStatus.WRITTEN);
        asset.setActiveWriteJobId(100L);
        when(jobDao.selectById(100L)).thenReturn(manualJob());
        when(assetDao.markVerified(eq(501L), eq(100L),
                eq(PdcNfcVerifySource.TOUCH.name()), isNull(), any())).thenReturn(1);
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item(1)));
        when(assetDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.touchVerify(asset);

        verify(assetDao).markVerified(eq(501L), eq(100L),
                eq(PdcNfcVerifySource.TOUCH.name()), isNull(), any());
    }

    @Test
    @DisplayName("野生触碰不动状态：工厂任务的 WRITTEN 资产不推进")
    void touchVerifyIgnoresFactoryJobAsset() {
        PdcNfcAssetEntity asset = asset(501L, PdcNfcAssetStatus.WRITTEN);
        asset.setActiveWriteJobId(100L);
        when(jobDao.selectById(100L)).thenReturn(factoryJob());

        service.touchVerify(asset);

        verify(assetDao, never()).markVerified(any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("锁后触碰复验：VERIFIED 且已锁卡未复验时记录复验时间")
    void touchVerifyRecordsPostLockVerification() {
        PdcNfcAssetEntity asset = asset(501L, PdcNfcAssetStatus.VERIFIED);
        asset.setLockedAt(new java.util.Date());

        service.touchVerify(asset);

        verify(assetDao).markLockVerified(eq(501L), any());
    }

    @Test
    @DisplayName("未锁卡的 VERIFIED 资产触碰不记录复验")
    void touchVerifySkipsUnlockedVerifiedAsset() {
        PdcNfcAssetEntity asset = asset(501L, PdcNfcAssetStatus.VERIFIED);

        service.touchVerify(asset);

        verify(assetDao, never()).markLockVerified(any(), any());
    }

    // --- fixtures ---

    private void stubMarkableJob() {
        when(jobDao.selectById(100L)).thenReturn(manualJob());
        when(jobItemDao.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
    }

    private static PdcNfcWriteJobEntity manualJob() {
        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setId(100L);
        job.setJobNo("WRT-100");
        job.setBatchId(10L);
        job.setMode(PdcNfcWriteJobMode.MANUAL.name());
        job.setStatus(PdcNfcWriteJobStatus.CREATED.name());
        return job;
    }

    private static PdcNfcWriteJobEntity factoryJob() {
        PdcNfcWriteJobEntity job = manualJob();
        job.setMode(PdcNfcWriteJobMode.FACTORY_CSV.name());
        job.setStatus(PdcNfcWriteJobStatus.EXPORTED.name());
        return job;
    }

    private static PdcNfcWriteJobItemEntity item(int sequenceNo) {
        PdcNfcWriteJobItemEntity item = new PdcNfcWriteJobItemEntity();
        item.setJobId(100L);
        item.setAssetId(10000L + sequenceNo);
        item.setSequenceNo(sequenceNo);
        return item;
    }

    private static PdcNfcAssetEntity asset(Long id, PdcNfcAssetStatus status) {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(id);
        asset.setAssetNo("A" + id);
        asset.setWechatSn("SN" + id);
        asset.setPrototype("KOI");
        asset.setStatus(status.name());
        return asset;
    }

    private static PdcNfcAssetEntity encryptedAsset() {
        PdcNfcAssetEntity asset = asset(501L, PdcNfcAssetStatus.SCHEME_GENERATED);
        asset.setSchemeKeyVersion("v1");
        asset.setSchemeNonce(new byte[12]);
        asset.setSchemeCiphertext(new byte[]{1, 2, 3});
        return asset;
    }
}
