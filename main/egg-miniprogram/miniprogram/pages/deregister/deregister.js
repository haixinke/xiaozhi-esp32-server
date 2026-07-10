const DEREGISTER_KEY = 'eggbaby_deregister_request_v1';

Page({
  data: { pending: false, endDate: '' },

  onShow() {
    const request = wx.getStorageSync(DEREGISTER_KEY);
    if (!request) return this.setData({ pending: false, endDate: '' });
    const end = new Date(request.endAt);
    this.setData({
      pending: true,
      endDate: `${end.getFullYear()}年${end.getMonth() + 1}月${end.getDate()}日`
    });
  },

  onConfirmDeregister() {
    if (this.data.pending) {
      wx.showToast({ title: '注销申请已提交，可取消注销', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '再次确认',
      content: '提交后进入 15 天冷静期，数据不会立即删除。确定继续吗？',
      confirmColor: '#D9463C',
      success: (res) => {
        if (!res.confirm) return;
        const submittedAt = Date.now();
        wx.setStorageSync(DEREGISTER_KEY, { submittedAt, endAt: submittedAt + 15 * 24 * 60 * 60 * 1000 });
        this.onShow();
        wx.showToast({ title: '注销申请已提交', icon: 'success' });
      }
    });
  },

  onCancelDeregister() {
    wx.showModal({
      title: '取消注销',
      content: '取消后账号会恢复正常，可以继续使用蛋宝宝、卡册和对话。',
      success: (res) => {
        if (!res.confirm) return;
        wx.removeStorageSync(DEREGISTER_KEY);
        this.onShow();
        wx.showToast({ title: '已取消注销', icon: 'success' });
      }
    });
  },

  onBackAccount() { wx.navigateBack(); },
  onCancel() { wx.navigateBack(); }
});
