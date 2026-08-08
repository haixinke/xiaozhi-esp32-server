package xiaozhi.modules.storyengine.service;

/**
 * 随机数来源抽象，便于测试注入确定性实现。
 */
@FunctionalInterface
public interface StoryRandomSource {
    /**
     * 返回 [originInclusive, boundExclusive) 区间内的随机整数。
     */
    int nextInt(int originInclusive, int boundExclusive);
}
