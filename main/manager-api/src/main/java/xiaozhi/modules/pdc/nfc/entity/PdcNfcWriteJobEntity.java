package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_write_job")
public class PdcNfcWriteJobEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String jobNo;

    private Long batchId;

    private String formatVersion;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private String fileSha256;

    private Integer rowCount;

    private Long exportUserId;

    private Date exportedAt;

    private String resultFileSha256;

    private String importRequestId;

    private String resultResponseJson;

    private Long importUserId;

    private Date importedAt;

    private Date completedAt;

    private Date cancelledAt;

    private Long creator;

    private Date createDate;

    private Long updater;

    private Date updateDate;
}
