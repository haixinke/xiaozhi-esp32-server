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

  onResetDemo() {
    wx.showModal({
      title: '重置本地体验数据',
      content: '将清除当前设备里的蛋宝宝、任务、收藏卡和对话，方便重新体验未添加蛋场景。',
      confirmColor: '#D9463C',
      success: (res) => {
        if (!res.confirm) return;
        petStore.resetDemo();
        wx.showToast({ title: '已重置', icon: 'success' });
        setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 600);
      }
    });
  },

  onDeregister() { wx.navigateTo({ url: '/pages/deregister/deregister' }); }
});
