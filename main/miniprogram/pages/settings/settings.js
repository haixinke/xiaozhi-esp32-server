// pages/settings/settings.js
Page({
  /**
   * 页面的初始数据
   */
  data: {
    darkMode: true
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    // 从本地存储读取主题设置
    const darkMode = wx.getStorageSync('darkMode');
    if (darkMode !== undefined) {
      this.setData({
        darkMode: darkMode
      });
    }
  },

  /**
   * 主题切换
   */
  onThemeChange(e) {
    const darkMode = e.detail.value;
    this.setData({
      darkMode: darkMode
    });

    // 保存到本地存储
    wx.setStorageSync('darkMode', darkMode);

    // 显示提示
    wx.showToast({
      title: darkMode ? '已切换至深色模式' : '已切换至浅色模式',
      icon: 'none',
      duration: 1500
    });
  },

  /**
   * 关于
   */
  onAbout() {
    wx.showModal({
      title: '关于完美女友',
      content: '完美女友是有温度、有灵魂、有记忆、最懂你的女友。',
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#864e5a'
    });
  }
});
