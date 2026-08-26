// 年龄区间本地兜底：仅在字典接口拉取失败时使用，正式数据以智控台字典管理（EGG_AGE_RANGE）为准
const FALLBACK_AGE_RANGES = Object.freeze([
  Object.freeze({ value: 'AGE_0_14', label: '14 周岁及以下' }),
  Object.freeze({ value: 'AGE_15_35', label: '15-35 周岁' }),
  Object.freeze({ value: 'AGE_36_60', label: '36-60 周岁' }),
  Object.freeze({ value: 'AGE_61_PLUS', label: '60 周岁以上' })
]);

module.exports = { FALLBACK_AGE_RANGES };
