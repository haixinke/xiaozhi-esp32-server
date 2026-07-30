package xiaozhi.modules.pdc.nfc.vo;

/**
 * 商品类型视图：只读组合视图，包含微信配置状态和发布证据。
 * 不包含 AppSecret/token 等敏感字段。
 *
 * @param id              商品类型 ID
 * @param typeCode        类型编码
 * @param typeName        类型名称
 * @param claimPagePath   微信小程序领取页路径
 * @param capabilityMode  能力模式
 * @param status          状态（ACTIVE / INACTIVE）
 * @param modelId         关联的模型 ID
 * @param modelStatus     模型状态
 * @param releaseReady    是否发布就绪
 * @param latestEvidence  最新发布证据
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
