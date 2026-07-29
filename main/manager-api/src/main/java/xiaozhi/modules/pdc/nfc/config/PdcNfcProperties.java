package xiaozhi.modules.pdc.nfc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * PDC NFC 实物生产域配置。
 * 所有功能默认关闭（fail-closed），需显式开启。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pdc.nfc")
public class PdcNfcProperties {

    private boolean enabled = false;
    private String modelId;
    private boolean releaseReady = false;
    private boolean schemeGenerationEnabled = false;
    private boolean activationEnabled = false;
    private boolean claimEnabled = false;
    private int maxBatchQuantity = 10000;
    private ClaimRef claimRef = new ClaimRef();

    @Data
    public static class ClaimRef {
        private String activeVersion;
        private String activeHmacKeyBase64;
        private String activeAesKeyBase64;
        private String previousVersion;
        private String previousHmacKeyBase64;
        private String previousAesKeyBase64;
    }
}
