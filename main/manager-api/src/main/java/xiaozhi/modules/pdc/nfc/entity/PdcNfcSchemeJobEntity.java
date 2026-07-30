package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Scheme 生成任务实体。
 * <p>
 * 异步任务，为批次内所有资产调用微信 API 生成 Scheme。
 * 支持游标分批处理、租约重试和断点续传。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_scheme_job")
public class PdcNfcSchemeJobEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务编号，全局唯一 */
    private String jobNo;

    /** 关联批次 ID（对应 pdc_nfc_batch.id） */
    private Long batchId;

    /** 任务状态（CREATED / RUNNING / PAUSED / COMPLETED / FAILED / CANCELLED） */
    private String status;

    /** 请求人用户 ID */
    private Long requestedBy;

    /** 待处理资产总数 */
    private Integer totalCount;

    /** 成功处理数 */
    private Integer successCount;

    /** 失败处理数 */
    private Integer failureCount;

    /** 游标资产 ID（用于断点续传，下次处理从此 ID 开始） */
    private Long cursorAssetId;

    /** 租约持有者标识（多实例部署时用于任务抢占） */
    private String leaseOwner;

    /** 租约到期时间（过期后其他实例可抢占） */
    private Date leaseUntil;

    /** 最近心跳时间 */
    private Date heartbeatAt;

    /** 下次重试时间（失败后延迟重试） */
    private Date nextRetryAt;

    /** 取消时间 */
    private Date cancelledAt;

    /** 创建时间 */
    private Date createDate;

    /** 更新时间 */
    private Date updateDate;
}
