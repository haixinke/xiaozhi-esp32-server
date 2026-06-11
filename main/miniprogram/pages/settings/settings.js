// pages/settings/settings.js
const { getTheme, applyTheme, toggleTheme } = require('../../utils/theme');
const { get } = require('../../utils/request');

function featureLabel(code) {
  var labels = {
    'long_term_memory': '留存甜蜜回忆',
    'voice_input': '语音聊天',
    'superpower': '超能力（天气）',
    'social_moments': '看我的朋友圈'
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
    wx.showToast({ title: '支付功能开发中', icon: 'none', duration: 1500 });
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
