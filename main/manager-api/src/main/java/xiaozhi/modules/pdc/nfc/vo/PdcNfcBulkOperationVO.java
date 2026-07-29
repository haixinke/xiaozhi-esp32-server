package xiaozhi.modules.pdc.nfc.vo;

import java.util.UUID;

/**
 * 批量操作结果 VO。
 */
public record PdcNfcBulkOperationVO(
        int processedCount,
        int successCount,
        int failureCount,
        String businessNo,
        UUID requestId
) {}
