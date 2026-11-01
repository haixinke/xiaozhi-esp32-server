package xiaozhi.common.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.Locale;

import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import xiaozhi.common.utils.SpringContextUtils;

/**
 * 纯单测环境的 MessageSource 安装工具。
 *
 * MessageUtils 每次通过 {@code SpringContextUtils.getBean("messageSource")} 现取，
 * 不再持有静态 messageSource 字段；因此单测不能再反射写字段，需把 mock 挂到
 * SpringContextUtils.applicationContext 上。
 *
 * 无需手动还原：MessageSourceCacheResetExtension（全局自动注册）会在每个测试类
 * afterAll 时恢复真实上下文。
 */
public final class MessageSourceMockSupport {

    private MessageSourceMockSupport() {
    }

    /**
     * 安装回显 defaultMessage 的 mock MessageSource 到 SpringContextUtils。
     * 桩用 lenient：部分测试类从不触发 MessageUtils，严格桩会报 UnnecessaryStubbingException。
     */
    public static void install() {
        MessageSource messageSource = mock(MessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(2));
        ApplicationContext context = mock(ApplicationContext.class);
        lenient().when(context.getBean("messageSource")).thenReturn(messageSource);
        SpringContextUtils.applicationContext = context;
    }
}
