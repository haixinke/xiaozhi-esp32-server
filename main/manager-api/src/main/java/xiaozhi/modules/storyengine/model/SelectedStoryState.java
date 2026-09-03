package xiaozhi.modules.storyengine.model;

/**
 * 一次成功选择产生的完整新状态快照，写入当前状态表后在持续期间保持不变。
 * tagImageUrl 为选中动作中场景特殊标签图（规则见 SpecialSceneTagRegistry）的 URL
 * （取当前时段首张），无规则或无该标签图时为 null。
 * tagImageCaption 为该标签图原始 captions 串的快照（| 分隔，未拆分），供客户端拆分随机展示。
 */
public record SelectedStoryState(String bigSceneId, String bigSceneName,
                                 String smallSceneId, String smallSceneName, String actionId, String actionName,
                                 String actionImageId, String imageUrl, String caption, int durationHours,
                                 String tagImageUrl, String tagImageCaption) {
}
