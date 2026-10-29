package xiaozhi.common.utils;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * 国际化
 * Copyright (c) 人人开源 All rights reserved.
 * Website: https://www.renren.io
 */
public class MessageUtils {

    public static String getMessage(int code) {
        return getMessage(code, new String[0]);
    }

    public static String getMessage(int code, String... params) {
        // 不缓存 MessageSource：单测会替换 SpringContextUtils.applicationContext 或
        // 本类的 messageSource 字段，缓存会导致跨测试类污染（错误消息退化为 "Error code: xxx"）。
        // getBean 查找本身开销极小，无需缓存。
        MessageSource source = (MessageSource) SpringContextUtils.getBean("messageSource");
        Locale locale = LocaleContextHolder.getLocale();
        // 单测会用 mock 替换 MessageSource；若 mock 返回 null（如 stubbing 失效），
        // 不能让最终消息为 null，应回退到 "Error code: xxx" 保证异常消息可诊断
        String defaultMessage = source.getMessage(code + "", params,
                "Error code: " + code, Locale.getDefault());
        String message = source.getMessage(code + "", params, defaultMessage, locale);
        if (System.getProperty("messageUtils.debug") != null) {
            System.err.println("[MSGDBG] code=" + code + " source=" + source.getClass().getName()
                    + " default=[" + defaultMessage + "] msg=[" + message + "]");
        }
        return message != null ? message : defaultMessage;
    }
}
