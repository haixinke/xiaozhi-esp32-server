package xiaozhi.modules.storyengine.service.impl;

import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.service.StoryRandomSource;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ThreadLocalStoryRandomSource implements StoryRandomSource {
    @Override
    public int nextInt(int originInclusive, int boundExclusive) {
        return ThreadLocalRandom.current().nextInt(originInclusive, boundExclusive);
    }
}
