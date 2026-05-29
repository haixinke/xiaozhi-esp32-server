/**
 * Agent 选择页 - 获取列表并绑定设备
 */
const app = getApp();
const { get } = require('../../utils/request');
const { bindDeviceToAgent, checkOrRegisterDevice } = require('../../utils/device');

Page({
  data: {
    loading: true,
    agents: [],
    bindingId: null
  },

  onLoad() {
    this.loadAgents();
  },

  /**
   * 加载 Agent 列表
   */
  async loadAgents() {
    this.setData({ loading: true });

    try {
      const res = await get('/agent/list');

      // 兼容不同返回格式
      const agents = Array.isArray(res) ? res : (res.data || res.list || []);

      this.setData({
        agents: agents,
        loading: false
      });
    } catch (err) {
      console.error('获取 Agent 列表失败:', err);
      this.setData({ loading: false, agents: [] });
      wx.showToast({ title: '加载失败，请重试', icon: 'none' });
    }
  },

  /**
   * 选择并绑定 Agent
   */
  async onSelectAgent(e) {
    const agent = e.currentTarget.dataset.agent;
    if (!agent || this.data.bindingId) return;

    this.setData({ bindingId: agent.id });

    const mac = app.globalData.virtualMAC;
    if (!mac) {
      wx.showToast({ title: '设备初始化未完成', icon: 'none' });
      this.setData({ bindingId: null });
      return;
    }

    try {
      // 绑定设备到选中的 Agent
      await bindDeviceToAgent(agent.id, mac);

      // 更新全局状态
      app.globalData.agentId = agent.id;
      app.globalData.agentName = agent.name;
      app.globalData.isDeviceBound = true;

      // 绑定成功后，重新获取 WS Token
      const otaRes = await checkOrRegisterDevice(mac);
      if (otaRes && otaRes.websocket) {
        app.globalData.wsUrl = otaRes.websocket.url;
        app.globalData.wsToken = otaRes.websocket.token;
      }

      wx.showToast({ title: '绑定成功', icon: 'success' });

      // 延迟跳转主页
      setTimeout(() => {
        wx.redirectTo({ url: '/pages/index/index' });
      }, 800);

    } catch (err) {
      console.error('绑定失败:', err);
      wx.showToast({ title: '绑定失败，请重试', icon: 'none' });
      this.setData({ bindingId: null });
    }
  }
});
