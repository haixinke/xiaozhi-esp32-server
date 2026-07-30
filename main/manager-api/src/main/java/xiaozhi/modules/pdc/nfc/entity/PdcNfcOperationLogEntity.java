package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * NFC 操作日志实体。
 * <p>
 * 记录所有 NFC 管理操作（写卡导入、入库、激活、停用、报废等），用于审计追踪。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_operation_log")
public class PdcNfcOperationLogEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人用户 ID */
    private Long operatorUserId;

    /** 幂等请求 ID（关联到具体的幂等请求记录） */
    private String requestId;

    /** 日志来源（ADMIN_API / SYSTEM_TASK） */
    private String source;

    /** 对象类型（BATCH / ASSET / SCHEME_JOB / WRITE_JOB） */
    private String objectType;

    /** 对象 ID（对应具体实体的 ID） */
    private Long objectId;

    /** 操作类型（WRITE_RESULT_IMPORT / STOCK_IN / ACTIVATE / DISABLE / SCRAP 等） */
    private String operationType;

    /** 操作前状态 */
    private String beforeStatus;

    /** 操作后状态 */
    private String afterStatus;

    /** 操作数量（批量操作时受影响资源数） */
    private Integer quantity;

    /** 业务单号 */
    private String businessNo;

    /** 操作结果（SUCCESS / FAILED / PARTIAL） */
    private String result;

    /** 错误码（失败时记录） */
    private String errorCode;

    /** 详情 JSON（操作结果的详细信息，经 allowlist 过滤） */
    private String detailJson;

    /** 创建时间 */
    private Date createDate;
}
