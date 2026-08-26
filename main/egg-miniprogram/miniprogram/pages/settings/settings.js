const ageRangeApi = require('../../utils/age-range-api');
const { FALLBACK_AGE_RANGES } = require('../../config/age-ranges');

Page({
  data: {
    ageRangeLabel: '',
    ageRangeLoading: true
  },

  onShow() {
    this.loadAgeRange();
  },

  // 并行拉取字典选项与用户资料，把 ageRange value 映射为展示文案；
  // 任一接口失败时静默降级：字典失败用本地兜底，资料失败则不显示当前值，均不阻塞页面
  loadAgeRange() {
    this.setData({ ageRangeLoading: true });
    const optionsPromise = ageRangeApi.listAgeRanges().catch(() => []);
    const profilePromise = ageRangeApi.getProfile().catch(() => null);
    Promise.all([optionsPromise, profilePromise]).then(([options, profile]) => {
      const dict = options.length ? options : FALLBACK_AGE_RANGES;
      const hit = dict.find((item) => item.value === (profile && profile.ageRange));
      this.setData({ ageRangeLabel: hit ? hit.label : '', ageRangeLoading: false });
    });
  },

  onNavAgeRange() { wx.navigateTo({ url: '/pages/age-range/age-range' }); },
  onNavFeedback() { wx.navigateTo({ url: '/pages/feedback/feedback' }); },
  onNavTerms() { wx.navigateTo({ url: '/pages/terms/terms' }); },
  onNavPrivacy() { wx.navigateTo({ url: '/pages/privacy/privacy' }); }
});
