/**
 * 小智语音助手 - 小程序入口
 * 启动流程：静默登录 → 检查设备绑定 → 进入主页或选择Agent
 */

const { post, get } = require('./utils/request');
const { setToken } = require('./utils/auth');
const { checkOrRegisterDevice, completeDeviceBinding } = require('./utils/device');

App({
  globalData: {
    token: null,
    openid: null,
    virtualMAC: null,
    wsToken: null,
    wsUrl: null,
    agentId: null,
    agentName: null,
    isDeviceBound: undefined,  // 设备绑定状态，undefined=检查中, true=已绑定, false=未绑定
    needsDestiny: false,       // 新用户无 agent，需要进入命运初见页面
    destinyFlow: null,         // 命运初见向导流程的中间数据
    companionAvatar: null,     // 伴侣头像 URL
    companionBgImage: null,    // 伴侣默认背景图 URL
    companionDataLoaded: false // 伴侣数据是否已加载完成（无论成功或失败）
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

      // 无 agent → 标记需要进入命运初见页面
      if (!this.globalData.agentId) {
        console.log('已登录但无 agent，标记 needsDestiny');
        this.globalData.needsDestiny = true;
        this.globalData.companionDataLoaded = true;
      }

      // 已登录，检查设备状态
      this.checkDeviceStatus().catch(err => {
        console.warn('设备状态检查失败:', err);
      });
      this.fetchCompanionData();
      return;
    }

    // 2. 未登录，后台执行静默登录
    this.silentLogin().then(() => {
      console.log('后台登录成功');

      // 无 agent → 标记需要进入命运初见页面（不再自动创建）
      if (!this.globalData.agentId) {
        console.log('新用户无 agent，标记 needsDestiny');
        this.globalData.needsDestiny = true;
        this.globalData.companionDataLoaded = true;
        return;
      }

      // 有 agent → 继续正常流程
      this.fetchCompanionData();
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
   * 获取伴侣数据（头像、背景图）
   */
  async fetchCompanionData() {
    const mac = this.globalData.virtualMAC;
    if (!mac) {
      this.globalData.companionDataLoaded = true;
      return;
    }
    try {
      const res = await get('/companion/detail/' + mac);
      if (res && res.code === 0 && res.data) {
        this.globalData.companionAvatar = res.data.avatar || null;
        this.globalData.companionBgImage = res.data.defaultImage || null;
      }
    } catch (err) {
      console.warn('获取伴侣数据失败:', err);
    } finally {
      this.globalData.companionDataLoaded = true;
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
      // 无 agent 时跳过设备绑定，由命运初见页面完成后处理
      console.log('无 Agent ID，跳过设备绑定（等待命运初见页面完成后处理）');
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
