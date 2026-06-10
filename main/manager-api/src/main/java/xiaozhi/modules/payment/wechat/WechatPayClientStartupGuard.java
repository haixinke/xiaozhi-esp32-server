package xiaozhi.modules.payment.wechat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 启动期校验：生产环境（profile=prod）下不允许使用 {@link MockWechatPayClient}。
 * 防止运维误开 {@code wechat.pay.mock=true} 导致 mock 通道在生产被加载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatPayClientStartupGuard implements ApplicationRunner {

    private final WechatPayClient wechatPayClient;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (wechatPayClient.isMockMode()
                && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "Production must not run with MockWechatPayClient. " +
                    "Provide a real WechatPayClient @Primary bean and disable wechat.pay.mock.");
        }
        log.info("WechatPayClient startup ok, mockMode={}, profiles={}",
                wechatPayClient.isMockMode(), String.join(",", environment.getActiveProfiles()));
    }
}
