package xiaozhi.modules.pdc.nfc.dto;

import lombok.Data;

/**
 * 批次列表查询 DTO。
 * 所有字段均为可选，支持组合筛选。
 */
@Data
public class PdcNfcBatchQueryDTO {

    /** 批次编号（模糊匹配） */
    private String batchNo;

    /** 商品类型 ID */
    private Long productTypeId;

    /** 批次状态（CREATED / SCHEME_GENERATING / READY_FOR_WRITE / STOCKED / COMPLETED / CANCELLED） */
    private String status;

    /** 原型标识 */
    private String prototype;
}
