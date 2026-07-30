package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    @DisplayName("当前配置版本的最新成功发布证据 - 通过")
    void acceptsLatestSuccessfulEvidenceForCurrentConfiguredVersion() {
        when(operationLogDao.selectOne(org.mockito.ArgumentMatchers.<QueryWrapper<PdcNfcOperationLogEntity>>any()))
                .thenReturn(releaseEvidence("SUCCESS", "1.2.3"));

        assertThat(auditService.hasCurrentReleaseEvidence()).isTrue();
    }

    @Test
    @DisplayName("旧版本或失败发布证据 - 不视为当前就绪")
    void rejectsStaleOrFailedReleaseEvidence() {
        when(operationLogDao.selectOne(org.mockito.ArgumentMatchers.<QueryWrapper<PdcNfcOperationLogEntity>>any()))
                .thenReturn(releaseEvidence("SUCCESS", "1.2.2"));

        assertThat(auditService.hasCurrentReleaseEvidence()).isFalse();
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
