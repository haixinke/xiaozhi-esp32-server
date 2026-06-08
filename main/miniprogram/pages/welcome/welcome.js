/**
 * pages/welcome/welcome.js
 *
 * 新用户启动文案页。
 * 两句文案依次淡入，第5秒显示"去见她"按钮，
 * 点击后涟漪动画 + 震动 → 跳转命运初见页面。
 */

Page({
  data: {
    showBtn: false,
    contentFading: false,
    rippling: false,
  },

  _btnTimer: null,

  onLoad() {
    // 第5秒显示按钮
    this._btnTimer = setTimeout(() => {
      this.setData({ showBtn: true });
    }, 8000);
  },

  onUnload() {
    if (this._btnTimer) {
      clearTimeout(this._btnTimer);
      this._btnTimer = null;
    }
  },

  onGoToHer() {
    if (this.data.rippling) return;

    // 温柔长震动
    wx.vibrateLong({ type: 'light' });

    // 先淡出文案和按钮
    this.setData({ contentFading: true, rippling: true });

    // 涟漪动画结束后跳转
    setTimeout(() => {
      wx.redirectTo({ url: '/pages/destiny/destiny' });
    }, 1000);
  },
});
