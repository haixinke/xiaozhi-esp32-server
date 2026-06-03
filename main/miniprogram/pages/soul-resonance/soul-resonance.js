/**
 * pages/soul-resonance/soul-resonance.js
 *
 * "灵魂共振"页面：选择灵魂特质和小任性。
 * 从命运初见页面跳入，选择后跳转到下一步。
 */

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
    soulTraits: [
      { label: '粘人精', selected: false },
      { label: '撒娇狂魔', selected: false },
      { label: '嘴硬心软', selected: false },
      { label: '护短狂魔', selected: false },
      { label: '直球选手', selected: false },
      { label: '人间清醒', selected: false },
    ],
    // 小任性
    quirks: ['重度起床气', '小醋坛子', '路痴晚期', '游戏黑洞', '熬夜修仙党', '选择困难症', '逻辑泥石流', '炸厨房选手'],
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
    return selectedCount >= 1 && quirkIdx >= 0;
  },

  // 下一步 - 携带参数跳转
  onNext() {
    if (!this.data.canProceed) {
      return;
    }
    var selectedTraits = this.data.soulTraits
      .filter(function (t) { return t.selected; })
      .map(function (t) { return t.label; });
    var selectedQuirk = this.data.selectedQuirk >= 0 ? this.data.quirks[this.data.selectedQuirk] : '';

    var app = getApp();
    var flow = app.globalData.destinyFlow || {};
    flow.traits = selectedTraits;
    flow.quirk = selectedQuirk;
    app.globalData.destinyFlow = flow;

    wx.navigateTo({ url: '/pages/memory-anchor/memory-anchor' });
  },
});
