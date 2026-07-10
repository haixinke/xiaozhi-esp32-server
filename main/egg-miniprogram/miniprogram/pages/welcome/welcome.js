const petStore = require('../../utils/pet-store');

Page({
  data: {
    agreed: false,
    authorizing: false
  },

  onLoad() {
    if (petStore.getUser()) {
      wx.switchTab({ url: '/pages/home/home' });
    }
  },

  onToggleAgreement() {
    this.setData({ agreed: !this.data.agreed });
  },

  onPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
  },

  onAuthorize() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意隐私政策', icon: 'none' });
      return;
    }
    if (this.data.authorizing) return;
    this.setData({ authorizing: true });

    const finish = (profile) => {
      petStore.saveUser({
        id: petStore.getIdentityId() || `user-${Date.now()}`,
        nickname: profile.nickName || '蛋友3024',
        avatarUrl: profile.avatarUrl || '',
        authorizedAt: Date.now()
      });
      wx.switchTab({ url: '/pages/home/home' });
    };

    wx.getUserProfile({
      desc: '用于展示你的蛋宝宝主人身份',
      success: ({ userInfo }) => finish(userInfo || {}),
      fail: () => wx.showToast({ title: '需要完成微信授权后才能进入', icon: 'none' }),
      complete: () => this.setData({ authorizing: false })
    });
  }
});
