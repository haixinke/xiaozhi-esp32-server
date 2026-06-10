package xiaozhi.modules.subscription.enums;

/**
 * 订阅状态
 */
public enum SubscriptionStatus {
    /** 未生效 */
    PENDING(0),
    /** 生效中 */
    ACTIVE(1),
    /** 已过期 */
    EXPIRED(2),
    /** 已退款 */
    REFUNDED(3);

    private final int value;

    SubscriptionStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
