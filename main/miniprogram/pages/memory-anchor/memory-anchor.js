/**
 * pages/memory-anchor/memory-anchor.js
 *
 * "记忆锚定"页面：通过情景视频互动建立共同记忆。
 * 两个情景：关系确认 + 宠物领养。
 */

var codes = require('../../config/companion-codes');
var request = require('../../utils/request');

function buildScenarioUrl(charId, voiceId, scenario) {
  var styles = Object.values(codes.VOICE_STYLES);
  var style = codes.VOICE_STYLES[voiceId] || (styles.indexOf(voiceId) >= 0 ? voiceId : '');
  var char = charId || 'baiyueguang';
  var voice = style || 'tianmei';
  var folder = scenario === 2 ? 'two' : 'one';
  return codes.VIDEO_BASE_URL + '/' + char + '/' + folder + '/' + char + '_' + voice + '.mp4';
}

Page({
  data: {
    statusBarHeight: 44,
    scenario: 1,
    videoUrl: '',
    relationOptions: codes.RELATION_TYPES,
    selectedRelation: -1,
    petOptions: codes.PET_TYPES,
    selectedPet: -1,
    petName: '',
    canComplete: false,
    videoEnded: false,
    showCompletion: false,
    _completionTimer: null,
  },

  onLoad: function () {
    var sysInfo = wx.getSystemInfoSync();
    var app = getApp();
    var flow = app.globalData.destinyFlow || {};
    var videoUrl = buildScenarioUrl(flow.charId, flow.voiceId, 1);
    this.setData({
      statusBarHeight: sysInfo.statusBarHeight || 44,
      videoUrl: videoUrl,
    });
    this.videoContext = wx.createVideoContext('anchorVideo');
  },

  onUnload: function () {
    if (this.data._completionTimer) {
      clearTimeout(this.data._completionTimer);
    }
  },

  onVideoEnded: function () {
    this.setData({ videoEnded: true });
  },

  onReplay: function () {
    this.setData({ videoEnded: false });
    this.videoContext.play();
  },

  // 情景一：关系选择（单选，选完自动切换到情景二）
  onRelationSelect: function (e) {
    var idx = e.currentTarget.dataset.index;
    if (idx === this.data.selectedRelation) return;

    this.setData({ selectedRelation: idx });

    var self = this;
    var flow = getApp().globalData.destinyFlow || {};
    setTimeout(function () {
      self.setData({
        scenario: 2,
        videoUrl: buildScenarioUrl(flow.charId, flow.voiceId, 2),
        videoEnded: false,
      });
    }, 600);
  },

  // 情景二：宠物类型选择（单选）
  onPetSelect: function (e) {
    var idx = e.currentTarget.dataset.index;
    var newIdx = idx === this.data.selectedPet ? -1 : idx;
    this.setData({
      selectedPet: newIdx,
      canComplete: newIdx >= 0 && this.data.petName.trim(),
    });
  },

  // 情景二：宠物名字输入
  onPetNameInput: function (e) {
    this.setData({
      petName: e.detail.value,
      canComplete: this.data.selectedPet >= 0 && e.detail.value.trim(),
    });
  },

  // 创造完成
  onComplete: async function () {
    if (this.data.showCompletion) return;

    if (this.data.selectedPet < 0) {
      wx.showToast({ title: '请选择宠物类型', icon: 'none' });
      return;
    }
    if (!this.data.petName.trim()) {
      wx.showToast({ title: '请给小家伙起个名字', icon: 'none' });
      return;
    }

    var app = getApp();
    var flow = app.globalData.destinyFlow || {};
    var charId = flow.charId || '';

    var body = {
      deviceId: app.globalData.virtualMAC,
      type: 'gf',
      avatar: codes.CHARACTER_AVATARS[charId] || '',
      defaultImage: codes.CHARACTER_IMAGES[charId] || '',
      character: charId,
      occupation: flow.occId || '',
      voice: flow.voiceId || '',
      soulTraits: (flow.traits || []).join(','),
      soulQuirk: flow.quirk || '',
      relationType: codes.RELATION_TYPES[this.data.selectedRelation].id,
      petType: codes.PET_TYPES[this.data.selectedPet].id,
      petName: this.data.petName.trim(),
      agentId: app.globalData.agentId || '',
    };
    if (flow.quirksText) {
      body.quirksText = flow.quirksText;
    }

    wx.showLoading({ title: '正在创造...', mask: true });

    try {
      var res = await request.post('/companion/setup', body);
      if (!res || res.code !== 0) {
        wx.hideLoading();
        console.error('[memory-anchor] 唤醒失败:', res);
        wx.showToast({ title: '唤醒失败', icon: 'none', duration: 2000 });
        return;
      }

      var data = res.data;
      app.globalData.agentId = data.agentId;
      wx.setStorageSync('agentId', data.agentId);
      app.globalData.companionAvatar = data.companion.avatar || null;
      app.globalData.companionBgImage = data.companion.defaultImage || null;
      app.globalData.companionDataLoaded = true;
      app.globalData.needsDestiny = false;

      if (data.deviceBound) {
        app.globalData.wsUrl = data.wsUrl;
        app.globalData.wsToken = data.wsToken;
        app.globalData.isDeviceBound = true;
      } else {
        await app.checkDeviceStatus();
      }

      wx.hideLoading();
      this.setData({ showCompletion: true });

      var page = this;
      page.data._completionTimer = setTimeout(function () {
        wx.reLaunch({ url: '/pages/index/index' });
      }, 6500);
    } catch (err) {
      wx.hideLoading();
      console.error('[memory-anchor] 唤醒异常:', err);
      wx.showToast({ title: '唤醒失败', icon: 'none', duration: 2000 });
    }
  },
});
