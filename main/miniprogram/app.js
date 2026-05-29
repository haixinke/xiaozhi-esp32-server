/**
 * 小智语音助手 - 小程序入口
 * 启动流程：静默登录 → 生成虚拟MAC → 检查设备绑定 → 进入主页或选择Agent
 */

const { post } = require('./utils/request');
const { setToken } = require('./utils/auth');
const { getOrCreateMAC, checkOrRegisterDevice, completeDeviceBinding } = require('./utils/device');

App({
  globalData: {
    token: null,
    openid: null,
    virtualMAC: null,
    wsToken: null,
    wsUrl: null,
    agentId: null,
    agentName: null,
    isDeviceBound: false
  },

  onLaunch() {
    // 快速初始化，不阻塞启动
    this.initInBackground();
  },

  initInBackground() {
    // 1. 尝试从 storage 恢复登录态
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      this.globalData.openid = wx.getStorageSync('openid');
      this.globalData.agentId = wx.getStorageSync('agentId');
      this.globalData.virtualMAC = wx.getStorageSync('virtualMAC');
      // 已登录，检查设备状态
      this.checkDeviceStatus().catch(err => {
        console.warn('设备状态检查失败:', err);
      });
      return;
    }

    // 2. 未登录，后台执行静默登录
    this.silentLogin().then(() => {
      console.log('后台登录成功');
      // 登录成功后检查设备状态
      return this.checkDeviceStatus();
    }).catch(err => {
      console.error('后台登录失败:', err);
    });
  },

  /**
   * 微信静默登录
   * wx.login() 获取 code → 后端换取 token + openid
   */
  async silentLogin() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: async (loginRes) => {
          if (!loginRes.code) {
            reject(new Error('wx.login 获取 code 失败'));
            return;
          }

          try {
            const data = await post('/wechat/login', {
              code: loginRes.code
            });

            // 保存登录态
            this.globalData.token = data.token;
            this.globalData.openid = data.openid;
            setToken(data.token, data.openid);

            // 保存 agentId 到 storage
            if (data.agentId) {
              this.globalData.agentId = data.agentId;
              wx.setStorageSync('agentId', data.agentId);
            }

            // 生成并缓存虚拟 MAC
            const mac = getOrCreateMAC(data.openid);
            this.globalData.virtualMAC = mac;

            console.log('静默登录成功, MAC:', mac, 'AgentId:', data.agentId);
            resolve();
          } catch (err) {
            console.error('静默登录请求失败:', err);
            reject(err);
          }
        },
        fail: (err) => {
          console.error('wx.login 调用失败:', err);
          reject(err);
        }
      });
    });
  },

  /**
   * 检查设备绑定状态
   * 已绑定：获取 wsUrl + wsToken
   * 未绑定：自动执行绑定流程
   */
  async checkDeviceStatus() {
    const mac = this.globalData.virtualMAC;
    const agentId = this.globalData.agentId;

    if (!mac) {
      console.warn('无虚拟 MAC，跳过设备检查');
      return;
    }

    if (!agentId) {
      console.error('无 Agent ID，无法自动绑定设备');
      wx.showModal({
        title: '提示',
        content: '登录信息异常，请重新登录',
        showCancel: false,
        success: () => {
          this.clearLoginState();
        }
      });
      return;
    }

    try {
      // 先尝试OTA检查，看设备是否已绑定
      const otaResponse = await checkOrRegisterDevice(mac);

      if (otaResponse.websocket) {
        // 设备已绑定，直接使用WebSocket信息
        this.globalData.wsUrl = otaResponse.websocket.url;
        this.globalData.wsToken = otaResponse.websocket.token;
        this.globalData.isDeviceBound = true;

        console.log('设备已绑定, WS URL:', otaResponse.websocket.url);
      } else if (otaResponse.activation && otaResponse.activation.code) {
        // 设备未绑定，执行自动绑定流程
        console.log('设备未绑定，开始自动绑定...验证码:', otaResponse.activation.code);

        const wsInfo = await completeDeviceBinding(mac, agentId);

        this.globalData.wsUrl = wsInfo.wsUrl;
        this.globalData.wsToken = wsInfo.wsToken;
        this.globalData.isDeviceBound = true;

        console.log('设备自动绑定成功, WS URL:', wsInfo.wsUrl);
      } else {
        throw new Error('OTA响应格式异常');
      }
    } catch (err) {
      console.error('设备绑定失败:', err);
      wx.showModal({
        title: '绑定失败',
        content: '设备绑定失败: ' + (err.message || '未知错误'),
        showCancel: false
      });
    }
  },

  /**
   * 清除登录状态
   */
  clearLoginState() {
    this.globalData.token = null;
    this.globalData.openid = null;
    this.globalData.agentId = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('openid');
  }
});
