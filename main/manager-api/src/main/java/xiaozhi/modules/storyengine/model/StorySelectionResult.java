package xiaozhi.modules.storyengine.model;

public record StorySelectionResult(StorySelectionResultType type, SelectedStoryState state) {
    public static StorySelectionResult selected(SelectedStoryState state) {
        return new StorySelectionResult(StorySelectionResultType.SELECTED, state);
    }

    public static StorySelectionResult remain() {
        return new StorySelectionResult(StorySelectionResultType.REMAIN, null);
    }

    public static StorySelectionResult invalid() {
        return new StorySelectionResult(StorySelectionResultType.INVALID_CONFIGURATION, null);
    }
}
