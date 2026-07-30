package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * NFC 资产核心实体。
 * <p>
 * 记录每个 NFC 标签的完整生命周期：创建 → Scheme 生成 → 写卡 → 校验 → 入库 → 激活 → 领取 → 停用 / 报废。
 * 敏感字段（claimRef、scheme）均加密存储，明文不落库。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_asset")
public class PdcNfcAssetEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 资产编号，全局唯一，格式如 NFC-YYYYMMDD-NNNNN */
    private String assetNo;

    /** 所属批次 ID（对应 pdc_nfc_batch.id） */
    private Long batchId;

    /** 批次内序号，从 1 递增 */
    private String itemNo;

    /** SKU 编码，标识产品型号 */
    private String skuCode;

    /** 原型标识，区分同一 SKU 下的不同硬件版本 */
    private String prototype;

    /** 微信序列号，由微信 NFC 服务分配 */
    private String wechatSn;

    /** claimRef 的 HMAC-SHA-256 查找哈希（用于通过 claimRef 快速定位资产） */
    private String claimRefHash;

    /** claimRef 查找哈希的版本号（对应 HMAC 密钥版本） */
    private String claimRefHashVersion;

    /** claimRef 加密密钥版本号 */
    private String claimRefKeyVersion;

    /** claimRef AES-256-GCM 加密随机 Nonce（12 字节） */
    private byte[] claimRefNonce;

    /** claimRef AES-256-GCM 加密密文（含认证标签） */
    private byte[] claimRefCiphertext;

    /** scheme 加密密钥版本号 */
    private String schemeKeyVersion;

    /** scheme AES-256-GCM 加密随机 Nonce（12 字节） */
    private byte[] schemeNonce;

    /** scheme AES-256-GCM 加密密文（微信小程序 Scheme 明文加密存储） */
    private byte[] schemeCiphertext;

    /** scheme 明文的 SHA-256 哈希（用于写卡前完整性校验） */
    private String schemeSha256;

    /** NFC 标签 UID（写卡后读取确认） */
    private String tagUid;

    /** 资产状态（CREATED / SCHEME_GENERATED / WRITTEN / VERIFIED / IN_STOCK / ACTIVE / CLAIMED / DISABLED / SCRAPPED） */
    private String status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 当前关联的 Scheme 生成任务 ID（对应 pdc_nfc_scheme_job.id） */
    private Long activeSchemeJobId;

    /** 当前关联的写卡任务 ID（对应 pdc_nfc_write_job.id） */
    private Long activeWriteJobId;

    /** Scheme 生成完成时间 */
    private Date schemeGeneratedAt;

    /** 写卡完成时间 */
    private Date writtenAt;

    /** 校验通过时间 */
    private Date verifiedAt;

    /** 入库时间 */
    private Date stockedAt;

    /** 激活时间 */
    private Date activatedAt;

    /** 用户领取时间 */
    private Date claimedAt;

    /** 停用时间 */
    private Date disabledAt;

    /** 报废时间 */
    private Date scrappedAt;

    /** 领取用户 ID（对应 sys_user.id） */
    private Long claimedUserId;

    /** 关联宠物 ID（领取时绑定） */
    private String petId;

    /** 入库业务单号（用于幂等和审计追踪） */
    private String stockBusinessNo;

    /** 激活业务单号（用于幂等和审计追踪） */
    private String activationBusinessNo;

    /** 创建人 ID */
    private Long creator;

    /** 创建时间 */
    private Date createDate;

    /** 更新人 ID */
    private Long updater;

    /** 更新时间 */
    private Date updateDate;
}
