package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_write_job_item")
public class PdcNfcWriteJobItemEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long jobId;

    private Long assetId;

    private Integer sequenceNo;

    private String assetNo;

    private String batchNo;

    private String wechatSn;

    private String skuCode;

    private String prototype;

    private String schemeSha256;

    private String uriSha256;

    private String uriTnf;

    private String uriType;

    private String uriPayload;

    private String aarTnf;

    private String aarType;

    private String aarPayload;

    private Date createDate;
}
