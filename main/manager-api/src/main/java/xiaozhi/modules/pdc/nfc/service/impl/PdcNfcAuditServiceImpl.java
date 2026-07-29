package xiaozhi.modules.pdc.nfc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    @Override
    public void registerReleaseEvidence(PdcNfcReleaseEvidenceDTO dto, Long operatorId) {
        PdcNfcOperationLogEntity entry = new PdcNfcOperationLogEntity();
        entry.setOperatorUserId(operatorId);
        entry.setObjectType("PRODUCT_TYPE");
        entry.setObjectId(dto.getProductTypeId());
        entry.setOperationType("RELEASE_EVIDENCE");
        entry.setDetailJson(toJson(dto.getEvidenceType(), dto.getEvidenceContent()));
        entry.setCreateDate(new Date());
        operationLogDao.insert(entry);
        log.info("发布证据已登记 productTypeId={}, operatorId={}", dto.getProductTypeId(), operatorId);
    }

    @Override
    public ReleaseEvidence latestReleaseEvidence(Long productTypeId) {
        QueryWrapper<PdcNfcOperationLogEntity> qw = new QueryWrapper<>();
        qw.eq("object_type", "PRODUCT_TYPE")
                .eq("object_id", productTypeId)
                .eq("operation_type", "RELEASE_EVIDENCE")
                .orderByDesc("create_date")
                .last("LIMIT 1");
        PdcNfcOperationLogEntity entry = operationLogDao.selectOne(qw);
        if (entry == null) {
            return null;
        }
        return new ReleaseEvidence(
                entry.getId(),
                extractEvidenceType(entry.getDetailJson()),
                entry.getDetailJson(),
                entry.getOperatorUserId(),
                entry.getCreateDate()
        );
    }

    private String toJson(String evidenceType, String evidenceContent) {
        return "{\"evidenceType\":\"" + evidenceType + "\",\"evidenceContent\":\"" + evidenceContent + "\"}";
    }

    private String extractEvidenceType(String detailJson) {
        if (detailJson == null) return null;
        int start = detailJson.indexOf("\"evidenceType\":\"");
        if (start < 0) return null;
        start += 16;
        int end = detailJson.indexOf("\"", start);
        return end > start ? detailJson.substring(start, end) : null;
    }
}
