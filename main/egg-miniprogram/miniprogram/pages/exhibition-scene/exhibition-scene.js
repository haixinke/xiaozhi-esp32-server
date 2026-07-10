const petStore = require('../../utils/pet-store');
const exhibitionScenes = require('../../utils/exhibition-scenes');

Page({
  data: { statusBarHeight: 20, pet: null, scene: null, hotspots: [], bubble: '', ripple: null },

  onLoad(query) {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    let pet = petStore.getPet();
    if (!pet || !pet.demoMode) pet = petStore.startExhibitionDemo();
    const scene = exhibitionScenes.getScene(query.scene);
    this.setData({ statusBarHeight: info.statusBarHeight || 20, pet, scene, hotspots: exhibitionScenes.HOTSPOTS[scene.key] || [] });
  },

  onBack() { wx.navigateBack(); },
  onChangeScene() { wx.navigateBack(); },
  onTapPet() { this.showReaction(this.data.scene.petLine, '50%', '52%'); },
  onTapHotspot(event) {
    const spot = this.data.hotspots[event.currentTarget.dataset.index];
    if (spot) this.showReaction(spot.line, spot.x, spot.y);
  },
  showReaction(text, x, y) {
    clearTimeout(this.bubbleTimer); clearTimeout(this.rippleTimer);
    this.setData({ bubble: text, ripple: { x, y } });
    this.rippleTimer = setTimeout(() => this.setData({ ripple: null }), 700);
    this.bubbleTimer = setTimeout(() => this.setData({ bubble: '' }), 2800);
    if (wx.vibrateShort) wx.vibrateShort({ type: 'light' });
  },
  onUnload() { clearTimeout(this.bubbleTimer); clearTimeout(this.rippleTimer); }
});
