package xiaozhi.modules.pet.config;

import com.volcengine.ark.runtime.service.ArkService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 火山引擎 Ark SDK 客户端配置。
 *
 * <p>仅在配置了 {@code seedream.key} 时创建 {@link ArkService}，并在应用关闭时释放其
 * OkHttp 线程池。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seedream", name = "key")
public class SeedreamArkConfig {

    private static final String IMAGE_GENERATIONS_PATH = "/images/generations";

    private final SeedreamProperties seedreamProperties;

    private ArkService arkService;

    @Bean
    public ArkService arkService() {
        String apiKey = seedreamProperties.getKey();
        String baseUrl = normalizeBaseUrl(seedreamProperties.getUrl());

        ArkService.Builder builder = ArkService.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(180));
        if (StringUtils.isNotBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
        }

        arkService = builder.build();
        log.info("ArkService 初始化完成，baseUrl={}", baseUrl);
        return arkService;
    }

    @PreDestroy
    public void destroy() {
        if (arkService != null) {
            arkService.shutdownExecutor();
            log.info("ArkService 已关闭");
        }
    }

    /**
     * 将可能包含完整接口路径的 URL 裁剪为 Ark SDK 所需的 base URL。
     *
     * <p>历史配置中 {@code seedream.url} 保存的是完整接口地址，例如：
     * {@code https://ark.cn-beijing.volces.com/api/v3/images/generations}。
     * SDK 的 {@code baseUrl} 只需要 {@code https://ark.cn-beijing.volces.com/api/v3}，
     * 因此需要去掉末尾的 {@code /images/generations}。
     */
    private String normalizeBaseUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.endsWith(IMAGE_GENERATIONS_PATH)) {
            return trimmed.substring(0, trimmed.length() - IMAGE_GENERATIONS_PATH.length());
        }
        return trimmed;
    }
}
