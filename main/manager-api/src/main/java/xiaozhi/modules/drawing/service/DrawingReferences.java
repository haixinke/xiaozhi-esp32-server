package xiaozhi.modules.drawing.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 静态 UI 项目的 41 个权威临摹素材 ID；客户端不得指定抽取目标。 */
public final class DrawingReferences {
    private DrawingReferences() { }
    private static final List<String> IDS = List.of(
            "boon_koi_fish_wooden_plaque",
            "boon_koi_fortune_bead",
            "boon_koi_lotus_lantern",
            "boon_koi_paper_pinwheel",
            "boon_koi_water_ripple_jade_pendant",
            "cork_coaster",
            "dali_cloud_stone",
            "dried_tea_tag",
            "ginkgo_leaf",
            "jade_rabbit_helmet",
            "jade_rabbit_herb_pouch",
            "jade_rabbit_mini_jade_pestle",
            "jade_rabbit_moon_phase_cards",
            "jade_rabbit_osmanthus_branch",
            "k_a01_prayer_stick",
            "k_a02_copper_coin",
            "k_a03_peace_amulet",
            "k_a04_sachet",
            "k_a05_red_string_bracelet",
            "k_a06_small_brass_bell",
            "k_k01_lotus_leaf_bookmark",
            "k_k02_polaroid_stone_bridge",
            "k_k04_origami_paper_boat",
            "k_k05_coffee_cup_sleeve",
            "k_k06_fish_shaped_bobber",
            "k_k07_dried_paint_palette",
            "k_k08_burnt_candle_stub",
            "k_r01_yellow_rubber_duck",
            "k_r02_bicycle_bell",
            "k_r04_traditional_shuttlecock",
            "k_r05_vintage_moon_rabbit_newspaper",
            "k_r06_incense_and_holder",
            "k_r07_rabbit_silhouette_badge",
            "k_r08_blue_paw_print_card",
            "old_wooden_button",
            "rainbow_carwash_cloth",
            "rainforest_seed_pod",
            "red_order_clip",
            "retained_sugar_painting_ladle",
            "short_pencil",
            "small_wood_rattle");

    /** 每局均匀随机选取素材。 */
    public static String pick() { return IDS.get(ThreadLocalRandom.current().nextInt(IDS.size())); }
}
