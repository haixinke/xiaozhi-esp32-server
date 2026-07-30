package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * NFC 领取记录实体。
 * <p>
 * 每次用户领取操作产生一条记录，用于审计和幂等性保护。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_claim_record")
public class PdcNfcClaimRecordEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 领取的资产 ID（对应 pdc_nfc_asset.id） */
    private Long assetId;

    /** 领取用户 ID（对应 sys_user.id） */
    private Long userId;

    /** 幂等请求 ID（客户端生成，防重复领取） */
    private String requestId;

    /** 请求指纹（userId + claimRef 的哈希，用于冲突检测） */
    private String requestFingerprint;

    /** 领取时绑定的宠物 ID */
    private String petId;

    /** 领取结果（CLAIMED / CLAIMED_BY_SELF / FAILED） */
    private String result;

    /** 创建时间 */
    private Date createDate;
}
