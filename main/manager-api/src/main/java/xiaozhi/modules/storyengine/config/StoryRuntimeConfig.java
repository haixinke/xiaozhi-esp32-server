package xiaozhi.modules.storyengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class StoryRuntimeConfig {
    @Bean("storyRuntimeClock")
    Clock storyRuntimeClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
