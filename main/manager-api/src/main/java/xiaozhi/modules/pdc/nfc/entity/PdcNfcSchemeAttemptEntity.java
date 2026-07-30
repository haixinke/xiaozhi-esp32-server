package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Scheme 生成尝试记录实体。
 * <p>
 * 每个资产在 Scheme 任务中的每次生成尝试都记录一条，用于追踪失败原因和重试计数。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_scheme_attempt")
public class PdcNfcSchemeAttemptEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属 Scheme 任务 ID（对应 pdc_nfc_scheme_job.id） */
    private Long jobId;

    /** 目标资产 ID（对应 pdc_nfc_asset.id） */
    private Long assetId;

    /** 尝试序号，从 1 递增，用于重试计数 */
    private Integer attemptNo;

    /** 请求指纹（微信 API 请求的规范化哈希） */
    private String requestFingerprint;

    /** 操作动作（GENERATE / RETRY） */
    private String action;

    /** 微信 API 返回的错误码（0 表示成功） */
    private Integer wechatErrorCode;

    /** 错误消息（失败时记录，已脱敏） */
    private String errorMessage;

    /** 尝试开始时间 */
    private Date startedAt;

    /** 尝试完成时间 */
    private Date finishedAt;
}
