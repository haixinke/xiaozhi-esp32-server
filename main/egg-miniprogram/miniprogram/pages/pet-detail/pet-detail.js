const petStore = require('../../utils/pet-store');

Page({
  data: { pet: null, card: null, dailyStatus: null },

  onShow() {
    const pet = petStore.getPet();
    if (!pet || pet.hatchStatus !== 'HATCHED') {
      wx.showToast({ title: '破壳后才会生成档案', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    const firstCard = (pet.collectionCards && pet.collectionCards[0]) || {};
    const card = { ...firstCard, petType: pet.prototype };
    this.setData({
      pet: { ...pet, petType: pet.prototype, avatarUrl: pet.avatarUrl || '' },
      card,
      avatarUrl: pet.avatarUrl || '',
      dailyStatus: petStore.getDailyStatus()
    });
  },

  onChat() { wx.navigateTo({ url: '/pages/chat/chat' }); },
  onCard() { wx.navigateTo({ url: '/pages/collection-card/collection-card' }); }
});
