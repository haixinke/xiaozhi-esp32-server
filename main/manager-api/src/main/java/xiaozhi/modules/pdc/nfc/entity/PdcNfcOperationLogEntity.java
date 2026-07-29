package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_operation_log")
public class PdcNfcOperationLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long operatorUserId;

    private String requestId;

    private String source;

    private String objectType;

    private Long objectId;

    private String operationType;

    private String beforeStatus;

    private String afterStatus;

    private Integer quantity;

    private String businessNo;

    private String result;

    private String errorCode;

    private String detailJson;

    private Date createDate;
}
