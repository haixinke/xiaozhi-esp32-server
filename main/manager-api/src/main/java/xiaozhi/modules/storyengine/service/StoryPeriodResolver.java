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
        // 19:00~06:59 深夜（跨零点），配黑夜图
        if (hour >= 19 || hour < 7) {
            return new StoryPeriodContext(StoryWeightPeriod.NIGHT, StoryImageTimeOfDay.NIGHT);
        }
        // 07:00~11:59 上午，配白天图
        if (hour < 12) {
            return new StoryPeriodContext(StoryWeightPeriod.MORNING, StoryImageTimeOfDay.DAY);
        }
        // 12:00~16:59 下午，配白天图
        if (hour < 17) {
            return new StoryPeriodContext(StoryWeightPeriod.AFTERNOON, StoryImageTimeOfDay.DAY);
        }
        // 17:00~18:59 傍晚，配落日图
        return new StoryPeriodContext(StoryWeightPeriod.EVENING, StoryImageTimeOfDay.SUNSET);
    }
}
