/* 蛋宝宝主页
   statusText 由业务数据拼装，不在主页外露上一句对话内容。
   已破壳在线：  在线 · 电量 82%
   已破壳离线：  离线 · 3小时前在线 · 电量 34%
   孵化中：      孵化中 · 42% · 约 6 天后破壳
   signalLevel: 1 弱 / 2 中 / 3 强，仅在线时使用。 */
Page({
  data: {
    devices: [
      {
        id: 'd1', name: '玉兔', hatched: true, status: 'online',
        mood: '平静', signalLevel: 3,
        statusText: '在线 · 电量 82%',
        gradientFrom: '#EDE78E', gradientTo: '#F4B9AE'
      },
      {
        id: 'd2', name: '锦鲤', hatched: true, status: 'offline',
        mood: '困倦', signalLevel: 0,
        statusText: '离线 · 3小时前在线 · 电量 34%',
        gradientFrom: '#9DB65B', gradientTo: '#EDE78E'
      },
      {
        id: 'd3', name: '神秘蛋', hatched: false, status: 'online',
        statusText: '孵化中 · 42% · 约 6 天后破壳',
        gradientFrom: '#EDE78E', gradientTo: '#9DB65B'
      }
    ]
  },

  onTapDevice(e) {
    const id = e.currentTarget.dataset.id;
    const dev = this.data.devices.find(d => d.id === id);
    if (!dev) return;
    if (dev.hatched) {
      wx.navigateTo({ url: `/pages/chat/chat?id=${id}` });
    } else {
      wx.navigateTo({ url: `/pages/egg-state/egg-state?id=${id}` });
    }
  },

  onAddDevice() {
    wx.navigateTo({ url: '/pages/add-device/add-device' });
  }
});
