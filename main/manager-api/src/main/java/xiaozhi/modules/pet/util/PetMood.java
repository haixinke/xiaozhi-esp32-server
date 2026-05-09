package xiaozhi.modules.pet.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 宠物今日心情枚举，按权重随机生成。
 */
public enum PetMood {

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

    PetMood(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按权重随机选取一个心情。
     */
    public static PetMood random() {
        int totalWeight = 0;
        for (PetMood mood : values()) {
            totalWeight += mood.weight;
        }
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (PetMood mood : values()) {
            cumulative += mood.weight;
            if (random < cumulative) {
                return mood;
            }
        }
        return JOY;
    }
}
