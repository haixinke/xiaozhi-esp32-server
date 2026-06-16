/**
 * profile：我的女友资料页（仅职业/性格/声音 3 项可换）。
 */
const { getTheme, applyTheme } = require('../../utils/theme');
const { get } = require('../../utils/request');
const codes = require('../../config/companion-codes');
const voiceCatalog = require('../../config/voice-catalog');

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    characterName: '', avatar: '',
    occLabel: '', soulLabel: '', voiceLabel: ''
  },

  onLoad() {
    applyTheme(this);
    const app = getApp();
    this.setData({ deviceId: (app.globalData && app.globalData.virtualMAC) || '' });
  },
  onShow() { applyTheme(this); this._load(); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      if (!c) return;
      const traitsArr = c.soulTraits ? c.soulTraits.split(',') : [];
      const soulLabel = codes.SOUL_TRAITS.filter(function (t) { return traitsArr.indexOf(t.id) > -1; })
        .map(function (t) { return t.label; }).join(' · ');
      const quirkLabel = codes.getLabel(codes.QUIRKS, c.soulQuirk);
      const vv = voiceCatalog.findById(c.voice);
      this.setData({
        characterName: c.character || '我的女友',
        avatar: c.avatar || '',
        occLabel: c.occupation || '未设置',
        soulLabel: (soulLabel || '未设置') + ' ／ ' + (quirkLabel || '未设置'),
        voiceLabel: vv ? vv.label : (c.voice || '未设置')
      });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onOcc() { wx.navigateTo({ url: '/pages/companion/change-occupation/change-occupation' }); },
  onSoul() { wx.navigateTo({ url: '/pages/companion/change-soul/change-soul' }); },
  onVoice() { wx.navigateTo({ url: '/pages/companion/change-voice/change-voice' }); }
});
