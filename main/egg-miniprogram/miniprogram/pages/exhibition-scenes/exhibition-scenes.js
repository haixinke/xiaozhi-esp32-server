const petStore = require('../../utils/pet-store');
const exhibitionScenes = require('../../utils/exhibition-scenes');

Page({
  data: { pet: null, scenes: exhibitionScenes.SCENES },

  onShow() {
    let pet = petStore.getPet();
    if (!pet || !pet.demoMode) pet = petStore.startExhibitionDemo();
    this.setData({ pet });
  },

  onSelectScene(event) {
    wx.navigateTo({ url: `/pages/exhibition-scene/exhibition-scene?scene=${event.currentTarget.dataset.scene}` });
  },

  onExit() {
    wx.showModal({
      title: '退出展会体验',
      content: '将恢复进入体验前的蛋宝宝数据。',
      confirmColor: '#002900',
      success: (result) => {
        if (!result.confirm) return;
        petStore.endExhibitionDemo();
        wx.switchTab({ url: '/pages/home/home' });
      }
    });
  }
});
