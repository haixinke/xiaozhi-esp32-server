const petStore = require('../../utils/pet-store');
Page({
  data: { card: null },
  onShow() {
    const pet = petStore.getPet();
    const card = pet && pet.collectionCard ? { ...pet.collectionCard, petType: pet.collectionCard.prototype } : null;
    this.setData({ card });
  },
  onOpen() { wx.navigateTo({ url: '/pages/collection-card/collection-card' }); }
});
