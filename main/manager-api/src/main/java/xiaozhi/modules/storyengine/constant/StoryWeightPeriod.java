package xiaozhi.modules.storyengine.constant;

/**
 * 权重时段，决定读取小场景的哪个权重字段。按 Asia/Shanghai 时间划分。
 */
public enum StoryWeightPeriod {
    /** 00:00~05:59，对应 weight_night */
    NIGHT,
    /** 06:00~11:59，对应 weight_morning */
    MORNING,
    /** 12:00~17:59，对应 weight_afternoon */
    AFTERNOON,
    /** 18:00~23:59，对应 weight_evening */
    EVENING
}
