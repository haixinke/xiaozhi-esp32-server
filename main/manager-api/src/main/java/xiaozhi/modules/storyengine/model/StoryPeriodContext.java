package xiaozhi.modules.storyengine.model;

import xiaozhi.modules.storyengine.constant.StoryImageTimeOfDay;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;

/**
 * 一次整点计算解析出的时段上下文：权重时段决定小场景权重取值，图片时段决定匹配哪类动作图片。
 */
public record StoryPeriodContext(StoryWeightPeriod weightPeriod, StoryImageTimeOfDay imageTimeOfDay) {
}
