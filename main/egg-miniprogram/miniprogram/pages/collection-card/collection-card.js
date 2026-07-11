const petStore = require('../../utils/pet-store');

Page({
  data: { card: null, pet: null, isNew: false },

  onLoad(query) {
    const pet = petStore.getPet();
    if (!pet || !pet.collectionCard) {
      wx.showToast({ title: '还没有破壳收藏卡', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }
    this.setData({ pet, card: pet.collectionCard, isNew: query.new === '1' });
  },

  onReady() {
    if (this.data.card) this.drawShareCard();
  },

  drawShareCard() {
    const card = this.data.card;
    const draw = (imagePath) => {
      const context = wx.createCanvasContext('shareCanvas', this);
      context.setFillStyle('#F8F7EF'); context.fillRect(0, 0, 600, 840);
      context.setFillStyle('#002900'); context.fillRect(0, 0, 600, 120);
      context.setFillStyle('#FFFFFF'); context.setFontSize(22); context.fillText('EGGBABY · 破壳收藏卡', 42, 72);
      context.setFillStyle('#FFF9E4'); context.beginPath(); context.arc(300, 290, 106, 0, Math.PI * 2); context.fill();
      if (imagePath) {
        context.drawImage(imagePath, 194, 184, 212, 212);
      } else if (card.prototype === '玉兔') {
        context.setFillStyle('#FFF9E4'); context.fillRect(238, 135, 42, 110); context.fillRect(320, 135, 42, 110);
        context.setFillStyle('#002900'); context.beginPath(); context.arc(266, 282, 7, 0, Math.PI * 2); context.arc(334, 282, 7, 0, Math.PI * 2); context.fill();
        context.setFillStyle('#F4B9AE'); context.beginPath(); context.arc(300, 318, 6, 0, Math.PI * 2); context.fill();
      } else {
        context.setFillStyle('#F4B9AE'); context.beginPath(); context.arc(340, 270, 48, 0, Math.PI * 2); context.fill();
        context.setFillStyle('#002900'); context.beginPath(); context.arc(252, 274, 7, 0, Math.PI * 2); context.fill();
      }
      context.setFillStyle('#002900'); context.setFontSize(38); context.fillText(card.name, 42, 500);
      context.setFillStyle('#54632C'); context.setFontSize(24); context.fillText(`${card.prototype} · ${card.style}`, 42, 548);
      context.setFillStyle('#5C5C5C'); context.setFontSize(22); context.fillText(`${card.mbti} · ${card.gender} · ${card.bloodType} 型`, 42, 606);
      context.setFontSize(20); context.fillText(card.serial, 42, 730);
      context.setFillStyle('#002900'); context.fillRect(480, 700, 32, 32); context.fillRect(528, 700, 32, 32); context.fillRect(480, 748, 32, 32); context.fillRect(528, 748, 16, 16);
      context.setFillStyle('#8C8C88'); context.setFontSize(16); context.fillText('小程序码接入位', 450, 808);
      context.setFontSize(20); context.fillText('来蛋宝宝小程序认识它', 42, 782);
      context.draw();
    };

    if (card.imageUrl) {
      wx.downloadFile({
        url: card.imageUrl,
        success: (res) => draw(res.tempFilePath),
        fail: () => {
          wx.showToast({ title: '分享图下载失败，使用默认头像', icon: 'none' });
          draw(null);
        }
      });
    } else {
      draw(null);
    }
  },

  onAlbum() { wx.navigateTo({ url: '/pages/album/album' }); },
  onProfile() { wx.navigateTo({ url: '/pages/pet-detail/pet-detail' }); },

  onSave() {
    wx.canvasToTempFilePath({
      canvasId: 'shareCanvas',
      width: 600,
      height: 840,
      destWidth: 1200,
      destHeight: 1680,
      success: ({ tempFilePath }) => {
        wx.saveImageToPhotosAlbum({
          filePath: tempFilePath,
          success: () => wx.showToast({ title: '收藏卡已保存', icon: 'success' }),
          fail: () => wx.showToast({ title: '请允许保存到相册', icon: 'none' })
        });
      },
      fail: () => wx.showToast({ title: '分享图生成失败，请重试', icon: 'none' })
    }, this);
  },

  onShareAppMessage() {
    return { title: `我孵化了${this.data.card.name}，编号 ${this.data.card.serial}`, path: '/pages/welcome/welcome' };
  }
});
