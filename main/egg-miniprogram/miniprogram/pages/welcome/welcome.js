const petStore = require('../../utils/pet-store');

Page({
  data: {
    agreed: false,
    authorizing: false
  },

  async onLoad() {
    const session = await getApp().ensureLogin().catch(() => null);
    if (session && session.userId) {
      wx.switchTab({ url: '/pages/home/home' });
    }
  },

  onToggleAgreement() {
    this.setData({ agreed: !this.data.agreed });
  },

  onPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
  },

  async onAuthorize() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意隐私政策', icon: 'none' });
      return;
    }
    if (this.data.authorizing) return;
    this.setData({ authorizing: true });
    try {
      const session = await getApp().silentLogin();
      if (!session || !session.userId) throw new Error('invalid login session');
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
