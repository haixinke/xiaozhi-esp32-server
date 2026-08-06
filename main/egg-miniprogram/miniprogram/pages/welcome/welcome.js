const auth = require('../../utils/auth');
const shareInvite = require('../../utils/share-invite');

Page({
  data: {
    ready: false,
    hasPendingInvite: false
  },

  async onLoad() {
    const hasPendingInvite = Boolean(shareInvite.getPending());
    // 同步检查本地登录态：已绑定会话直接跳转首页
    const cached = auth.getSession();
    if (cached && !auth.isExpired()) {
      if (cached.hasPhone === true) {
        wx.switchTab({ url: '/pages/home/home' });
        return;
      }
      this.setData({ ready: true, hasPendingInvite });
      return;
    }
    // 本地无有效登录态时渲染欢迎页内容并尝试静默登录
    this.setData({ ready: true, hasPendingInvite });
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
