const STORAGE_KEY = 'eggbaby_notification_preferences_v1';
const DEFAULTS = { daily: true, hatch: true, growth: true, bday: false };

Page({
  data: { notifs: DEFAULTS },

  onLoad() {
    this.setData({ notifs: wx.getStorageSync(STORAGE_KEY) || DEFAULTS });
  },

  update(key, value) {
    this.setData({ [`notifs.${key}`]: value }, () => wx.setStorageSync(STORAGE_KEY, this.data.notifs));
  },

  onToggleDaily(e) { this.update('daily', e.detail.value); },
  onToggleHatch(e) { this.update('hatch', e.detail.value); },
  onToggleGrowth(e) { this.update('growth', e.detail.value); },
  onToggleBday(e) { this.update('bday', e.detail.value); }
});
