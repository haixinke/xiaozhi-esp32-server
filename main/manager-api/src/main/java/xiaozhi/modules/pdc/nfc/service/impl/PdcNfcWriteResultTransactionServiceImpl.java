package xiaozhi.modules.pdc.nfc.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteRecordDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcWriteResultRow;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteRecordEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAssetStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultTransactionService;
import xiaozhi.modules.pdc.nfc.service.ValidatedWriteResult;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.SCHEME_GENERATED;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.SCRAPPED;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.VERIFIED;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.WRITTEN;

/**
 * 写卡结果的原子数据库写入边界。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcWriteResultTransactionServiceImpl
        implements PdcNfcWriteResultTransactionService {

    private final PdcNfcWriteJobDao jobDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcWriteRecordDao writeRecordDao;
    private final PdcNfcOperationLogDao operationLogDao;
    private final PdcNfcWriteJobStateMachine writeJobStateMachine;
    private final PdcNfcAssetStateMachine assetStateMachine;
    private final PdcNfcBatchStateMachine batchStateMachine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcWriteImportVO apply(
            Long jobId,
            List<ValidatedWriteResult> rows,
            String resultFileSha256,
            Long operatorId,
            UUID requestId) {
        validateArguments(jobId, rows, resultFileSha256, operatorId, requestId);
        PdcNfcWriteJobEntity job = loadJob(jobId);
        String originalJobStatus = job.getStatus();
        PdcNfcBatchEntity batch = loadBatch(job.getBatchId());
        Map<Long, PdcNfcAssetEntity> lockedAssets = lockAssets(rows);

        int verifiedCount = 0;
        int writtenCount = 0;
        int failureCount = 0;
        Date importedAt = new Date();

        for (ValidatedWriteResult validated : rows) {
            PdcNfcAssetEntity asset =
                    lockedAssets.get(validated.asset().getId());
            if (asset == null
                    || !validated.item().getAssetId().equals(asset.getId())) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }

            applyAssetState(asset, validated, operatorId, importedAt);
            insertWriteRecord(jobId, asset, validated, operatorId, importedAt);

            if (validated.fullyVerified()) {
                verifiedCount++;
            } else if (validated.targetStatus() == WRITTEN) {
                writtenCount++;
            } else {
                failureCount++;
            }
        }

        boolean allVerified = verifiedCount == rows.size();
        updateJob(
                job,
                originalJobStatus,
                verifiedCount,
                failureCount,
                resultFileSha256,
                requestId,
                operatorId,
                importedAt,
                allVerified);
        updateBatch(batch, operatorId, importedAt, allVerified);
        insertAuditLog(
                job,
                originalJobStatus,
                operatorId,
                requestId,
                rows.size(),
                importedAt);

        log.info(
                "NFC write result imported: jobId={}, verified={}, written={}, failed={}",
                jobId, verifiedCount, writtenCount, failureCount);
        return new PdcNfcWriteImportVO(
                jobId,
                job.getJobNo(),
                verifiedCount,
                writtenCount,
                failureCount,
                resultFileSha256,
                requestId);
    }

    private void validateArguments(
            Long jobId,
            List<ValidatedWriteResult> rows,
            String resultFileSha256,
            Long operatorId,
            UUID requestId) {
        if (jobId == null || rows == null || rows.isEmpty()
                || rows.stream().anyMatch(row ->
                        row == null || row.row() == null
                                || row.item() == null || row.asset() == null
                                || row.targetStatus() == null)
                || resultFileSha256 == null
                || !resultFileSha256.matches("[0-9a-fA-F]{64}")
                || operatorId == null || requestId == null) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }

    private PdcNfcWriteJobEntity loadJob(Long jobId) {
        PdcNfcWriteJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }
        if (!PdcNfcWriteJobStatus.EXPORTED.name().equals(job.getStatus())
                && !PdcNfcWriteJobStatus.RESULT_IMPORTED.name().equals(job.getStatus())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
        return job;
    }

    private PdcNfcBatchEntity loadBatch(Long batchId) {
        PdcNfcBatchEntity batch = batchDao.selectById(batchId);
        if (batch == null) {
            throw new RenException(ErrorCode.PDC_NFC_BATCH_NOT_FOUND);
        }
        return batch;
    }

    private Map<Long, PdcNfcAssetEntity> lockAssets(
            List<ValidatedWriteResult> rows) {
        List<Long> assetIds = rows.stream()
                .map(row -> row.asset().getId())
                .sorted()
                .toList();
        if (assetIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(assetIds).size() != assetIds.size()) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
        List<PdcNfcAssetEntity> assets =
                assetDao.selectByIdsForUpdate(assetIds);
        if (assets == null || assets.size() != assetIds.size()) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }
        Map<Long, PdcNfcAssetEntity> indexed = new HashMap<>();
        for (PdcNfcAssetEntity asset : assets) {
            if (asset == null || asset.getId() == null
                    || !assetIds.contains(asset.getId())
                    || indexed.put(asset.getId(), asset) != null) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        }
        return Map.copyOf(indexed);
    }

    private void applyAssetState(
            PdcNfcAssetEntity asset,
            ValidatedWriteResult validated,
            Long operatorId,
            Date importedAt) {
        PdcNfcAssetStatus current = PdcNfcAssetStatus.valueOf(asset.getStatus());
        PdcNfcAssetStatus target = validated.targetStatus();
        Date writtenAt = Timestamp.valueOf(validated.row().writtenAt());

        if (current != target) {
            if (current == SCHEME_GENERATED && target == VERIFIED) {
                assetStateMachine.requireTransition(SCHEME_GENERATED, WRITTEN);
                assetStateMachine.requireTransition(WRITTEN, VERIFIED);
                asset.setWrittenAt(writtenAt);
                asset.setVerifiedAt(importedAt);
            } else {
                assetStateMachine.requireTransition(current, target);
                if (target == WRITTEN) {
                    asset.setWrittenAt(writtenAt);
                } else if (target == VERIFIED) {
                    if (asset.getWrittenAt() == null) {
                        asset.setWrittenAt(writtenAt);
                    }
                    asset.setVerifiedAt(importedAt);
                } else if (target == SCRAPPED) {
                    asset.setScrappedAt(importedAt);
                }
            }
            asset.setStatus(target.name());
        }

        if (target == WRITTEN || target == VERIFIED) {
            asset.setActiveWriteJobId(null);
            asset.setTagUid(emptyToNull(validated.row().tagUid()));
        }
        asset.setUpdater(operatorId);
        asset.setUpdateDate(importedAt);
        if (assetDao.updateById(asset) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }

    private void insertWriteRecord(
            Long jobId,
            PdcNfcAssetEntity asset,
            ValidatedWriteResult validated,
            Long operatorId,
            Date importedAt) {
        PdcNfcWriteResultRow row = validated.row();
        PdcNfcWriteRecordEntity record = new PdcNfcWriteRecordEntity();
        record.setJobId(jobId);
        record.setAssetId(asset.getId());
        record.setAttemptNo(1);
        record.setWriteResult(row.writeResult());
        record.setVerifyResult(row.verifyResult());
        record.setTagUid(emptyToNull(row.tagUid()));
        record.setNdefRecordCount(row.ndefRecordCount());
        record.setUriSha256(row.uriSha256());
        record.setAarPackage(row.aarPackage());
        record.setIsReadOnly(row.isReadOnly());
        record.setErrorCode(emptyToNull(row.errorCode()));
        record.setErrorMessage(emptyToNull(row.errorMessage()));
        record.setWrittenAt(Timestamp.valueOf(row.writtenAt()));
        record.setImportedAt(importedAt);
        record.setImportUserId(operatorId);
        if (writeRecordDao.insert(record) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }

    private void updateJob(
            PdcNfcWriteJobEntity job,
            String originalStatus,
            int verifiedCount,
            int failureCount,
            String resultFileSha256,
            UUID requestId,
            Long operatorId,
            Date importedAt,
            boolean allVerified) {
        PdcNfcWriteJobStatus from = PdcNfcWriteJobStatus.valueOf(originalStatus);
        if (from == PdcNfcWriteJobStatus.EXPORTED) {
            writeJobStateMachine.requireTransition(
                    PdcNfcWriteJobStatus.EXPORTED,
                    PdcNfcWriteJobStatus.RESULT_IMPORTED);
        }
        if (allVerified) {
            writeJobStateMachine.requireTransition(
                    PdcNfcWriteJobStatus.RESULT_IMPORTED,
                    PdcNfcWriteJobStatus.COMPLETED);
            job.setStatus(PdcNfcWriteJobStatus.COMPLETED.name());
            job.setCompletedAt(importedAt);
        } else {
            job.setStatus(PdcNfcWriteJobStatus.RESULT_IMPORTED.name());
        }
        job.setSuccessCount(verifiedCount);
        job.setFailureCount(failureCount);
        job.setResultFileSha256(resultFileSha256);
        job.setImportRequestId(requestId.toString());
        job.setImportUserId(operatorId);
        job.setImportedAt(importedAt);
        job.setUpdater(operatorId);
        job.setUpdateDate(importedAt);
        if (jobDao.updateById(job) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }

    private void updateBatch(
            PdcNfcBatchEntity batch,
            Long operatorId,
            Date importedAt,
            boolean allVerified) {
        if (!allVerified
                || !PdcNfcBatchStatus.WRITING.name().equals(batch.getStatus())) {
            return;
        }
        batchStateMachine.requireTransition(
                PdcNfcBatchStatus.WRITING,
                PdcNfcBatchStatus.READY_FOR_STOCK);
        batch.setStatus(PdcNfcBatchStatus.READY_FOR_STOCK.name());
        batch.setUpdater(operatorId);
        batch.setUpdateDate(importedAt);
        if (batchDao.updateById(batch) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }

    private void insertAuditLog(
            PdcNfcWriteJobEntity job,
            String originalStatus,
            Long operatorId,
            UUID requestId,
            int quantity,
            Date importedAt) {
        PdcNfcOperationLogEntity operation = new PdcNfcOperationLogEntity();
        operation.setOperatorUserId(operatorId);
        operation.setRequestId(requestId.toString());
        operation.setSource("ADMIN");
        operation.setObjectType("WRITE_JOB");
        operation.setObjectId(job.getId());
        operation.setOperationType("IMPORT_RESULT");
        operation.setBeforeStatus(originalStatus);
        operation.setAfterStatus(job.getStatus());
        operation.setQuantity(quantity);
        operation.setResult("SUCCESS");
        operation.setCreateDate(importedAt);
        if (operationLogDao.insert(operation) != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
