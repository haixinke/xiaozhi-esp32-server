package xiaozhi.modules.storyengine.constant;

/**
 * 图片时段，用于匹配动作图片的 time_of_day 列。数据库与展示均使用中文值。
 */
public enum StoryImageTimeOfDay {
    DAY("白天"),
    SUNSET("落日"),
    NIGHT("黑夜");

    /** 动作图片表 time_of_day 列存储的中文值 */
    private final String databaseValue;

    StoryImageTimeOfDay(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
