package xiaozhi.modules.sys.enums;

/**
 * 操作日志类型
 * <p>
 * 集中管理，避免魔法字符串散落。新业务接入时在此追加枚举值。
 */
public enum OperationType {

    /** 导出聊天记录 */
    CHAT_HISTORY_EXPORT("导出聊天记录"),

    /** 删除聊天记录 */
    CHAT_HISTORY_DELETE("删除聊天记录");

    private final String desc;

    OperationType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
