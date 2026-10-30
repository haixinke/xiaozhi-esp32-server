package xiaozhi.common.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import xiaozhi.common.utils.SpringContextUtils;

/**
 * 测试全局扩展：每个测试类结束后清理 {@link SpringContextUtils} 的进程级静态状态。
 *
 * 背景：多个单元测试类在 @BeforeAll 中把 {@link SpringContextUtils#applicationContext} 替换为 mock
 * ApplicationContext（其 messageSource 桩为回显 defaultMessage），且从不恢复。@SpringBootTest 复用
 * 缓存的 Spring 上下文时不会重新触发 ApplicationContextAware 回调，于是这个 mock 会一路泄漏到后续
 * 测试类，导致 @SpringBootTest 场景拿不到真实 i18n 文案（例如 10374 的报错参数丢失，只剩
 * "Error code: 10374"）。
 *
 * afterAll 记住最近一次真实上下文，并在检测到被 mock 污染时恢复它。真实上下文在
 * @SpringBootTest 运行时由 Spring 注入，afterAll 时即可捕获。
 * （MessageUtils 已不再缓存 MessageSource，无需再清反射清其字段。）
 */
public class MessageSourceCacheResetExtension implements AfterAllCallback {

    /** 最近一次观察到的真实（非 mock）ApplicationContext，用于恢复被单测 mock 污染的静态引用 */
    private static ApplicationContext realApplicationContext;

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ApplicationContext current = SpringContextUtils.applicationContext;
        if (isRealContext(current)) {
            // 记下真实上下文，供后续被 mock 污染时恢复
            realApplicationContext = current;
        } else if (realApplicationContext != null) {
            // 上游被单测替换为 mock：恢复真实上下文，避免后续测试取回 mock MessageSource
            SpringContextUtils.applicationContext = realApplicationContext;
        }
    }

    /** 真实 Spring 上下文（非 null 且非 Mockito mock） */
    private static boolean isRealContext(ApplicationContext ctx) {
        return ctx != null && !Mockito.mockingDetails(ctx).isMock();
    }
}