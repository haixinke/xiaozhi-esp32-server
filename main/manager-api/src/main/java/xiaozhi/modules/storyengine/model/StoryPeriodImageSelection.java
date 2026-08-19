package xiaozhi.modules.storyengine.model;

/**
 * 未到期换图（时段边界切换或同时段轮换）的选图结果。仅承载图片相关字段，
 * 动作、场景、时长等故事状态字段在未到期换图中保持不变。
 */
public record StoryPeriodImageSelection(String imageId, String imageUrl, String caption, String tagImageUrl) {
}
