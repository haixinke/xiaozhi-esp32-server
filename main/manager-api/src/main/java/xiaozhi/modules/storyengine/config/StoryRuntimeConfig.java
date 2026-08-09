package xiaozhi.modules.storyengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 故事运行时配置。开启调度并提供 Asia/Shanghai 时区时钟，时钟可注入以便测试替换。
 */
@Configuration
@EnableScheduling
public class StoryRuntimeConfig {
    @Bean("storyRuntimeClock")
    Clock storyRuntimeClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
