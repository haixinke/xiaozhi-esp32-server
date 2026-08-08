package xiaozhi.modules.storyengine.constant;

public enum StoryImageTimeOfDay {
    DAY("白天"),
    SUNSET("落日"),
    NIGHT("黑夜");

    private final String databaseValue;

    StoryImageTimeOfDay(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
