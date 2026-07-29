package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 写卡任务视图对象。
 */
public record PdcNfcWriteJobVO(
        Long id,
        String jobNo,
        Long batchId,
        String batchNo,
        String formatVersion,
        String status,
        int totalCount,
        int successCount,
        int failureCount,
        String fileSha256,
        int rowCount,
        Date exportedAt,
        Date createdAt
) {}
