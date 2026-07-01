package xiaozhi.modules.subscription.enums;

/**
 * 订阅可解锁的能力点
 * 展示顺序：long_term_memory、chat_no_limit、voice_input、superpower、voice_call、memory_enhance、message_speed、message_delete
 */
public final class FeatureCode {
    /** 长久的记忆（女友的文字和语音） */
    public static final String LONG_TERM_MEMORY = "long_term_memory";
    /** 每日对话无限畅聊 */
    public static final String CHAT_NO_LIMIT = "chat_no_limit";
    /** 语音输入，解放双手 */
    public static final String VOICE_INPUT = "voice_input";
    /** 女友超能力（天气、新闻） */
    public static final String SUPERPOWER = "superpower";
    /** 语音通话无限畅聊 */
    public static final String VOICE_CALL = "voice_call";
    /** 记忆增强（标记权益，暂无功能改造） */
    public static final String MEMORY_ENHANCE = "memory_enhance";
    /** 消息回复速度提升（标记权益，暂无功能改造） */
    public static final String MESSAGE_SPEED = "message_speed";
    /** 历史消息撤回（标记权益，暂无功能改造） */
    public static final String MESSAGE_DELETE = "message_delete";

    private FeatureCode() {
    }
}
