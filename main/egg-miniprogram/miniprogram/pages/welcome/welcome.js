const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const wechatApi = require('../../utils/wechat-api');

Page({
  data: {
    agreed: false,
    authorizing: false
  },

  async onLoad() {
    const session = await getApp().ensureLogin().catch(() => null);
    if (session && session.userId && session.hasPhone) {
      wx.switchTab({ url: '/pages/home/home' });
    }
  },

  onToggleAgreement() {
    this.setData({ agreed: !this.data.agreed });
  },

  onPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
  },

  async onAuthorize(event) {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意隐私政策', icon: 'none' });
      return;
    }
    const phoneCode = event && event.detail && event.detail.code;
    if (!phoneCode) {
      wx.showToast({ title: '需要授权手机号后才能使用蛋宝宝', icon: 'none' });
      return;
    }
    if (this.data.authorizing) return;
    this.setData({ authorizing: true });
    try {
      const app = getApp();
      const session = await app.ensureLogin();
      if (!session || !session.userId) throw new Error('invalid login session');
      await wechatApi.bindPhone(phoneCode);
      const boundSession = auth.markPhoneBound();
      if (!boundSession) throw new Error('invalid login session');
      app.applySession(boundSession);
      petStore.saveUser({
        id: session.userId,
        nickname: '蛋友',
        avatarUrl: '',
        authorizedAt: Date.now()
      });
      wx.switchTab({ url: '/pages/home/home' });
    } catch (error) {
      wx.showToast({ title: error.userMessage || '暂时无法连接服务，请稍后重试', icon: 'none' });
    } finally {
      this.setData({ authorizing: false });
    }
  }
});
