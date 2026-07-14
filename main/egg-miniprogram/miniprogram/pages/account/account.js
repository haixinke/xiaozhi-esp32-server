const petStore = require('../../utils/pet-store');

Page({
  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后将清除本机账号及蛋宝宝体验数据。',
      confirmColor: '#D9463C',
      success: (res) => {
        if (!res.confirm) return;
        getApp().clearLoginState();
        petStore.clearAccountData();
        wx.reLaunch({ url: '/pages/welcome/welcome' });
      }
    });
  },

  onDeregister() { wx.navigateTo({ url: '/pages/deregister/deregister' }); }
});
