# 「我的背包」道具页面 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在「完美女友」小程序新建 `pages/backpack` 二级页面，展示全部有效道具 + 用户持有量 + 购买能力，复用已实现的后端 `/item` 与 `/payment` 接口。

**Architecture:** 纯展示/交易逻辑拆分到无微信依赖的 `logic.js`（Node 可单测）；页面 `backpack.js` 只做请求 + 编排 + `setData`；WXML/WXSS 沿用 Ethereal Companion 设计系统。目录与库存在前端按 `skuCode` 合并；购买复用 `settings.js` 已验证的下单→支付→轮询履约链路（含 mock 模式）。

**Tech Stack:** 微信小程序（WXML/WXSS/JS，CommonJS），`utils/request.js`（自动 Bearer token + 401 重试），Node `assert` 做纯逻辑单测（零依赖）。

**Spec:** `docs/superpowers/specs/2026-06-15-backpack-items-page-design.md`

---

## 文件结构

| 文件 | 职责 | 新建/修改 |
|---|---|---|
| `main/miniprogram/pages/backpack/logic.js` | 纯逻辑：合并、价格、卡片视图、数量规则、轮询归类、分类映射 | 新建 |
| `main/miniprogram/pages/backpack/logic.test.js` | `logic.js` 的 Node `assert` 单测 | 新建 |
| `main/miniprogram/pages/backpack/backpack.js` | 页面逻辑：加载/编排/购买/轮询 | 新建 |
| `main/miniprogram/pages/backpack/backpack.wxml` | 页面结构 + 购买面板 | 新建 |
| `main/miniprogram/pages/backpack/backpack.wxss` | 样式（含暗色） | 新建 |
| `main/miniprogram/pages/backpack/backpack.json` | 页面配置 | 新建 |
| `main/miniprogram/app.json` | `pages` 数组注册新页 | 修改 |
| `main/miniprogram/pages/settings/settings.js` | `onBackpackTap` 改为 `navigateTo` | 修改（424-426 行） |

后端 **零改动**（`item` / `payment` 模块已实现，`listSkus` 已过滤 `status=1`）。

---

## Task 1: 纯逻辑模块（TDD）

**Files:**
- Create: `main/miniprogram/pages/backpack/logic.js`
- Test: `main/miniprogram/pages/backpack/logic.test.js`

- [ ] **Step 1: 写失败测试**

创建 `main/miniprogram/pages/backpack/logic.test.js`：

```js
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
  assert.strictEqual(groups[0].label, '重塑命运');
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
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `node main/miniprogram/pages/backpack/logic.test.js`
Expected: 报错 `Cannot find module './logic'`

- [ ] **Step 3: 实现 logic.js**

创建 `main/miniprogram/pages/backpack/logic.js`：

```js
// 道具页面纯逻辑：无微信/网络依赖，可在 Node 下用 assert 单测。
// 与后端 ItemSkuVO / UserItemVO / OrderVO 字段对齐。

