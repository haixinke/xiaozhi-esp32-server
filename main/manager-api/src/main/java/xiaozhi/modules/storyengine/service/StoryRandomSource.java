package xiaozhi.modules.storyengine.service;

@FunctionalInterface
public interface StoryRandomSource {
    int nextInt(int originInclusive, int boundExclusive);
}
