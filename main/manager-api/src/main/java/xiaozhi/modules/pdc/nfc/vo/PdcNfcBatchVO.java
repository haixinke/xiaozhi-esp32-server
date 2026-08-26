package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 批次视图：包含批次基本信息和资产统计（不含明文 claimRef）。
 *
 * @param id              批次 ID
 * @param batchNo         批次编号
 * @param productTypeId   关联商品类型 ID
 * @param productTypeName 关联商品类型名称（关联 pdc_nfc_product_type.type_name，供列表展示，避免只显示 ID）
 * @param typeCode        关联商品类型编码（同上，type_code）
 * @param skuCode         SKU 编码
 * @param prototype       原型标识
 * @param plannedQuantity 计划生产数量
 * @param status          批次状态
 * @param remark          备注
 * @param assetCount      已分配资产数
 * @param schemeJobId     最新 Scheme 任务 ID（无任务时为 null）
 * @param writeJobId      最新写卡任务 ID（无任务时为 null）
 * @param writeJobStatus  最新写卡任务状态（无任务时为 null）
 * @param creator         创建人 ID
 * @param createDate      创建时间
 */
public record PdcNfcBatchVO(
        Long id,
        String batchNo,
        Long productTypeId,
        String productTypeName,
        String typeCode,
        String skuCode,
        String prototype,
        Integer plannedQuantity,
        String status,
        String remark,
        Integer assetCount,
        Long schemeJobId,
        Long writeJobId,
        String writeJobStatus,
        Long creator,
        Date createDate
) {}
