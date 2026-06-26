package xiaozhi.modules.companion.util;

public enum MenstrualPhase {
    MENSTRUATION("经期"),
    FOLLICULAR("卵泡期"),
    OVULATION("排卵期"),
    LUTEAL("黄体期");

    private final String label;

    MenstrualPhase(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
