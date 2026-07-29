package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_asset")
public class PdcNfcAssetEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String assetNo;

    private Long batchId;

    private String itemNo;

    private String skuCode;

    private String prototype;

    private String wechatSn;

    private String claimRefHash;

    private String claimRefHashVersion;

    private String claimRefKeyVersion;

    private byte[] claimRefNonce;

    private byte[] claimRefCiphertext;

    private String schemeKeyVersion;

    private byte[] schemeNonce;

    private byte[] schemeCiphertext;

    private String schemeSha256;

    private String tagUid;

    private String status;

    @Version
    private Integer version;

    private Long activeSchemeJobId;

    private Long activeWriteJobId;

    private Date schemeGeneratedAt;

    private Date writtenAt;

    private Date verifiedAt;

    private Date stockedAt;

    private Date activatedAt;

    private Date claimedAt;

    private Date disabledAt;

    private Date scrappedAt;

    private Long claimedUserId;

    private String petId;

    private String stockBusinessNo;

    private String activationBusinessNo;

    private Long creator;

    private Date createDate;

    private Long updater;

    private Date updateDate;
}
