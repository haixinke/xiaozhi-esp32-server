package xiaozhi.modules.pdc.nfc.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcSchemeJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcSchemeJobDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcReadinessService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcSchemeJobService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcSchemeProgressVO;

import java.util.Date;

/**
 * NFC Scheme 任务服务实现：HTTP 线程仅创建/查询/取消 job 记录后立即返回。
 * 实际生成由 dispatcher 调度、worker 异步执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcSchemeJobServiceImpl implements PdcNfcSchemeJobService {

    private final PdcNfcSchemeJobDao jobDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcReadinessService readiness;
    private final PdcNfcBatchStateMachine batchStateMachine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(Long batchId, Long operatorId) {
        readiness.requireSchemeGenerationReady();

        PdcNfcBatchEntity batch = batchDao.selectById(batchId);
        if (batch == null) {
            throw new RenException(ErrorCode.PDC_NFC_BATCH_NOT_FOUND);
        }
        if (!PdcNfcBatchStatus.DRAFT.name().equals(batch.getStatus())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        int totalCount = assetDao.countCreatedAssets(batchId);
        if (totalCount == 0) {
            throw new RenException(ErrorCode.PDC_NFC_RELEASE_NOT_READY);
        }

        // 批次状态转换 DRAFT → SCHEME_GENERATING
        batchStateMachine.requireTransition(PdcNfcBatchStatus.DRAFT, PdcNfcBatchStatus.SCHEME_GENERATING);
        batch.setStatus(PdcNfcBatchStatus.SCHEME_GENERATING.name());
        batch.setUpdater(operatorId);
        batch.setUpdateDate(new Date());
        batchDao.updateById(batch);

        // 创建 PENDING job
        Date now = new Date();
        PdcNfcSchemeJobEntity job = new PdcNfcSchemeJobEntity();
        job.setJobNo("SCH-" + batchId + "-" + now.getTime());
        job.setBatchId(batchId);
        job.setStatus(PdcNfcSchemeJobStatus.PENDING.name());
        job.setRequestedBy(operatorId);
        job.setTotalCount(totalCount);
        job.setSuccessCount(0);
        job.setFailureCount(0);
        job.setCursorAssetId(0L);
        job.setCreateDate(now);
        jobDao.insert(job);

        // 绑定 job 到 CREATED 资产
        assetDao.assignJobToCreatedAssets(batchId, job.getId());

        log.info("Scheme job {} created for batch {}, total={}, operator={}",
                job.getId(), batchId, totalCount, operatorId);
        return job.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long retry(Long batchId, Long operatorId) {
        readiness.requireSchemeGenerationReady();

        PdcNfcSchemeJobEntity previous = jobDao.selectLatestByBatchId(batchId);
        if (previous == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }

        String prevStatus = previous.getStatus();
        if (!PdcNfcSchemeJobStatus.FAILED.name().equals(prevStatus)
                && !PdcNfcSchemeJobStatus.PARTIAL_SUCCESS.name().equals(prevStatus)) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_CONFLICT);
        }

        Date now = new Date();
        PdcNfcSchemeJobEntity job = new PdcNfcSchemeJobEntity();
        job.setJobNo("SCH-" + batchId + "-" + now.getTime());
        job.setBatchId(batchId);
        job.setStatus(PdcNfcSchemeJobStatus.PENDING.name());
        job.setRequestedBy(operatorId);
        job.setTotalCount(previous.getTotalCount());
        job.setSuccessCount(previous.getSuccessCount() != null ? previous.getSuccessCount() : 0);
        job.setFailureCount(previous.getFailureCount() != null ? previous.getFailureCount() : 0);
        job.setCursorAssetId(previous.getCursorAssetId() != null ? previous.getCursorAssetId() : 0L);
        job.setCreateDate(now);
        jobDao.insert(job);

        assetDao.assignJobToCreatedAssets(batchId, job.getId());

        log.info("Scheme retry job {} created for batch {}, cursor={}, operator={}",
                job.getId(), batchId, job.getCursorAssetId(), operatorId);
        return job.getId();
    }

    @Override
    public PdcNfcSchemeProgressVO progress(Long batchId) {
        PdcNfcSchemeJobEntity job = jobDao.selectLatestByBatchId(batchId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }
        return new PdcNfcSchemeProgressVO(
                job.getId(),
                job.getJobNo(),
                job.getBatchId(),
                job.getStatus(),
                job.getTotalCount(),
                job.getSuccessCount(),
                job.getFailureCount(),
                job.getCursorAssetId(),
                job.getNextRetryAt(),
                null
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long jobId, Long operatorId) {
        PdcNfcSchemeJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }

        String status = job.getStatus();
        if (!PdcNfcSchemeJobStatus.PENDING.name().equals(status)
                && !PdcNfcSchemeJobStatus.RUNNING.name().equals(status)) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_CONFLICT);
        }

        int cancelled = jobDao.cancelJob(jobId, new Date());
        if (cancelled == 0) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_CONFLICT);
        }

        assetDao.releaseAssetsForJob(job.getBatchId(), jobId);
        log.info("Scheme job {} cancelled by {}", jobId, operatorId);
    }
}
