package xiaozhi.common.support;

import java.lang.reflect.Field;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import xiaozhi.common.utils.MessageUtils;

/**
 * 测试全局扩展：每个测试类结束后清空 {@link MessageUtils} 的静态 MessageSource 缓存。
 *
 * 背景：MessageUtils.messageSource 是进程级静态缓存，懒初始化后永不刷新。多个单元测试类在
 * @BeforeAll 中通过反射注入 mock MessageSource，若不清理，mock 会泄漏到后续测试类，导致
 * @SpringBootTest 场景取不到真实 i18n 文案（例如 10374 的报错参数丢失，只剩 defaultMessage）。
 *
 * 置 null 而非还原旧值：MessageUtils 是懒初始化，下次调用时会按当时 Spring 上下文重新解析，
 * 因此无需（也无法可靠地）保存原值。
 */
public class MessageSourceCacheResetExtension implements AfterAllCallback {

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        Field field = MessageUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, null);
    }
}