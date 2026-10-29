package xiaozhi.modules.dailynote.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 小纸条模块配置。提供 Asia/Shanghai 时区的时钟 Bean，
 * "当天"由该时钟决定；测试可替换注入以验证日界，不依赖真实午夜。
 */
@Configuration
public class DailyNoteConfig {

    @Bean("dailyNoteClock")
    Clock dailyNoteClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
