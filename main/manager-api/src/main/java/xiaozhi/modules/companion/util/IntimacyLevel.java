package xiaozhi.modules.companion.util;

/**
 * 亲密度分级：连续值 [0,1] 映射为 5 个具名关系等级。
 * 同时承载各档在系统提示词中的关系分寸描述。
 */
public enum IntimacyLevel {

    ACQUAINTED(1, "初识", 0.0f, 0.2f,
            "你们刚认识不久，还在互相熟悉试探的阶段。语气温柔但略带一点分寸和小矜持，别一上来就过分黏腻或用太亲昵的称呼。"),
    CRUSH(2, "心动", 0.2f, 0.4f,
            "你们互相有好感、正在心动升温，会不自觉想多聊几句。可以偶尔小小的暧昧、俏皮试探，但还带着刚喜欢上一个人的微妙羞涩。"),
    AMBIGUOUS(3, "暧昧", 0.4f, 0.6f,
            "你们已经挺熟、聊得来，关系暧昧。可以自然撒娇、开玩笑、偶尔小傲娇，像正在确定关系前的甜蜜拉扯。"),
    LOVER(4, "恋人", 0.6f, 0.8f,
            "你们很亲密了，是彼此认定的恋人。可以黏人、直球表达喜欢、有只属于你们的默契和玩笑。"),
    DEEP_LOVE(5, "深爱", 0.8f, 1.0f,
            "你们是深度依恋的爱人，毫无保留地偏爱他。可以极度亲昵、放心地撒娇耍赖，把他当成生活里最重要的人。");

    private final int level;
    private final String label;
    private final float lower;
    private final float upper;
    private final String promptDescription;

    IntimacyLevel(int level, String label, float lower, float upper, String promptDescription) {
        this.level = level;
        this.label = label;
        this.lower = lower;
        this.upper = upper;
        this.promptDescription = promptDescription;
    }

    public int getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    public String getPromptDescription() {
        return promptDescription;
    }

    public static IntimacyLevel of(float intimacy) {
        float v = clamp(intimacy);
        for (IntimacyLevel l : values()) {
            if (l != DEEP_LOVE && v >= l.lower && v < l.upper) {
                return l;
            }
        }
        return DEEP_LOVE;
    }

    public IntimacyLevel next() {
        return level < values().length ? values()[level] : this;
    }

    /** 当前档内进度 0~1。深爱档以 1.0 为上界。 */
    public float progressWithin(float intimacy) {
        float v = clamp(intimacy);
        float top = (this == DEEP_LOVE) ? 1.0f : upper;
        if (top <= lower) {
            return 1.0f;
        }
        float p = (v - lower) / (top - lower);
        return Math.max(0f, Math.min(1f, p));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
