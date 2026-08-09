package xiaozhi.modules.storyengine.model;

import java.util.List;

/**
 * 小场景候选。weight 为当前权重时段的百分比权重；actions 防御性复制为不可变。
 */
public record StorySceneCandidate(String bigSceneId, String bigSceneName,
                                  String smallSceneId, String smallSceneName, int weight,
                                  List<StoryActionCandidate> actions) {
    public StorySceneCandidate {
        actions = List.copyOf(actions);
    }
}
