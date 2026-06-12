// pages/settings/settings.js
const { getTheme, applyTheme, toggleTheme } = require('../../utils/theme');
const { get, post } = require('../../utils/request');

function featureLabel(code) {
  var labels = {
    'long_term_memory': '永久留存甜蜜回忆',
    'voice_input': '和女友语音聊天',
    'superpower': '赋予女友超能力（天气）',
    'social_moments': '看女友的私密空间'
  };
  return labels[code] || code;
}

Page({
  data: {
    darkMode: getTheme(),
    // 羁绊面板
    companionAvatar: '',
    userAvatar: '/images/user-default.png',
    planCode: null,
    identityName: '普通陪伴',
    // 契约浮窗
    showContractPopup: false,
    plans: [],
    selectedPlanId: null,
    contractLoading: false
  },

  onLoad() {
    applyTheme(this);
  },

  onShow() {
    applyTheme(this);
    this.setData({
      userAvatar: wx.getStorageSync('userAvatar') || '/images/user-default.png'
    });
    this.loadCompanionAvatar();
    this.loadSubscription();
  },

  loadCompanionAvatar() {
    var app = getApp();
    this.setData({
      companionAvatar: app.globalData.companionAvatar || '/images/avatar-default.png'
    });
  },

  async loadSubscription() {
    var app = getApp();
    var gd = app.globalData || {};
    if (gd.planCode) {
      this.setData({
        planCode: gd.planCode,
        identityName: this.getIdentityName(gd.planCode)
      });
    }
    try {
      var res = await get('/subscription/entitlements');
      if (res && res.code === 0 && res.data) {
        var planCode = res.data.active ? res.data.planCode : null;
        this.setData({
          planCode: planCode,
          identityName: this.getIdentityName(planCode)
        });
      }
    } catch (err) {
      console.warn('[settings] subscription check failed:', err);
    }
  },

  getIdentityName(planCode) {
    if (planCode === 'silver') return '专属守护';
    if (planCode === 'gold') return '特权家属';
    return '普通陪伴';
  },

  onChooseAvatar(e) {
    var avatarUrl = e.detail.avatarUrl;
    if (avatarUrl) {
      this.setData({ userAvatar: avatarUrl });
      wx.setStorageSync('userAvatar', avatarUrl);
    }
  },

  onContractTap() {
    if (this.data.planCode) {
      wx.showToast({ title: '您已有契约', icon: 'none', duration: 1500 });
      return;
    }
    this.loadPlans();
  },

  async loadPlans() {
    if (this.data.contractLoading) return;
    this.setData({ contractLoading: true });
    try {
      var res = await get('/subscription/plans');
      if (res && res.code === 0 && res.data) {
        var plans = res.data.map(function(plan) {
          return {
            id: plan.id,
            planCode: plan.planCode,
            planName: plan.planName,
            priceYuan: (plan.priceFen / 100).toFixed(2),
            promoYuan: (plan.promoPriceFen / 100).toFixed(2),
            features: (plan.features || []).map(function(code) {
              return { code: code, label: featureLabel(code) };
            })
          };
        });
        this.setData({
          plans: plans,
          showContractPopup: true,
          selectedPlanId: null
        });
      }
    } catch (err) {
      console.warn('[settings] load plans failed:', err);
      wx.showToast({ title: '加载失败', icon: 'none', duration: 1500 });
    } finally {
      this.setData({ contractLoading: false });
    }
  },

  onContractOverlayTap() {
    this.setData({ showContractPopup: false });
  },

  onContractPanelTap() {
    // prevent bubbling
  },

  onPlanSelect(e) {
    var id = e.currentTarget.dataset.id;
    this.setData({ selectedPlanId: id });
  },

  onSignContract() {
    if (!this.data.selectedPlanId) return;
    this._doSignContract();
  },

  async _doSignContract() {
    var planId = this.data.selectedPlanId;
    wx.showLoading({ title: '正在下单...', mask: true });
    try {
      // 1. 创建订单
      var orderRes = await post('/payment/order', {
        productType: 'SUBSCRIPTION',
        productRefId: planId,
        quantity: 1
      });
      if (!orderRes || orderRes.code !== 0 || !orderRes.data) {
        wx.hideLoading();
        wx.showToast({ title: (orderRes && orderRes.msg) || '下单失败', icon: 'none', duration: 2000 });
        return;
      }
      var order = orderRes.data;
      var prepayParams = order.prepayParams || {};

      // 2. 检测 Mock 模式（MockPaymentNotifyController 返回 {code:"SUCCESS", message:"SUCCESS"}）
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
        // 真实微信支付：调用 wx.requestPayment
        // TODO: 接入真实 wx.requestPayment
        wx.hideLoading();
        wx.showToast({ title: '真实支付待接入', icon: 'none', duration: 2000 });
        return;
      }

      // 3. 刷新全局订阅状态
      var app = getApp();
      if (app.fetchSubscription) {
        await app.fetchSubscription();
      }

      // 4. 刷新页面状态
      this.setData({
        showContractPopup: false,
        selectedPlanId: null
      });
      await this.loadSubscription();

      wx.hideLoading();
      wx.showToast({ title: '契约签订成功', icon: 'success', duration: 2000 });
    } catch (err) {
      wx.hideLoading();
      console.warn('[settings] sign contract failed:', err);
      wx.showToast({ title: '操作失败，请重试', icon: 'none', duration: 2000 });
    }
  },

  onBackpackTap() {
    wx.showToast({ title: '即将上线', icon: 'none', duration: 1500 });
  },

  onThemeChange() {
    toggleTheme(this);
  },

  onAbout() {
    wx.showModal({
      title: '关于完美女友',
      content: '完美女友是有温度、有灵魂、有记忆、最懂你的女友。',
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#864e5a'
    });
  }
});
