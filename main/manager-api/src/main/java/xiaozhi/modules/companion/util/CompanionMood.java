package xiaozhi.modules.companion.util;

public enum CompanionMood {

    JOY("愉快"),
    CALM("平静"),
    EXCITEMENT("兴奋"),
    CURIOSITY("好奇"),
    CARE("关怀"),
    ANXIETY("焦虑"),
    FRUSTRATION("沮丧"),
    FATIGUE("疲惫");

    private final String label;

    CompanionMood(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
