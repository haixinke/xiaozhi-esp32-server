package xiaozhi.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import xiaozhi.modules.sys.enums.OperationType;

/**
 * 操作日志注解（AOP 落库）
 * <p>
 * 标注在 Controller 方法上，方法调用即记录操作日志，
 * 自动填充请求路径、请求方法、IP、操作人。
 * 区别于遗留的哑注解 {@link LogOperation}（无实现、不落库）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作类型 */
    OperationType type();
}
