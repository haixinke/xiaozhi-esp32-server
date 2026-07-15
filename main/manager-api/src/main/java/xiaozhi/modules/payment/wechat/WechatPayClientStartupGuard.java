package xiaozhi.modules.payment.wechat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 启动期校验：生产环境（profile=prod）下不允许使用 {@link MockWechatPayClient}。
 * 防止运维误开 {@code wechat.pay.mock=true} 导致 mock 通道在生产被加载。
 * <p>当 {@code wechat.pay.mock=false} 且无真实实现时，WechatPayClient bean 不存在，
 * Guard 跳过校验并输出提示日志。</p>
 */
@Slf4j
// [暂时屏蔽微信支付功能] 取消注释以下注解即可恢复
// @Component
public class WechatPayClientStartupGuard implements ApplicationRunner {

    @Autowired(required = false)
    private WechatPayClient wechatPayClient;

    private final Environment environment;

    public WechatPayClientStartupGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean isProd = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (wechatPayClient == null) {
            if (isProd) {
                throw new IllegalStateException(
                        "Production missing WechatPayClient bean. Set wechat.pay.mock=false and provide " +
                        "valid wechat.pay.* sys_params (mchid/serial_no/private_key/api_v3_key/notify_url).");
            }
            log.warn("WechatPayClient bean not found. Payment features will be unavailable. " +
                    "Set wechat.pay.mock=true for local development or provide a real WechatPayClient @Primary bean.");
            return;
        }
        if (wechatPayClient.isMockMode() && isProd) {
            throw new IllegalStateException(
                    "Production must not run with MockWechatPayClient. " +
                    "Provide a real WechatPayClient @Primary bean and disable wechat.pay.mock.");
        }
        log.info("WechatPayClient startup ok, mockMode={}, profiles={}",
                wechatPayClient.isMockMode(), String.join(",", environment.getActiveProfiles()));
    }
}
