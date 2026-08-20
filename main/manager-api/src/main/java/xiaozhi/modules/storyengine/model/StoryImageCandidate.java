package xiaozhi.modules.storyengine.model;

/**
 * 动作图片候选。captions 为以 | 分隔的原始配文串，选择时再拆分取一条。
 * tag 为管理端标签，语义由 SpecialSceneTagRegistry 按场景组合决定（如卧室的"窗户"图）；
 * 未命中规则的场景中 tag 不影响主图抽取。
 */
public record StoryImageCandidate(String id, String imageUrl, String captions, String tag) {
}
