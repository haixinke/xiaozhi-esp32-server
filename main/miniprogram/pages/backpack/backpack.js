const { getTheme, applyTheme } = require('../../utils/theme');

Page({
  data: {
    darkMode: getTheme(),
    loading: false,
    error: false,
    empty: false,
    groups: [],
    chips: [],
    allItems: []
  },
  onLoad() {
    applyTheme(this);
  },
  onShow() {
    applyTheme(this);
  }
});
