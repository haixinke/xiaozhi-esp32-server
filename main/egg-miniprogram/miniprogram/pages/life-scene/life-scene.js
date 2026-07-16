var petStore = require('../../utils/pet-store');
var lifeScenes = require('../../utils/life-scenes');
var analytics = require('../../services/analytics');
var timeService = require('../../services/time-service');

Page({
  data: {
    statusBarHeight: 20,
    pet: null,
    scene: null,
    hotspots: [],
    sceneDecorations: [],
    bubble: '',
    ripple: null,
    flowerEffect: null,
    butterflyEffect: null,
    sceneEffect: null,
    isActive: true,
    sceneKicker: ''
  },

  onLoad: function (query) {
    this.pageActive = true;
    var info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    var pet = petStore.getPet();
    if (!pet || petStore.getStage(pet) !== 'hatched') {
      wx.showToast({ title: '破壳后才能进入生活场景', icon: 'none' });
      setTimeout(function () { wx.switchTab({ url: '/pages/home/home' }); }, 600);
      return;
    }
    var scene = lifeScenes.getScene(query.scene);
    // 直接使用后端返回的 sceneUrl 作为背景图，无需前端构建
    if (pet.sceneUrl) scene = Object.assign({}, scene, { image: pet.sceneUrl });
    this.enteredAt = timeService.now();
    this.setData({
      statusBarHeight: info.statusBarHeight || 20,
      pet: pet,
      scene: scene,
      hotspots: lifeScenes.HOTSPOTS[scene.key] || [],
      sceneDecorations: [],
      sceneKicker: (pet.prototype || pet.petType || '') + ' · 生活场景'
    });
    analytics.track('scene_enter', { scene_id: scene.key, character: pet.prototype, entry_type: query.entry || 'scene' });
  },

  onBack: function () {
    wx.navigateBack();
  },

  onTapPet: function () {
    this.showReaction(this.data.scene.petLine, '50%', '52%');
  },

  onTapHotspot: function (event) {
    var spot = this.data.hotspots[event.currentTarget.dataset.index];
    if (!spot) return;
    analytics.track('interaction_point_tap', { scene_id: this.data.scene.key, point_id: spot.label, character: this.data.pet.prototype });
    this.showReaction(spot.line, spot.x, spot.y);
    if (this.data.scene.key === 'grass' && spot.label === '小花') this.showFlowerSway(spot);
    if (this.data.scene.key === 'grass' && spot.label === '蝴蝶') this.showButterflyFlight(spot);
    if (spot.effect) this.showSceneEffect(spot);
  },

  showFlowerSway: function (spot) {
    var self = this;
    clearTimeout(this.flowerStartTimer);
    clearTimeout(this.flowerHideTimer);
    this.setData({ flowerEffect: null });
    this.flowerStartTimer = setTimeout(function () {
      self.setData({ flowerEffect: { x: spot.x, y: spot.y } });
      self.flowerHideTimer = setTimeout(function () { self.setData({ flowerEffect: null }); }, 3050);
    }, 20);
  },

  showButterflyFlight: function (spot) {
    var self = this;
    clearTimeout(this.butterflyStartTimer);
    clearTimeout(this.butterflyHideTimer);
    this.setData({ butterflyEffect: null });
    this.butterflyStartTimer = setTimeout(function () {
      self.setData({ butterflyEffect: { x: spot.x, y: spot.y } });
      self.butterflyHideTimer = setTimeout(function () { self.setData({ butterflyEffect: null }); }, 3100);
    }, 20);
  },

  showSceneEffect: function (spot) {
    var self = this;
    clearTimeout(this.sceneEffectStartTimer);
    clearTimeout(this.sceneEffectHideTimer);
    this.setData({ sceneEffect: null });
    this.sceneEffectStartTimer = setTimeout(function () {
      self.setData({ sceneEffect: { type: spot.effect, x: spot.x, y: spot.y } });
      self.sceneEffectHideTimer = setTimeout(function () { self.setData({ sceneEffect: null }); }, 3050);
    }, 20);
  },

  showReaction: function (text, x, y) {
    var self = this;
    clearTimeout(this.bubbleTimer);
    clearTimeout(this.rippleTimer);
    this.setData({ bubble: text, ripple: { x: x, y: y } });
    this.rippleTimer = setTimeout(function () { self.setData({ ripple: null }); }, 700);
    this.bubbleTimer = setTimeout(function () { self.setData({ bubble: '' }); }, 2800);
    if (wx.vibrateShort) wx.vibrateShort({ type: 'light' });
  },

  clearEffectTimers: function () {
    clearTimeout(this.bubbleTimer);
    clearTimeout(this.rippleTimer);
    clearTimeout(this.flowerStartTimer);
    clearTimeout(this.flowerHideTimer);
    clearTimeout(this.butterflyStartTimer);
    clearTimeout(this.butterflyHideTimer);
    clearTimeout(this.sceneEffectStartTimer);
    clearTimeout(this.sceneEffectHideTimer);
  },

  onShow: function () {
    this.pageActive = true;
    this.setData({ sceneDecorations: [] });
    if (!this.data.isActive) this.setData({ isActive: true });
  },

  onHide: function () {
    this.pageActive = false;
    this.clearEffectTimers();
    this.setData({ isActive: false, bubble: '', ripple: null, flowerEffect: null, butterflyEffect: null, sceneEffect: null });
  },

  onUnload: function () {
    this.pageActive = false;
    this.clearEffectTimers();
    analytics.track('scene_exit', { scene_id: this.data.scene ? this.data.scene.key : '', dwell_time: Math.max(0, timeService.now() - (this.enteredAt || timeService.now())) });
  }
});
