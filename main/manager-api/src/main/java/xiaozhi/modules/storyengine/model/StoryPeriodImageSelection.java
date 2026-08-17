package xiaozhi.modules.storyengine.model;

/**
 * 图片时段切换时的换图结果。仅承载图片相关字段，
 * 动作、场景、时长等故事状态字段在时段换图中保持不变。
 */
public record StoryPeriodImageSelection(String imageId, String imageUrl, String caption, String tagImageUrl) {
}
