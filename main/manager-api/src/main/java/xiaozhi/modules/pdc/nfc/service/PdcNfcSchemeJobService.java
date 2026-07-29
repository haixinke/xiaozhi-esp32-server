package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.vo.PdcNfcSchemeProgressVO;

/**
 * NFC Scheme 任务服务：发起、重试、查询进度、取消。
 * <p>
 * HTTP 线程仅创建/查询 job 记录后立即返回，实际 Scheme 生成由
 * {@link xiaozhi.modules.pdc.nfc.task.PdcNfcSchemeJobDispatcher} 调度、
 * {@link xiaozhi.modules.pdc.nfc.task.PdcNfcSchemeJobWorker} 异步执行。
 */
public interface PdcNfcSchemeJobService {

    /**
     * 发起 Scheme 生成任务。批次从 DRAFT 转为 SCHEME_GENERATING，创建 PENDING job。
     *
     * @return job ID
     */
    Long start(Long batchId, Long operatorId);

    /**
     * 重试已失败/部分成功的任务。从上次游标继续，创建新的 PENDING job。
     *
     * @return 新 job ID
     */
    Long retry(Long batchId, Long operatorId);

    /**
     * 查询批次最新 Scheme 任务进度。
     */
    PdcNfcSchemeProgressVO progress(Long batchId);

    /**
     * 取消任务：仅 PENDING/RUNNING 可取消，标记 CANCELLED 并释放资产绑定。
     */
    void cancel(Long jobId, Long operatorId);
}
