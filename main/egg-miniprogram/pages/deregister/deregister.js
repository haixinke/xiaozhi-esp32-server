Page({
  onConfirmDeregister() {
    wx.showModal({
      title: '再次确认',
      content: '提交后将进入 15 天冷静期，确定要继续注销账号吗？',
      confirmColor: '#D9463C',
      success: (res) => {
        if (!res.confirm) return;
        // TODO: 调用注销接口，例如：
        // wx.request({ url: 'https://your-api/account/deregister', method: 'POST' })
        wx.showToast({ title: '已提交注销申请', icon: 'success' });
        setTimeout(() => {
          wx.reLaunch({ url: '/pages/my/my' });
        }, 1200);
      }
    });
  },

  onCancel() {
    wx.navigateBack();
  }
});
