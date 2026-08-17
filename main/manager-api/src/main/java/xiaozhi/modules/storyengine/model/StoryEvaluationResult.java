package xiaozhi.modules.storyengine.model;

/**
 * 一个原型一次整点检查的结果。
 */
public enum StoryEvaluationResult {
    /** 首次初始化成功，占位行激活，不写历史 */
    INITIALIZED,
    /** 到期后成功切换，旧状态已归档并写入新状态 */
    SWITCHED,
    /** 动作未到期，保持当前状态 */
    KEPT_NOT_DUE,
    /** 动作未到期但图片时段已切换，仅刷新背景图相关字段，动作与时长不变 */
    REFRESHED_PERIOD_IMAGE,
    /** 随机数落入剩余概率，保持当前状态 */
    KEPT_REMAINDER,
    /** 配置不完整或非法，保持当前状态 */
    KEPT_INVALID_CONFIGURATION,
    /** 本整点时槽已计算过（多实例幂等），直接跳过 */
    SKIPPED_ALREADY_EVALUATED
}
