package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 批次视图：包含批次基本信息和资产统计（不含明文 claimRef）。
 *
 * @param id              批次 ID
 * @param batchNo         批次编号
 * @param productTypeId   关联商品类型 ID
 * @param skuCode         SKU 编码
 * @param prototype       原型标识
 * @param plannedQuantity 计划生产数量
 * @param status          批次状态
 * @param remark          备注
 * @param assetCount      已分配资产数
 * @param creator         创建人 ID
 * @param createDate      创建时间
 */
public record PdcNfcBatchVO(
        Long id,
        String batchNo,
        Long productTypeId,
        String skuCode,
        String prototype,
        Integer plannedQuantity,
        String status,
        String remark,
        Integer assetCount,
        Long creator,
        Date createDate
) {}
