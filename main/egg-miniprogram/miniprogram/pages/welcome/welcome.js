const auth = require('../../utils/auth');

Page({
  data: {
    ready: false
  },

  async onLoad() {
    // 同步检查本地登录态：有效会话直接跳转首页，不渲染欢迎页内容
    const cached = auth.getSession();
    if (cached && !auth.isExpired()) {
      wx.switchTab({ url: '/pages/home/home' });
      return;
    }
    // 本地无有效登录态时渲染欢迎页内容并尝试静默登录
    this.setData({ ready: true });
    const session = await getApp().ensureLogin().catch(() => null);
    if (session && session.userId) {
      wx.switchTab({ url: '/pages/home/home' });
    }
  },

  onEnterIsland() {
    wx.switchTab({ url: '/pages/home/home' });
  }
});
