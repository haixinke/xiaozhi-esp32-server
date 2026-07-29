package xiaozhi.modules.pdc.nfc.service;

import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;

/**
 * NFC Scheme 生成就绪门：检查功能开关、Scheme 生成开关、发布就绪证据。
 * <p>
 * 三个门控全部通过才允许发起 Scheme 任务，任一未通过立即抛出 RenException。
 */
@Component
public class PdcNfcReadinessService {

    private final PdcNfcProperties properties;

    public PdcNfcReadinessService(PdcNfcProperties properties) {
        this.properties = properties;
    }

    /**
     * 要求 Scheme 生成就绪：enabled、schemeGenerationEnabled、releaseReady 三者全部为 true。
     */
    public void requireSchemeGenerationReady() {
        if (!properties.isEnabled()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (!properties.isSchemeGenerationEnabled()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (!properties.isReleaseReady()) {
            throw new RenException(ErrorCode.PDC_NFC_RELEASE_NOT_READY);
        }
    }
}
