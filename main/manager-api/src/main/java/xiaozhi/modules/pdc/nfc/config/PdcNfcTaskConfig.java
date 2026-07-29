package xiaozhi.modules.pdc.nfc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import xiaozhi.modules.pdc.nfc.wechat.WechatNfcHttpTransport;
import xiaozhi.modules.pdc.nfc.wechat.WechatNfcSchemeClient;
import xiaozhi.modules.wechat.service.WechatAccessTokenProvider;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * NFC Scheme 任务专用线程池和定时调度配置。
 * <p>
 * 线程池 core 1、max 2、queue 20、AbortPolicy——拒绝时 job 保持 PENDING，
 * 由 5 秒 dispatcher 重新拾取。管理 HTTP 线程绝不执行 worker。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pdc.nfc", name = "enabled", havingValue = "true")
public class PdcNfcTaskConfig {

    /** 线程池等待队列容量 */
    public static final int QUEUE_CAPACITY = 20;

    @Bean(name = "pdcNfcSchemeExecutor")
    public ThreadPoolExecutor pdcNfcSchemeExecutor() {
        return new ThreadPoolExecutor(
                1,
                2,
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "pdc-nfc-scheme-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    public WechatNfcHttpTransport wechatNfcHttpTransport() {
        return new WechatNfcHttpTransport();
    }

    @Bean
    public WechatNfcSchemeClient wechatNfcSchemeClient(
            WechatAccessTokenProvider accessTokens,
            WechatNfcHttpTransport transport,
            PdcNfcProperties properties) {
        return new WechatNfcSchemeClient(accessTokens, transport, properties);
    }
}
