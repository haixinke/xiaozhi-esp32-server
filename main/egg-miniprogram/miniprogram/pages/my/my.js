const petStore = require('../../utils/pet-store');

Page({
  data: { userName: '蛋友3024', eggCount: 0, pet: null, stage: '', statusLine: '', actionLabel: '' },

  onShow() {
    const user = petStore.getUser();
    const pet = petStore.getPet();
    if (!pet) {
      this.setData({ userName: (user && user.nickname) || '蛋友3024', eggCount: 0, pet: null });
      return;
    }
    const stage = petStore.getStage(pet);
    const presentation = petStore.getStagePresentation(stage);
    const status = petStore.getDailyStatus();
    this.setData({
      userName: (user && user.nickname) || '蛋友3024',
      eggCount: 1,
      pet,
      stage,
      statusLine: stage === 'hatched' ? status.line : petStore.getCountdown(pet),
      actionLabel: stage === 'hatched' ? '去看看' : presentation.actionLabel,
      stageLabel: presentation.myStage
    });
  },

  onTapUserCard() { wx.navigateTo({ url: '/pages/profile/profile' }); },
  onPetAction() {
    if (!this.data.pet) return wx.navigateTo({ url: '/pages/add-device/add-device' });
    if (this.data.stage === 'ready') return wx.navigateTo({ url: '/pages/hatch/hatch' });
    wx.switchTab({ url: '/pages/home/home' });
  },
  onNavAlbum() { wx.navigateTo({ url: '/pages/album/album' }); },
  onNavCodes() { wx.navigateTo({ url: '/pages/invite-codes/invite-codes' }); },
  onNavSettings() { wx.navigateTo({ url: '/pages/settings/settings' }); },
  onNavAccount() { wx.navigateTo({ url: '/pages/account/account' }); },
  onNavPrivacy() { wx.navigateTo({ url: '/pages/privacy/privacy' }); },
  onNavHelp() { wx.navigateTo({ url: '/pages/help/help' }); }
});
