/**
 * pages/index/index.js
 *
 * 主页：文字对话控制器。
 * 负责：
 *   1. 等待 app 启动完成（登录 + 设备绑定）。
 *   2. 初始化 WebSocketManager 进行文字对话。
 *   3. 维护会话状态机：idle → thinking → idle。
 *   4. 切后台/前台时按需重连。
 */

const WebSocketManager = require('../../utils/websocket');

const app = getApp();

const STATE_IDLE = 'idle';
const STATE_THINKING = 'thinking';

Page({
  data: {
    // 连接状态：disconnected / connecting / connected
    connectionState: 'disconnected',

    // 会话状态机
    chatState: STATE_IDLE,

    // 设备/Agent 信息
    agentName: '',
    sessionId: '',

    // 消息列表
    messages: [],
    scrollToView: '',

    // AI 流式回复缓冲区
    currentReply: '',

    // 启动加载态
    booting: true,

    // 文字输入
    inputText: '',
  },

  // 非响应式资源，挂在 this 上以避免 setData 开销
  wsManager: null,
  _bootTimer: null,
  _appShowHandler: null,
  _msgIdSeed: 1,

  // -------------------------------------------------------------------------
  // 生命周期
  // -------------------------------------------------------------------------

  onLoad() {
    this._waitForAppReady()
      .then(() => this._bootstrap())
      .catch((err) => {
        console.error('启动失败:', err);
        this.setData({ booting: false });
        wx.showToast({ title: '启动失败，请重试', icon: 'none' });
      });

    // 监听小程序切回前台 → 触发重连
    this._appShowHandler = () => this._handleAppShow();
    if (wx.onAppShow) wx.onAppShow(this._appShowHandler);
  },

  onShow() {
    // 每次返回页面（例如切换 Agent 后）刷新 agent 名称
    if (app.globalData) {
      this.setData({ agentName: app.globalData.agentName || '小智助手' });
    }
  },

  onUnload() {
    this._teardown();
  },

  onHide() {
    // 切后台时保持连接，交给系统调度
  },

  // -------------------------------------------------------------------------
  // 启动序列
  // -------------------------------------------------------------------------

  _waitForAppReady() {
    return new Promise((resolve, reject) => {
      const check = () => {
        const g = app.globalData || {};
        if (g.token && g.virtualMAC && g.isDeviceBound && g.wsUrl) {
          resolve();
          return true;
        }
        if (g.token && g.virtualMAC && g.isDeviceBound === false) {
          reject(new Error('device not bound'));
          return true;
        }
        return false;
      };
      if (check()) return;

      let elapsed = 0;
      const TIMEOUT_MS = 30000; // 增加到 30 秒，给设备绑定更多时间
      this._bootTimer = setInterval(() => {
        elapsed += 250;
        if (check()) {
          clearInterval(this._bootTimer);
          this._bootTimer = null;
        } else if (elapsed >= TIMEOUT_MS) {
          clearInterval(this._bootTimer);
          this._bootTimer = null;
          reject(new Error('app init timeout'));
        }
      }, 250);
    });
  },

  _bootstrap() {
    const g = app.globalData;
    this.setData({
      agentName: g.agentName || '小智助手',
      booting: false,
    });

    this._initWebSocket();
  },

  _initWebSocket() {
    const g = app.globalData;
    this.wsManager = new WebSocketManager({
      onStateChange: (state) => {
        this.setData({ connectionState: state });
        if (state === 'disconnected' && this.data.chatState !== STATE_IDLE) {
          // 连接断开时把会话状态拉回 idle
          this.setData({ chatState: STATE_IDLE, currentReply: '' });
        }
      },
      onMessage: (msg) => this._handleWSMessage(msg),
      onError: (err, scope) => {
        console.warn('[WS:' + scope + ']', err && err.message ? err.message : err);
      },
    });

    this.wsManager.connect(g.wsUrl, g.virtualMAC, g.wsToken);
  },

  // -------------------------------------------------------------------------
  // WebSocket 消息分发
  // -------------------------------------------------------------------------

  _handleWSMessage(msg) {
    switch (msg.type) {
      case 'hello':
        this.setData({ sessionId: msg.sessionId || '' });
        break;

      case 'stt':
        if (msg.text) this._addMessage('user', msg.text);
        // STT 抵达后通常进入思考阶段
        this.setData({ chatState: STATE_THINKING });
        break;

      case 'llm':
        // 流式文本：累加到 currentReply
        if (msg.text) {
          this.setData({
            currentReply: (this.data.currentReply || '') + msg.text,
            chatState: STATE_THINKING,
          });
          this._scrollToBottom();
        }
        break;

      case 'tts':
        this._handleTtsState(msg);
        break;

      case 'goodbye':
        this.setData({ chatState: STATE_IDLE, currentReply: '' });
        break;

      default:
        // 未知消息：忽略
        break;
    }
  },

  _handleTtsState(msg) {
    if (msg.state === 'start') {
      this.setData({ chatState: STATE_THINKING });
    } else if (msg.state === 'sentence_start' || msg.state === 'sentence_end') {
      // 服务端通过 tts 消息发送文本内容，累加到 currentReply
      if (msg.text) {
        this.setData({
          currentReply: (this.data.currentReply || '') + msg.text,
          chatState: STATE_THINKING,
        });
        this._scrollToBottom();
      }
    } else if (msg.state === 'stop') {
      const reply = (this.data.currentReply || '').trim();
      if (reply) {
        this._addMessage('assistant', reply);
      }
      this.setData({ chatState: STATE_IDLE, currentReply: '' });
    }
  },

  _addMessage(role, content) {
    const id = 'msg-' + (this._msgIdSeed++);
    const messages = this.data.messages.concat([{ id, role, content }]);
    this.setData({ messages, scrollToView: id });
  },

  _scrollToBottom() {
    if (this.data.messages.length === 0) return;
    const last = this.data.messages[this.data.messages.length - 1];
    if (last && last.id) this.setData({ scrollToView: last.id });
  },

  // -------------------------------------------------------------------------
  // 用户交互
  // -------------------------------------------------------------------------

  onSwitchAgent() {
    wx.navigateTo({ url: '/pages/agent-select/agent-select' });
  },

  onTapStatus() {
    if (this.data.connectionState !== 'connected') {
      this._reconnect();
    }
  },

  onTextInput(e) {
    this.setData({ inputText: e.detail.value });
  },

  onTextSend() {
    const text = (this.data.inputText || '').trim();
    if (!text) return;
    if (!this._isReadyForAction()) return;

    // 发送文本消息到服务器
    try {
      this.wsManager.sendText(text);
    } catch (err) {
      console.error('发送文本失败:', err);
      wx.showToast({ title: '发送失败，请重试', icon: 'none' });
      return;
    }

    // 清空输入框并更新状态
    // 注意：不在这里添加用户消息，等待服务端的 stt 消息来触发显示
    this.setData({
      inputText: '',
      chatState: STATE_THINKING,
      currentReply: '',
    });
  },

  // -------------------------------------------------------------------------
  // 工具
  // -------------------------------------------------------------------------

  _sendAbort() {
    try { this.wsManager && this.wsManager.sendAbort(); } catch (_) {}
  },

  _isReadyForAction() {
    if (this.data.connectionState !== 'connected') {
      wx.showToast({ title: '正在连接服务...', icon: 'none' });
      this._reconnect();
      return false;
    }
    return true;
  },

  _reconnect() {
    if (!this.wsManager) return;
    const g = app.globalData;
    if (!g || !g.wsUrl || !g.virtualMAC) return;
    this.wsManager.connect(g.wsUrl, g.virtualMAC, g.wsToken);
  },

  _handleAppShow() {
    // 从后台恢复：如果连接已断开则重连
    if (this.wsManager && !this.wsManager.isConnected()) {
      this._reconnect();
    }
  },

  _teardown() {
    if (this._bootTimer) {
      clearInterval(this._bootTimer);
      this._bootTimer = null;
    }
    if (this._appShowHandler && wx.offAppShow) {
      try { wx.offAppShow(this._appShowHandler); } catch (_) {}
      this._appShowHandler = null;
    }
    if (this.wsManager) {
      try { this.wsManager.destroy(); } catch (_) {}
      this.wsManager = null;
    }
  },
});
