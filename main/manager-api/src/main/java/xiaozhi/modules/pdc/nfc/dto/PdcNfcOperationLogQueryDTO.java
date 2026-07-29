package xiaozhi.modules.pdc.nfc.dto;

import lombok.Data;

import java.util.Date;

/**
 * 操作日志分页查询 DTO。
 */
@Data
public class PdcNfcOperationLogQueryDTO {

    private String objectType;

    private Long objectId;

    private String operationType;

    /** 查询起始日期（operateTime >= startDate） */
    private Date startDate;

    /** 查询截止日期（operateTime <= endDate） */
    private Date endDate;

    /** 页码，默认 1 */
    private Integer page = 1;

    /** 每页条数，默认 20，最大 100 */
    private Integer limit = 20;
}
