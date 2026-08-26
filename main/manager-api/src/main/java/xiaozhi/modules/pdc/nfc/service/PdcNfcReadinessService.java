package xiaozhi.modules.pdc.nfc.service;

import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;

/**
 * NFC Scheme 生成就绪门：检查功能开关、Scheme 生成开关、模型 ID 和发布就绪证据。
 * <p>
 * 三个门控全部通过才允许发起 Scheme 任务，任一未通过立即抛出 RenException。
 */
@Component
public class PdcNfcReadinessService {

    private final PdcNfcProperties properties;
    private final PdcNfcAuditService auditService;

    public PdcNfcReadinessService(PdcNfcProperties properties, PdcNfcAuditService auditService) {
        this.properties = properties;
        this.auditService = auditService;
    }

    /**
     * 要求 Scheme 生成就绪：功能开关、模型 ID 与当前发布证据均已就绪。
     * <p>
     * 发布就绪拆为两道独立检查，分别使用不同错误码，便于运维定位：
     * release-ready 开关（环境变量）与发布证据（后台登记记录）。
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
        String modelId = properties.getModelId();
        if (modelId == null || modelId.isBlank()
                || (modelId.trim().startsWith("<") && modelId.trim().endsWith(">"))) {
            throw new RenException(ErrorCode.PDC_NFC_MODEL_ID_NOT_CONFIGURED);
        }
        if (!auditService.hasCurrentReleaseEvidence()) {
            // 开关已开但没有版本匹配的登记记录，错误码与开关未开区分开，避免运维无法判断该做哪一步
            throw new RenException(ErrorCode.PDC_NFC_RELEASE_EVIDENCE_MISSING);
        }
    }
}
