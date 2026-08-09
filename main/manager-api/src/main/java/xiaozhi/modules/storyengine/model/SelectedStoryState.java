package xiaozhi.modules.storyengine.model;

/**
 * 一次成功选择产生的完整新状态快照，写入当前状态表后在持续期间保持不变。
 */
public record SelectedStoryState(String bigSceneId, String bigSceneName,
                                 String smallSceneId, String smallSceneName, String actionId, String actionName,
                                 String actionImageId, String imageUrl, String caption, int durationHours) {
}
