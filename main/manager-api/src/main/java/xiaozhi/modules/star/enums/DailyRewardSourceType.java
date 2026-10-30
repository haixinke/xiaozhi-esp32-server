package xiaozhi.modules.star.enums;

/**
 * 每日奖励来源类型枚举。落库字符串为规范大写值，
 * 调用方传入大小写不敏感，统一转大写比较，避免大小写差异形成两份每日资格。
 */
public enum DailyRewardSourceType {

    /** 小纸条每日奖励（source_key 固定为 DAILY_NOTE） */
    DAILY_NOTE,
    /** 游戏每日奖励（source_key 为游戏类型 code，由游戏管理配置校验保证规范值） */
    GAME;

    /**
     * 按规范值解析来源类型（大小写不敏感）。
     *
     * @throws IllegalArgumentException 非法或未知来源类型
     */
    public static DailyRewardSourceType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("奖励来源类型不能为空");
        }
        for (DailyRewardSourceType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知奖励来源类型: " + value);
    }
}
