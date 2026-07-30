package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 写卡操作记录实体。
 * <p>
 * 记录工厂回传的每个资产写卡结果，包括写卡、校验、标签信息等。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_write_record")
public class PdcNfcWriteRecordEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属写卡任务 ID（对应 pdc_nfc_write_job.id） */
    private Long jobId;

    /** 对应资产 ID（对应 pdc_nfc_asset.id） */
    private Long assetId;

    /** 写卡尝试序号 */
    private Integer attemptNo;

    /** 写卡结果（SUCCESS / FAILED） */
    private String writeResult;

    /** 校验结果（PASS / FAIL / SKIPPED） */
    private String verifyResult;

    /** NFC 标签 UID（写卡后读取） */
    private String tagUid;

    /** NDEF 记录数 */
    private Integer ndefRecordCount;

    /** URI 记录的 SHA-256 哈希（完整性校验） */
    private String uriSha256;

    /** Android AAR 包名 */
    private String aarPackage;

    /** 标签是否已设为只读 */
    private Boolean isReadOnly;

    /** 错误码（失败时记录） */
    private String errorCode;

    /** 错误消息（失败时记录） */
    private String errorMessage;

    /** 工厂写卡完成时间 */
    private Date writtenAt;

    /** 结果导入时间 */
    private Date importedAt;

    /** 导入操作人用户 ID */
    private Long importUserId;
}
