package xiaozhi.common.support;

import java.lang.reflect.Field;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import xiaozhi.common.utils.MessageUtils;
import xiaozhi.common.utils.SpringContextUtils;

/**
 * 测试全局扩展：每个测试类结束后清理 {@link MessageUtils} / {@link SpringContextUtils} 的进程级静态状态。
 *
 * 背景：多个单元测试类在 @BeforeAll 中把 {@link SpringContextUtils#applicationContext} 替换为 mock
 * ApplicationContext（其 messageSource 桩为回显 defaultMessage），且从不恢复。@SpringBootTest 复用
 * 缓存的 Spring 上下文时不会重新触发 ApplicationContextAware 回调，于是这个 mock 会一路泄漏到后续
 * 测试类；MessageUtils 懒初始化又从这个 mock 取回 mock MessageSource 并缓存，导致 @SpringBootTest
 * 场景拿不到真实 i18n 文案（例如 10374 的报错参数丢失，只剩 "Error code: 10374"）。
 *
 * 因此仅清空 MessageUtils.messageSource（懒初始化会按当时上下文重新解析）还不够：上游 applicationContext
 * 若仍是 mock，重新解析只会再次取回 mock。afterAll 需同时——记住最近一次真实上下文，并在检测到被 mock
 * 污染时恢复它。真实上下文在 @SpringBootTest 运行时由 Spring 注入，afterAll 时即可捕获。
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
            // 上游被单测替换为 mock：恢复真实上下文，避免懒加载再次取回 mock MessageSource
            SpringContextUtils.applicationContext = realApplicationContext;
        }

        // 下游缓存置 null：MessageUtils 懒初始化，下次调用时按当时（已恢复的）上下文重新解析
        Field field = MessageUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, null);
    }

    /** 真实 Spring 上下文（非 null 且非 Mockito mock） */
    private static boolean isRealContext(ApplicationContext ctx) {
        return ctx != null && !Mockito.mockingDetails(ctx).isMock();
    }
}