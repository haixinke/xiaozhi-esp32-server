const petStore = require('../../utils/pet-store');
Page({
  data: { card: null },
  onShow() { const pet = petStore.getPet(); this.setData({ card: pet && pet.collectionCard ? pet.collectionCard : null }); },
  onOpen() { wx.navigateTo({ url: '/pages/collection-card/collection-card' }); }
});
