package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_scheme_job")
public class PdcNfcSchemeJobEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String jobNo;

    private Long batchId;

    private String status;

    private Long requestedBy;

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private Long cursorAssetId;

    private String leaseOwner;

    private Date leaseUntil;

    private Date heartbeatAt;

    private Date nextRetryAt;

    private Date cancelledAt;

    private Date createDate;

    private Date updateDate;
}
