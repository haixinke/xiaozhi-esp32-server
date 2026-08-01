package xiaozhi.modules.sys.service.impl;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.common.utils.HttpContextUtils;
import xiaozhi.common.utils.IpUtils;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.sys.dao.SysOperationLogDao;
import xiaozhi.modules.sys.dto.SysOperationLogDTO;
import xiaozhi.modules.sys.entity.SysOperationLogEntity;
import xiaozhi.modules.sys.enums.OperationType;
import xiaozhi.modules.sys.service.OperationLogService;

/**
 * 通用操作日志服务实现
 * <p>
 * 异步落库，写库失败只记日志、绝不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends BaseServiceImpl<SysOperationLogDao, SysOperationLogEntity>
        implements OperationLogService {

    private final SysOperationLogDao operationLogDao;

    @Override
    public void record(OperationType type, boolean success, String detail, String errorMsg) {
        SysOperationLogEntity entity = new SysOperationLogEntity();
        entity.setOperationType(type.name());
        entity.setOperationDesc(type.getDesc());
        entity.setStatus(success ? 1 : 0);
        entity.setDetail(detail);
        entity.setErrorMsg(success ? null : StringUtils.truncate(errorMsg, 500));
        fillOperator(entity);
        save(entity);
    }

    @Override
    @Async("taskExecutor")
    public void save(SysOperationLogEntity entity) {
        try {
            operationLogDao.insert(entity);
        } catch (Exception e) {
            // 审计日志写库失败不能影响主流程，仅记录错误
            log.error("操作日志落库失败，type={}, userId={}", entity.getOperationType(), entity.getUserId(), e);
        }
    }

    @Override
    public PageData<SysOperationLogDTO> page(Map<String, Object> params) {
        IPage<SysOperationLogEntity> page = operationLogDao.selectPage(
                getPage(params, "create_date", false),
                buildQueryWrapper(params));
        return getPageData(page, SysOperationLogDTO.class);
    }

    private QueryWrapper<SysOperationLogEntity> buildQueryWrapper(Map<String, Object> params) {
        QueryWrapper<SysOperationLogEntity> wrapper = new QueryWrapper<>();
        Object userId = params.get("userId");
        if (userId != null && StringUtils.isNotBlank(userId.toString())) {
            wrapper.eq("user_id", Long.valueOf(userId.toString()));
        }
        Object operationType = params.get("operationType");
        if (operationType != null && StringUtils.isNotBlank(operationType.toString())) {
            wrapper.eq("operation_type", operationType.toString());
        }
        Object startDate = params.get("startDate");
        if (startDate != null && StringUtils.isNotBlank(startDate.toString())) {
            wrapper.ge("create_date", startDate.toString());
        }
        Object endDate = params.get("endDate");
        if (endDate != null && StringUtils.isNotBlank(endDate.toString())) {
            wrapper.le("create_date", endDate.toString());
        }
        return wrapper;
    }

    /**
     * 填充操作人与请求上下文（当前登录用户 + IP），匿名/系统操作则为空
     */
    private void fillOperator(SysOperationLogEntity entity) {
        try {
            UserDetail user = SecurityUser.getUser();
            if (user != null && user.getId() != null) {
                entity.setUserId(user.getId());
                entity.setUsername(user.getUsername());
            }
            if (HttpContextUtils.getHttpServletRequest() != null) {
                entity.setIp(IpUtils.getIpAddr(HttpContextUtils.getHttpServletRequest()));
            }
        } catch (Exception e) {
            // 无请求上下文（如异步线程）时忽略，操作人留空
            log.debug("操作日志无法获取请求上下文，操作人留空", e);
        }
    }
}
