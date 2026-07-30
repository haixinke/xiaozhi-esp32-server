package xiaozhi.modules.pdc.nfc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcReleaseEvidenceDTO;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcAuditServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.ReleaseEvidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdcNfcAuditService 发布证据测试")
class PdcNfcAuditServiceTest {

    @Mock
    private PdcNfcOperationLogDao operationLogDao;

    private PdcNfcProperties properties;
    private PdcNfcAuditService auditService;

    @BeforeEach
    void setUp() {
        properties = new PdcNfcProperties();
        properties.setModelId("MODEL_001");
        properties.setReleaseVersion("1.2.3");
        auditService = new PdcNfcAuditServiceImpl(operationLogDao, properties);
    }

    @Test
    @DisplayName("登记发布证据 - 写入成功的管理接口审计记录")
    void registersSuccessfulAdminApiReleaseEvidence() {
        PdcNfcReleaseEvidenceDTO dto = new PdcNfcReleaseEvidenceDTO(
                "MODEL_001", "2026-07-30T10:00:00+08:00", "smoke-passed");

        auditService.registerReleaseEvidence(dto, 99L);

        ArgumentCaptor<PdcNfcOperationLogEntity> entryCaptor = ArgumentCaptor.forClass(PdcNfcOperationLogEntity.class);
        verify(operationLogDao).insert(entryCaptor.capture());
        PdcNfcOperationLogEntity entry = entryCaptor.getValue();
        assertThat(entry.getSource()).isEqualTo("ADMIN_API");
        assertThat(entry.getResult()).isEqualTo("SUCCESS");
        assertThat(entry.getCreateDate()).isNotNull();
        assertThat(entry.getDetailJson())
                .contains("\"releaseVersion\":\"MODEL_001\"")
                .contains("\"publishedAt\":\"2026-07-30T10:00:00+08:00\"")
                .contains("\"smokeEvidence\":\"smoke-passed\"");
    }

    @Test
    @DisplayName("当前版本证据之后登记其他版本 - 当前版本仍就绪")
    void remainsReadyWhenCurrentVersionEvidenceExistsAfterOtherVersionIsRegistered() {
        when(operationLogDao.selectLatestSuccessfulReleaseEvidence("1.2.3"))
                .thenReturn(releaseEvidence("SUCCESS", "1.2.3"));

        assertThat(auditService.hasCurrentReleaseEvidence()).isTrue();
        verify(operationLogDao).selectLatestSuccessfulReleaseEvidence("1.2.3");
    }

    @Test
    @DisplayName("旧版本或失败发布证据 - 不视为当前就绪")
    void rejectsStaleOrFailedReleaseEvidence() {
        when(operationLogDao.selectLatestSuccessfulReleaseEvidence("1.2.3"))
                .thenReturn(releaseEvidence("SUCCESS", "1.2.2"));

        assertThat(auditService.hasCurrentReleaseEvidence()).isFalse();
    }

    @Test
    @DisplayName("当前版本最新证据 - 作为全局证据返回")
    void returnsLatestCurrentReleaseEvidenceGlobally() {
        when(operationLogDao.selectLatestSuccessfulReleaseEvidence("1.2.3"))
                .thenReturn(releaseEvidence("SUCCESS", "1.2.3"));

        ReleaseEvidence evidence = auditService.latestCurrentReleaseEvidence();

        assertThat(evidence.releaseVersion()).isEqualTo("1.2.3");
        verify(operationLogDao).selectLatestSuccessfulReleaseEvidence("1.2.3");
    }

    private PdcNfcOperationLogEntity releaseEvidence(String result, String releaseVersion) {
        PdcNfcOperationLogEntity entry = new PdcNfcOperationLogEntity();
        entry.setSource("ADMIN_API");
        entry.setResult(result);
        entry.setDetailJson("{\"releaseVersion\":\"" + releaseVersion
                + "\",\"publishedAt\":\"2026-07-30T10:00:00+08:00\",\"smokeEvidence\":\"smoke-passed\"}");
        return entry;
    }
}
