package xiaozhi.modules.storyengine.service.impl;

import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.service.StoryRandomSource;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 ThreadLocalRandom 的生产随机数实现。
 */
@Component
public class ThreadLocalStoryRandomSource implements StoryRandomSource {
    @Override
    public int nextInt(int originInclusive, int boundExclusive) {
        return ThreadLocalRandom.current().nextInt(originInclusive, boundExclusive);
    }
}
