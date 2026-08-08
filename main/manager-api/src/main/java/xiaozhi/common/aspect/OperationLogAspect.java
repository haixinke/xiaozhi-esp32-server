package xiaozhi.common.aspect;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.annotation.OperationLog;
import xiaozhi.common.utils.HttpContextUtils;
import xiaozhi.common.utils.IpUtils;
import xiaozhi.modules.sys.entity.SysOperationLogEntity;
import xiaozhi.modules.sys.service.OperationLogService;

/**
 * 操作日志切面
 * <p>
 * 拦截 {@link OperationLog} 注解方法，方法调用即记录操作日志，
 * 自动填充请求路径、请求方法、IP。成功/失败按方法是否抛异常判定。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        SysOperationLogEntity entity = new SysOperationLogEntity();
        entity.setOperationType(operationLog.type().name());
        entity.setOperationDesc(operationLog.type().getDesc());
        fillRequest(entity);

        try {
            Object result = point.proceed();
            entity.setStatus(1);
            operationLogService.save(entity);
            return result;
        } catch (Throwable e) {
            entity.setStatus(0);
            entity.setErrorMsg(truncate(e.getMessage()));
            operationLogService.save(entity);
            throw e;
        }
    }

    private void fillRequest(SysOperationLogEntity entity) {
        HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
        if (request != null) {
            entity.setRequestUri(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
            entity.setIp(IpUtils.getIpAddr(request));
        }
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
