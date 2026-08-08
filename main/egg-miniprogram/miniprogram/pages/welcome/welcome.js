const auth = require('../../utils/auth');
const { getPendingNfcClaimIntent } = require('../../utils/nfc-claim-intent');
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
      // 有 NFC 领取意图时优先进入领取页，不要求已绑定手机号
      if (this.navigateNfcClaim()) return;
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
    if (session && session.userId) {
      // 静默登录后同样先恢复 NFC 领取，再按手机号状态分流
      if (this.navigateNfcClaim()) return;
      if (session.hasPhone === true) {
        wx.switchTab({ url: '/pages/home/home' });
      }
    }
  },

  navigateNfcClaim() {
    const intent = getPendingNfcClaimIntent();
    if (intent && intent.claimRef) {
      getApp().globalData.welcomeCompleted = true;
      wx.redirectTo({ url: '/pages/nfc-claim/nfc-claim' });
      return true;
    }
    return false;
  },

  onEnterIsland() {
    // 进入小岛前先恢复 NFC 领取
    if (this.navigateNfcClaim()) return;
    getApp().globalData.welcomeCompleted = true;
    wx.switchTab({ url: '/pages/home/home' });
  }
});
