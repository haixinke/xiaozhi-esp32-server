const petStore = require('../../utils/pet-store');

Page({
  data: {
    phase: 'confirm',
    pet: null,
    particles: [
      { tx: '-140rpx', ty: '-110rpx', color: '#EDE78E' },
      { tx: '150rpx', ty: '-90rpx', color: '#F4B9AE' },
      { tx: '-170rpx', ty: '70rpx', color: '#9DB65B' },
      { tx: '160rpx', ty: '90rpx', color: '#EDE78E' },
      { tx: '0rpx', ty: '-180rpx', color: '#FFFFFF' },
      { tx: '30rpx', ty: '180rpx', color: '#F4B9AE' }
    ]
  },

  onLoad() {
    const pet = petStore.getPet();
    if (pet && pet.collectionCard) {
      wx.redirectTo({ url: '/pages/collection-card/collection-card' });
      return;
    }
    if (!pet || petStore.getStage(pet) !== 'ready') {
      wx.showToast({ title: '还没到破壳时间', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    this.setData({ pet });
  },

  onReveal() {
    this.setData({ phase: 'reveal' });
    setTimeout(() => {
      const result = petStore.createCollectionCard();
      if (!result.ok) {
        this.setData({ phase: 'confirm' });
        wx.showToast({ title: result.message, icon: 'none' });
        return;
      }
      wx.redirectTo({ url: '/pages/collection-card/collection-card?new=1' });
    }, 1450);
  }
});
