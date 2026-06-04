/**
 * pages/soul-resonance/soul-resonance.js
 *
 * "灵魂共振"页面：选择灵魂特质和小任性。
 * 从命运初见页面跳入，选择后跳转到下一步。
 */

var codes = require('../../config/companion-codes');

// 角色数据（与 destiny 页面保持一致）
const CHARACTERS = [
  {
    id: 'baiyueguang',
    name: '高冷白月光',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/baiyueguang.png',
  },
  {
    id: 'linjiamei',
    name: '元气邻家妹',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/linjiamei.png',
  },
  {
    id: 'zhixingyujie',
    name: '知性御姐',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/zhichangjie.png',
  },
  {
    id: 'erciyuan',
    name: '潮酷二次元',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/erciyuan.png',
  },
];

Page({
  data: {
    statusBarHeight: 44,
    characterName: '',
    characterImage: '',
    // 来自命运初见的参数
    charId: '',
    occId: '',
    voiceId: '',
    quirksText: '',
    // 灵魂特质
    soulTraits: codes.SOUL_TRAITS.map(function (t) { return { id: t.id, label: t.label, selected: false }; }),
    // 小任性
    quirks: codes.QUIRKS.map(function (q) { return { id: q.id, label: q.label }; }),
    selectedQuirk: -1,
    canProceed: false,
  },

  onLoad() {
    var app = getApp();
    var flow = app.globalData.destinyFlow || {};
    var sysInfo = wx.getSystemInfoSync();
    var character = CHARACTERS.find(function (c) { return c.id === flow.charId; }) || CHARACTERS[0];

    this.setData({
      statusBarHeight: sysInfo.statusBarHeight || 44,
      charId: flow.charId || character.id,
      occId: flow.occId || '',
      voiceId: flow.voiceId || '',
      quirksText: flow.quirksText || '',
      characterName: character.name,
      characterImage: character.image,
    });
  },

  // 灵魂特质选择（最多 2 条）
  onTraitTap(e) {
    const idx = e.currentTarget.dataset.index;
    const traits = this.data.soulTraits;
    const selected = traits[idx].selected;

    if (selected) {
      // 取消选择
      traits[idx].selected = false;
    } else {
      const selectedCount = traits.filter(function (t) { return t.selected; }).length;
      if (selectedCount >= 2) {
        wx.showToast({ title: '最多选择两条', icon: 'none' });
        return;
      }
      traits[idx].selected = true;
    }

    this.setData({ soulTraits: traits, canProceed: this._checkCanProceed(traits, this.data.selectedQuirk) });
  },

  // 小任性选择（单选）
  onQuirkTap(e) {
    const idx = e.currentTarget.dataset.index;
    this.setData({ selectedQuirk: idx === this.data.selectedQuirk ? -1 : idx, canProceed: this._checkCanProceed(this.data.soulTraits, idx === this.data.selectedQuirk ? -1 : idx) });
  },

  _checkCanProceed(traits, quirkIdx) {
    var selectedCount = traits.filter(function (t) { return t.selected; }).length;
    return selectedCount >= 2 && quirkIdx >= 0;
  },

  // 下一步 - 携带参数跳转
  onNext() {
    if (!this.data.canProceed) {
      return;
    }
    var selectedTraits = this.data.soulTraits
      .filter(function (t) { return t.selected; })
      .map(function (t) { return t.id; });
    var selectedQuirk = this.data.selectedQuirk >= 0 ? this.data.quirks[this.data.selectedQuirk].id : '';

    var app = getApp();
    var flow = app.globalData.destinyFlow || {};
    flow.traits = selectedTraits;
    flow.quirk = selectedQuirk;
    app.globalData.destinyFlow = flow;

    wx.navigateTo({ url: '/pages/memory-anchor/memory-anchor' });
  },
});
