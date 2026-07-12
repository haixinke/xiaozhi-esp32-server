package xiaozhi.modules.pet.constant;

import java.util.Optional;

/**
 * 蛋宝宝孵化修炼动作类型。
 * <p>
 * minutes: 该动作可折算的孵化加速分钟数(小时换算)。
 * oneTime: true 表示该动作每个宠物只能做一次(NICKNAME/DOODLE)；false 表示每日可做一次(CUDDLE/WISH/LESSON)。
 */
public enum HatchActionType {

    NICKNAME(10080, true),
    CUDDLE(60, false),
    WISH(60, false),
    LESSON(60, false),
    DOODLE(720, true);

    private final int minutes;
    private final boolean oneTime;

    HatchActionType(int minutes, boolean oneTime) {
        this.minutes = minutes;
        this.oneTime = oneTime;
    }

    public int minutes() {
        return minutes;
    }

    public boolean oneTime() {
        return oneTime;
    }

    public static Optional<HatchActionType> from(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(HatchActionType.valueOf(code.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
