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
    private static MessageSource messageSource;

    public static String getMessage(int code) {
        return getMessage(code, new String[0]);
    }

    public static String getMessage(int code, String... params) {
        if (messageSource == null) {
            // 延迟初始化，确保Spring上下文已完全初始化
            messageSource = (MessageSource) SpringContextUtils.getBean("messageSource");
        }
        Locale locale = LocaleContextHolder.getLocale();
        String defaultMessage = messageSource.getMessage(code + "", params,
                "Error code: " + code, Locale.getDefault());
        return messageSource.getMessage(code + "", params, defaultMessage, locale);
    }
}
