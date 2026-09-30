package xiaozhi.modules.pdc.nfc.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcVerifySource;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobMode;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
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
import xiaozhi.modules.pdc.nfc.service.PdcNfcAssetStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcTrustReleaseService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobStateMachine;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcTrustReleaseVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.SCHEME_GENERATED;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.VERIFIED;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.WRITTEN;

/**
 * 免检放行实现（ADR 0005）。
 * <p>
 * 全有或全无：任务内任一资产不满足前置（非 SCHEME_GENERATED、租约不属于本任务）
 * 则整体回滚，不做部分放行。免检不提供 uri_sha256 / is_read_only 文件证据，
 * 锁卡保障来自操作员人工声明（审计落库）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcTrustReleaseServiceImpl implements PdcNfcTrustReleaseService {

    private final PdcNfcWriteJobDao jobDao;
    private final PdcNfcWriteJobItemDao jobItemDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcOperationLogDao operationLogDao;
    private final PdcNfcAssetStateMachine assetStateMachine;
    private final PdcNfcWriteJobStateMachine writeJobStateMachine;
    private final PdcNfcBatchStateMachine batchStateMachine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcTrustReleaseVO trustRelease(Long jobId, boolean lockConfirmed, Long operatorId, UUID requestId) {
        // 免检通道的唯一锁卡保障是人工声明，未声明显式确认即拒绝
        if (!lockConfirmed) {
            throw new RenException(ErrorCode.PDC_NFC_LOCK_DECLARATION_REQUIRED);
        }

        PdcNfcWriteJobEntity job = jobDao.selectByIdForUpdate(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }
        // 手动模式任务不走免检通道（ADR 0003，两模式互斥）；mode 为空视为工厂模式
        if (PdcNfcWriteJobMode.MANUAL.name().equals(job.getMode())) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_MODE_MISMATCH);
        }
        // 仅 EXPORTED 可免检放行；RESULT_IMPORTED 已有文件证据，与导入互斥
        if (!PdcNfcWriteJobStatus.EXPORTED.name().equals(job.getStatus())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        PdcNfcBatchEntity batch = batchDao.selectById(job.getBatchId());
        if (batch == null) {
            throw new RenException(ErrorCode.PDC_NFC_BATCH_NOT_FOUND);
        }

        List<PdcNfcWriteJobItemEntity> items = jobItemDao.selectList(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId)
                        .orderByAsc(PdcNfcWriteJobItemEntity::getSequenceNo));
        if (items.isEmpty()) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        List<Long> assetIds = items.stream()
                .map(PdcNfcWriteJobItemEntity::getAssetId)
                .sorted()
                .toList();
        List<PdcNfcAssetEntity> assets = assetDao.selectByIdsForUpdate(assetIds);
        if (assets == null || assets.size() != assetIds.size()) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }

        Date now = new Date();
        for (PdcNfcAssetEntity asset : assets) {
            // 预检（fail-fast）：资产必须属于本批次、仍被本任务租约持有、且待写卡
            if (!batch.getId().equals(asset.getBatchId())
                    || !jobId.equals(asset.getActiveWriteJobId())
                    || !SCHEME_GENERATED.name().equals(asset.getStatus())) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        }

        for (PdcNfcAssetEntity asset : assets) {
            // 与导入路径同一转换链：SCHEME_GENERATED -> WRITTEN -> VERIFIED；
            // writtenAt 留空——免检通道不知道工厂真实写卡时间，不虚记
            assetStateMachine.requireTransition(SCHEME_GENERATED, WRITTEN);
            assetStateMachine.requireTransition(WRITTEN, VERIFIED);
            asset.setStatus(VERIFIED.name());
            asset.setVerifiedAt(now);
            asset.setVerifySource(PdcNfcVerifySource.FACTORY_TRUST.name());
            asset.setUpdater(operatorId);
            asset.setUpdateDate(now);
            if (assetDao.updateById(asset) != 1) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
            // updateById 忽略 null 字段，租约释放走显式 CAS 更新（与 cancel 同路径）
            if (assetDao.releaseWriteLease(asset.getId(), jobId, operatorId, now) != 1) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        }

        // 任务 EXPORTED -> COMPLETED（免检放行收尾，resultResponseJson 备注放行方式）
        writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.EXPORTED, PdcNfcWriteJobStatus.COMPLETED);
        job.setStatus(PdcNfcWriteJobStatus.COMPLETED.name());
        job.setSuccessCount(assets.size());
        job.setFailureCount(0);
        job.setCompletedAt(now);
        job.setResultResponseJson("{\"trustRelease\":true,\"lockConfirmed\":true}");
        job.setUpdater(operatorId);
        job.setUpdateDate(now);
        if (jobDao.updateById(job) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        // 批次 WRITING -> READY_FOR_STOCK，条件 UPDATE 原子翻转：
        // 批次若已被其他流程推进，影响 0 行 -> 整体回滚
        if (PdcNfcBatchStatus.WRITING.name().equals(batch.getStatus())) {
            batchStateMachine.requireTransition(
                    PdcNfcBatchStatus.WRITING, PdcNfcBatchStatus.READY_FOR_STOCK);
            if (batchDao.transitionStatus(
                    batch.getId(),
                    PdcNfcBatchStatus.WRITING.name(),
                    PdcNfcBatchStatus.READY_FOR_STOCK.name(),
                    operatorId,
                    now) != 1) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        } else {
            // 资产与任务已推进但批次非 WRITING（如并发取消/状态漂移）：不阻断放行，留痕排查
            log.warn("Trust release: batch {} in unexpected status {}, skip READY_FOR_STOCK transition, jobId={}",
                    batch.getId(), batch.getStatus(), jobId);
        }

        // 审计：操作人/任务/资产数/锁卡声明落库，失败回滚整个放行
        PdcNfcOperationLogEntity operation = new PdcNfcOperationLogEntity();
        operation.setOperatorUserId(operatorId);
        operation.setRequestId(requestId.toString());
        operation.setSource("ADMIN");
        operation.setObjectType("WRITE_JOB");
        operation.setObjectId(job.getId());
        operation.setOperationType("TRUST_RELEASE");
        operation.setBeforeStatus(PdcNfcWriteJobStatus.EXPORTED.name());
        operation.setAfterStatus(PdcNfcWriteJobStatus.COMPLETED.name());
        operation.setQuantity(assets.size());
        operation.setDetailJson("{\"lockConfirmed\":true}");
        operation.setResult("SUCCESS");
        operation.setCreateDate(now);
        if (operationLogDao.insert(operation) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        log.info("Trust release applied: jobId={}, released={}, operator={}",
                jobId, assets.size(), operatorId);
        return new PdcNfcTrustReleaseVO(jobId, job.getJobNo(), assets.size(), requestId);
    }
}
