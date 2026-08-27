package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 写卡任务实体。
 * <p>
 * 管理写卡 CSV 的导出和工厂回传结果的导入。
 * 生命周期：CREATED → EXPORTED → RESULT_IMPORTED → COMPLETED / CANCELLED。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_write_job")
public class PdcNfcWriteJobEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务编号，全局唯一 */
    private String jobNo;

    /** 关联批次 ID（对应 pdc_nfc_batch.id） */
    private Long batchId;

    /** CSV 格式版本号（如 "1.0"） */
    private String formatVersion;

    /** 写卡模式（FACTORY_CSV 工厂CSV模式 / MANUAL 手动模式），创建时选定不可变更 */
    private String mode;

    /** 任务状态（CREATED / EXPORTED / RESULT_IMPORTED / COMPLETED / CANCELLED） */
    private String status;

    /** 待处理资产总数 */
    private Integer totalCount;

    /** 写卡成功数 */
    private Integer successCount;

    /** 写卡失败数 */
    private Integer failureCount;

    /** 导出 CSV 文件的 SHA-256 哈希 */
    private String fileSha256;

    /** CSV 数据行数（不含表头） */
    private Integer rowCount;

    /** 导出操作人用户 ID */
    private Long exportUserId;

    /** 导出时间 */
    private Date exportedAt;

    /** 工厂回传结果文件的 SHA-256 哈希 */
    private String resultFileSha256;

    /** 导入幂等请求 ID（UUID 字符串） */
    private String importRequestId;

    /** 导入响应 JSON（完整的导入结果序列化） */
    private String resultResponseJson;

    /** 导入操作人用户 ID */
    private Long importUserId;

    /** 导入时间 */
    private Date importedAt;

    /** 任务完成时间 */
    private Date completedAt;

    /** 任务取消时间 */
    private Date cancelledAt;

    /** 创建人 ID */
    private Long creator;

    /** 创建时间 */
    private Date createDate;

    /** 更新人 ID */
    private Long updater;

    /** 更新时间 */
    private Date updateDate;
}
