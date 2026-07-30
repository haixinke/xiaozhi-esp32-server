package xiaozhi.modules.pdc.nfc.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcReleaseEvidenceDTO;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAuditService;
import xiaozhi.modules.pdc.nfc.vo.ReleaseEvidence;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcAuditServiceImpl implements PdcNfcAuditService {

    private final PdcNfcOperationLogDao operationLogDao;
    private final PdcNfcProperties properties;

    @Override
    public void registerReleaseEvidence(PdcNfcReleaseEvidenceDTO dto, Long operatorId) {
        PdcNfcOperationLogEntity entry = new PdcNfcOperationLogEntity();
        entry.setOperatorUserId(operatorId);
        entry.setObjectType("NFC_RELEASE");
        entry.setOperationType("RELEASE_EVIDENCE");
        entry.setSource("ADMIN_API");
        entry.setResult("SUCCESS");
        entry.setDetailJson(toJson(dto));
        entry.setCreateDate(new Date());
        operationLogDao.insert(entry);
        log.info("发布证据已登记 releaseVersion={}, operatorId={}", dto.getReleaseVersion(), operatorId);
    }

    @Override
    public boolean hasCurrentReleaseEvidence() {
        String releaseVersion = properties.getReleaseVersion();
        if (releaseVersion == null || releaseVersion.isBlank()) {
            return false;
        }

        PdcNfcOperationLogEntity entry = operationLogDao.selectLatestSuccessfulReleaseEvidence(releaseVersion);
        return entry != null && releaseVersion.equals(extractReleaseVersion(entry.getDetailJson()));
    }

    @Override
    public ReleaseEvidence latestCurrentReleaseEvidence() {
        String releaseVersion = properties.getReleaseVersion();
        if (releaseVersion == null || releaseVersion.isBlank()) {
            return null;
        }
        PdcNfcOperationLogEntity entry = operationLogDao.selectLatestSuccessfulReleaseEvidence(releaseVersion);
        if (entry == null) {
            return null;
        }
        return toReleaseEvidence(entry);
    }

    private String toJson(PdcNfcReleaseEvidenceDTO dto) {
        return JSONUtil.createObj()
                .set("releaseVersion", dto.getReleaseVersion())
                .set("publishedAt", dto.getPublishedAt())
                .set("smokeEvidence", dto.getSmokeEvidence())
                .toString();
    }

    private String extractReleaseVersion(String detailJson) {
        return extractField(detailJson, "releaseVersion");
    }

    private String extractPublishedAt(String detailJson) {
        return extractField(detailJson, "publishedAt");
    }

    private String extractSmokeEvidence(String detailJson) {
        return extractField(detailJson, "smokeEvidence");
    }

    private String extractField(String detailJson, String fieldName) {
        if (detailJson == null || !JSONUtil.isTypeJSON(detailJson)) {
            return null;
        }
        JSONObject detail = JSONUtil.parseObj(detailJson);
        return detail.getStr(fieldName);
    }

    private ReleaseEvidence toReleaseEvidence(PdcNfcOperationLogEntity entry) {
        return new ReleaseEvidence(
                entry.getId(),
                extractReleaseVersion(entry.getDetailJson()),
                extractPublishedAt(entry.getDetailJson()),
                extractSmokeEvidence(entry.getDetailJson()),
                entry.getOperatorUserId(),
                entry.getCreateDate()
        );
    }
}
