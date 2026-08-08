package xiaozhi.modules.storyengine.service;

import xiaozhi.modules.storyengine.model.StoryEvaluationResult;

import java.time.ZonedDateTime;

public interface PrototypeStoryStateService {
    StoryEvaluationResult evaluate(String prototype, ZonedDateTime evaluatedAt);
}
