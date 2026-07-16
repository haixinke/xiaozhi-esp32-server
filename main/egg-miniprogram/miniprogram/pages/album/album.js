const petStore = require('../../utils/pet-store');
Page({
  data: { cards: [] },
  onShow() {
    const pet = petStore.getPet();
    const cards = (pet && pet.collectionCards) ? pet.collectionCards.map((card, index) => ({
      ...card,
      petType: pet.prototype || '玉兔',
      name: pet.name || pet.prototype || '蛋宝宝',
      index
    })) : [];
    this.setData({ cards });
  },
  onOpen(e) {
    const index = e.currentTarget.dataset.index || 0;
    wx.navigateTo({ url: `/pages/collection-card/collection-card?index=${index}` });
  }
});
const petStore = require('../../utils/pet-store');
Page({
  data: { card: null },
  onShow() {
    const pet = petStore.getPet();
    const card = pet && pet.collectionCard ? { ...pet.collectionCard, petType: pet.collectionCard.prototype, avatarUrl: pet.avatarUrl || '' } : null;
    this.setData({ card });
  },
  onOpen() { wx.navigateTo({ url: '/pages/collection-card/collection-card' }); }
});
