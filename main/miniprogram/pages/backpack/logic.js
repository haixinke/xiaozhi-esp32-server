// 道具页面纯逻辑：无微信/网络依赖，可在 Node 下用 assert 单测。
// 与后端 ItemSkuVO / UserItemVO / OrderVO 字段对齐。

var CATEGORY_ORDER = ['consumable_change', 'voice_quota', 'outfit', 'intimacy'];
var CATEGORY_LABEL = {
  consumable_change: '变更券',
  voice_quota: '声音',
  outfit: '外观',
  intimacy: '亲密度礼物'
};

function effectivePriceFen(sku) {
  var promo = sku.promoPriceFen;
  if (promo != null && promo < sku.priceFen) return promo;
  return sku.priceFen;
}

function hasPromo(sku) {
  return sku.promoPriceFen != null && sku.promoPriceFen < sku.priceFen;
}

// 合并目录(skus) 与 库存(inventory)，回填 remainCount，按 [分类顺序, sort] 排序
function mergeInventory(skus, inventory) {
  var invMap = {};
  (inventory || []).forEach(function (it) { invMap[it.skuCode] = it; });
  var items = (skus || []).map(function (sku) {
    var inv = invMap[sku.skuCode];
    var remain = inv ? (inv.remainCount || 0) : 0;
    return {
      id: sku.id,
      skuCode: sku.skuCode,
      skuName: sku.skuName,
      category: sku.category,
      description: sku.description || '',
      iconUrl: sku.iconUrl || '',
      attributes: sku.attributes || '',
      sort: sku.sort || 0,
      remainCount: remain,
      priceFen: sku.priceFen,
      promoPriceFen: sku.promoPriceFen,
      effectivePriceFen: effectivePriceFen(sku),
      hasPromo: hasPromo(sku)
    };
  });
  items.sort(function (a, b) {
    var ca = CATEGORY_ORDER.indexOf(a.category);
    var cb = CATEGORY_ORDER.indexOf(b.category);
    if (ca !== cb) return ca - cb;
    return (a.sort || 0) - (b.sort || 0);
  });
  return items;
}

// 按 CATEGORY_ORDER 分组，仅保留非空分组
function groupByCategory(items) {
  var groups = [];
  CATEGORY_ORDER.forEach(function (cat) {
    var list = items.filter(function (it) { return it.category === cat; });
    if (list.length) groups.push({ category: cat, label: CATEGORY_LABEL[cat], items: list });
  });
  return groups;
}

// 卡片视图：徽标类型 + CTA
function cardView(item) {
  if (item.category === 'outfit' && item.remainCount > 0) {
    return { badgeType: 'unlocked', badgeText: '已解锁', cta: 'go-equip' };
  }
  if (item.remainCount > 0) {
    return { badgeType: 'owned', badgeText: '拥有 ×' + item.remainCount, cta: 'buy' };
  }
  return { badgeType: 'none', badgeText: '', cta: 'buy' };
}

// 数量规则：券/外观固定 1；声音额度、礼物可步进
function quantityRule(category) {
  if (category === 'voice_quota') return { stepper: true, min: 1, max: 9, defaultQty: 1 };
  if (category === 'intimacy') return { stepper: true, min: 1, max: 99, defaultQty: 1 };
  return { stepper: false, min: 1, max: 1, defaultQty: 1 };
}

// 订单查询结果归类（用于轮询决策）：2=已履约 / 3,4,5=失败 / 0,1=进行中
function orderTerminal(status) {
  if (status === 2) return 'fulfilled';
  if (status === 3 || status === 4 || status === 5) return 'failed';
  return 'pending';
}

// 概览条 chips：仅 remainCount>0
function deriveChips(items) {
  return items
    .filter(function (it) { return it.remainCount > 0; })
    .map(function (it) {
      return {
        skuCode: it.skuCode,
        skuName: it.skuName,
        count: it.remainCount,
        unlocked: it.category === 'outfit'
      };
    });
}

module.exports = {
  CATEGORY_ORDER: CATEGORY_ORDER,
  CATEGORY_LABEL: CATEGORY_LABEL,
  effectivePriceFen: effectivePriceFen,
  hasPromo: hasPromo,
  mergeInventory: mergeInventory,
  groupByCategory: groupByCategory,
  cardView: cardView,
  quantityRule: quantityRule,
  orderTerminal: orderTerminal,
  deriveChips: deriveChips
};
