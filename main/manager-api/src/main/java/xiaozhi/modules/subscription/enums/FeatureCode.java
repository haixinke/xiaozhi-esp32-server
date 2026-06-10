package xiaozhi.modules.subscription.enums;

/**
 * 订阅可解锁的能力点
 */
public final class FeatureCode {
    /** 换装能力 */
    public static final String OUTFIT = "outfit";
    /** 换职业能力（订阅期内不消耗道具） */
    public static final String OCCUPATION_CHANGE = "occupation_change";
    /** 换小任性能力 */
    public static final String SOUL_QUIRK_CHANGE = "soul_quirk_change";
    /** 自定义克隆音色 */
    public static final String CUSTOM_VOICE = "custom_voice";
    /** 优先聊天通道 */
    public static final String PRIORITY_CHAT = "priority_chat";

    private FeatureCode() {
    }
}
