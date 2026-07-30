package xiaozhi.modules.pdc.nfc.vo;

import java.util.UUID;

/**
 * 批量操作结果 VO。
 *
 * @param processedCount 本次处理的资产总数
 * @param successCount   操作成功数
 * @param failureCount   操作失败数
 * @param businessNo     业务单号
 * @param requestId      幂等请求 ID
 */
public record PdcNfcBulkOperationVO(
        int processedCount,
        int successCount,
        int failureCount,
        String businessNo,
        UUID requestId
) {}
