package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * Scheme 任务进度视图：包含总数、成功数、失败数、游标和最近脱敏错误。
 *
 * @param jobId         任务 ID
 * @param jobNo         任务编号
 * @param batchId       关联批次 ID
 * @param status        任务状态
 * @param totalCount    待处理总数
 * @param successCount  成功数
 * @param failureCount  失败数
 * @param cursorAssetId 当前游标资产 ID（断点续传位置）
 * @param nextRetryAt   下次重试时间
 * @param lastError     最近一次错误信息（已脱敏）
 */
public record PdcNfcSchemeProgressVO(
        Long jobId,
        String jobNo,
        Long batchId,
        String status,
        Integer totalCount,
        Integer successCount,
        Integer failureCount,
        Long cursorAssetId,
        Date nextRetryAt,
        String lastError
) {}
