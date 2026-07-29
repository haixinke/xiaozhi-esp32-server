package xiaozhi.modules.pdc.nfc.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcSchemeJobStatus;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.crypto.EncryptedField;
import xiaozhi.modules.pdc.nfc.crypto.SchemeEncryption;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NFC Scheme 任务 worker：在专用线程池中异步执行。
 * <p>
 * 游标分页迭代 CREATED 资产，每次最多 200 个；每个资产调用微信接口前先经过 Redis
 * 秒级限速器；成功后加密 Scheme 并推进资产状态；RETRYABLE 指数退避重试，QUOTA_DEFER
 * 延后到次日 00:05（Asia/Shanghai），TASK_FATAL 标记任务失败。
 * <p>
 * Worker 每 20 秒发送心跳续租；租约 60 秒，过期后由 dispatcher 恢复。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pdc.nfc", name = "enabled", havingValue = "true")
public class PdcNfcSchemeJobWorker {

    static final int BATCH_SIZE = 200;
    static final long LEASE_SECONDS = 60L;
    static final long HEARTBEAT_INTERVAL_MS = 20_000L;
    static final int MAX_RETRIES = 5;
    static final long[] BACKOFF_MS = {1_000L, 2_000L, 4_000L, 8_000L, 16_000L};
    static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final PdcNfcSchemeJobDao jobDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcBatchDao batchDao;
    private final ClaimRefProtection claimRefProtection;
    private final WechatNfcSchemeClient schemeClient;
    private final PdcNfcSchemeRateLimiter rateLimiter;
    private final PdcNfcBatchStateMachine batchStateMachine;

    public PdcNfcSchemeJobWorker(PdcNfcSchemeJobDao jobDao,
                                 PdcNfcAssetDao assetDao,
                                 PdcNfcBatchDao batchDao,
                                 ClaimRefProtection claimRefProtection,
                                 WechatNfcSchemeClient schemeClient,
                                 PdcNfcSchemeRateLimiter rateLimiter,
                                 PdcNfcBatchStateMachine batchStateMachine) {
        this.jobDao = jobDao;
        this.assetDao = assetDao;
        this.batchDao = batchDao;
        this.claimRefProtection = claimRefProtection;
        this.schemeClient = schemeClient;
        this.rateLimiter = rateLimiter;
        this.batchStateMachine = batchStateMachine;
    }

    /**
     * 执行指定 job。由 dispatcher 在获取租约后调用。
     */
    public void run(Long jobId, String instanceId) {
        PdcNfcSchemeJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            log.warn("Scheme job {} not found", jobId);
            return;
        }
        if (!PdcNfcSchemeJobStatus.RUNNING.name().equals(job.getStatus())) {
            log.warn("Scheme job {} status is {}, skip", jobId, job.getStatus());
            return;
        }

        int successCount = job.getSuccessCount() != null ? job.getSuccessCount() : 0;
        int failureCount = job.getFailureCount() != null ? job.getFailureCount() : 0;
        long cursor = job.getCursorAssetId() != null ? job.getCursorAssetId() : 0L;
        long lastHeartbeatMs = System.currentTimeMillis();

        log.info("Scheme job {} started, batchId={}, cursor={}, success={}, failure={}",
                jobId, job.getBatchId(), cursor, successCount, failureCount);

