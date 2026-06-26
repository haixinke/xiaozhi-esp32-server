package xiaozhi.modules.companion.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 伴侣今日心情枚举，按权重随机生成。
 * 正面/中性心情权重较高，负面心情权重较低，避免整体体验过于消极。
 */
public enum CompanionMood {

    JOY("愉快", 20),
    CALM("平静", 20),
    EXCITEMENT("兴奋", 15),
    CURIOSITY("好奇", 15),
    CARE("关怀", 15),
    ANXIETY("焦虑", 5),
    FRUSTRATION("沮丧", 5),
    FATIGUE("疲惫", 5);

    private final String label;
    private final int weight;

    CompanionMood(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * 按权重随机选取一个心情。
     *
     * @return 随机心情
     */
    public static CompanionMood random() {
        int totalWeight = 0;
        for (CompanionMood mood : values()) {
            totalWeight += mood.weight;
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (CompanionMood mood : values()) {
            cumulative += mood.weight;
            if (random < cumulative) {
                return mood;
            }
        }
        return CALM;
    }

    /**
     * 根据编码获取心情，找不到时返回平静。
     *
     * @param code 心情编码
     * @return 对应心情
     */
    public static CompanionMood fromCode(String code) {
        if (code == null || code.isBlank()) {
            return CALM;
        }
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CALM;
        }
    }
}
