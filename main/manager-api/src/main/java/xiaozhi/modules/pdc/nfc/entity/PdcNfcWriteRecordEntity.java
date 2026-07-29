package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_write_record")
public class PdcNfcWriteRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long jobId;

    private Long assetId;

    private Integer attemptNo;

    private String writeResult;

    private String verifyResult;

    private String tagUid;

    private Integer ndefRecordCount;

    private String uriSha256;

    private String aarPackage;

    private Boolean isReadOnly;

    private String errorCode;

    private String errorMessage;

    private Date writtenAt;

    private Date importedAt;

    private Long importUserId;
}
