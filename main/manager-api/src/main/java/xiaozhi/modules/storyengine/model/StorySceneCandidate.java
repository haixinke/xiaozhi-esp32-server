package xiaozhi.modules.storyengine.model;

import java.util.List;

public record StorySceneCandidate(String bigSceneId, String bigSceneName,
                                  String smallSceneId, String smallSceneName, int weight,
                                  List<StoryActionCandidate> actions) {
    public StorySceneCandidate {
        actions = List.copyOf(actions);
    }
}
