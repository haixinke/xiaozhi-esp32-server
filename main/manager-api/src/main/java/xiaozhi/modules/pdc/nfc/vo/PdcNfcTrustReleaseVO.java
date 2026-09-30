package xiaozhi.modules.pdc.nfc.vo;

import java.util.UUID;

/**
 * 免检放行响应 VO（ADR 0005）。
 *
 * @param jobId         写卡任务 ID
 * @param jobNo         写卡任务编号
 * @param releasedCount 本次放行为 VERIFIED 的资产数
 * @param requestId     幂等请求 ID
 */
public record PdcNfcTrustReleaseVO(
        Long jobId,
        String jobNo,
        int releasedCount,
        UUID requestId
) {}
