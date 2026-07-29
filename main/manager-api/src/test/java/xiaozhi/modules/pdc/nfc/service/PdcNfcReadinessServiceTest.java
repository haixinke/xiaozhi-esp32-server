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

    @BeforeEach
    void setUp() {
        properties = new PdcNfcProperties();
        readiness = new PdcNfcReadinessService(properties);
    }

    @Test
    @DisplayName("三个门控全开 - 通过")
    void allGatesOpenPasses() {
        properties.setEnabled(true);
        properties.setSchemeGenerationEnabled(true);
        properties.setReleaseReady(true);

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
}
