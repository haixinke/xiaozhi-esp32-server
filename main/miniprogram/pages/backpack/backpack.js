const { getTheme, applyTheme } = require('../../utils/theme');
const { get, post } = require('../../utils/request');
const logic = require('./logic');

var ICON_BY_SKU = {
  voice_clone_quota: '🎙️',
  rose: '🌹', milktea: '🥤', diamond_ring: '💎'
};
var ICON_BY_CATEGORY = {
  consumable_change: '🎟️', voice_quota: '🎙️', outfit: '👗', intimacy: '🎁'
};
// 自带浅/深 PNG 图标的道具：值为 /images/<stem>[-dark].png 的 stem
var ICON_IMG_BY_SKU = {
  occupation_change: 'occupation',
  soul_quirk_change: 'soul',
  voice_change: 'voice',
  role_change: 'role'
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
    if (this.data.allItems && this.data.allItems.length) {
      this.refreshInventory();
    } else if (!this.data.loading) {
      this.loadAll();
    }
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
        iconImg: ICON_IMG_BY_SKU[it.skuCode] || '',
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
      this.setData({ loading: false, error: true, groups: [], chips: [], allItems: [] });
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
    if (item.cta === 'use') {
      var target = ({
        occupation_change: '/pages/companion/change-occupation/change-occupation',
        soul_quirk_change: '/pages/companion/change-soul/change-soul',
        voice_change: '/pages/companion/change-voice/change-voice',
        role_change: '/pages/companion/change-role/change-role'
      })[item.skuCode];
      if (target) {
        wx.navigateTo({ url: target + '?sku=' + item.skuCode });
      } else {
        wx.showToast({ title: '该道具暂不支持使用', icon: 'none' });
      }
      return;
    }
    var rule = logic.quantityRule(item.category);
    var qty = rule.defaultQty;
    this._openSheet(item, rule, qty);
  },

  _openSheet(item, rule, qty) {
    this.setData({
      showSheet: true,
      sheetItem: item,
      sheetRule: rule,
      sheetQty: qty,
      sheetUnitYuan: this._yuan(item.effectivePriceFen),
      sheetTotalYuan: this._yuan(item.effectivePriceFen * qty)
    });
  },

  // consumable_change 持有时，次入口「加购」强制走购买面板
  onBuyAgain(e) {
    var skuCode = e.currentTarget.dataset.sku;
    var item = (this.data.allItems || []).filter(function (it) { return it.skuCode === skuCode; })[0];
    if (!item) return;
    var rule = logic.quantityRule(item.category);
    this._openSheet(item, rule, rule.defaultQty);
  },

  _changeQty(q) {
    var rule = this.data.sheetRule;
    if (!rule || !rule.stepper) return;
    var item = this.data.sheetItem;
    if (!item) return;
    q = Math.max(rule.min, Math.min(rule.max, q));
    var unit = item.effectivePriceFen;
    this.setData({ sheetQty: q, sheetTotalYuan: this._yuan(unit * q) });
  },
  onQtyInc() { this._changeQty((this.data.sheetQty || 1) + 1); },
  onQtyDec() { this._changeQty((this.data.sheetQty || 1) - 1); },

  onSheetOverlayTap() {
    if (this.data.paying) return; // 支付中禁止关闭
    this.setData({ showSheet: false });
  },
  onSheetPanelTap() { /* 阻止冒泡 */ },

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

      // 3. 轮询订单直到履约完成（区分 成功 / 失败 / 超时）
      var outcome = await this._waitOrderFulfilled(order.outTradeNo);

      // 4. 关闭面板 + 刷新库存
      this.setData({ showSheet: false });
      await this.refreshInventory();
      wx.hideLoading();
      if (outcome === 'ok') {
        wx.showToast({ title: '购买成功', icon: 'success', duration: 2000 });
      } else if (outcome === 'failed') {
        wx.showToast({ title: '购买失败，请联系客服', icon: 'none', duration: 2500 });
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

  // 轮询订单状态：'ok'=已履约 / 'failed'=终态失败(取消/退款/超时) / 'timeout'=轮询超时
  async _waitOrderFulfilled(outTradeNo) {
    for (var i = 0; i < 12; i++) {
      try {
        var res = await get('/payment/order/' + outTradeNo);
        if (res && res.code === 0 && res.data) {
          var terminal = logic.orderTerminal(res.data.status);
          if (terminal === 'fulfilled') return 'ok';
          if (terminal === 'failed') {
            console.warn('[backpack] 订单履约失败 outTradeNo=' + outTradeNo + ' status=' + res.data.status);
            return 'failed';
          }
        }
      } catch (e) {
        // 单次查询失败不中断轮询
      }
      await new Promise(function (r) { setTimeout(r, 1000); });
    }
    console.warn('[backpack] 订单履约轮询超时 outTradeNo=' + outTradeNo);
    return 'timeout';
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
});
