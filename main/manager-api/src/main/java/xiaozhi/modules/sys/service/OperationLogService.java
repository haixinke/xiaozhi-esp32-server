package xiaozhi.modules.sys.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.sys.dto.SysOperationLogDTO;
import xiaozhi.modules.sys.entity.SysOperationLogEntity;
import xiaozhi.modules.sys.enums.OperationType;

/**
 * 通用操作日志服务
 */
public interface OperationLogService {

    /**
     * 记录一条操作日志（异步落库，失败不影响主流程）
     *
     * @param type    操作类型
     * @param success 是否成功
     * @param detail  业务上下文（JSON 字符串，不含敏感信息，可为 null）
     * @param errorMsg 失败原因（成功时传 null）
     */
    void record(OperationType type, boolean success, String detail, String errorMsg);

    /**
     * 落库（供 AOP 与内部调用），调用方需先组装好实体
     */
    void save(SysOperationLogEntity entity);

    /**
     * 分页查询（支持按 userId / operationType / 时间范围过滤）
     */
    PageData<SysOperationLogDTO> page(Map<String, Object> params);
}
