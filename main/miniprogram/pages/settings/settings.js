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
      title: '关于小智',
      content: '小智语音助手是一款基于ESP32的智能语音助手设备，为您提供便捷的语音交互体验。\n\n版本：1.0.0',
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#07C160'
    });
  }
});
