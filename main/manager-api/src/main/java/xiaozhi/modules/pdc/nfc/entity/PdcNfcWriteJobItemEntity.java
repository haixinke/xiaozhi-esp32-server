package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 写卡任务快照项实体。
 * <p>
 * 创建写卡任务时从资产表拷贝的不可变快照，确保导出 CSV 内容稳定，
 * 不受资产后续状态变更影响。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_write_job_item")
public class PdcNfcWriteJobItemEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属写卡任务 ID（对应 pdc_nfc_write_job.id） */
    private Long jobId;

    /** 对应资产 ID（对应 pdc_nfc_asset.id） */
    private Long assetId;

    /** CSV 行序号，从 1 递增 */
    private Integer sequenceNo;

    /** 资产编号（快照） */
    private String assetNo;

    /** 批次编号（快照） */
    private String batchNo;

    /** 微信序列号（快照） */
    private String wechatSn;

    /** SKU 编码（快照） */
    private String skuCode;

    /** 原型标识（快照） */
    private String prototype;

    /** URI 记录的 SHA-256 哈希（用于写卡后完整性校验） */
    private String uriSha256;

    /** URI NDEF 记录 TNF（Type Name Format） */
    private String uriTnf;

    /** URI NDEF 记录类型（如 "U"） */
    private String uriType;

    /** AAR NDEF 记录 TNF */
    private String aarTnf;

    /** AAR NDEF 记录类型（如 "android.com:pkg"） */
    private String aarType;

    /** AAR NDEF 记录载荷（Android 包名） */
    private String aarPayload;

    /** 创建时间 */
    private Date createDate;
}
