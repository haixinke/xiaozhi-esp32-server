const petStore = require('../../utils/pet-store');
const request = require('../../utils/request');

Page({
  data: { userName: '蛋友', avatarUrl: '', eggCount: 0 },

  onShow() {
    this.loadUserProfile();
    this.loadPetStatus();
  },

  loadUserProfile() {
    request.get('/wechat/profile')
      .then((profile) => {
        petStore.syncUserProfile(profile);
        this.applyUserData();
      })
      .catch(() => this.applyUserData());
  },

  applyUserData() {
    const user = petStore.getUser() || {};
    this.setData({ userName: user.nickname || '蛋友', avatarUrl: user.avatarUrl || '' });
  },

  loadPetStatus() {
    const pet = petStore.getPet();
    this.setData({ eggCount: pet ? 1 : 0 });
  },

  onTapUserCard() { wx.navigateTo({ url: '/pages/profile/profile' }); },
  onNavAlbum() { wx.navigateTo({ url: '/pages/album/album' }); },
  onNavCodes() { wx.navigateTo({ url: '/pages/invite-codes/invite-codes' }); },
  onNavSettings() { wx.navigateTo({ url: '/pages/settings/settings' }); },
  onNavAccount() { wx.navigateTo({ url: '/pages/account/account' }); },
  onNavPrivacy() { wx.navigateTo({ url: '/pages/privacy/privacy' }); },
  onNavHelp() { wx.navigateTo({ url: '/pages/help/help' }); }
});
