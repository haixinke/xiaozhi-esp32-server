package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 批次视图：包含批次基本信息和资产统计（不含明文 claimRef）。
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
