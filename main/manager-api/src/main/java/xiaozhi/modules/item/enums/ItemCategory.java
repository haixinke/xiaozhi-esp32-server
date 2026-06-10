package xiaozhi.modules.item.enums;

/**
 * 道具大类
 */
public final class ItemCategory {
    /** 一次性变更券（occupation/soulQuirk） */
    public static final String CONSUMABLE_CHANGE = "consumable_change";
    /** 服装（拥有即可重复使用） */
    public static final String OUTFIT = "outfit";
    /** 声音克隆额度 */
    public static final String VOICE_QUOTA = "voice_quota";
    /** 亲密度道具 */
    public static final String INTIMACY = "intimacy";

    private ItemCategory() {
    }

    public static boolean isOutfit(String category) {
        return OUTFIT.equals(category);
    }
}
