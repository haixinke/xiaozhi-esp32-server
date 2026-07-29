package xiaozhi.modules.pdc.nfc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 NFC 模块的测试覆盖率和 Testcontainers 依赖已正确配置。
 */
class PdcNfcCoverageConfigurationTest {

    @Test
    void pomEnforcesPdcLineAndBranchCoverage() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<minimum>0.80</minimum>")
                .contains("xiaozhi.modules.pdc.nfc.*")
                .contains("spring-boot-testcontainers")
                .contains("<artifactId>mysql</artifactId>");
    }
}
