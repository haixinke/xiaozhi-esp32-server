package xiaozhi.modules.storyengine.model;

import java.util.List;

/**
 * 动作候选。仅包含当前原型与图片时段下有匹配图片的动作；images 防御性复制为不可变。
 */
public record StoryActionCandidate(String id, String name, int durationMin, int durationMax,
                                   List<StoryImageCandidate> images) {
    public StoryActionCandidate {
        images = List.copyOf(images);
    }
}
