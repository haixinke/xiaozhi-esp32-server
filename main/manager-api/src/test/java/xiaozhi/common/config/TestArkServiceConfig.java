package xiaozhi.common.config;

import com.volcengine.ark.runtime.service.ArkService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 测试环境共享的 ArkService 假 bean。
 *
 * <p>生产配置 {@code SeedreamArkConfig} 仅在 {@code seedream.key} 存在时才装配，
 * @SpringBootTest 环境无该配置，导致依赖 ArkService 的 bean（如
 * CollectionCardImageServiceImpl）装配失败、整个 ApplicationContext 加载失败。
 * 此处提供 Mockito mock 仅用于满足装配——不会发起真实 Ark API 调用；
 * 真正走生图链路的单测（ImageGenExecutorTest 等）各自 mock 交互行为。
 */
@TestConfiguration
public class TestArkServiceConfig {

    @Bean
    public ArkService arkService() {
        return Mockito.mock(ArkService.class);
    }
}
