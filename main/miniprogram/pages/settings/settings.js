// pages/settings/settings.js
const { getTheme, applyTheme, toggleTheme } = require('../../utils/theme');
const { get } = require('../../utils/request');

Page({
  data: {
    darkMode: getTheme(),
    // 羁绊面板
    companionAvatar: '',
    userAvatar: '/images/user-default.png',
    planCode: null,
    identityName: '普通陪伴'
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
    wx.showToast({ title: '即将上线', icon: 'none', duration: 1500 });
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