        try {
            while (true) {
                // 检查取消
                PdcNfcSchemeJobEntity current = jobDao.selectById(jobId);
                if (current == null
                        || PdcNfcSchemeJobStatus.CANCELLED.name().equals(current.getStatus())) {
                    log.info("Scheme job {} cancelled, exiting", jobId);
                    return;
                }

                // 心跳
                lastHeartbeatMs = maybeHeartbeat(jobId, instanceId, lastHeartbeatMs);

                // 游标分页查询
                List<PdcNfcAssetEntity> assets = assetDao.selectCreatedAssetsAfterCursor(
                        job.getBatchId(), cursor, BATCH_SIZE);

                if (assets.isEmpty()) {
                    // 全部处理完毕
                    String finalStatus = (failureCount == 0)
                            ? PdcNfcSchemeJobStatus.SUCCEEDED.name()
                            : PdcNfcSchemeJobStatus.PARTIAL_SUCCESS.name();
                    jobDao.completeJob(jobId, finalStatus, null, null, new Date());
                    maybeTransitionBatch(job.getBatchId(), finalStatus);
                    log.info("Scheme job {} completed: {} (success={}, failure={})",
                            jobId, finalStatus, successCount, failureCount);
                    return;
                }

                for (PdcNfcAssetEntity asset : assets) {
                    // 检查取消
                    current = jobDao.selectById(jobId);
                    if (current == null
                            || PdcNfcSchemeJobStatus.CANCELLED.name().equals(current.getStatus())) {
                        return;
                    }

                    lastHeartbeatMs = maybeHeartbeat(jobId, instanceId, lastHeartbeatMs);

                    ProcessingOutcome outcome = processAsset(jobId, job.getBatchId(), asset);

                    switch (outcome) {
                        case SUCCESS -> successCount++;
                        case FAILURE -> {
                            failureCount++;
                            log.warn("Scheme job {} asset {} exhausted retries, continuing",
                                    jobId, asset.getId());
                        }
                        case QUOTA_DEFER -> {
                            Date nextRetryAt = nextDayAt005Shanghai();
                            jobDao.completeJob(jobId, PdcNfcSchemeJobStatus.RUNNING.name(),
                                    nextRetryAt, null, new Date());
                            log.info("Scheme job {} quota deferred to {}", jobId, nextRetryAt);
                            return;
                        }
                        case FATAL -> {
                            assetDao.releaseAssetsForJob(job.getBatchId(), jobId);
                            jobDao.completeJob(jobId, PdcNfcSchemeJobStatus.FAILED.name(),
                                    null, null, new Date());
                            log.error("Scheme job {} FATAL error, marked FAILED", jobId);
                            return;
                        }
                    }

                    cursor = asset.getId();
                    jobDao.updateProgress(jobId, cursor, successCount, failureCount, new Date());
                }
            }
        } catch (Exception e) {
            log.error("Scheme job {} unexpected error, lease will expire for recovery", jobId, e);
        } finally {
            // 释放租约（completeJob 已清租约的此处为 no-op；异常退出时释放）
            try {
                jobDao.releaseLease(jobId, instanceId, new Date());
            } catch (Exception e) {
                log.warn("Failed to release lease for job {}", jobId, e);
            }
        }
    }

    /**
     * 处理单个资产：解密 claimRef → 调用微信 → 加密 Scheme → 更新资产。
     */
    private ProcessingOutcome processAsset(Long jobId, Long batchId, PdcNfcAssetEntity asset) {

        EncryptedField field = new EncryptedField(
                asset.getClaimRefKeyVersion(),
                asset.getClaimRefNonce(),
                asset.getClaimRefCiphertext()
        );
        String claimRef = claimRefProtection.decrypt(asset.getId(), field);

        WechatNfcSchemeResult result = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                long backoff = BACKOFF_MS[attempt - 1];
                long jitter = ThreadLocalRandom.current().nextLong(250);
                sleep(backoff + jitter);
            }

            rateLimiter.acquire();
            result = schemeClient.generate(asset.getWechatSn(), claimRef);

            if (result.success()) {
                SchemeEncryption enc = claimRefProtection.encryptScheme(asset.getId(), result.scheme());
                int updated = assetDao.markSchemeGenerated(
                        asset.getId(),
                        enc.encrypted().keyVersion(),
                        enc.encrypted().nonce(),
                        enc.encrypted().ciphertext(),
                        enc.sha256(),
                        jobId,
                        new Date()
                );
                if (updated == 0) {
                    // 已被其他实例处理（恢复后不重复覆盖），视为成功
                    log.info("Scheme job {} asset {} already processed, skipping", jobId, asset.getId());
                }
                return ProcessingOutcome.SUCCESS;
            }

            if (result.action() == WechatNfcErrorAction.QUOTA_DEFER) {
                log.warn("Scheme job {} asset {} quota deferred: {} {}",
                        jobId, asset.getId(), result.errcode(), result.errmsg());
                return ProcessingOutcome.QUOTA_DEFER;
            }

            if (result.action() == WechatNfcErrorAction.TASK_FATAL) {
                log.error("Scheme job {} asset {} FATAL: {} {}",
                        jobId, asset.getId(), result.errcode(), result.errmsg());
                return ProcessingOutcome.FATAL;
            }

            // RETRYABLE → 继续退避重试
        }

        // 退避重试耗尽，记录失败并继续
        assert result != null;
        log.warn("Scheme job {} asset {} retries exhausted: {} {}",
                jobId, asset.getId(), result.errcode(), result.errmsg());
        return ProcessingOutcome.FAILURE;
    }

    private long maybeHeartbeat(Long jobId, String instanceId, long lastHeartbeatMs) {
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
            Date nowDate = new Date(now);
            Date leaseUntil = new Date(now + LEASE_SECONDS * 1_000L);
            jobDao.heartbeat(jobId, instanceId, nowDate, leaseUntil);
            return now;
        }
        return lastHeartbeatMs;
    }

    private void maybeTransitionBatch(Long batchId, String jobStatus) {
        if (!PdcNfcSchemeJobStatus.SUCCEEDED.name().equals(jobStatus)) {
            return;
        }
        try {
            PdcNfcBatchEntity batch = batchDao.selectById(batchId);
            if (batch == null) {
                return;
            }
            if (!PdcNfcBatchStatus.SCHEME_GENERATING.name().equals(batch.getStatus())) {
                return;
            }
            batchStateMachine.requireTransition(
                    PdcNfcBatchStatus.SCHEME_GENERATING,
                    PdcNfcBatchStatus.READY_FOR_WRITE
            );
            batch.setStatus(PdcNfcBatchStatus.READY_FOR_WRITE.name());
            batch.setUpdateDate(new Date());
            batchDao.updateById(batch);
            log.info("Batch {} transitioned to READY_FOR_WRITE", batchId);
        } catch (Exception e) {
            log.warn("Failed to transition batch {} to READY_FOR_WRITE", batchId, e);
        }
    }

    static Date nextDayAt005Shanghai() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI_ZONE);
        ZonedDateTime nextDay = now.toLocalDate().plusDays(1).atTime(0, 5).atZone(SHANGHAI_ZONE);
        return Date.from(nextDay.toInstant());
    }

    /**
     * 可重写的 sleep，便于单测以 spy 消除等待。
     */
    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private enum ProcessingOutcome {
        SUCCESS, FAILURE, QUOTA_DEFER, FATAL
    }
}
