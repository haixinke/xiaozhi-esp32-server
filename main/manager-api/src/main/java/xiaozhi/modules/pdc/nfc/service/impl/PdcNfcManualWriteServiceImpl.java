package xiaozhi.modules.pdc.nfc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
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
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcManualWriteService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobStateMachine;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcManualAssetVO;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * NFC 手动写卡模式服务实现（ADR 0003）。
 * <p>
 * 与工厂 CSV 通道互斥：所有操作先校验任务 mode=MANUAL。
 * 资产状态推进全部走 CAS 更新，并发或乱序操作影响 0 行即失败，
 * 不会出现半成功状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcManualWriteServiceImpl implements PdcNfcManualWriteService {

    private final PdcNfcWriteJobDao jobDao;
    private final PdcNfcWriteJobItemDao jobItemDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcOperationLogDao operationLogDao;
    private final ClaimRefProtection claimRefProtection;
    private final PdcNfcWriteJobStateMachine writeJobStateMachine;
    private final PdcNfcBatchStateMachine batchStateMachine;

    @Override
    public List<PdcNfcManualAssetVO> listAssets(Long jobId) {
        requireManualJob(jobId);
        List<PdcNfcWriteJobItemEntity> items = jobItemDao.selectList(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId)
                        .orderByAsc(PdcNfcWriteJobItemEntity::getSequenceNo));
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> assetIds = items.stream().map(PdcNfcWriteJobItemEntity::getAssetId).toList();
        Map<Long, PdcNfcAssetEntity> assetsById = assetDao.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(PdcNfcAssetEntity::getId, a -> a));
        return items.stream()
                .map(item -> {
                    PdcNfcAssetEntity asset = assetsById.get(item.getAssetId());
                    if (asset == null) {
                        // 快照行引用的资产缺失，数据不一致
                        throw new RenException(ErrorCode.PDC_NFC_ASSET_DATA_INCONSISTENT);
                    }
                    return toVO(asset);
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String revealScheme(Long jobId, Long assetId, Long operatorId) {
        PdcNfcWriteJobEntity job = requireManualJob(jobId);
        requireJobInProgress(job);
        requireJobItem(jobId, assetId);

        PdcNfcAssetEntity asset = assetDao.selectById(assetId);
        if (asset == null
                || asset.getSchemeKeyVersion() == null
                || asset.getSchemeNonce() == null
                || asset.getSchemeCiphertext() == null) {
            // Scheme 加密三要素缺失，资产数据不完整
            throw new RenException(ErrorCode.PDC_NFC_ASSET_DATA_INCONSISTENT);
        }
        String scheme = claimRefProtection.decrypt(assetId, new EncryptedField(
                asset.getSchemeKeyVersion(), asset.getSchemeNonce(), asset.getSchemeCiphertext()));

        // Scheme 明文是敏感数据，逐条查看必须留审计；
        // 审计失败则整个调用回滚，不出现"明文已出但无审计"的窗口。
        logOperation(operatorId, assetId, "SCHEME_REVEAL", null, null);
        return scheme;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcManualAssetVO mark(Long jobId, Long assetId, PdcNfcManualMarkAction action, Long operatorId) {
        PdcNfcWriteJobEntity job = requireManualJob(jobId);
        // 锁卡确认例外：全部资产验证通过后任务即 COMPLETED，但锁卡按 ADR 0003
        // 顺序必然发生在验证之后，因此 COMPLETED 任务仍须允许 MARK_LOCKED。
        boolean jobOpen = PdcNfcWriteJobStatus.CREATED.name().equals(job.getStatus());
        if (!jobOpen && !(action == PdcNfcManualMarkAction.MARK_LOCKED
                && PdcNfcWriteJobStatus.COMPLETED.name().equals(job.getStatus()))) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
        requireJobItem(jobId, assetId);
        if (action == null) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        Date now = new Date();
        String beforeStatus = currentStatus(assetId);
        switch (action) {
            case MARK_WRITTEN -> {
                if (assetDao.markWritten(assetId, jobId, operatorId, now) != 1) {
                    throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
                }
            }
            case MARK_WRITE_FAILED -> {
                // 写坏回退：留任务内重写，不报废（ADR 0003 Q6 决策）
                if (assetDao.revertWrittenToSchemeGenerated(assetId, jobId, operatorId, now) != 1) {
                    throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
                }
            }
            case MARK_VERIFIED -> {
                if (assetDao.markVerified(assetId, jobId,
                        PdcNfcVerifySource.MANUAL.name(), operatorId, now) != 1) {
                    throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
                }
            }
            case MARK_LOCKED -> {
                // 锁卡不可逆：仅 VERIFIED 后可确认（ADR 0003 顺序约束）
                if (assetDao.markLocked(assetId, operatorId, now) != 1) {
                    throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
                }
            }
        }
        String afterStatus = currentStatus(assetId);
        logOperation(operatorId, assetId, action.name(), beforeStatus, afterStatus);

        if (action == PdcNfcManualMarkAction.MARK_VERIFIED) {
            maybeComplete(jobId, operatorId);
        }
        return toVO(assetDao.selectById(assetId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touchVerify(PdcNfcAssetEntity asset) {
        if (asset == null || asset.getId() == null) {
            return;
        }
        Date now = new Date();
        if (PdcNfcAssetStatus.WRITTEN.name().equals(asset.getStatus())
                && asset.getActiveWriteJobId() != null) {
            PdcNfcWriteJobEntity job = jobDao.selectById(asset.getActiveWriteJobId());
            if (job == null || !isManual(job)
                    || !PdcNfcWriteJobStatus.CREATED.name().equals(job.getStatus())) {
                // 野生触碰或非手动任务：不动状态
                return;
            }
            // 触碰即证明 URI 写对、openlink 有效、微信能打开页（ADR 0003）
            int updated = assetDao.markVerified(asset.getId(), job.getId(),
                    PdcNfcVerifySource.TOUCH.name(), null, now);
            if (updated == 1) {
                log.info("Manual write touch verified: assetId={}, jobId={}", asset.getId(), job.getId());
                // 触碰验证落操作日志（source=MINI，无操作人），事后可追溯
                logOperation("MINI", null, asset.getId(), "TOUCH_VERIFY",
                        PdcNfcAssetStatus.WRITTEN.name(), PdcNfcAssetStatus.VERIFIED.name());
                maybeComplete(job.getId(), null);
            }
            return;
        }
        // 锁后触碰复验：确认锁卡后标签仍可读，废卡拦在操作员手里
        if (PdcNfcAssetStatus.VERIFIED.name().equals(asset.getStatus())
                && asset.getLockedAt() != null && asset.getLockVerifiedAt() == null) {
            assetDao.markLockVerified(asset.getId(), now);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void maybeComplete(Long jobId, Long operatorId) {
        PdcNfcWriteJobEntity job = jobDao.selectById(jobId);
        if (job == null || !isManual(job)
                || !PdcNfcWriteJobStatus.CREATED.name().equals(job.getStatus())) {
            return;
        }
        List<PdcNfcWriteJobItemEntity> items = jobItemDao.selectList(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId));
        if (items.isEmpty()) {
            return;
        }
        List<Long> assetIds = items.stream().map(PdcNfcWriteJobItemEntity::getAssetId).toList();
        Long verifiedCount = assetDao.selectCount(
                new LambdaQueryWrapper<PdcNfcAssetEntity>()
                        .in(PdcNfcAssetEntity::getId, assetIds)
                        .eq(PdcNfcAssetEntity::getStatus, PdcNfcAssetStatus.VERIFIED.name()));
        if (verifiedCount == null || verifiedCount != assetIds.size()) {
            return;
        }

        // 全部验证通过：任务 CREATED → COMPLETED，批次 WRITING → READY_FOR_STOCK
        Date now = new Date();
        writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.CREATED, PdcNfcWriteJobStatus.COMPLETED);
        job.setStatus(PdcNfcWriteJobStatus.COMPLETED.name());
        job.setCompletedAt(now);
        job.setSuccessCount(assetIds.size());
        job.setFailureCount(0);
        job.setUpdater(operatorId);
        job.setUpdateDate(now);
        if (jobDao.updateById(job) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        PdcNfcBatchEntity batch = batchDao.selectById(job.getBatchId());
        if (batch != null && PdcNfcBatchStatus.WRITING.name().equals(batch.getStatus())) {
            batchStateMachine.requireTransition(
                    PdcNfcBatchStatus.WRITING, PdcNfcBatchStatus.READY_FOR_STOCK);
            batchDao.transitionStatus(job.getBatchId(),
                    PdcNfcBatchStatus.WRITING.name(),
                    PdcNfcBatchStatus.READY_FOR_STOCK.name(),
                    operatorId, now);
        }
        log.info("Manual write job {} completed, total={}", jobId, assetIds.size());
    }

    // --- helpers ---

    private PdcNfcWriteJobEntity requireManualJob(Long jobId) {
        PdcNfcWriteJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }
        if (!isManual(job)) {
            // 工厂 CSV 任务不允许走手动通道，反之亦然
            throw new RenException(ErrorCode.PDC_NFC_JOB_MODE_MISMATCH);
        }
        return job;
    }

    private static boolean isManual(PdcNfcWriteJobEntity job) {
        // 存量任务 mode 为空，按工厂 CSV 处理
        return PdcNfcWriteJobMode.MANUAL.name().equals(job.getMode());
    }

    private static void requireJobInProgress(PdcNfcWriteJobEntity job) {
        if (!PdcNfcWriteJobStatus.CREATED.name().equals(job.getStatus())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }

    private void requireJobItem(Long jobId, Long assetId) {
        Long count = jobItemDao.selectCount(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId)
                        .eq(PdcNfcWriteJobItemEntity::getAssetId, assetId));
        if (count == null || count == 0) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }
    }

    private String currentStatus(Long assetId) {
        PdcNfcAssetEntity asset = assetDao.selectById(assetId);
        return asset != null ? asset.getStatus() : null;
    }

    private PdcNfcManualAssetVO toVO(PdcNfcAssetEntity asset) {
        return new PdcNfcManualAssetVO(
                asset.getId(),
                asset.getAssetNo(),
                asset.getWechatSn(),
                asset.getPrototype(),
                asset.getStatus(),
                asset.getVerifySource(),
                asset.getWrittenAt(),
                asset.getVerifiedAt(),
                asset.getLockedAt(),
                asset.getLockVerifiedAt());
    }

    private void logOperation(Long operatorId, Long assetId, String operationType,
                              String beforeStatus, String afterStatus) {
        logOperation("ADMIN", operatorId, assetId, operationType, beforeStatus, afterStatus);
    }

    private void logOperation(String source, Long operatorId, Long assetId, String operationType,
                              String beforeStatus, String afterStatus) {
        PdcNfcOperationLogEntity logEntry = new PdcNfcOperationLogEntity();
        logEntry.setOperatorUserId(operatorId);
        logEntry.setSource(source);
        logEntry.setObjectType("ASSET");
        logEntry.setObjectId(assetId);
        logEntry.setOperationType(operationType);
        logEntry.setBeforeStatus(beforeStatus);
        logEntry.setAfterStatus(afterStatus);
        logEntry.setResult("SUCCESS");
        logEntry.setCreateDate(new Date());
        operationLogDao.insert(logEntry);
    }
}
