package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.dto.PdcNfcReleaseEvidenceDTO;
import xiaozhi.modules.pdc.nfc.vo.ReleaseEvidence;

/**
 * 审计服务：append-only 操作日志，支持发布证据登记和查询。
 */
public interface PdcNfcAuditService {

    /**
     * 登记发布证据（append-only，不修改已有记录）。
     */
    void registerReleaseEvidence(PdcNfcReleaseEvidenceDTO dto, Long operatorId);

    /**
     * 查询商品类型的最新发布证据，无记录返回 null。
     */
    ReleaseEvidence latestReleaseEvidence(Long productTypeId);
}
