package xiaozhi.modules.item.enums;

/**
 * 道具发放来源
 */
public final class ItemGrantSource {
    /** 用户购买 */
    public static final String PURCHASE = "purchase";
    /** 订阅附赠 */
    public static final String SUBSCRIPTION_BONUS = "subscription_bonus";
    /** 运营后台手动发放 */
    public static final String ADMIN_GRANT = "admin_grant";

    private ItemGrantSource() {
    }
}
