const ageRangeApi = require('../../utils/age-range-api');
const petStore = require('../../utils/pet-store');
const { FALLBACK_AGE_RANGES } = require('../../config/age-ranges');

const SAVE_SUCCESS_RETURN_DELAY_MS = 900;

Page({
  data: {
    options: FALLBACK_AGE_RANGES,
    selected: '',
    confirmed: '',
    loading: true,
    saving: false,
    leaving: false,
    saveError: '',
    // force 模式：从聊天页强制进入，隐藏返回，保存前不允许离开
    force: false
  },

  onLoad(query) {
    this.setData({ force: !!(query && query.force === '1') });
    this.loadOptions();
    this.loadAgeRange();
  },

  // 拉取字典选项，失败时静默使用本地兜底列表
  loadOptions() {
    ageRangeApi.listAgeRanges().then((options) => {
      if (options.length) this.setData({ options });
    }).catch(() => {});
  },

  // 读取已保存的年龄区间；失败时按未选择处理，不阻塞用户重新选择
  loadAgeRange() {
    ageRangeApi.getProfile().then((profile) => {
      const confirmed = this.normalizeAgeRange(profile && profile.ageRange);
      this.setData({ loading: false, confirmed, selected: confirmed });
    }).catch(() => {
      this.setData({ loading: false, confirmed: '', selected: '' });
    });
  },

  // 仅接受当前选项列表中的合法值，防止脏数据
  normalizeAgeRange(value) {
    const hit = this.data.options.find((item) => item.value === value);
    return hit ? hit.value : '';
  },

  onSelect(event) {
    if (this.data.saving || this.data.leaving) return;
    const selected = this.normalizeAgeRange(event.currentTarget.dataset.value);
    if (selected) this.setData({ selected, saveError: '' });
  },

  onConfirm() {
    if (this.data.saving || this.data.leaving || !this.data.selected) return;
    this.setData({ saving: true, saveError: '' });
    ageRangeApi.saveAgeRange(this.data.selected).then(() => {
      const confirmed = this.data.selected;
      // 同步本地用户缓存，聊天页门槛依赖该字段
      petStore.syncUserProfile({ ageRange: confirmed });
      if (this.data.force) {
        wx.redirectTo({ url: '/pages/chat/chat' });
        return;
      }
      this.setData({ saving: false, leaving: true, confirmed });
      // 保存成功提示与其他页面统一使用原生 toast
      wx.showToast({ title: '保存成功', icon: 'success', duration: SAVE_SUCCESS_RETURN_DELAY_MS });
      this.returnTimer = setTimeout(() => {
        this.returnTimer = null;
        wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/my/my' }) });
      }, SAVE_SUCCESS_RETURN_DELAY_MS);
    }).catch((error) => {
      this.setData({
        saving: false,
        saveError: (error && error.userMessage) || '保存失败，请重试'
      });
    });
  },

  onUnload() {
    clearTimeout(this.returnTimer);
    this.returnTimer = null;
  }
});
