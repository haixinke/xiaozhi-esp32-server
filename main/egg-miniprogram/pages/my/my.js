Page({
  data: {
    userName: '蛋友3024',
    eggCount: 1
  },

  onTapUserCard() {
    wx.navigateTo({ url: '/pages/profile/profile' });
  },
  onNavSettings() {
    wx.navigateTo({ url: '/pages/settings/settings' });
  },
  onNavAccount() {
    wx.navigateTo({ url: '/pages/account/account' });
  },
  onNavPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
  },
  onNavHelp() {
    wx.navigateTo({ url: '/pages/help/help' });
  }
});
