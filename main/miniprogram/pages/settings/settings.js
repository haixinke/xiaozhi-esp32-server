// pages/settings/settings.js
const { getTheme, applyTheme, toggleTheme } = require('../../utils/theme');
const { get } = require('../../utils/request');

function moodLabel(code) {
  var labels = {
    'JOY': '愉快',
    'CALM': '平静',
    'EXCITEMENT': '兴奋',
    'CURIOSITY': '好奇',
    'CARE': '关怀',
    'ANXIETY': '焦虑',
    'FRUSTRATION': '沮丧',
    'FATIGUE': '疲惫'
  };
  return labels[code] || code;
}

Page({
  data: {
    darkMode: getTheme(),
    // 羁绊面板
    companionAvatar: '',
    userAvatar: '/images/user-default.png',
    menstrualStatus: null,
    mood: null,
    moodLabel: '',
    planCode: null,
    identityName: '普通陪伴',
    // 信件浮窗（一封写给你的信）
    showLetterPopup: false
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
    this.loadCompanionStatus();
    this.loadSubscription();
  },

  loadCompanionAvatar() {
    var app = getApp();
    this.setData({
      companionAvatar: app.globalData.companionAvatar || '/images/avatar-default.png'
    });
  },

  loadCompanionStatus() {
    var app = getApp();
    var mood = app.globalData.companionMood || null;
    this.setData({
      menstrualStatus: app.globalData.companionMenstrualStatus || null,
      mood: mood,
      moodLabel: mood ? moodLabel(mood) : ''
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
    var tab = this.data.planCode || 'silver';
    wx.navigateTo({ url: '/pages/subscription/subscription?from=settings&tab=' + tab });
  },

  onBackpackTap() {
    wx.navigateTo({ url: '/pages/backpack/backpack' });
  },

  onCompanionTap() {
    wx.navigateTo({ url: '/pages/companion/profile/profile' });
  },

  onOrdersTap() {
    wx.navigateTo({ url: '/pages/orders/orders' });
  },

  onThemeChange() {
    toggleTheme(this);
  },

  onLetterTap() {
    this.setData({ showLetterPopup: true });
  },

  onLetterOverlayTap() {
    this.setData({ showLetterPopup: false });
  },

  onLetterPanelTap() {
    // prevent bubbling
  }
});
