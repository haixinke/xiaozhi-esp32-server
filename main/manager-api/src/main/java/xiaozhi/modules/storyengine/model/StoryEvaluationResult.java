package xiaozhi.modules.storyengine.model;

public enum StoryEvaluationResult {
    INITIALIZED,
    SWITCHED,
    KEPT_NOT_DUE,
    KEPT_REMAINDER,
    KEPT_INVALID_CONFIGURATION,
    SKIPPED_ALREADY_EVALUATED
}
