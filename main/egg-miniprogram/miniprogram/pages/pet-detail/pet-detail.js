const petStore = require('../../utils/pet-store');

Page({
  data: { pet: null, card: null, dailyStatus: null },

  onShow() {
    const pet = petStore.getPet();
    if (!pet || !pet.collectionCard) {
      wx.showToast({ title: '破壳后才会生成档案', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    const card = pet.collectionCard;
    this.setData({
      pet,
      card,
      dailyStatus: petStore.getDailyStatus()
    });
  },

  onChat() { wx.navigateTo({ url: '/pages/chat/chat' }); },
  onCard() { wx.navigateTo({ url: '/pages/collection-card/collection-card' }); }
});
