package xiaozhi.modules.storyengine.service;

import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.constant.StoryImageTimeOfDay;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;
import xiaozhi.modules.storyengine.model.StoryPeriodContext;

import java.time.ZonedDateTime;

/**
 * 时段解析器。把 Asia/Shanghai 当前时间映射为权重时段与图片时段。
 */
@Component
public class StoryPeriodResolver {
    public StoryPeriodContext resolve(ZonedDateTime time) {
        int hour = time.getHour();
        // 00:00~05:59 深夜，配黑夜图
        if (hour < 6) {
            return new StoryPeriodContext(StoryWeightPeriod.NIGHT, StoryImageTimeOfDay.NIGHT);
        }
        // 06:00~11:59 上午，配白天图
        if (hour < 12) {
            return new StoryPeriodContext(StoryWeightPeriod.MORNING, StoryImageTimeOfDay.DAY);
        }
        // 12:00~17:59 下午，配白天图
        if (hour < 18) {
            return new StoryPeriodContext(StoryWeightPeriod.AFTERNOON, StoryImageTimeOfDay.DAY);
        }
        // 18:00~23:59 傍晚，配落日图
        return new StoryPeriodContext(StoryWeightPeriod.EVENING, StoryImageTimeOfDay.SUNSET);
    }
}