var CATEGORY_ORDER = ['consumable_change', 'voice_quota', 'outfit', 'intimacy'];
var CATEGORY_LABEL = {
  consumable_change: '重塑命运',
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
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `node main/miniprogram/pages/backpack/logic.test.js`
Expected: `logic.test.js: ALL PASS`

- [ ] **Step 5: 提交**

```bash
git add main/miniprogram/pages/backpack/logic.js main/miniprogram/pages/backpack/logic.test.js
git commit -m "feat(miniprogram): add backpack pure logic + node tests"
```

---

## Task 2: 页面骨架 + 注册 + 入口

让「我 → 我的背包」能跳转到一个空白页（原生导航栏带返回）。

**Files:**
- Create: `main/miniprogram/pages/backpack/backpack.json`
- Create: `main/miniprogram/pages/backpack/backpack.js`
- Create: `main/miniprogram/pages/backpack/backpack.wxml`
- Create: `main/miniprogram/pages/backpack/backpack.wxss`
- Modify: `main/miniprogram/app.json`（pages 数组）
- Modify: `main/miniprogram/pages/settings/settings.js:424-426`（`onBackpackTap`）

- [ ] **Step 1: 创建 backpack.json**

`main/miniprogram/pages/backpack/backpack.json`：

```json
{
  "navigationStyle": "default",
  "navigationBarTitleText": "我的背包",
  "usingComponents": {}
}
```

- [ ] **Step 2: 创建最小 backpack.js**

`main/miniprogram/pages/backpack/backpack.js`：

```js
const { getTheme, applyTheme } = require('../../utils/theme');

Page({
  data: {
    darkMode: getTheme(),
    loading: false,
    error: false,
    empty: false,
    groups: [],
    chips: [],
    allItems: []
  },
  onLoad() {
    applyTheme(this);
  },
  onShow() {
    applyTheme(this);
  }
});
```

- [ ] **Step 3: 创建最小 backpack.wxml**

`main/miniprogram/pages/backpack/backpack.wxml`：

```html
<view class="bp-container {{darkMode ? 'dark' : ''}}">
  <view class="bp-placeholder">我的背包（建设中）</view>
</view>
```

- [ ] **Step 4: 创建最小 backpack.wxss**

`main/miniprogram/pages/backpack/backpack.wxss`：

```css
.bp-container { min-height: 100vh; background: #f6f3f2; padding: 40rpx 32rpx 60rpx; }
.bp-container.dark { background: #121220; }
.bp-placeholder { text-align: center; color: #8a7a7e; padding-top: 200rpx; }
.dark .bp-placeholder { color: #6a6468; }
```

- [ ] **Step 5: 注册到 app.json**

修改 `main/miniprogram/app.json`，在 `pages` 数组中追加一行（紧跟在 `"pages/index/index"` 之后）：

```json
"pages/backpack/backpack",
```

- [ ] **Step 6: 接入入口 onBackpackTap**

修改 `main/miniprogram/pages/settings/settings.js`，把 424-426 行：

```js
  onBackpackTap() {
    wx.showToast({ title: '即将上线', icon: 'none', duration: 1500 });
  },
```

替换为：

```js
  onBackpackTap() {
    wx.navigateTo({ url: '/pages/backpack/backpack' });
  },
```

- [ ] **Step 7: 手动验证**

在微信开发者工具编译运行 → 进入「我」tab → 点「我的背包」。
Expected: 跳转到新页，原生导航栏显示「我的背包 ‹」，页面中央显示「我的背包（建设中）」，返回键可回到「我」。

- [ ] **Step 8: 提交**

```bash
git add main/miniprogram/pages/backpack/backpack.json main/miniprogram/pages/backpack/backpack.js main/miniprogram/pages/backpack/backpack.wxml main/miniprogram/pages/backpack/backpack.wxss main/miniprogram/app.json main/miniprogram/pages/settings/settings.js
git commit -m "feat(miniprogram): scaffold backpack page + wire entry"
```

---

## Task 3: 加载并渲染分类目录

并行拉 `/item/skus` + `/item/inventory`，合并、装饰、分组、渲染卡片（先不含购买面板交互）。

**Files:**
- Modify: `main/miniprogram/pages/backpack/backpack.js`
- Modify: `main/miniprogram/pages/backpack/backpack.wxml`
- Modify: `main/miniprogram/pages/backpack/backpack.wxss`

- [ ] **Step 1: 改写 backpack.js（加载 + 装饰 + 分组）**

完整替换 `main/miniprogram/pages/backpack/backpack.js`：

```js
const { getTheme, applyTheme } = require('../../utils/theme');
const { get } = require('../../utils/request');
const logic = require('./logic');

var ICON_BY_SKU = {
  occupation_change: '👘', soul_quirk_change: '🌀', voice_change: '🎤',
  voice_clone_quota: '🎙️',
  rose: '🌹', milktea: '🥤', diamond_ring: '💎'
};
var ICON_BY_CATEGORY = {
  consumable_change: '🎟️', voice_quota: '🎙️', outfit: '👗', intimacy: '🎁'
};

Page({
  data: {
    darkMode: getTheme(),
    loading: false,
    error: false,
    empty: false,
    groups: [],
    chips: [],
    allItems: []
  },

  onLoad() {
    applyTheme(this);
    this.loadAll();
  },
  onShow() {
    applyTheme(this);
  },

  // 分 → 元；整元不显示小数
  _yuan(fen) {
    var y = (fen || 0) / 100;
    return (y % 1 === 0) ? String(y) : y.toFixed(2);
  },

  _emoji(sku) {
    return ICON_BY_SKU[sku.skuCode] || ICON_BY_CATEGORY[sku.category] || '🎁';
  },

  // 把合并后的逻辑项装饰成渲染项
  _decorate(items) {
    var self = this;
    return items.map(function (it) {
      var view = logic.cardView(it);
      return Object.assign({}, it, {
        iconEmoji: self._emoji(it),
        priceYuan: self._yuan(it.effectivePriceFen),
        origYuan: it.hasPromo ? self._yuan(it.priceFen) : '',
        badgeType: view.badgeType,
        badgeText: view.badgeText,
        cta: view.cta
      });
    });
  },

  async loadAll() {
    this.setData({ loading: true, error: false });
    try {
      var results = await Promise.all([get('/item/skus'), get('/item/inventory')]);
      var skusRes = results[0];
      var invRes = results[1];
      var skus = (skusRes && skusRes.code === 0 && skusRes.data) ? skusRes.data : [];
      var inventory = (invRes && invRes.code === 0 && invRes.data) ? invRes.data : [];
      var merged = logic.mergeInventory(skus, inventory);
      var decorated = this._decorate(merged);
      this.setData({
        allItems: decorated,
        groups: logic.groupByCategory(decorated),
        chips: logic.deriveChips(merged),
        empty: decorated.length === 0,
        loading: false
      });
    } catch (err) {
      console.warn('[backpack] load failed:', err);
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadAll();
  }
});
```

- [ ] **Step 2: 改写 backpack.wxml（分类目录）**

完整替换 `main/miniprogram/pages/backpack/backpack.wxml`：

```html
<view class="bp-container {{darkMode ? 'dark' : ''}}">

  <!-- 我的持有概览 -->
  <view class="bp-holder" wx:if="{{chips.length}}">
    <view class="bp-holder-label">我 的 持 有</view>
    <view class="bp-chips">
      <view class="bp-chip" wx:for="{{chips}}" wx:key="skuCode">
        {{item.skuName}}<text wx:if="{{!item.unlocked}}"> ×{{item.count}}</text><text wx:else> ✓</text>
      </view>
    </view>
  </view>

  <!-- 加载骨架 -->
  <block wx:if="{{loading}}">
    <view class="bp-skeleton" wx:for="{{[1,2,3,4]}}" wx:key="*this"></view>
  </block>

  <!-- 错误 -->
  <view class="bp-state" wx:elif="{{error}}">
    <text class="bp-state-text">加载失败</text>
    <view class="bp-retry" bindtap="onRetry">重试</view>
  </view>

  <!-- 目录空 -->
  <view class="bp-state" wx:elif="{{empty}}">
    <text class="bp-state-text">道具马上就来</text>
  </view>

  <!-- 分类目录 -->
  <block wx:else>
    <view class="bp-cat" wx:for="{{groups}}" wx:for-item="group" wx:key="category">
      <view class="bp-cat-title"><view class="bp-cat-dot"></view>{{group.label}}</view>
      <view class="bp-item" wx:for="{{group.items}}" wx:for-item="sku" wx:key="skuCode">
        <view class="bp-ico bp-ico-{{sku.category}}">{{sku.iconEmoji}}</view>
        <view class="bp-meta">
          <view class="bp-nm">{{sku.skuName}}</view>
          <view class="bp-ds">{{sku.description}}</view>
        </view>
        <view class="bp-right">
          <view class="bp-badge bp-badge-{{sku.badgeType}}" wx:if="{{sku.badgeType !== 'none'}}">{{sku.badgeText}}</view>
          <view class="bp-px" wx:if="{{sku.cta === 'buy'}}">¥{{sku.priceYuan}}<text class="bp-px-orig" wx:if="{{sku.hasPromo}}"> ¥{{sku.origYuan}}</text></view>
          <view class="bp-cta {{sku.cta === 'go-equip' ? 'bp-cta-ghost' : ''}}" data-sku="{{sku.skuCode}}" bindtap="onCardTap">{{sku.cta === 'go-equip' ? '去换装' : '购买'}}</view>
        </view>
      </view>
    </view>
  </block>

</view>
```

- [ ] **Step 3: 改写 backpack.wxss（卡片样式 + 暗色）**

完整替换 `main/miniprogram/pages/backpack/backpack.wxss`：

```css
/* 我的背包 - Ethereal Companion 设计系统 */
.bp-container { min-height: 100vh; background: #f6f3f2; padding: 32rpx 28rpx 80rpx; }

/* 持有概览（金色身份） */
.bp-holder {
  background: linear-gradient(135deg, rgba(184,134,11,.10), rgba(212,115,122,.06));
  border: 1rpx solid rgba(184,134,11,.18);
  border-radius: 24rpx; padding: 28rpx 30rpx; margin-bottom: 28rpx;
}
.bp-holder-label { font-size: 22rpx; color: #b8860b; letter-spacing: 4rpx; font-weight: 600; margin-bottom: 16rpx; }
.bp-chips { display: flex; flex-wrap: wrap; gap: 14rpx; }
.bp-chip { font-size: 24rpx; color: #8a6a35; background: rgba(255,255,255,.7); border: 1rpx solid rgba(184,134,11,.2); border-radius: 24rpx; padding: 8rpx 22rpx; }

/* 分类标题（宋体） */
.bp-cat { margin-bottom: 12rpx; }
.bp-cat-title {
  font-family: "Songti SC","STSong","Noto Serif CJK SC",serif;
  font-size: 30rpx; color: #864e5a; letter-spacing: 4rpx; font-weight: 600;
  display: flex; align-items: center; gap: 14rpx; margin: 28rpx 8rpx 18rpx;
}
.bp-cat-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #d4737a; }

/* 道具卡片（瓷白玻璃态） */
.bp-item {
  display: flex; align-items: center; gap: 22rpx;
  background: rgba(255,255,255,.78); backdrop-filter: blur(40rpx); -webkit-backdrop-filter: blur(40rpx);
  border: 1rpx solid rgba(255,255,255,.85); border-radius: 26rpx;
  box-shadow: 0 4rpx 16rpx rgba(134,78,90,.07);
  padding: 24rpx 26rpx; margin-bottom: 18rpx;
}
.bp-ico { width: 76rpx; height: 76rpx; border-radius: 22rpx; flex: 0 0 76rpx;
  display: flex; align-items: center; justify-content: center; font-size: 38rpx; }
.bp-ico-consumable_change { background: linear-gradient(135deg, rgba(134,78,90,.12), rgba(134,78,90,.04)); }
.bp-ico-voice_quota { background: linear-gradient(135deg, rgba(122,111,138,.14), rgba(122,111,138,.04)); }
.bp-ico-outfit { background: linear-gradient(135deg, rgba(212,115,122,.14), rgba(212,115,122,.04)); }
.bp-ico-intimacy { background: linear-gradient(135deg, rgba(184,134,11,.14), rgba(184,134,11,.04)); }
.bp-meta { flex: 1; min-width: 0; }
.bp-nm { font-size: 30rpx; color: #1b1c1c; font-weight: 600; }
.bp-ds { font-size: 22rpx; color: #8a7a7e; margin-top: 4rpx; line-height: 1.4; }
.bp-right { display: flex; flex-direction: column; align-items: flex-end; gap: 10rpx; }

.bp-badge { font-size: 20rpx; padding: 4rpx 14rpx; border-radius: 16rpx; font-weight: 600; }
.bp-badge-owned { color: #b8860b; background: rgba(184,134,11,.12); }
.bp-badge-unlocked { color: #5a8a4e; background: rgba(90,138,78,.12); }
.bp-px { font-size: 28rpx; color: #864e5a; font-weight: 700; }
.bp-px-orig { color: #c2b4a2; font-weight: 400; font-size: 22rpx; text-decoration: line-through; }
.bp-cta { font-size: 24rpx; color: #fff; background: linear-gradient(135deg, #864e5a, #d4737a);
  border-radius: 28rpx; padding: 12rpx 30rpx; box-shadow: 0 6rpx 18rpx rgba(134,78,90,.22); }
.bp-cta:active { opacity: .85; }
.bp-cta-ghost { background: #fff; color: #b8860b; border: 1rpx solid #ecd9b0; box-shadow: none; }

/* 骨架 / 状态 */
.bp-skeleton { height: 130rpx; background: rgba(255,255,255,.6); border-radius: 26rpx; margin-bottom: 18rpx; }
.bp-state { text-align: center; padding: 200rpx 40rpx; }
.bp-state-text { display: block; font-size: 28rpx; color: #8a7a7e; margin-bottom: 28rpx; }
.bp-retry { display: inline-block; font-size: 26rpx; color: #fff; background: linear-gradient(135deg, #864e5a, #d4737a); border-radius: 30rpx; padding: 14rpx 48rpx; }

/* ===== 暗色模式 ===== */
.bp-container.dark { background: #121220; }
.dark .bp-holder { background: linear-gradient(135deg, rgba(184,134,11,.15), rgba(212,115,122,.08)); border-color: rgba(184,134,11,.25); }
.dark .bp-holder-label { color: #daa520; }
.dark .bp-chip { background: rgba(30,28,46,.7); color: #daa520; border-color: rgba(184,134,11,.3); }
.dark .bp-cat-title { color: #d4737a; }
.dark .bp-item { background: rgba(30,28,46,.85); border-color: rgba(134,78,90,.15); box-shadow: 0 4rpx 16rpx rgba(0,0,0,.15); }
.dark .bp-nm { color: #e8e4e3; }
.dark .bp-ds { color: #6a6468; }
.dark .bp-badge-owned { color: #daa520; background: rgba(184,134,11,.18); }
.dark .bp-badge-unlocked { color: #7ab86e; background: rgba(90,138,78,.15); }
.dark .bp-px { color: #d4737a; }
.dark .bp-px-orig { color: #6a6468; }
.dark .bp-cta-ghost { background: rgba(30,28,46,.85); color: #daa520; border-color: rgba(184,134,11,.3); }
.dark .bp-skeleton { background: rgba(30,28,46,.6); }
.dark .bp-state-text { color: #6a6468; }
```

- [ ] **Step 4: 手动验证**

编译运行 → 进入「我的背包」（确保后端 `manager-api` 已起，且有上架的 SKU 种子数据）。
Expected: 顶部金色「我的持有」chips（若用户有库存）；下方按 重塑命运/声音/外观/亲密度礼物 分段，每张卡片显示 emoji 图标、名称、说明、价格、持有徽标、购买按钮；促销价有划线原价。

- [ ] **Step 5: 提交**

```bash
git add main/miniprogram/pages/backpack/backpack.js main/miniprogram/pages/backpack/backpack.wxml main/miniprogram/pages/backpack/backpack.wxss
git commit -m "feat(miniprogram): load and render grouped item catalog"
```

---

## Task 4: 购买确认面板（UI + 数量步进）

卡片点击打开底部面板；数量按 `quantityRule` 控制；合计随数量变化。本任务只做面板 UI，不含真实下单（Task 5）。

**Files:**
- Modify: `main/miniprogram/pages/backpack/backpack.js`
- Modify: `main/miniprogram/pages/backpack/backpack.wxml`
- Modify: `main/miniprogram/pages/backpack/backpack.wxss`

- [ ] **Step 1: backpack.js 增加面板状态与处理函数**

在 `backpack.js` 的 `data` 中追加字段：

```js
    showSheet: false,
    sheetItem: null,
    sheetRule: null,
    sheetQty: 1,
    sheetUnitYuan: '',
    sheetTotalYuan: '',
    paying: false
```

在 `onRetry()` 之后追加方法：

```js
  onCardTap(e) {
    var skuCode = e.currentTarget.dataset.sku;
    var item = (this.data.allItems || []).filter(function (it) { return it.skuCode === skuCode; })[0];
    if (!item) return;
    if (item.cta === 'go-equip') {
      wx.showToast({ title: '换装功能即将上线', icon: 'none', duration: 1500 });
      return;
    }
    var rule = logic.quantityRule(item.category);
    var qty = rule.defaultQty;
    this.setData({
      showSheet: true,
      sheetItem: item,
      sheetRule: rule,
      sheetQty: qty,
      sheetUnitYuan: this._yuan(item.effectivePriceFen),
      sheetTotalYuan: this._yuan(item.effectivePriceFen * qty)
    });
  },

  _changeQty(q) {
    var rule = this.data.sheetRule;
    if (!rule || !rule.stepper) return;
    q = Math.max(rule.min, Math.min(rule.max, q));
    var unit = this.data.sheetItem.effectivePriceFen;
    this.setData({ sheetQty: q, sheetTotalYuan: this._yuan(unit * q) });
  },
  onQtyInc() { this._changeQty((this.data.sheetQty || 1) + 1); },
  onQtyDec() { this._changeQty((this.data.sheetQty || 1) - 1); },

  onSheetOverlayTap() {
    if (this.data.paying) return; // 支付中禁止关闭
    this.setData({ showSheet: false });
  },
  onSheetPanelTap() { /* 阻止冒泡 */ }
```

- [ ] **Step 2: backpack.wxml 追加购买面板**

在 `backpack.wxml` 的 `</view>`（最外层 `bp-container` 闭合）之前插入：

```html
  <!-- 购买确认面板 -->
  <view class="bp-overlay {{showSheet ? 'bp-overlay-show' : ''}}" bindtap="onSheetOverlayTap">
    <view class="bp-sheet" catchtap="onSheetPanelTap" wx:if="{{sheetItem}}">
      <view class="bp-grab"></view>
      <view class="bp-romantic">想给她一点小惊喜吗？</view>
      <view class="bp-sheet-row">
        <view class="bp-ico bp-ico-{{sheetItem.category}}">{{sheetItem.iconEmoji}}</view>
        <view class="bp-meta">
          <view class="bp-nm">{{sheetItem.skuName}}</view>
          <view class="bp-ds">{{sheetItem.description}}</view>
        </view>
      </view>
      <view class="bp-qty" wx:if="{{sheetRule.stepper}}">
        <text class="bp-qty-label">数量</text>
        <view class="bp-step">
          <view class="bp-step-btn {{sheetQty <= sheetRule.min ? 'bp-step-disabled' : ''}}" bindtap="onQtyDec">−</view>
          <view class="bp-step-n">{{sheetQty}}</view>
          <view class="bp-step-btn {{sheetQty >= sheetRule.max ? 'bp-step-disabled' : ''}}" bindtap="onQtyInc">+</view>
        </view>
      </view>
      <view class="bp-sum">
        <text>合计（¥{{sheetUnitYuan}} × {{sheetQty}}）</text>
        <text class="bp-sum-a">¥{{sheetTotalYuan}}</text>
      </view>
      <button class="bp-pay" bindtap="onPay">微信支付 ¥{{sheetTotalYuan}}</button>
      <view class="bp-sheet-note" wx:if="{{sheetItem.category === 'intimacy'}}">亲密度效果将在后续版本生效</view>
      <view class="bp-sheet-note" wx:else>券类道具数量固定为 1</view>
    </view>
  </view>
```

- [ ] **Step 3: backpack.wxss 追加面板样式**

在 `backpack.wxss` 末尾（暗色段之前）追加：

```css
/* 购买确认面板（复用契约浮窗动效） */
.bp-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,.4); z-index: 100;
  opacity: 0; visibility: hidden; transition: opacity .3s ease, visibility .3s ease;
}
.bp-overlay-show { opacity: 1; visibility: visible; }
.bp-sheet {
  position: absolute; bottom: 0; left: 0; right: 0; background: #fbf9f8;
  border-radius: 40rpx 40rpx 0 0; padding: 40rpx 36rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom, 24rpx));
  transform: translateY(100%); transition: transform .3s cubic-bezier(0.16, 1, 0.3, 1);
}
.bp-overlay-show .bp-sheet { transform: translateY(0); }
.bp-grab { width: 64rpx; height: 8rpx; background: #e7ddcf; border-radius: 4rpx; margin: 0 auto 28rpx; }
.bp-romantic { text-align: center; font-size: 28rpx; color: #514345; line-height: 1.6; letter-spacing: 1rpx; margin-bottom: 30rpx; }
.bp-sheet-row { display: flex; align-items: center; gap: 22rpx; background: rgba(134,78,90,.04); border-radius: 24rpx; padding: 24rpx; margin-bottom: 24rpx; }
.bp-qty { display: flex; align-items: center; justify-content: space-between; background: #faf6f2; border-radius: 22rpx; padding: 22rpx 26rpx; margin-bottom: 24rpx; }
.bp-qty-label { font-size: 28rpx; color: #514345; }
.bp-step { display: flex; align-items: center; gap: 32rpx; }
.bp-step-btn { width: 56rpx; height: 56rpx; border-radius: 50%; background: #fff; border: 1rpx solid #e3c9ac; color: #864e5a; display: flex; align-items: center; justify-content: center; font-size: 36rpx; }
.bp-step-disabled { opacity: .35; }
.bp-step-n { font-size: 34rpx; color: #1b1c1c; font-weight: 700; min-width: 40rpx; text-align: center; }
.bp-sum { display: flex; justify-content: space-between; align-items: baseline; padding: 0 6rpx 26rpx; font-size: 26rpx; color: #8a7a7e; }
.bp-sum-a { font-size: 42rpx; color: #864e5a; font-weight: 700; }
.bp-pay { width: 100%; background: linear-gradient(135deg, #864e5a, #d4737a); color: #fff;
  border: none; border-radius: 48rpx; padding: 26rpx; font-size: 32rpx; font-weight: 600; letter-spacing: 4rpx;
  box-shadow: 0 8rpx 24rpx rgba(134,78,90,.28); line-height: 1; }
.bp-pay::after { border: none; }
.bp-pay:active { opacity: .85; }
.bp-sheet-note { text-align: center; font-size: 20rpx; color: #8a7a7e; margin-top: 18rpx; }
```

在暗色段末尾追加面板暗色：

```css
.dark .bp-sheet { background: #1e1c2e; }
.dark .bp-grab { background: #3a3450; }
.dark .bp-romantic { color: #a09a9c; }
.dark .bp-sheet-row { background: rgba(134,78,90,.1); }
.dark .bp-qty { background: rgba(30,28,46,.6); }
.dark .bp-qty-label { color: #a09a9c; }
.dark .bp-step-btn { background: rgba(30,28,46,.8); border-color: rgba(184,134,11,.3); color: #d4737a; }
.dark .bp-step-n { color: #e8e4e3; }
.dark .bp-sum { color: #6a6468; }
.dark .bp-sum-a { color: #d4737a; }
.dark .bp-sheet-note { color: #6a6468; }
```

- [ ] **Step 4: 手动验证**

编译运行 → 进入背包 → 点任意「购买」按钮。
Expected: 底部面板上滑出现；券类不显示数量步进器；礼物/声音额度显示步进器，点 +/− 合计与按钮金额随数量变化，达到 min/max 时按钮变淡；点遮罩或空白处面板关闭；点「微信支付」暂无反应（Task 5 实现）。

- [ ] **Step 5: 提交**

```bash
git add main/miniprogram/pages/backpack/backpack.js main/miniprogram/pages/backpack/backpack.wxml main/miniprogram/pages/backpack/backpack.wxss
git commit -m "feat(miniprogram): add purchase confirm sheet with quantity stepper"
```

---

## Task 5: 购买流程（下单 → 支付 → 轮询履约 → 刷新）

复用 `settings.js` 已验证的下单 / mock 检测 / `wx.requestPayment` / `_waitOrderFulfilled` 模式。

**Files:**
- Modify: `main/miniprogram/pages/backpack/backpack.js`

- [ ] **Step 1: 在 backpack.js 顶部 require post**

把第 2 行的 require 改为同时引入 `post`：

```js
const { get, post } = require('../../utils/request');
```

- [ ] **Step 2: 追加 onPay / _waitOrderFulfilled / refreshInventory**

在 `onSheetPanelTap()` 之后追加：

```js
  async onPay() {
    if (this.data.paying) return;
    var item = this.data.sheetItem;
    var qty = this.data.sheetQty || 1;
    if (!item) return;
    this.setData({ paying: true });
    wx.showLoading({ title: '正在下单', mask: true });
    try {
      // 1. 下单（金额由服务端按 SKU 计算，前端不传 amount）
      var orderRes = await post('/payment/order', {
        productType: 'ITEM',
        productRefId: item.id,
        quantity: qty
      });
      if (!orderRes || orderRes.code !== 0 || !orderRes.data) {
        wx.hideLoading();
        wx.showToast({ title: (orderRes && orderRes.msg) || '下单失败', icon: 'none', duration: 2000 });
        return;
      }
      var order = orderRes.data;
      var prepayParams = order.prepayParams || {};

      // 2. Mock 模式（dev profile）：直接走 mock 回调履约
      if (prepayParams.mockNotifyUrl) {
        var notifyRes = await post('/payment/notify/mock', {
          outTradeNo: order.outTradeNo,
          transactionId: 'MOCK_TX_' + Date.now(),
          amountFen: order.amountFen
        });
        if (!notifyRes || notifyRes.code !== 'SUCCESS') {
          wx.hideLoading();
          wx.showToast({ title: '支付失败，请重试', icon: 'none', duration: 2000 });
          return;
        }
      } else {
        // 真实微信支付
        var required = ['timeStamp', 'nonceStr', 'package', 'paySign'];
        var missing = required.filter(function (k) { return !prepayParams[k]; });
        if (missing.length) {
          wx.hideLoading();
          wx.showToast({ title: '支付参数异常，请重试', icon: 'none', duration: 2000 });
          return;
        }
        await new Promise(function (resolve, reject) {
          wx.requestPayment({
            timeStamp: prepayParams.timeStamp,
            nonceStr: prepayParams.nonceStr,
            package: prepayParams.package,
            signType: prepayParams.signType || 'RSA',
            paySign: prepayParams.paySign,
            success: resolve,
            fail: function (err) {
              if (err && err.errMsg && err.errMsg.indexOf('cancel') > -1) {
                reject({ cancelled: true });
              } else {
                reject(err);
              }
            }
          });
        });
      }

      // 3. 轮询订单直到履约完成(FULFILLED=2)
      var fulfilled = await this._waitOrderFulfilled(order.outTradeNo);

      // 4. 关闭面板 + 刷新库存
      this.setData({ showSheet: false });
      await this.refreshInventory();
      wx.hideLoading();
      if (fulfilled) {
        wx.showToast({ title: '购买成功', icon: 'success', duration: 2000 });
      } else {
        wx.showToast({ title: '支付成功，道具稍后到账', icon: 'none', duration: 2000 });
      }
    } catch (err) {
      wx.hideLoading();
      if (err && err.cancelled) {
        wx.showToast({ title: '已取消支付', icon: 'none', duration: 1500 });
      } else {
        console.warn('[backpack] pay failed:', err);
        wx.showToast({ title: '操作失败，请重试', icon: 'none', duration: 2000 });
      }
    } finally {
      this.setData({ paying: false });
    }
  },

  // 轮询订单状态直到 FULFILLED=2（12 次 × 1s）。mock 模式下首次即命中。
  async _waitOrderFulfilled(outTradeNo) {
    var FULFILLED = 2;
    for (var i = 0; i < 12; i++) {
      try {
        var res = await get('/payment/order/' + outTradeNo);
        if (res && res.code === 0 && res.data && res.data.status === FULFILLED) return true;
      } catch (e) {
        // 单次查询失败不中断轮询
      }
      await new Promise(function (r) { setTimeout(r, 1000); });
    }
    console.warn('[backpack] 订单履约轮询超时 outTradeNo=' + outTradeNo);
    return false;
  },

  // 购买成功后仅刷新库存（轻量）
  async refreshInventory() {
    try {
      var invRes = await get('/item/inventory');
      var inventory = (invRes && invRes.code === 0 && invRes.data) ? invRes.data : [];
      var invMap = {};
      inventory.forEach(function (it) { invMap[it.skuCode] = it; });
      var merged = (this.data.allItems || []).map(function (it) {
        var inv = invMap[it.skuCode];
        return Object.assign({}, it, { remainCount: inv ? (inv.remainCount || 0) : 0 });
      });
      // 重新装饰（徽标/CTA 随 remainCount 变化）
      var decorated = this._decorate(merged);
      this.setData({
        allItems: decorated,
        groups: logic.groupByCategory(decorated),
        chips: logic.deriveChips(merged)
      });
    } catch (e) {
      console.warn('[backpack] refresh inventory failed:', e);
    }
  }
```

- [ ] **Step 3: 手动验证（mock 模式）**

前提：后端 `manager-api` 以 dev profile 运行（启用 `MockWechatPayClient`，`prepayParams` 含 `mockNotifyUrl`），且有上架的低价 SKU。
进入背包 → 点某道具「购买」→「微信支付」。
Expected: 显示「正在下单」→ 自动 mock 履约 → 面板关闭 → 该卡片「拥有 ×N」徽标出现/数字 +1 → 顶部 chips 同步更新 → 「购买成功」toast。

- [ ] **Step 4: 手动验证（异常路径）**

- 在微信支付弹窗点「取消」→ toast「已取消支付」，面板保留可重试，库存不变。
- 断开后端再点购买 → toast「操作失败，请重试」。

- [ ] **Step 5: 提交**

```bash
git add main/miniprogram/pages/backpack/backpack.js
git commit -m "feat(miniprogram): implement item purchase flow with fulfillment polling"
```

---

## Task 6: onShow 刷新 + 最终核验

确保从其它页返回时库存保持新鲜；通读对照 spec 验收标准。

**Files:**
- Modify: `main/miniprogram/pages/backpack/backpack.js`

- [ ] **Step 1: onShow 增量刷新库存**

把 `backpack.js` 的 `onShow` 改为（已加载过则只刷库存，避免骨架闪烁）：

```js
  onShow() {
    applyTheme(this);
    if (this.data.allItems && this.data.allItems.length) {
      this.refreshInventory();
    } else if (!this.data.loading) {
      this.loadAll();
    }
  },
```

- [ ] **Step 2: 重新跑纯逻辑单测，确保未回归**

Run: `node main/miniprogram/pages/backpack/logic.test.js`
Expected: `logic.test.js: ALL PASS`

- [ ] **Step 3: 对照 spec 验收标准全量手动核验**

逐条核对 `docs/superpowers/specs/2026-06-15-backpack-items-page-design.md` §14：

- 从「我」→「我的背包」可进入、可返回。
- 全部有效道具按 重塑命运/声音/外观/亲密度礼物 分段、类内按 sort 排序。
- 每张卡片含图标、名称、说明、价格（促销划线）、持有徽标。
- 「我的持有」概览条正确反映 `remainCount>0`。
- 购买：确认面板 → 微信支付(mock) → 履约轮询 → 库存 +N、徽标与 chips 刷新。
- 取消 / 下单失败 / 断网 有对应提示，不卡死。
- 新用户（库存空）页面正常；概览条隐藏或降级文案（当前实现：无 chips 时不显示概览条，符合）。
- 暗色模式样式正确，与「我」页面一致。
- 金额以前端预览展示、以服务端 `amountFen` 实扣。

- [ ] **Step 4: 提交**

```bash
git add main/miniprogram/pages/backpack/backpack.js
git commit -m "feat(miniprogram): refresh inventory on show + final verification"
```

---

## Self-Review

**Spec coverage** — 对照 spec 各节：
- §3 信息架构/分类 → Task 3（groupByCategory + wxml 分段） ✓
- §4 组件（holder/category-section/item-card/purchase-sheet）→ Task 3 + 4 ✓
- §5 数据契约 → Task 1 logic（字段对齐 VO）+ Task 3/5 请求 ✓
- §6.1 加载 → Task 3 `loadAll`（Promise.all + 降级）✓
- §6.2 购买 → Task 5 `onPay`（金额权威性：不传 amount）✓
- §6.3 履约轮询 → Task 5 `_waitOrderFulfilled` ✓
- §7 卡片状态机 → Task 1 `cardView` + Task 3 渲染 ✓
- §8 数量规则/面板/动效/入口 → Task 4 + Task 2 入口 ✓
- §9 空/加载/错误 → Task 3 wxml（loading/error/empty）✓
- §10 视觉 token → Task 3 wxss ✓
- §11 暗色 → Task 3/4 wxss `.dark` ✓
- §12 文件清单 → 全部任务覆盖；后端零改动 ✓

**Placeholder scan** — 无 TBD/TODO；所有代码步骤含完整代码；命令含期望输出。 ✓

**Type consistency** — `logic.js` 导出的函数名（`mergeInventory`/`groupByCategory`/`cardView`/`quantityRule`/`orderTerminal`/`deriveChips`/`effectivePriceFen`/`hasPromo`）在 `backpack.js` 与测试中一致；`data` 字段（`groups/chips/allItems/showSheet/sheetItem/sheetRule/sheetQty/sheetUnitYuan/sheetTotalYuan/paying`）跨 Task 一致；wxml 绑定的 `onCardTap/onQtyInc/onQtyDec/onSheetOverlayTap/onSheetPanelTap/onPay/onRetry` 均在 js 中定义。 ✓

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-16-backpack-items-page.md`. Two execution options:

1. **Subagent-Driven (recommended)** — 我为每个 Task 派一个全新 subagent 执行，任务之间我来 review，迭代快、上下文干净。
2. **Inline Execution** — 在当前会话里按 executing-plans 批量执行，带检查点 review。

你选哪种？
