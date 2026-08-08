package xiaozhi.modules.sys.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 通用操作日志实体
 * <p>
 * 记录用户重要/危险操作。独立于 BaseEntity（不跟踪 creator/updater），
 * 操作人信息显式存储于 user_id/username。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人用户ID（系统/匿名操作可为空） */
    private Long userId;

    /** 操作人用户名（冗余，便于查询展示） */
    private String username;

    /** 操作类型，如 CHAT_HISTORY_EXPORT */
    private String operationType;

    /** 操作描述（人类可读） */
    private String operationDesc;

    /** 请求路径（注解方式自动填充） */
    private String requestUri;

    /** 请求方法 GET/POST（注解方式自动填充） */
    private String requestMethod;

    /** 操作IP */
    private String ip;

    /** 业务上下文JSON（不含敏感信息） */
    private String detail;

    /** 状态：1成功 0失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 操作时间 */
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
