package xiaozhi.modules.pdc.nfc.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xiaozhi.common.exception.ErrorCode.PDC_NFC_MODEL_ID_NOT_CONFIGURED;
import static xiaozhi.common.exception.ErrorCode.PDC_NFC_RELEASE_EVIDENCE_MISSING;
import static xiaozhi.common.exception.ErrorCode.PDC_NFC_RELEASE_NOT_READY;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PdcNfcReadinessService 就绪门测试")
class PdcNfcReadinessServiceTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    private PdcNfcProperties properties;
    private PdcNfcReadinessService readiness;
    private PdcNfcAuditService auditService;

    @BeforeEach
    void setUp() {
        properties = new PdcNfcProperties();
        auditService = mock(PdcNfcAuditService.class);
        when(auditService.hasCurrentReleaseEvidence()).thenReturn(true);
        readiness = new PdcNfcReadinessService(properties, auditService);
    }

    @Test
    @DisplayName("三个门控全开 - 通过")
    void allGatesOpenPasses() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(true);
        properties.setModelId("MODEL_001");

        assertThatCode(() -> readiness.requireSchemeGenerationReady())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("功能关闭 - 拒绝")
    void featureDisabledRejects() {
        properties.setEnabled(false);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(true);

        assertThatThrownBy(() -> readiness.requireSchemeGenerationReady())
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("Scheme 生成关闭 - 拒绝")
    void schemeGenerationDisabledRejects() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(false);
        properties.setReleaseReady(true);

        assertThatThrownBy(() -> readiness.requireSchemeGenerationReady())
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("发布未就绪 - 拒绝")
    void releaseNotReadyRejects() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(false);

        assertThatThrownBy(() -> readiness.requireSchemeGenerationReady())
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("全部门控关闭 - 拒绝")
    void allGatesClosedRejects() {
        properties.setEnabled(false);
        properties.setSchemeGenerationEnabled(false);
        properties.setReleaseReady(false);

        assertThatThrownBy(() -> readiness.requireSchemeGenerationReady())
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("仅发布就绪 - 仍拒绝（功能未开）")
    void onlyReleaseReadyStillRejects() {
        properties.setEnabled(false);
        properties.setSchemeGenerationEnabled(false);
        properties.setReleaseReady(true);

        assertThatThrownBy(() -> readiness.requireSchemeGenerationReady())
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("空白 model ID - 在创建 Scheme 任务前拒绝")
    void rejectsBlankModelIdBeforeSchemeJobCreation() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(true);
        properties.setModelId(" ");

        assertThatThrownBy(readiness::requireSchemeGenerationReady)
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(PDC_NFC_MODEL_ID_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("占位 model ID - 拒绝")
    void rejectsPlaceholderModelId() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(true);
        properties.setModelId("<WECHAT_MODEL_ID>");

        assertThatThrownBy(readiness::requireSchemeGenerationReady)
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(PDC_NFC_MODEL_ID_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("缺少当前发布证据 - 拒绝并报证据缺失专用错误码")
    void rejectsMissingCurrentReleaseEvidence() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(true);
        properties.setModelId("MODEL_001");
        when(auditService.hasCurrentReleaseEvidence()).thenReturn(false);

        assertThatThrownBy(readiness::requireSchemeGenerationReady)
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(PDC_NFC_RELEASE_EVIDENCE_MISSING);
    }

    @Test
    @DisplayName("发布就绪开关未开 - 错误码与证据缺失区分")
    void rejectsReleaseNotReadyWithDistinctCode() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(false);
        properties.setModelId("MODEL_001");

        // 开关未开时应报 10502，而非证据缺失的 10521；两者修复动作完全不同
        assertThatThrownBy(readiness::requireSchemeGenerationReady)
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(PDC_NFC_RELEASE_NOT_READY);
    }
}
