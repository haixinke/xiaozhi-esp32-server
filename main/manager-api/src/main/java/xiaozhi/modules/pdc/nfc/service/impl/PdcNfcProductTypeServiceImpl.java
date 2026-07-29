package xiaozhi.modules.pdc.nfc.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAuditService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcProductTypeService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcProductTypeVO;
import xiaozhi.modules.pdc.nfc.vo.ReleaseEvidence;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdcNfcProductTypeServiceImpl implements PdcNfcProductTypeService {

    private final PdcNfcProductTypeDao productTypeDao;
    private final PdcNfcAuditService auditService;
    private final PdcNfcProperties properties;

    @Override
    public List<PdcNfcProductTypeVO> list() {
        List<PdcNfcProductTypeEntity> entities = productTypeDao.selectList(null);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    public PdcNfcProductTypeVO toVO(PdcNfcProductTypeEntity entity) {
        String modelId = properties.getModelId();
        ReleaseEvidence evidence = auditService.latestReleaseEvidence(entity.getId());
        return new PdcNfcProductTypeVO(
                entity.getId(), entity.getTypeCode(), entity.getTypeName(),
                entity.getClaimPagePath(), entity.getCapabilityMode(), entity.getStatus(),
                StringUtils.isBlank(modelId) ? null : modelId,
                StringUtils.isBlank(modelId) ? "待微信审核配置" : "已配置",
                properties.isReleaseReady(), evidence);
    }
}
