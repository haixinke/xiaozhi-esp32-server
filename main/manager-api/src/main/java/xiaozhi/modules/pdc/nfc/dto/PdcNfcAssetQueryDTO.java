package xiaozhi.modules.pdc.nfc.dto;

import lombok.Data;

import java.util.Date;

/**
 * 资产分页查询 DTO。
 * 默认每页 20 条，最大 100 条。
 */
@Data
public class PdcNfcAssetQueryDTO {

    /** 商品类型 ID */
    private Long productTypeId;

    /** 批次 ID */
    private Long batchId;

    /** 资产状态（CREATED / SCHEME_GENERATED / WRITTEN / VERIFIED / IN_STOCK / ACTIVE / CLAIMED / DISABLED / SCRAPPED） */
    private String status;

    /** SKU 编码 */
    private String skuCode;

    /** 原型标识 */
    private String prototype;

    /** 资产编号 */
    private String assetNo;

    /** 微信序列号 */
    private String wechatSn;

    /** 查询起始日期（createDate >= startDate） */
    private Date startDate;

    /** 查询截止日期（createDate <= endDate） */
    private Date endDate;

    /** 页码，默认 1 */
    private Integer page = 1;

    /** 每页条数，默认 20，最大 100 */
    private Integer limit = 20;
}
