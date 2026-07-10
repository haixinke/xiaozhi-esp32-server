App({
  globalData: {
    version: '1.0.0-mvp'
  },

  onLaunch() {
    wx.login({
      success: ({ code }) => {
        this.globalData.loginCode = code || '';
      }
    });
  }
});
