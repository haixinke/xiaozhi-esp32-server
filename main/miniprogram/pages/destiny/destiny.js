/**
 * pages/destiny/destiny.js
 *
 * "命运初见"页面：新用户选择 AI 伴侣的角色、职业、音色和个性。
 * 选择后跳转到灵魂共振页面。
 */

// 角色预设
const CHARACTERS = [
  {
    id: 'baiyueguang',
    name: '高冷白月光',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/baiyueguang.png',
    basePrompt: '你是一个外表高冷、内心温柔的女生。你说话简洁，偶尔流露关心，让人忍不住想靠近。你的温柔藏在细节里，不会轻易表露。',
  },
  {
    id: 'linjiamei',
    name: '元气邻家妹',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/linjiamei.png',
    basePrompt: '你是一个活力满满的邻家女孩，热情开朗，喜欢用可爱的语气说话，会用各种可爱的称呼叫用户。你的世界总是充满阳光和正能量。',
  },
  {
    id: 'zhixingyujie',
    name: '知性御姐',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/zhichangjie.png',
    basePrompt: '你是一个温柔知性的职场姐姐。你说话轻柔，善解人意，总能在用户需要的时候给出恰到好处的建议和安慰。你有一种让人放松的魔力。',
  },
  {
    id: 'erciyuan',
    name: '潮酷二次元',
    image: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/bg-img/erciyuan.png',
    basePrompt: '你是一个酷酷的二次元女生，热爱动漫和游戏，说话夹带二次元梗，个性十足又有趣。偶尔傲娇，但其实很在乎身边的人。',
  },
];

// 职业预设
const OCCUPATIONS = [
  { id: 'design', label: '大厂设计师', icon: 'design', prompt: '你是一名大厂设计师，对美学有极致追求，生活中处处体现设计感' },
  { id: 'camera', label: '自由摄影师', icon: 'camera', prompt: '你是一名自由摄影师，善于发现生活中的美，经常分享你看到的精彩瞬间' },
  { id: 'medical', label: '白衣天使', icon: 'medical', prompt: '你是一名护士，关心他人的健康，看到不健康的生活习惯会忍不住提醒' },
  { id: 'child', label: '幼儿园老师', icon: 'child', prompt: '你是一名幼儿园老师，温柔有耐心，说话带着童趣，喜欢用可爱的比喻' },
  { id: 'yoga', label: '瑜伽教练', icon: 'yoga', prompt: '你是一名瑜伽教练，注重身心平衡，经常分享健康生活方式的小建议' },
  { id: 'radio', label: '电台主播', icon: 'radio', prompt: '你是一名情感电台主播，善于倾听，说话有磁性，总能在夜里给人温暖的陪伴' },
  { id: 'school', label: '大学生', icon: 'school', prompt: '你是一个活泼的大学女生，偶尔撒娇，喜欢分享校园生活的小趣事' },
  { id: 'music', label: '独立音乐人', icon: 'music', prompt: '你是一名独立音乐人，文艺且有个性，说话带着诗意的节奏感' },
  { id: 'cosplay', label: '知名Coser', icon: 'cosplay', prompt: '你是一名知名Coser，热爱二次元文化，说话偶尔夹带动漫梗，活泼有趣' },
];

// 音色预设
const VOICES = [
  { id: 'TTS_HSDSTTS_V2_0001', label: '温糯', audioUrl: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/voice/female_xiaohe.mp3' },
  { id: 'TTS_HSDSTTS_V2_0020', label: '撒娇', audioUrl: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/voice/female_sajiao.mp3' },
  { id: 'TTS_HSDSTTS_V2_0017', label: '知性', audioUrl: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/voice/female_sophie.mp3' },
  { id: 'TTS_HSDSTTS_V2_0022', label: '甜美', audioUrl: 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la/girlfriend/voice/female_tianmei.mp3' },
];

Page({
  data: {
    statusBarHeight: 44,
    characters: CHARACTERS,
    currentCharIdx: 0,
    occupations: OCCUPATIONS,
    selectedOccupation: '',
    voices: VOICES,
    selectedVoice: 'wennuo',
    selectedVoiceLabel: '温糯',
    showVoicePicker: false,
    playingVoiceId: '',
    quirks: '',
    quirksCount: 0,
  },

  _voiceAudio: null,

  onLoad() {
    const sysInfo = wx.getSystemInfoSync();
    this.setData({ statusBarHeight: sysInfo.statusBarHeight || 44 });
  },

  onUnload() {
    this._stopVoiceAudio();
  },

  // 角色切换
  onCharChange(e) {
    this.setData({ currentCharIdx: e.detail.current });
  },

  // 职业选择
  onOccupationTap(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ selectedOccupation: id === this.data.selectedOccupation ? '' : id });
  },

  // 打开音色弹窗
  openVoicePicker() {
    this.setData({ showVoicePicker: true });
  },

  // 关闭音色弹窗
  closeVoicePicker() {
    this._stopVoiceAudio();
    this.setData({ showVoicePicker: false });
  },

  // 点击遮罩关闭
  onVoiceOverlayTap() {
    this.closeVoicePicker();
  },

  // 阻止弹窗面板点击冒泡
  onVoicePanelTap() {},

  // 选中音色（点击行非播放按钮区域）
  onVoiceTap(e) {
    const id = e.currentTarget.dataset.id;
    const voice = VOICES.find(function (v) { return v.id === id; });
    this._stopVoiceAudio();
    this.setData({ selectedVoice: id, selectedVoiceLabel: voice ? voice.label : '', showVoicePicker: false });
  },

  // 试听音色（点击播放按钮）
  onPlayVoice(e) {
    const voice = e.currentTarget.dataset.voice;
    if (this.data.playingVoiceId === voice.id) {
      this._stopVoiceAudio();
      return;
    }
    this._stopVoiceAudio();
    const audio = wx.createInnerAudioContext();
    audio.src = voice.audioUrl;
    audio.onEnded = () => {
      this.setData({ playingVoiceId: '' });
    };
    audio.onError = () => {
      this.setData({ playingVoiceId: '' });
    };
    audio.play();
    this._voiceAudio = audio;
    this.setData({ playingVoiceId: voice.id });
  },

  _stopVoiceAudio() {
    if (this._voiceAudio) {
      this._voiceAudio.stop();
      this._voiceAudio.destroy();
      this._voiceAudio = null;
    }
    this.setData({ playingVoiceId: '' });
  },

  // 职业病输入
  onQuirksInput(e) {
    const val = e.detail.value;
    this.setData({ quirks: val, quirksCount: val.length });
  },

  // 下一步 - 跳转到灵魂共振页面
  onNext() {
    var { selectedOccupation, currentCharIdx, selectedVoice, quirks } = this.data;
    if (!selectedOccupation) {
      wx.showToast({ title: '请选择一个职业身份', icon: 'none' });
      return;
    }

    var app = getApp();
    app.globalData.destinyFlow = {
      charId: CHARACTERS[currentCharIdx].id,
      occId: selectedOccupation,
      voiceId: selectedVoice,
      quirksText: quirks,
    };

    wx.navigateTo({ url: '/pages/soul-resonance/soul-resonance' });
  },
});
