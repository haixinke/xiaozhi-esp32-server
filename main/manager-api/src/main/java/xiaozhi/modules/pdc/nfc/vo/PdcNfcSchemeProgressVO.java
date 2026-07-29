package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * Scheme 任务进度视图：包含总数、成功数、失败数、游标和最近脱敏错误。
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
