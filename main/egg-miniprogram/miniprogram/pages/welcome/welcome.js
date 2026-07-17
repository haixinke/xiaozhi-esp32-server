const auth = require('../../utils/auth');

Page({
  data: {
    ready: false
  },

  async onLoad() {
    // 同步检查本地登录态：已绑定会话直接跳转首页
    const cached = auth.getSession();
    if (cached && !auth.isExpired()) {
      if (cached.hasPhone === true) {
        wx.switchTab({ url: '/pages/home/home' });
        return;
      }
      this.setData({ ready: true });
      return;
    }
    // 本地无有效登录态时渲染欢迎页内容并尝试静默登录
    this.setData({ ready: true });
    const session = await getApp().ensureLogin().catch(() => null);
    if (session && session.userId && session.hasPhone === true) {
      wx.switchTab({ url: '/pages/home/home' });
    }
  },

  onEnterIsland() {
    getApp().globalData.welcomeCompleted = true;
    wx.switchTab({ url: '/pages/home/home' });
  }
});
