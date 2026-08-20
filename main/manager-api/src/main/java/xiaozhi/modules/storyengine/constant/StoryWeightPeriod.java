package xiaozhi.modules.storyengine.constant;

/**
 * 权重时段，决定读取小场景的哪个权重字段。按 Asia/Shanghai 时间划分。
 */
public enum StoryWeightPeriod {
    /** 19:00~06:59（跨零点），对应 weight_night */
    NIGHT,
    /** 07:00~11:59，对应 weight_morning */
    MORNING,
    /** 12:00~16:59，对应 weight_afternoon */
    AFTERNOON,
    /** 17:00~18:59，对应 weight_evening */
    EVENING
}
