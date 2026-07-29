package xiaozhi.modules.pdc.nfc.vo;

import java.util.UUID;

/**
 * 写卡结果导入响应 VO。
 */
public record PdcNfcWriteImportVO(
        Long jobId,
        String jobNo,
        int verifiedCount,
        int writtenCount,
        int failureCount,
        String resultFileSha256,
        UUID requestId
) {}
