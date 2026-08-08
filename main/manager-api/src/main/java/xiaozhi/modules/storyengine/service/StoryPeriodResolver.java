package xiaozhi.modules.storyengine.service;

import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.constant.StoryImageTimeOfDay;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;
import xiaozhi.modules.storyengine.model.StoryPeriodContext;

import java.time.ZonedDateTime;

@Component
public class StoryPeriodResolver {
    public StoryPeriodContext resolve(ZonedDateTime time) {
        int hour = time.getHour();
        if (hour < 6) {
            return new StoryPeriodContext(StoryWeightPeriod.NIGHT, StoryImageTimeOfDay.NIGHT);
        }
        if (hour < 12) {
            return new StoryPeriodContext(StoryWeightPeriod.MORNING, StoryImageTimeOfDay.DAY);
        }
        if (hour < 18) {
            return new StoryPeriodContext(StoryWeightPeriod.AFTERNOON, StoryImageTimeOfDay.DAY);
        }
        return new StoryPeriodContext(StoryWeightPeriod.EVENING, StoryImageTimeOfDay.SUNSET);
    }
}
