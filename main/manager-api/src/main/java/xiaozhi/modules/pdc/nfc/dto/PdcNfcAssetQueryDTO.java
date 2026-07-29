package xiaozhi.modules.pdc.nfc.dto;

import lombok.Data;

import java.util.Date;

/**
 * 资产分页查询 DTO。
 * 默认每页 20 条，最大 100 条。
 */
@Data
public class PdcNfcAssetQueryDTO {

    private Long productTypeId;

    private Long batchId;

    private String status;

    private String skuCode;

    private String prototype;

    private String assetNo;

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
