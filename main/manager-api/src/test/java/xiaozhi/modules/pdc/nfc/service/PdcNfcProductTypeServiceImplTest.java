package xiaozhi.modules.pdc.nfc.service;

import org.junit.jupiter.api.Test;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcProductTypeServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcProductTypeVO;
import xiaozhi.modules.pdc.nfc.vo.ReleaseEvidence;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdcNfcProductTypeServiceImplTest {

    @Test
    void exposesGlobalCurrentReleaseEvidenceForEveryProductType() {
        PdcNfcProductTypeDao productTypeDao = mock(PdcNfcProductTypeDao.class);
        PdcNfcAuditService auditService = mock(PdcNfcAuditService.class);
        PdcNfcProperties properties = new PdcNfcProperties();
        properties.setModelId("MODEL_001");
        ReleaseEvidence evidence = new ReleaseEvidence(
                1L, "1.2.3", "2026-07-30T10:00:00+08:00", "smoke-passed", 99L, new Date());
        when(auditService.latestCurrentReleaseEvidence()).thenReturn(evidence);

        PdcNfcProductTypeServiceImpl service = new PdcNfcProductTypeServiceImpl(
                productTypeDao, auditService, properties);
        PdcNfcProductTypeEntity productType = new PdcNfcProductTypeEntity();
        productType.setId(100L);
        productType.setTypeCode("PET_COLLAR");
        productType.setTypeName("Pet Collar");

        PdcNfcProductTypeVO view = service.toVO(productType);

        assertThat(view.latestEvidence()).isSameAs(evidence);
        verify(auditService).latestCurrentReleaseEvidence();
    }
}
