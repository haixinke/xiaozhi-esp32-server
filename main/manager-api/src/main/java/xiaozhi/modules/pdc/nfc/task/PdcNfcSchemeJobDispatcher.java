package xiaozhi.modules.pdc.nfc.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcSchemeJobDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * NFC Scheme 任务调度器：每 5 秒查询可恢复任务（PENDING 或 RUNNING 且租约过期），
 * 通过条件 UPDATE 获取租约后提交到专用线程池。
 * <p>
 * 多实例只有一个 lease_owner 成功获取租约；线程池满时 AbortPolicy 拒绝，
 * job 保持原状态由下次调度重新拾取。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pdc.nfc", name = "enabled", havingValue = "true")
public class PdcNfcSchemeJobDispatcher {

    private static final long LEASE_SECONDS = 60L;

    private final PdcNfcSchemeJobDao jobDao;
    private final PdcNfcSchemeJobWorker worker;
    private final ThreadPoolExecutor executor;

    /** 当前实例唯一标识 */
    private final String instanceId;

    public PdcNfcSchemeJobDispatcher(PdcNfcSchemeJobDao jobDao,
                                    PdcNfcSchemeJobWorker worker,
                                    ThreadPoolExecutor pdcNfcSchemeExecutor) {
        this.jobDao = jobDao;
        this.worker = worker;
        this.executor = pdcNfcSchemeExecutor;
        this.instanceId = UUID.randomUUID().toString();
    }

    @Scheduled(fixedDelay = 5000)
    public void dispatchRecoverableJobs() {
        List<PdcNfcSchemeJobEntity> jobs;
        try {
            jobs = jobDao.selectRecoverableJobs();
        } catch (Exception e) {
            log.warn("Failed to query recoverable scheme jobs", e);
            return;
        }
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        for (PdcNfcSchemeJobEntity job : jobs) {
            Date now = new Date();
            Date leaseUntil = new Date(now.getTime() + LEASE_SECONDS * 1_000L);

            int claimed = jobDao.claimLease(job.getId(), instanceId, now, leaseUntil);
            if (claimed != 1) {
                continue;
            }

            log.info("Claimed scheme job {}, instance={}", job.getId(), instanceId);
            try {
                executor.execute(() -> {
                    try {
                        worker.run(job.getId(), instanceId);
                    } catch (Exception e) {
                        log.error("Scheme job {} worker error", job.getId(), e);
                    }
                });
            } catch (RejectedExecutionException e) {
                log.warn("Scheme job {} rejected by executor, will retry next cycle", job.getId());
                // 释放租约，下次调度重新拾取
                jobDao.releaseLease(job.getId(), instanceId, new Date());
            }
        }
    }

    /**
     * 暴露实例 ID，便于测试。
     */
    String getInstanceId() {
        return instanceId;
    }
}
