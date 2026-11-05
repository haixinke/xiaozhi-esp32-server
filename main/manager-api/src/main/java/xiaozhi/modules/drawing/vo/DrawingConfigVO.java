package xiaozhi.modules.drawing.vo;

/** 可开局配置；remainingPlays=-1 表示不限，金额仅为当前配置，最终以完成时配置结算。 */
public record DrawingConfigVO(int earnStars, int consumeStars, int dailyLimit,
        long remainingPlays, boolean rewardAvailable) { }
