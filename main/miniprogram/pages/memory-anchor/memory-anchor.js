/**
 * pages/memory-anchor/memory-anchor.js
 *
 * "记忆锚定"页面：通过情景视频互动建立共同记忆。
 * 两个情景：关系确认 + 宠物领养。
 */

var VIDEO_URLS = [
  'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/video/baiyueguang_tianmei.mp4',
  'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/video/baiyueguang_tianmei2.mp4',
];

var RELATION_OPTIONS = ['青梅竹马', '欢喜冤家', '一见钟情'];
var PET_OPTIONS = ['猫', '狗'];

Page({
  data: {
    statusBarHeight: 44,
    scenario: 1,
    videoUrl: VIDEO_URLS[0],
    relationOptions: RELATION_OPTIONS,
    selectedRelation: -1,
    petOptions: PET_OPTIONS,
    selectedPet: -1,
    petName: '',
    canComplete: false,
  },

  onLoad: function () {
    var sysInfo = wx.getSystemInfoSync();
    this.setData({ statusBarHeight: sysInfo.statusBarHeight || 44 });
  },

  // 情景一：关系选择（单选，选完自动切换到情景二）
  onRelationSelect: function (e) {
    var idx = e.currentTarget.dataset.index;
    if (idx === this.data.selectedRelation) return;

    this.setData({ selectedRelation: idx });

    var self = this;
    setTimeout(function () {
      self.setData({
        scenario: 2,
        videoUrl: VIDEO_URLS[1],
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
  onComplete: function () {
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
    flow.relation = RELATION_OPTIONS[this.data.selectedRelation];
    flow.petType = PET_OPTIONS[this.data.selectedPet];
    flow.petName = this.data.petName.trim();
    app.globalData.destinyFlow = flow;

    wx.reLaunch({ url: '/pages/index/index' });
  },
});
