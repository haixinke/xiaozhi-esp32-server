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
     * 当前配置模型版本是否具有最新的成功发布证据。
     */
    boolean hasCurrentReleaseEvidence();

    /**
     * 查询当前配置发布版本的最新成功证据，无记录返回 null。
     */
    ReleaseEvidence latestCurrentReleaseEvidence();
}
