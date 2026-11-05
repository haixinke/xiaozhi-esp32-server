// 临摹素材目录沿用静态工程，图片按实际显示尺寸压缩为兼容 PNG。
const REFERENCES = Object.freeze([
  {
    "id": "boon_koi_fish_wooden_plaque",
    "name": "鱼形木牌",
    "line": "/drawing/assets/boon_koi_fish_wooden_plaque-line.png",
    "color": "/drawing/assets/boon_koi_fish_wooden_plaque-color.png"
  },
  {
    "id": "boon_koi_fortune_bead",
    "name": "转运珠",
    "line": "/drawing/assets/boon_koi_fortune_bead-line.png",
    "color": "/drawing/assets/boon_koi_fortune_bead-color.png"
  },
  {
    "id": "boon_koi_lotus_lantern",
    "name": "莲花灯",
    "line": "/drawing/assets/boon_koi_lotus_lantern-line.png",
    "color": "/drawing/assets/boon_koi_lotus_lantern-color.png"
  },
  {
    "id": "boon_koi_paper_pinwheel",
    "name": "纸风车",
    "line": "/drawing/assets/boon_koi_paper_pinwheel-line.png",
    "color": "/drawing/assets/boon_koi_paper_pinwheel-color.png"
  },
  {
    "id": "boon_koi_water_ripple_jade_pendant",
    "name": "水纹玉佩",
    "line": "/drawing/assets/boon_koi_water_ripple_jade_pendant-line.png",
    "color": "/drawing/assets/boon_koi_water_ripple_jade_pendant-color.png"
  },
  {
    "id": "cork_coaster",
    "name": "软木杯垫",
    "line": "/drawing/assets/cork_coaster-line.png",
    "color": "/drawing/assets/cork_coaster-color.png"
  },
  {
    "id": "dali_cloud_stone",
    "name": "大理云石",
    "line": "/drawing/assets/dali_cloud_stone-line.png",
    "color": "/drawing/assets/dali_cloud_stone-color.png"
  },
  {
    "id": "dried_tea_tag",
    "name": "干茶签",
    "line": "/drawing/assets/dried_tea_tag-line.png",
    "color": "/drawing/assets/dried_tea_tag-color.png"
  },
  {
    "id": "ginkgo_leaf",
    "name": "银杏叶",
    "line": "/drawing/assets/ginkgo_leaf-line.png",
    "color": "/drawing/assets/ginkgo_leaf-color.png"
  },
  {
    "id": "jade_rabbit_helmet",
    "name": "玉兔头盔",
    "line": "/drawing/assets/jade_rabbit_helmet-line.png",
    "color": "/drawing/assets/jade_rabbit_helmet-color.png"
  },
  {
    "id": "jade_rabbit_herb_pouch",
    "name": "药草包",
    "line": "/drawing/assets/jade_rabbit_herb_pouch-line.png",
    "color": "/drawing/assets/jade_rabbit_herb_pouch-color.png"
  },
  {
    "id": "jade_rabbit_mini_jade_pestle",
    "name": "迷你玉杵",
    "line": "/drawing/assets/jade_rabbit_mini_jade_pestle-line.png",
    "color": "/drawing/assets/jade_rabbit_mini_jade_pestle-color.png"
  },
  {
    "id": "jade_rabbit_moon_phase_cards",
    "name": "月相卡",
    "line": "/drawing/assets/jade_rabbit_moon_phase_cards-line.png",
    "color": "/drawing/assets/jade_rabbit_moon_phase_cards-color.png"
  },
  {
    "id": "jade_rabbit_osmanthus_branch",
    "name": "桂花枝",
    "line": "/drawing/assets/jade_rabbit_osmanthus_branch-line.png",
    "color": "/drawing/assets/jade_rabbit_osmanthus_branch-color.png"
  },
  {
    "id": "k_a01_prayer_stick",
    "name": "祈福签",
    "line": "/drawing/assets/k_a01_prayer_stick-line.png",
    "color": "/drawing/assets/k_a01_prayer_stick-color.png"
  },
  {
    "id": "k_a02_copper_coin",
    "name": "铜钱",
    "line": "/drawing/assets/k_a02_copper_coin-line.png",
    "color": "/drawing/assets/k_a02_copper_coin-color.png"
  },
  {
    "id": "k_a03_peace_amulet",
    "name": "平安符",
    "line": "/drawing/assets/k_a03_peace_amulet-line.png",
    "color": "/drawing/assets/k_a03_peace_amulet-color.png"
  },
  {
    "id": "k_a04_sachet",
    "name": "香囊",
    "line": "/drawing/assets/k_a04_sachet-line.png",
    "color": "/drawing/assets/k_a04_sachet-color.png"
  },
  {
    "id": "k_a05_red_string_bracelet",
    "name": "红绳手链",
    "line": "/drawing/assets/k_a05_red_string_bracelet-line.png",
    "color": "/drawing/assets/k_a05_red_string_bracelet-color.png"
  },
  {
    "id": "k_a06_small_brass_bell",
    "name": "小铜铃",
    "line": "/drawing/assets/k_a06_small_brass_bell-line.png",
    "color": "/drawing/assets/k_a06_small_brass_bell-color.png"
  },
  {
    "id": "k_k01_lotus_leaf_bookmark",
    "name": "荷叶书签",
    "line": "/drawing/assets/k_k01_lotus_leaf_bookmark-line.png",
    "color": "/drawing/assets/k_k01_lotus_leaf_bookmark-color.png"
  },
  {
    "id": "k_k02_polaroid_stone_bridge",
    "name": "石桥拍立得",
    "line": "/drawing/assets/k_k02_polaroid_stone_bridge-line.png",
    "color": "/drawing/assets/k_k02_polaroid_stone_bridge-color.png"
  },
  {
    "id": "k_k04_origami_paper_boat",
    "name": "折纸船",
    "line": "/drawing/assets/k_k04_origami_paper_boat-line.png",
    "color": "/drawing/assets/k_k04_origami_paper_boat-color.png"
  },
  {
    "id": "k_k05_coffee_cup_sleeve",
    "name": "咖啡杯套",
    "line": "/drawing/assets/k_k05_coffee_cup_sleeve-line.png",
    "color": "/drawing/assets/k_k05_coffee_cup_sleeve-color.png"
  },
  {
    "id": "k_k06_fish_shaped_bobber",
    "name": "鱼形浮漂",
    "line": "/drawing/assets/k_k06_fish_shaped_bobber-line.png",
    "color": "/drawing/assets/k_k06_fish_shaped_bobber-color.png"
  },
  {
    "id": "k_k07_dried_paint_palette",
    "name": "干颜料盘",
    "line": "/drawing/assets/k_k07_dried_paint_palette-line.png",
    "color": "/drawing/assets/k_k07_dried_paint_palette-color.png"
  },
  {
    "id": "k_k08_burnt_candle_stub",
    "name": "蜡烛头",
    "line": "/drawing/assets/k_k08_burnt_candle_stub-line.png",
    "color": "/drawing/assets/k_k08_burnt_candle_stub-color.png"
  },
  {
    "id": "k_r01_yellow_rubber_duck",
    "name": "小黄鸭",
    "line": "/drawing/assets/k_r01_yellow_rubber_duck-line.png",
    "color": "/drawing/assets/k_r01_yellow_rubber_duck-color.png"
  },
  {
    "id": "k_r02_bicycle_bell",
    "name": "自行车铃",
    "line": "/drawing/assets/k_r02_bicycle_bell-line.png",
    "color": "/drawing/assets/k_r02_bicycle_bell-color.png"
  },
  {
    "id": "k_r04_traditional_shuttlecock",
    "name": "毽子",
    "line": "/drawing/assets/k_r04_traditional_shuttlecock-line.png",
    "color": "/drawing/assets/k_r04_traditional_shuttlecock-color.png"
  },
  {
    "id": "k_r05_vintage_moon_rabbit_newspaper",
    "name": "玉兔旧报纸",
    "line": "/drawing/assets/k_r05_vintage_moon_rabbit_newspaper-line.png",
    "color": "/drawing/assets/k_r05_vintage_moon_rabbit_newspaper-color.png"
  },
  {
    "id": "k_r06_incense_and_holder",
    "name": "香与香插",
    "line": "/drawing/assets/k_r06_incense_and_holder-line.png",
    "color": "/drawing/assets/k_r06_incense_and_holder-color.png"
  },
  {
    "id": "k_r07_rabbit_silhouette_badge",
    "name": "兔子剪影徽章",
    "line": "/drawing/assets/k_r07_rabbit_silhouette_badge-line.png",
    "color": "/drawing/assets/k_r07_rabbit_silhouette_badge-color.png"
  },
  {
    "id": "k_r08_blue_paw_print_card",
    "name": "蓝爪印卡",
    "line": "/drawing/assets/k_r08_blue_paw_print_card-line.png",
    "color": "/drawing/assets/k_r08_blue_paw_print_card-color.png"
  },
  {
    "id": "old_wooden_button",
    "name": "旧木纽扣",
    "line": "/drawing/assets/old_wooden_button-line.png",
    "color": "/drawing/assets/old_wooden_button-color.png"
  },
  {
    "id": "rainbow_carwash_cloth",
    "name": "彩虹洗车布",
    "line": "/drawing/assets/rainbow_carwash_cloth-line.png",
    "color": "/drawing/assets/rainbow_carwash_cloth-color.png"
  },
  {
    "id": "rainforest_seed_pod",
    "name": "雨林种子荚",
    "line": "/drawing/assets/rainforest_seed_pod-line.png",
    "color": "/drawing/assets/rainforest_seed_pod-color.png"
  },
  {
    "id": "red_order_clip",
    "name": "红色单据夹",
    "line": "/drawing/assets/red_order_clip-line.png",
    "color": "/drawing/assets/red_order_clip-color.png"
  },
  {
    "id": "retained_sugar_painting_ladle",
    "name": "糖画勺",
    "line": "/drawing/assets/retained_sugar_painting_ladle-line.png",
    "color": "/drawing/assets/retained_sugar_painting_ladle-color.png"
  },
  {
    "id": "short_pencil",
    "name": "短铅笔",
    "line": "/drawing/assets/short_pencil-line.png",
    "color": "/drawing/assets/short_pencil-color.png"
  },
  {
    "id": "small_wood_rattle",
    "name": "小木摇铃",
    "line": "/drawing/assets/small_wood_rattle-line.png",
    "color": "/drawing/assets/small_wood_rattle-color.png"
  }
].map(Object.freeze));

function getReference(id) { return REFERENCES.find(item => item.id === id) || null; }
module.exports = { REFERENCES, getReference };
