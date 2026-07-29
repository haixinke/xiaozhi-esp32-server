package xiaozhi.modules.pdc.nfc;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import xiaozhi.modules.pdc.nfc.service.PdcNfcMetrics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFC 后端验收测试。
 * <p>
 * 验证：
 * 1. 所有错误码 10500-10520 在 messages.properties 和 messages_zh_CN.properties 中都有非空消息
 * 2. 错误消息不包含内部实现细节（SQL、Exception、stack、internal）
 * 3. PdcNfcMetrics Bean 功能正确（模拟 MeterRegistry，调用所有方法，验证计数器创建）
 */
@DisplayName("NFC 后端验收测试")
class PdcNfcBackendAcceptanceTest {

    private static final int FIRST_CODE = 10500;
    private static final int LAST_CODE = 10520;

    private static final Properties EN_PROPS = new Properties();
    private static final Properties ZH_PROPS = new Properties();

    /** 不得出现在用户可见错误消息中的内部关键字 */
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "SQL", "Exception", "stack", "internal"
    );

    @BeforeAll
    static void loadProperties() throws IOException {
        Path enPath = Paths.get("src/main/resources/i18n/messages.properties");
        Path zhPath = Paths.get("src/main/resources/i18n/messages_zh_CN.properties");

        assertThat(Files.exists(enPath)).as("messages.properties 必须存在").isTrue();
        assertThat(Files.exists(zhPath)).as("messages_zh_CN.properties 必须存在").isTrue();

        try (InputStream en = Files.newInputStream(enPath);
             InputStream zh = Files.newInputStream(zhPath)) {
            EN_PROPS.load(en);
            ZH_PROPS.load(zh);
        }
    }

    @Nested
    @DisplayName("错误码消息完整性")
    class ErrorCodeMessages {

        @Test
        @DisplayName("所有 10500-10520 错误码在英文 properties 中都有非空消息")
        void allCodesHaveEnglishMessages() {
            IntStream.rangeClosed(FIRST_CODE, LAST_CODE).forEach(code -> {
                String key = String.valueOf(code);
                String value = EN_PROPS.getProperty(key);
                assertThat(value)
                        .as("错误码 %s 在 messages.properties 中必须有消息", code)
                        .isNotNull()
                        .isNotBlank();
            });
        }

        @Test
        @DisplayName("所有 10500-10520 错误码在中文 properties 中都有非空消息")
        void allCodesHaveChineseMessages() {
            IntStream.rangeClosed(FIRST_CODE, LAST_CODE).forEach(code -> {
                String key = String.valueOf(code);
                String value = ZH_PROPS.getProperty(key);
                assertThat(value)
                        .as("错误码 %s 在 messages_zh_CN.properties 中必须有消息", code)
                        .isNotNull()
                        .isNotBlank();
            });
        }

        @Test
        @DisplayName("错误消息不包含内部实现细节（英文）")
        void englishMessagesDoNotLeakInternals() {
            IntStream.rangeClosed(FIRST_CODE, LAST_CODE).forEach(code -> {
                String value = EN_PROPS.getProperty(String.valueOf(code));
                if (value != null) {
                    for (String keyword : FORBIDDEN_KEYWORDS) {
                        assertThat(value)
                                .as("错误码 %s 英文消息不得包含 '%s'", code, keyword)
                                .doesNotContainIgnoringCase(keyword);
                    }
                }
            });
        }

        @Test
        @DisplayName("错误消息不包含内部实现细节（中文）")
        void chineseMessagesDoNotLeakInternals() {
            IntStream.rangeClosed(FIRST_CODE, LAST_CODE).forEach(code -> {
                String value = ZH_PROPS.getProperty(String.valueOf(code));
                if (value != null) {
                    for (String keyword : FORBIDDEN_KEYWORDS) {
                        assertThat(value)
                                .as("错误码 %s 中文消息不得包含 '%s'", code, keyword)
                                .doesNotContainIgnoringCase(keyword);
                    }
                }
            });
        }
    }

    @Nested
    @DisplayName("PdcNfcMetrics 指标组件")
    class MetricsComponent {

        @Test
        @DisplayName("PdcNfcMetrics 所有方法均正确创建计数器")
        void allMetricsMethodsCreateCounters() {
            MeterRegistry registry = new SimpleMeterRegistry();
            PdcNfcMetrics metrics = new PdcNfcMetrics(registry);

            // Scheme
            metrics.schemeRequest();
            metrics.schemeSuccess();
            metrics.schemeFailure("10504");
            metrics.schemeDeferred();

            // Write
            metrics.writeImport();
            metrics.writeVerifyFailure();
            metrics.writeScrapped();

            // Inventory
            metrics.inventoryStocked();
            metrics.inventoryActivated();
            metrics.inventoryDisabled();

            // Claim
            metrics.claimPreview();
            metrics.claimSuccess();
            metrics.claimConflict();
            metrics.claimInvalidRef();
            metrics.claimMultiUserAlert();

            // Permission
            metrics.permissionDenied();

            // 验证计数器已注册且计数为 1
            assertThat(registry.find("pdc.nfc.scheme.requests").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.scheme.successes").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.scheme.failures").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.scheme.deferred").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));

            assertThat(registry.find("pdc.nfc.write.imports").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.write.verify_failures").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.write.scrapped").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));

            assertThat(registry.find("pdc.nfc.inventory.stocked").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.inventory.activated").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.inventory.disabled").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));

            assertThat(registry.find("pdc.nfc.claim.preview").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.claim.success").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.claim.conflict").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.claim.invalid_ref").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
            assertThat(registry.find("pdc.nfc.claim.multi_user_alert").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));

            assertThat(registry.find("pdc.nfc.permission.denied").counter())
                    .isNotNull()
                    .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
        }
    }
}
