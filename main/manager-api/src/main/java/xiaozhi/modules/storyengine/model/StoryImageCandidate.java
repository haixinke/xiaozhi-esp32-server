package xiaozhi.modules.storyengine.model;

/**
 * 动作图片候选。captions 为以 | 分隔的原始配文串，选择时再拆分取一条。
 * tag 为管理端标签，用于定位窗户等特殊图片。
 */
public record StoryImageCandidate(String id, String imageUrl, String captions, String tag) {
}
