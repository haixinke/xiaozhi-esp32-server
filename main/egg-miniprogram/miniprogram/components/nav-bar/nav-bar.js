/* nav-bar：自定义导航栏
   - status-bar 高度用 wx.getWindowInfo().statusBarHeight 动态获取，
     兼容全面屏 / 刘海屏机型（app.json 中 navigationStyle 设为 custom
     后系统不再提供原生导航栏，需要自己补这段高度）。
   - 返回箭头默认调用 wx.navigateBack()；如需自定义返回行为（例如
     从底部弹层返回而非真正 navigateBack），监听 bind:back 自行处理。
*/
Component({
  properties: {
    title: { type: String, value: '' },
    showBack: { type: Boolean, value: true }
  },
  data: {
    statusBarHeight: 20
  },
  lifetimes: {
    attached() {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      this.setData({ statusBarHeight: info.statusBarHeight || 20 });
    }
  },
  methods: {
    onBack() {
      this.triggerEvent('back');
      const pages = getCurrentPages();
      if (pages.length > 1) {
        wx.navigateBack();
      }
    }
  }
});
