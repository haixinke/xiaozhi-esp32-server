package xiaozhi.modules.wechat.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 将破壳后手机号门槛接到实际 MVC 请求链，统一覆盖直接访问业务接口。 */
@Configuration
@RequiredArgsConstructor
public class WechatPhoneWebConfig implements WebMvcConfigurer {
    private final WechatPhoneInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }
}
