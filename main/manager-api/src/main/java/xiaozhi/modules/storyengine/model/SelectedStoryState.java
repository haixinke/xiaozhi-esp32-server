package xiaozhi.modules.storyengine.model;

public record SelectedStoryState(String bigSceneId, String bigSceneName,
                                 String smallSceneId, String smallSceneName, String actionId, String actionName,
                                 String actionImageId, String imageUrl, String caption, int durationHours) {
}
