/**
 * 小智语音助手 - 小程序入口
 * 启动流程：静默登录 → 检查设备绑定 → 进入主页或选择Agent
 */

const { post } = require('./utils/request');
const { setToken } = require('./utils/auth');
const { checkOrRegisterDevice, completeDeviceBinding, createPet } = require('./utils/device');

App({
  globalData: {
    token: null,
    openid: null,
    virtualMAC: null,
    wsToken: null,
    wsUrl: null,
    agentId: null,
    agentName: null,
    isDeviceBound: undefined  // 设备绑定状态，undefined=检查中, true=已绑定, false=未绑定
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
      this.globalData.virtualMAC = wx.getStorageSync('openid');
      this.globalData.agentId = wx.getStorageSync('agentId');
      // 已登录，先创建宠物，再检查设备状态
      this.createPetIfNeeded().then(() => {
        return this.checkDeviceStatus();
      }).catch(err => {
        console.warn('设备状态检查失败:', err);
      });
      return;
    }

    // 2. 未登录，后台执行静默登录
    this.silentLogin().then(() => {
      console.log('后台登录成功');
      // 确保智能体存在
      return this.ensureAgentExists();
    }).then(() => {
      // 创建宠物（在设备绑定前）
      return this.createPetIfNeeded();
    }).then(() => {
      // 宠物就绪后，检查设备状态
      return this.checkDeviceStatus();
    }).catch(err => {
      console.error('后台流程失败:', err);
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
            const response = await post('/wechat/login', {
              code: loginRes.code
            });

            // 后端返回: {code: 0, msg: "success", data: {token, expire, openid, ...}}
            console.log('完整登录响应:', response);

            // 提取内层的数据
            const loginData = response.data;
            const token = loginData.token;
            const openid = loginData.openid;
            const agentId = loginData.agentId;

            // 保存登录态
            this.globalData.token = token;
            this.globalData.openid = openid;
            setToken(token, openid);

            // 保存 agentId 到 storage（可能为null）
            if (agentId) {
              this.globalData.agentId = agentId;
              wx.setStorageSync('agentId', agentId);
            }

            // 使用 openid 作为设备标识
            this.globalData.virtualMAC = openid;

            console.log('静默登录成功, OpenID:', openid.substring(0, 8) + '...', 'AgentId:', agentId, 'Token:', token ? token.substring(0, 20) + '...' : 'null');
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
   * 确保智能体存在
   * 如果当前用户没有智能体，则自动创建一个
   */
  async ensureAgentExists() {
    if (this.globalData.agentId) {
      console.log('智能体已存在:', this.globalData.agentId);
      return;
    }

    try {
      console.log('智能体不存在，开始创建...');
      const storedToken = wx.getStorageSync('token');
      console.log('创建agent前检查token - 存在:', !!storedToken, '前缀:', storedToken ? storedToken.substring(0, 20) : 'N/A');

      const response = await post('/agent', {
        agentName: this.globalData.openid
      });
      const agentId = response.data;

      this.globalData.agentId = agentId;
      wx.setStorageSync('agentId', agentId);
      console.log('智能体创建成功:', agentId);
    } catch (err) {
      console.error('创建智能体失败:', err);
      wx.showModal({
        title: '初始化失败',
        content: '创建智能体失败: ' + (err.message || '未知错误'),
        showCancel: false,
        success: () => {
          this.clearLoginState();
        }
      });
      throw err;
    }
  },

  /**
   * 创建宠物（如果需要）
   * 在调用 OTA 接口前先创建宠物
   */
  async createPetIfNeeded() {
    const mac = this.globalData.virtualMAC;
    if (!mac) {
      console.warn('无设备标识，跳过创建宠物');
      return;
    }

    try {
      console.log('开始创建宠物, deviceId:', mac);
      await createPet(mac);
      console.log('宠物创建完成');
    } catch (err) {
      // 创建失败不阻断流程（可能宠物已存在）
      console.warn('创建宠物失败（可能已存在）:', err);
    }
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
      console.warn('无设备标识，跳过设备检查');
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

      if (otaResponse.activation && otaResponse.activation.code) {
        // 有激活码 → 设备未绑定，执行自动绑定流程
        console.log('设备未绑定，开始自动绑定...验证码:', otaResponse.activation.code);

        const wsInfo = await completeDeviceBinding(mac, agentId, otaResponse.activation.code);

        this.globalData.wsUrl = wsInfo.wsUrl;
        this.globalData.wsToken = wsInfo.wsToken;
        this.globalData.isDeviceBound = true;

        console.log('设备自动绑定成功, WS URL:', wsInfo.wsUrl);
      } else {
        // 无激活码 → 设备已绑定，直接使用WebSocket信息
        if (otaResponse.websocket) {
          this.globalData.wsUrl = otaResponse.websocket.url;
          this.globalData.wsToken = otaResponse.websocket.token;
          this.globalData.isDeviceBound = true;
          console.log('设备已绑定, WS URL:', otaResponse.websocket.url);
        } else {
          throw new Error('设备已绑定但OTA响应缺少websocket信息');
        }
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
