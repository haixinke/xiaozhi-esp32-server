package xiaozhi.modules.pet.constant;

/**
 * 今日心情类型（PRD §8.6：MVP 仅保留 5 类，避免愤怒/焦虑等重情绪）。
 * 对应 ai_pet.today_mood 列存储中文 label。
 */
public enum TodayMood {

    HAPPY("开心"),
    CALM("平静"),
    MISS("想念"),
    EXCITED("兴奋"),
    LOW("低落");

    private final String label;

    TodayMood(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TodayMood fromLabel(String label) {
        if (label == null) {
            return null;
        }
        for (TodayMood m : values()) {
            if (m.label.equals(label)) {
                return m;
            }
        }
        return null;
    }
}
