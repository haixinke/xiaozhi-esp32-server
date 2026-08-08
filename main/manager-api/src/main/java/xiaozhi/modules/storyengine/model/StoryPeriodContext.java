package xiaozhi.modules.storyengine.model;

import xiaozhi.modules.storyengine.constant.StoryImageTimeOfDay;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;

public record StoryPeriodContext(StoryWeightPeriod weightPeriod, StoryImageTimeOfDay imageTimeOfDay) {
}
