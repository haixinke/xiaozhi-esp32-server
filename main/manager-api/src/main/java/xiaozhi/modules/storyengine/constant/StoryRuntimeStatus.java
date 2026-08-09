package xiaozhi.modules.storyengine.constant;

/**
 * 原型共享故事的运行状态。
 */
public enum StoryRuntimeStatus {
    /** 占位状态：迁移时预置，尚未产生首个有效故事，不参与历史归档 */
    UNINITIALIZED,
    /** 运行中：具有完整的场景/动作/图片/时间快照 */
    ACTIVE
}
