Page({
  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      confirmColor: '#D9463C',
      success: (res) => {
        if (!res.confirm) return;
        // TODO: 清除本地登录态（例如 wx.clearStorageSync() / 让本地 token 失效），
        // 并按你们的登录流程重新引导用户登录。
        wx.reLaunch({ url: '/pages/my/my' });
      }
    });
  },

  onDeregister() {
    wx.navigateTo({ url: '/pages/deregister/deregister' });
  }
});
