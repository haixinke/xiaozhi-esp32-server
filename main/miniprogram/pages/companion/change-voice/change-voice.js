/**
 * change-voice：换声音。列表结构（容纳更多音色），每条可试听。
 * 试听逻辑搬自 destiny.js 的 InnerAudioContext 单实例。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');
const catalog = require('../../../config/voice-catalog');

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    voices: catalog.all(),
    curVoiceId: '',
    curLabel: '',
    selected: '',
    selectedLabel: '',
    selectedAudio: '',
    playingId: '',
    remain: 0,
    showConfirm: false,
    submitting: false,
    done: false
  },

  _audio: null,

  onLoad() {
    applyTheme(this);
    const app = getApp();
    this.setData({ deviceId: (app.globalData && app.globalData.virtualMAC) || '' });
    this._load();
  },
  onShow() { applyTheme(this); },
  onUnload() { this._stop(); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      const id = c ? c.voice : '';
      const v = catalog.findById(id);
      this.setData({ curVoiceId: id, curLabel: v ? v.label : id });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'voice_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  // 点 ▶ 试听
  onPlay(e) {
    const id = e.currentTarget.dataset.id;
    const v = this.data.voices.filter(function (x) { return x.id === id; })[0];
    if (!v) return;
    if (this.data.playingId === id) { this._stop(); return; }
    this._stop();
    const audio = wx.createInnerAudioContext();
    audio.src = v.audioUrl;
    audio.onEnded = () => this.setData({ playingId: '' });
    audio.onError = () => this.setData({ playingId: '' });
    audio.play();
    this._audio = audio;
    this.setData({ playingId: id });
  },
  _stop() {
    if (this._audio) { this._audio.stop(); this._audio.destroy(); this._audio = null; }
    this.setData({ playingId: '' });
  },

  // 点整行（非播放按钮）选中
  onVoiceTap(e) {
    const id = e.currentTarget.dataset.id;
    const v = this.data.voices.filter(function (x) { return x.id === id; })[0];
    if (!v) return;
    this.setData({ selected: id, selectedLabel: v.label, selectedAudio: v.audioUrl });
  },

  onConfirmTap() {
    if (this.data.submitting) return;
    if (!this.data.selected) { wx.showToast({ title: '请选择新声音', icon: 'none' }); return; }
    if (this.data.selected === this.data.curVoiceId) { wx.showToast({ title: '请选择不同的声音', icon: 'none' }); return; }
    if (this.data.remain <= 0) { this._noVoucher(); return; }
    this.setData({ showConfirm: true });
  },

  // 确认面板内的「再试听」
  onListen() {
    if (this.data.selectedAudio) {
      this._stop();
      const audio = wx.createInnerAudioContext();
      audio.src = this.data.selectedAudio;
      audio.onEnded = () => this.setData({ playingId: '' });
      audio.onError = () => this.setData({ playingId: '' });
      audio.play();
      this._audio = audio;
      this.setData({ playingId: this.data.selected });
    }
  },

  _noVoucher() {
    wx.showModal({
      title: '还没有换声音券', content: '换声音需要一张换声音券（¥99）',
      confirmText: '去背包获取', cancelText: '再想想',
      success: (r) => { if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=voice_change' }); }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    this._stop();
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', { deviceId: this.data.deviceId, voice: this.data.selected });
      wx.hideLoading();
      if (!res || res.code !== 0) {
        if (res && res.code === 10321) { this._noVoucher(); }
        else { wx.showToast({ title: (res && res.msg) || '更换失败', icon: 'none' }); }
        this.setData({ submitting: false }); return;
      }
      getApp().globalData.needReconnectAfterReshape = true;
      this.setData({ done: true });
    } catch (e) {
      wx.hideLoading(); wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    } finally { this.setData({ submitting: false }); }
  },

  onDone() { wx.navigateBack(); },
  onCloseConfirm() { this.setData({ showConfirm: false }); }
});
