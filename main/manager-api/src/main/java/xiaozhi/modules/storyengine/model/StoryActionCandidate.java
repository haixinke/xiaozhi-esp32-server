package xiaozhi.modules.storyengine.model;

import java.util.List;

public record StoryActionCandidate(String id, String name, int durationMin, int durationMax,
                                   List<StoryImageCandidate> images) {
    public StoryActionCandidate {
        images = List.copyOf(images);
    }
}
