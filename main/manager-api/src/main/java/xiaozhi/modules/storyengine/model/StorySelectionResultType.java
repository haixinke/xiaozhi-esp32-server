package xiaozhi.modules.storyengine.model;

/**
 * 故事选择结果类型。
 */
public enum StorySelectionResultType {
    /** 命中并产出完整新状态 */
    SELECTED,
    /** 随机数落入剩余概率，保持原状态 */
    REMAIN,
    /** 配置不完整或非法（无有效候选/权重非法/时长非法等），保持原状态 */
    INVALID_CONFIGURATION
}
