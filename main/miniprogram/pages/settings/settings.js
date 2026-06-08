// pages/settings/settings.js
const { getTheme, applyTheme, toggleTheme } = require('../../utils/theme');

Page({
  data: {
    darkMode: getTheme()
  },

  onLoad() {
    applyTheme(this);
  },

  onShow() {
    applyTheme(this);
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
