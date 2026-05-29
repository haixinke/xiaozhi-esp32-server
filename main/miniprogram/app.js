/**
 * 小智语音助手 - 小程序入口
 * 启动流程：静默登录 → 生成虚拟MAC → 检查设备绑定 → 进入主页或选择Agent
 */

const { post } = require('./utils/request');
const { setToken, clearToken } = require('./utils/auth');
const { getOrCreateMAC, checkOrRegisterDevice } = require('./utils/device');

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

            // 生成并缓存虚拟 MAC
            const mac = getOrCreateMAC(data.openid);
            this.globalData.virtualMAC = mac;

            console.log('静默登录成功, MAC:', mac);
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
   * 未绑定：跳转 agent-select 页面
   */
  async checkDeviceStatus() {
    const mac = this.globalData.virtualMAC;
    if (!mac) {
      console.warn('无虚拟 MAC，跳过设备检查');
      return;
    }

    try {
      const res = await checkOrRegisterDevice(mac);

      if (res && res.websocket) {
        // 设备已绑定，获取 WebSocket 连接信息
        this.globalData.wsUrl = res.websocket.url;
        this.globalData.wsToken = res.websocket.token;
        this.globalData.isDeviceBound = true;

        if (res.agent) {
          this.globalData.agentId = res.agent.id;
          this.globalData.agentName = res.agent.name;
        }

        console.log('设备已绑定, WS URL:', res.websocket.url);
      } else {
        // 设备未绑定，需要选择 Agent
        this.globalData.isDeviceBound = false;
        console.log('设备未绑定，需选择 Agent');

        wx.redirectTo({
          url: '/pages/agent-select/agent-select'
        });
      }
    } catch (err) {
      console.error('设备状态检查失败:', err);
      // 如果是首次未注册的设备，也跳转到选择页
      this.globalData.isDeviceBound = false;
    }
  }
});
