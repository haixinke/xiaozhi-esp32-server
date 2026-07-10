Page({
  data: {
    scrollTarget: ''
  },

  onTocTap(e) {
    const target = e.currentTarget.dataset.target;
    // 先清空再赋值，确保重复点击同一个目录项也能触发滚动跳转
    this.setData({ scrollTarget: '' });
    wx.nextTick(() => {
      this.setData({ scrollTarget: target });
    });
  }
});
