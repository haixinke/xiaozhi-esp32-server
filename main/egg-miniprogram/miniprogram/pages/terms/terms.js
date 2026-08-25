Page({
  data: {
    scrollTarget: ''
  },

  onTocTap(event) {
    const target = String(event.currentTarget.dataset.target || '');
    if (!target) return;
    // 先清空再赋值，确保重复点击同一个目录项也能触发滚动跳转
    this.setData({ scrollTarget: '' });
    wx.nextTick(() => this.setData({ scrollTarget: target }));
  }
});
