package xiaozhi.modules.companion.util;

/**
 * 动态亲密度算法核心（纯函数，无 Spring 依赖，便于单测）。
 * 参数即"手感旋钮"，集中在此，便于统一调优。
 */
public final class IntimacyRule {

    /** 起步基准（心动档中部） */
    static final float BASE_START = 0.35f;
    /** 当日投入度饱和所需的用户消息数 */
    static final int ENGAGE_SATURATION = 15;
    /** 增长基础系数 */
    static final float UP_RATE = 0.06f;
    /** 单日增长硬上限 */
    static final float UP_DAILY_CAP = 0.05f;
    /** 连续天数加成封顶天数 */
    static final int STREAK_CAP = 7;
    /** 每连续一天的加成步长 */
    static final float STREAK_STEP = 0.08f;
    /** 衰减基础系数 */
    static final float DECAY_BASE = 0.012f;
    /** 亲密度越高衰减越慢的抗性系数 */
    static final float DECAY_RESIST = 0.4f;
    /** 亲密度硬下限（相识过就不再跌回陌生） */
    static final float FLOOR = 0.15f;
    /** 冷落宽限天数（含）：不活跃 <= 此天数不衰减 */
    static final int GRACE_DAYS = 2;

    private IntimacyRule() {
    }

    public static float startValue(String relationType) {
        if (relationType == null) {
            return BASE_START;
        }
        return switch (relationType) {
            case "childhood" -> 0.38f;
            case "loveAtFirst" -> 0.35f;
            case "bickering" -> 0.32f;
            default -> BASE_START;
        };
    }

    public static float engagement(int userMsgs) {
        if (userMsgs <= 0) {
            return 0f;
        }
        double e = Math.log(1 + userMsgs) / Math.log(1 + ENGAGE_SATURATION);
        return (float) Math.min(1.0, e);
    }

    public static float streakFactor(int streak) {
        int capped = Math.min(Math.max(streak, 1), STREAK_CAP);
        return 1f + (capped - 1) * STREAK_STEP;
    }

    public static float grow(float intimacy, int userMsgs, int streak) {
        float e = engagement(userMsgs);
        if (e <= 0f) {
            return intimacy;
        }
        float gain = UP_RATE * e * (1f - intimacy) * streakFactor(streak);
        gain = Math.min(gain, UP_DAILY_CAP);
        return clamp(intimacy + gain);
    }

    public static float decay(float intimacy, int daysSinceActive) {
        if (daysSinceActive <= GRACE_DAYS) {
            return intimacy;
        }
        float d = DECAY_BASE * (1f - DECAY_RESIST * intimacy);
        return Math.max(FLOOR, intimacy - d);
    }

    public static int nextStreak(int currentStreak, boolean activeToday, boolean consecutive) {
        if (!activeToday) {
            return 0;
        }
        return consecutive ? currentStreak + 1 : 1;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
