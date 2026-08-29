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
import xiaozhi.modules.pdc.nfc.service.PdcNfcReadinessService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteCsvExporter;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteCsvRow;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobStateMachine;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteFile;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteJobVO;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NFC 写卡任务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcWriteJobServiceImpl implements PdcNfcWriteJobService {

    private static final int ASSET_LOAD_BATCH_SIZE = 500;

    private final PdcNfcWriteJobDao jobDao;
    private final PdcNfcWriteJobItemDao jobItemDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcOperationLogDao operationLogDao;
    private final PdcNfcReadinessService readiness;
    private final PdcNfcBatchStateMachine batchStateMachine;
    private final PdcNfcWriteJobStateMachine writeJobStateMachine;
    private final PdcNfcWriteCsvExporter csvExporter;
    private final ClaimRefProtection claimRefProtection;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcWriteJobVO create(Long batchId, String mode, Long operatorId) {
        readiness.requireSchemeGenerationReady();

        // 模式创建时选定不可变更（ADR 0003）；为空默认工厂 CSV，保持存量调用兼容
        String jobMode = mode == null || mode.isBlank()
                ? PdcNfcWriteJobMode.FACTORY_CSV.name() : mode.trim().toUpperCase();
        try {
            PdcNfcWriteJobMode.valueOf(jobMode);
        } catch (IllegalArgumentException e) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_JOB_MODE);
        }

        PdcNfcBatchEntity batch = batchDao.selectById(batchId);
        if (batch == null) {
            throw new RenException(ErrorCode.PDC_NFC_BATCH_NOT_FOUND);
        }

        // 检查批次状态为 READY_FOR_WRITE
        if (!PdcNfcBatchStatus.READY_FOR_WRITE.name().equals(batch.getStatus())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        // 检查是否已有活跃写卡任务
        Long existingJobCount = jobDao.selectCount(
                new LambdaQueryWrapper<PdcNfcWriteJobEntity>()
                        .eq(PdcNfcWriteJobEntity::getBatchId, batchId)
                        .in(PdcNfcWriteJobEntity::getStatus,
                                PdcNfcWriteJobStatus.CREATED.name(),
                                PdcNfcWriteJobStatus.EXPORTED.name()));
        if (existingJobCount != null && existingJobCount > 0) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_CONFLICT);
        }

        // 选取 SCHEME_GENERATED 资产，按 itemNo 排序
        List<PdcNfcAssetEntity> assets = assetDao.selectList(
                new LambdaQueryWrapper<PdcNfcAssetEntity>()
                        .eq(PdcNfcAssetEntity::getBatchId, batchId)
                        .eq(PdcNfcAssetEntity::getStatus, PdcNfcAssetStatus.SCHEME_GENERATED.name())
                        .orderByAsc(PdcNfcAssetEntity::getItemNo));

        if (assets.isEmpty()) {
            // 批次内没有 SCHEME_GENERATED 状态资产可写卡，与发布就绪无关
            throw new RenException(ErrorCode.PDC_NFC_NO_AVAILABLE_ASSETS);
        }

        // 批量上限校验
        if (assets.size() > 10000) {
            throw new RenException(ErrorCode.PDC_NFC_BULK_LIMIT_EXCEEDED);
        }

        // 创建写卡任务
        Date now = new Date();

        // 批次 READY_FOR_WRITE -> WRITING，与任务创建同事务。
        // 用条件 UPDATE 原子翻转：并发请求只有一个能影响 1 行，
        // 输家影响 0 行 → 冲突，不会创建第二个有效任务。
        batchStateMachine.requireTransition(
                PdcNfcBatchStatus.READY_FOR_WRITE, PdcNfcBatchStatus.WRITING);
        int transitioned = batchDao.transitionStatus(
                batchId,
                PdcNfcBatchStatus.READY_FOR_WRITE.name(),
                PdcNfcBatchStatus.WRITING.name(),
                operatorId,
                now);
        if (transitioned != 1) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_CONFLICT);
        }

        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setJobNo("WRT-" + batchId + "-" + now.getTime());
        job.setBatchId(batchId);
        job.setFormatVersion(PdcNfcWriteCsvExporter.FORMAT_VERSION);
        job.setMode(jobMode);
        job.setStatus(PdcNfcWriteJobStatus.CREATED.name());
        job.setTotalCount(assets.size());
        job.setSuccessCount(0);
        job.setFailureCount(0);
        job.setRowCount(assets.size());
        job.setCreator(operatorId);
        job.setCreateDate(now);
        jobDao.insert(job);

        // 为每个资产创建快照行并绑定 active_write_job_id
        int seq = 1;
        for (PdcNfcAssetEntity asset : assets) {
            PdcNfcWriteJobItemEntity item = new PdcNfcWriteJobItemEntity();
            item.setJobId(job.getId());
            item.setAssetId(asset.getId());
            item.setSequenceNo(seq++);
            item.setAssetNo(asset.getAssetNo());
            item.setBatchNo(batch.getBatchNo());
            item.setWechatSn(asset.getWechatSn());
            item.setSkuCode(asset.getSkuCode());
            item.setPrototype(asset.getPrototype());
            item.setUriSha256(asset.getSchemeSha256());
            // NDEF 常量
            item.setUriTnf(PdcNfcWriteCsvExporter.URI_TNF);
            item.setUriType(PdcNfcWriteCsvExporter.URI_TYPE);
            item.setAarTnf(PdcNfcWriteCsvExporter.AAR_TNF);
            item.setAarType(PdcNfcWriteCsvExporter.AAR_TYPE);
            item.setAarPayload(PdcNfcWriteCsvExporter.AAR_PAYLOAD);
            item.setCreateDate(now);
            jobItemDao.insert(item);

            // 绑定 active_write_job_id
            asset.setActiveWriteJobId(job.getId());
            asset.setUpdater(operatorId);
            asset.setUpdateDate(now);
            assetDao.updateById(asset);
        }

        log.info("Write job {} created for batch {}, total={}, operator={}",
                job.getId(), batchId, assets.size(), operatorId);

        return toVO(job, batch.getBatchNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcWriteFile export(Long jobId, Long operatorId) {
        PdcNfcWriteJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }
        // 手动模式任务不走 CSV 导出通道（ADR 0003，两模式互斥）；mode 为空视为工厂模式
        if (PdcNfcWriteJobMode.MANUAL.name().equals(job.getMode())) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_MODE_MISMATCH);
        }

        PdcNfcBatchEntity batch = batchDao.selectById(job.getBatchId());
        String batchNo = batch != null ? batch.getBatchNo() : "";

        // 状态转换 CREATED → EXPORTED（首次导出）
        String status = job.getStatus();
        if (PdcNfcWriteJobStatus.CREATED.name().equals(status)) {
            writeJobStateMachine.requireTransition(
                    PdcNfcWriteJobStatus.CREATED, PdcNfcWriteJobStatus.EXPORTED);
            job.setStatus(PdcNfcWriteJobStatus.EXPORTED.name());
            job.setExportUserId(operatorId);
            job.setExportedAt(new Date());
        } else if (!PdcNfcWriteJobStatus.EXPORTED.name().equals(status)) {
            // 已导出的任务可重复下载
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        // 查询不可变快照行；Scheme 明文仅在本次导出期间存在于内存。
        List<PdcNfcWriteJobItemEntity> items = jobItemDao.selectList(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId)
                        .orderByAsc(PdcNfcWriteJobItemEntity::getSequenceNo));

        Map<Long, PdcNfcAssetEntity> assetsById = loadAssetsById(items);
        List<PdcNfcWriteCsvRow> csvRows = items.stream()
                .map(item -> decryptCsvRow(item, assetsById.get(item.getAssetId())))
                .toList();

        // 生成 CSV
        byte[] csvBytes = csvExporter.generate(job.getJobNo(), batchNo, csvRows);
        String sha256 = PdcNfcWriteCsvExporter.sha256Hex(csvBytes);

        // 先写审计再更新 job：审计失败则整个导出回滚，
        // 不会出现"明文已出库但无审计记录"的窗口。
        logOperation(operatorId, "WRITE_JOB", job.getId(), "EXPORT",
                null, PdcNfcWriteJobStatus.EXPORTED.name(), null);

        // 更新 job
        job.setFileSha256(sha256);
        jobDao.updateById(job);

        String fileName = job.getJobNo() + "_" + batchNo + ".csv";
        log.info("Write job {} exported, sha256={}, operator={}", jobId, sha256, operatorId);
        return new PdcNfcWriteFile(fileName, csvBytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long jobId, Long operatorId) {
        PdcNfcWriteJobEntity job = jobDao.selectByIdForUpdate(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }

        String status = job.getStatus();
        PdcNfcWriteJobStatus currentStatus = PdcNfcWriteJobStatus.valueOf(status);

        // 仅 CREATED/EXPORTED 可取消，且无导入结果
        if (job.getResultResponseJson() != null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_CONFLICT);
        }
        writeJobStateMachine.requireTransition(currentStatus, PdcNfcWriteJobStatus.CANCELLED);

        Date now = new Date();
        job.setStatus(PdcNfcWriteJobStatus.CANCELLED.name());
        job.setCancelledAt(now);
        job.setUpdater(operatorId);
        job.setUpdateDate(now);
        if (jobDao.updateById(job) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        // 释放 active_write_job_id
        List<PdcNfcWriteJobItemEntity> items = jobItemDao.selectList(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId));
        for (PdcNfcWriteJobItemEntity item : items) {
            if (assetDao.releaseWriteLease(
                    item.getAssetId(), jobId, operatorId, now) != 1) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        }

        // 批次 WRITING -> READY_FOR_WRITE，与取消同事务。
        // 取消仅在无写卡结果时允许，资产租约已释放，批次必须回到可建任务状态，
        // 否则批次会卡在 WRITING 既无活跃任务也无法新建任务。
        // 用条件 UPDATE 原子翻转：批次若已被其他流程推进，影响 0 行 → 整体回滚。
        batchStateMachine.requireTransition(
                PdcNfcBatchStatus.WRITING, PdcNfcBatchStatus.READY_FOR_WRITE);
        if (batchDao.transitionStatus(
                job.getBatchId(),
                PdcNfcBatchStatus.WRITING.name(),
                PdcNfcBatchStatus.READY_FOR_WRITE.name(),
                operatorId,
                now) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        log.info("Write job {} cancelled by {}", jobId, operatorId);
    }

    @Override
    public PdcNfcWriteJobVO getProgress(Long jobId) {
        PdcNfcWriteJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }
        PdcNfcBatchEntity batch = batchDao.selectById(job.getBatchId());
        String batchNo = batch != null ? batch.getBatchNo() : "";
        return toVO(job, batchNo);
    }

    // --- helpers ---

    private Map<Long, PdcNfcAssetEntity> loadAssetsById(
            List<PdcNfcWriteJobItemEntity> items) {
        List<Long> assetIds = items.stream()
                .map(PdcNfcWriteJobItemEntity::getAssetId)
                .toList();
        if (assetIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(assetIds).size() != assetIds.size()) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        Map<Long, PdcNfcAssetEntity> assetsById = new HashMap<>();
        for (int start = 0; start < assetIds.size(); start += ASSET_LOAD_BATCH_SIZE) {
            int end = Math.min(start + ASSET_LOAD_BATCH_SIZE, assetIds.size());
            List<Long> batchIds = assetIds.subList(start, end);
            Set<Long> expectedBatchIds = new HashSet<>(batchIds);
            List<PdcNfcAssetEntity> batchAssets = assetDao.selectBatchIds(batchIds);
            if (batchAssets == null) {
                // selectBatchIds 不应返回 null，走到这里属于内部数据异常
                throw new RenException(ErrorCode.PDC_NFC_ASSET_DATA_INCONSISTENT);
            }
            for (PdcNfcAssetEntity asset : batchAssets) {
                if (asset == null
                        || asset.getId() == null
                        || !expectedBatchIds.contains(asset.getId())
                        || assetsById.putIfAbsent(asset.getId(), asset) != null) {
                    throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
                }
            }
        }

        if (assetsById.size() != assetIds.size()
                || !assetsById.keySet().containsAll(assetIds)) {
            // 快照行引用的资产在库中缺失，数据不一致
            throw new RenException(ErrorCode.PDC_NFC_ASSET_DATA_INCONSISTENT);
        }
        return Map.copyOf(assetsById);
    }

    private PdcNfcWriteCsvRow decryptCsvRow(
            PdcNfcWriteJobItemEntity item, PdcNfcAssetEntity asset) {
        if (asset == null
                || asset.getSchemeKeyVersion() == null
                || asset.getSchemeNonce() == null
                || asset.getSchemeCiphertext() == null) {
            // Scheme 加密三要素缺失，资产数据不完整，无法解密导出
            throw new RenException(ErrorCode.PDC_NFC_ASSET_DATA_INCONSISTENT);
        }

        EncryptedField schemeField = new EncryptedField(
                asset.getSchemeKeyVersion(),
                asset.getSchemeNonce(),
                asset.getSchemeCiphertext());
        String scheme = claimRefProtection.decrypt(asset.getId(), schemeField);
        String uriSha256 = PdcNfcWriteCsvExporter.sha256Hex(
                scheme.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!uriSha256.equals(item.getUriSha256())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        return new PdcNfcWriteCsvRow(
                item.getSequenceNo(),
                item.getAssetNo(),
                item.getWechatSn(),
                item.getSkuCode(),
                item.getPrototype(),
                scheme);
    }

    private PdcNfcWriteJobVO toVO(PdcNfcWriteJobEntity job, String batchNo) {
        return new PdcNfcWriteJobVO(
                job.getId(),
                job.getJobNo(),
                job.getBatchId(),
                batchNo,
                job.getFormatVersion(),
                job.getMode() != null ? job.getMode() : PdcNfcWriteJobMode.FACTORY_CSV.name(),
                job.getStatus(),
                job.getTotalCount() != null ? job.getTotalCount() : 0,
                job.getSuccessCount() != null ? job.getSuccessCount() : 0,
                job.getFailureCount() != null ? job.getFailureCount() : 0,
                job.getFileSha256(),
                job.getRowCount() != null ? job.getRowCount() : 0,
                job.getExportedAt(),
                job.getCreateDate()
        );
    }

    private void logOperation(Long operatorId, String objectType, Long objectId,
                              String operationType, String beforeStatus, String afterStatus,
                              Integer quantity) {
        // 导出是敏感操作（Scheme 明文出库），审计写入失败必须透出，
        // 由 @Transactional 回滚整个导出，不允许业务成功但无审计。
        PdcNfcOperationLogEntity logEntry = new PdcNfcOperationLogEntity();
        logEntry.setOperatorUserId(operatorId);
        logEntry.setSource("ADMIN");
        logEntry.setObjectType(objectType);
        logEntry.setObjectId(objectId);
        logEntry.setOperationType(operationType);
        logEntry.setBeforeStatus(beforeStatus);
        logEntry.setAfterStatus(afterStatus);
        logEntry.setQuantity(quantity);
        logEntry.setResult("SUCCESS");
        logEntry.setCreateDate(new Date());
        operationLogDao.insert(logEntry);
    }
}
