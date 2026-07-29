package xiaozhi.modules.pdc.nfc.vo;

/**
 * 商品类型视图：只读组合视图，包含微信配置状态和发布证据。
 * 不包含 AppSecret/token 等敏感字段。
 */
public record PdcNfcProductTypeVO(
        Long id,
        String typeCode,
        String typeName,
        String claimPagePath,
        String capabilityMode,
        String status,
        String modelId,
        String modelStatus,
        boolean releaseReady,
        ReleaseEvidence latestEvidence
) {}
