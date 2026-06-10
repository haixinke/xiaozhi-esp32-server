package xiaozhi.modules.payment.enums;

/**
 * 订单状态
 */
public final class OrderStatus {
    /** 待支付 */
    public static final int PENDING = 0;
    /** 已支付 */
    public static final int PAID = 1;
    /** 已发货 */
    public static final int FULFILLED = 2;
    /** 已取消 */
    public static final int CANCELLED = 3;
    /** 已退款 */
    public static final int REFUNDED = 4;
    /** 已超时 */
    public static final int EXPIRED = 5;

    private OrderStatus() {
    }
}
