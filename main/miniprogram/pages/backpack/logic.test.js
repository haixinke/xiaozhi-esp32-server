const assert = require('assert');
const L = require('./logic');

(function () {
  // effectivePriceFen / hasPromo
  assert.strictEqual(L.effectivePriceFen({ priceFen: 9900, promoPriceFen: null }), 9900);
  assert.strictEqual(L.effectivePriceFen({ priceFen: 1200, promoPriceFen: 900 }), 900);
  assert.strictEqual(L.effectivePriceFen({ priceFen: 900, promoPriceFen: 1200 }), 900); // promo>=price 不生效
  assert.strictEqual(L.hasPromo({ priceFen: 1200, promoPriceFen: 900 }), true);
  assert.strictEqual(L.hasPromo({ priceFen: 9900, promoPriceFen: null }), false);

  // mergeInventory：remainCount 回填 + 排序（分类顺序优先，再 sort）
  var skus = [
    { id: 1, skuCode: 'rose', skuName: '玫瑰花', category: 'intimacy', priceFen: 600, promoPriceFen: null, sort: 1, description: '赠送' },
    { id: 2, skuCode: 'occupation_change', skuName: '换职业券', category: 'consumable_change', priceFen: 1800, promoPriceFen: null, sort: 1, description: '换职业' },
    { id: 3, skuCode: 'voice_change', skuName: '换声音券', category: 'consumable_change', priceFen: 12900, promoPriceFen: 9900, sort: 2, description: '换声音' }
  ];
  var inv = [{ skuCode: 'occupation_change', remainCount: 3 }];
  var items = L.mergeInventory(skus, inv);
  assert.strictEqual(items.length, 3);
  assert.strictEqual(items[0].skuCode, 'occupation_change'); // consumable_change 排在前
  assert.strictEqual(items[0].remainCount, 3);
  assert.strictEqual(items[1].skuCode, 'voice_change');
  assert.strictEqual(items[1].remainCount, 0); // 无库存
  assert.strictEqual(items[1].effectivePriceFen, 9900);
  assert.strictEqual(items[1].hasPromo, true);
  assert.strictEqual(items[2].skuCode, 'rose'); // intimacy 最后

  // groupByCategory：仅非空分组，按 CATEGORY_ORDER
  var groups = L.groupByCategory(items);
  assert.strictEqual(groups.length, 2);
  assert.strictEqual(groups[0].category, 'consumable_change');
  assert.strictEqual(groups[0].label, '变更券');
  assert.strictEqual(groups[0].items.length, 2);
  assert.strictEqual(groups[1].label, '亲密度礼物');

  // cardView
  var outfitOwned = { category: 'outfit', remainCount: 1 };
  assert.deepStrictEqual(L.cardView(outfitOwned), { badgeType: 'unlocked', badgeText: '已解锁', cta: 'go-equip' });
  var owned = { category: 'consumable_change', remainCount: 3 };
  assert.deepStrictEqual(L.cardView(owned), { badgeType: 'owned', badgeText: '拥有 ×3', cta: 'buy' });
  var fresh = { category: 'intimacy', remainCount: 0 };
  assert.deepStrictEqual(L.cardView(fresh), { badgeType: 'none', badgeText: '', cta: 'buy' });

  // quantityRule
  assert.strictEqual(L.quantityRule('voice_quota').stepper, true);
  assert.strictEqual(L.quantityRule('voice_quota').max, 9);
  assert.strictEqual(L.quantityRule('intimacy').max, 99);
  assert.strictEqual(L.quantityRule('consumable_change').stepper, false);
  assert.strictEqual(L.quantityRule('outfit').stepper, false);

  // orderTerminal
  assert.strictEqual(L.orderTerminal(2), 'fulfilled');
  assert.strictEqual(L.orderTerminal(1), 'pending');
  assert.strictEqual(L.orderTerminal(0), 'pending');
  assert.strictEqual(L.orderTerminal(3), 'failed');
  assert.strictEqual(L.orderTerminal(5), 'failed');

  // deriveChips：仅 remainCount>0；outfit 标记 unlocked
  var chips = L.deriveChips([
    { skuCode: 'occupation_change', skuName: '换职业券', category: 'consumable_change', remainCount: 3 },
    { skuCode: 'rose', skuName: '玫瑰花', category: 'intimacy', remainCount: 0 },
    { skuCode: 'dress', skuName: '连衣裙', category: 'outfit', remainCount: 1 }
  ]);
  assert.strictEqual(chips.length, 2);
  assert.strictEqual(chips[0].count, 3);
  assert.strictEqual(chips[0].unlocked, false);
  assert.strictEqual(chips[1].unlocked, true);

  console.log('logic.test.js: ALL PASS');
})();
