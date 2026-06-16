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
    allItems: [],
    showSheet: false,
    sheetItem: null,
    sheetRule: null,
    sheetQty: 1,
    sheetUnitYuan: '',
    sheetTotalYuan: '',
    paying: false
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
  },

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
});
