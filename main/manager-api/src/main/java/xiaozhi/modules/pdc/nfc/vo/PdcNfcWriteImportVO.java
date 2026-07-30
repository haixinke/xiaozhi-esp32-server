package xiaozhi.modules.pdc.nfc.vo;

import java.util.UUID;

/**
 * 写卡结果导入响应 VO。
 *
 * @param jobId            写卡任务 ID
 * @param jobNo            写卡任务编号
 * @param verifiedCount    校验通过数
 * @param writtenCount     写卡成功数
 * @param failureCount     写卡失败数
 * @param resultFileSha256 导入文件的 SHA-256 哈希
 * @param requestId        幂等请求 ID
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
