package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_claim_record")
public class PdcNfcClaimRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long assetId;

    private Long userId;

    private String requestId;

    private String requestFingerprint;

    private String petId;

    private String result;

    private Date createDate;
}
