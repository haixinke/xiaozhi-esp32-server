/**
 * pages/index/index.js
 *
 * 主页：文字对话控制器。
 * 负责：
 *   1. 等待 app 启动完成（登录 + 设备绑定）。
 *   2. 初始化 WebSocketManager 和 AudioManager 进行文字对话和音频播放。
 *   3. 维护会话状态机：idle → thinking → speaking → idle。
 *   4. 切后台/前台时按需重连。
 */

const AudioManager = require('../../utils/audio');
const WebSocketManager = require('../../utils/websocket');

const app = getApp();

const STATE_IDLE = 'idle';
const STATE_THINKING = 'thinking';
const STATE_SPEAKING = 'speaking';

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

    // 启动加载态：初始为 true，确认不需要跳转后才显示 UI
    booting: true,

    // 文字输入
    inputText: '',
  },

  // 非响应式资源，挂在 this 上以避免 setData 开销
  audioManager: null,
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
        if (err.message === 'redirecting to destiny') return;
        console.error('启动失败:', err);
        this.setData({ booting: false });
        wx.showToast({ title: '启动失败，请重试', icon: 'none' });
      });

    // 监听小程序切回前台 → 触发重连
    this._appShowHandler = () => this._handleAppShow();
    if (wx.onAppShow) wx.onAppShow(this._appShowHandler);
  },

  onShow() {
    // 每次返回页面（例如从命运初见页面返回）刷新 agent 名称
    if (app.globalData) {
      const g = app.globalData;
      if (g.needsDestiny) {
        wx.redirectTo({ url: '/pages/destiny/destiny' });
        return;
      }
      this.setData({
        agentName: g.agentName || '',
        companionAvatar: g.companionAvatar || '',
        companionBgImage: g.companionBgImage || '',
      });
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

        // 新用户无 agent → 跳转到命运初见页面
        if (g.needsDestiny) {
          wx.redirectTo({ url: '/pages/destiny/destiny' });
          reject(new Error('redirecting to destiny'));
          return true;
        }

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
      agentName: g.agentName || '',
      companionAvatar: g.companionAvatar || '',
      companionBgImage: g.companionBgImage || '',
      booting: false,
    });

    this._initAudio();
    // 不再自动连接 WebSocket，等待用户点击"召唤"按钮
    this._initWebSocketManager();
  },

  _initAudio() {
    this.audioManager = new AudioManager({
      onAudioFrame: () => {
        // 不需要处理录音帧，只用于播放
      },
      onRecordStart: () => {
        // 不再使用录音功能
      },
      onRecordStop: () => {
        // 不再使用录音功能
      },
      onPlayEnd: () => {
        // 播放队列清空：若还在 speaking 状态则可视为补完
      },
      onError: (err, scope) => {
        console.warn('[Audio:' + scope + ']', err);
      },
    });

    this.audioManager.ready().catch((err) => {
      console.error('Opus runtime not ready:', err);
      wx.showToast({ title: '音频引擎加载失败', icon: 'none' });
    });
  },

  _initWebSocketManager() {
    const g = app.globalData;
    this.wsManager = new WebSocketManager({
      onStateChange: (state) => {
        this.setData({ connectionState: state });
        if (state === 'disconnected') {
          // 连接断开时禁用自动重连，等待用户手动召唤
          try {
            this.wsManager.disconnect();
          } catch (_) {}
        }
        if (state === 'disconnected' && this.data.chatState !== STATE_IDLE) {
          // 连接断开时把会话状态拉回 idle
          this.setData({ chatState: STATE_IDLE, currentReply: '' });
          if (this.audioManager) {
            try { this.audioManager.stopPlayback(); } catch (_) {}
          }
        }
      },
      onMessage: (msg) => this._handleWSMessage(msg),
      onError: (err, scope) => {
        console.warn('[WS:' + scope + ']', err && err.message ? err.message : err);
      },
    });
    // 不在这里自动连接，等待用户手动触发
  },

  _connectToChat() {
    if (!this.wsManager) return;
    const g = app.globalData;
    if (!g || !g.wsUrl || !g.virtualMAC) {
      wx.showToast({ title: '连接信息未就绪', icon: 'none' });
      return;
    }
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

      case 'audio':
        // 二进制 Opus 帧 → 解码播放
        if (this.audioManager) this.audioManager.appendOpusFrame(msg.data);
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
        // 同理：一次性完成 messages 追加 + currentReply 清空
        if ((this.data.currentReply || '').trim()) {
          const id = 'msg-' + (this._msgIdSeed++);
          const now = new Date();
          const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
          const messages = this.data.messages.concat([{ id, role: 'assistant', content: this.data.currentReply.trim(), time }]);
          this.setData({ messages, scrollToView: id, chatState: STATE_IDLE, currentReply: '' });
        } else {
          this.setData({ chatState: STATE_IDLE });
        }
        break;

      default:
        // 未知消息：忽略
        break;
    }
  },

  _handleTtsState(msg) {
    if (msg.state === 'start') {
      this.setData({ chatState: STATE_SPEAKING });
    } else if (msg.state === 'sentence_start' || msg.state === 'sentence_end') {
      // 服务端通过 tts 消息发送文本内容，累加到 currentReply
      if (msg.text) {
        this.setData({
          currentReply: (this.data.currentReply || '') + msg.text,
          chatState: STATE_SPEAKING,
        });
        this._scrollToBottom();
      }
    } else if (msg.state === 'stop') {
      const reply = (this.data.currentReply || '').trim();
      if (reply) {
        // 在同一次 setData 中完成：追加到 messages + 清空 currentReply + 更新 scrollToView
        // 避免分两次渲染导致内容从流式区域"跳动"到消息列表
        const id = 'msg-' + (this._msgIdSeed++);
        const now = new Date();
        const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
        const messages = this.data.messages.concat([{ id, role: 'assistant', content: reply, time }]);
        this.setData({ messages, scrollToView: id, chatState: STATE_IDLE, currentReply: '' });
      } else {
        this.setData({ chatState: STATE_IDLE, currentReply: '' });
      }
    }
  },

  _addMessage(role, content) {
    const id = 'msg-' + (this._msgIdSeed++);
    const now = new Date();
    const time = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
    const messages = this.data.messages.concat([{ id, role, content, time }]);
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
      this._connectToChat();
    }
  },

  onSummon() {
    this._connectToChat();
  },

  onDisconnect() {
    if (this.wsManager && this.data.connectionState === 'connected') {
      try {
        this.wsManager.disconnect();
      } catch (_) {}
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
      wx.showToast({ title: '请先点击召唤按钮连接服务', icon: 'none' });
      return false;
    }
    return true;
  },

  _reconnect() {
    this._connectToChat();
  },

  _handleAppShow() {
    // 从后台恢复：不再自动重连，需要用户手动点击召唤按钮
    // 保持当前连接状态，如果已断开则等待用户操作
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
    if (this.audioManager) {
      try { this.audioManager.destroy(); } catch (_) {}
      this.audioManager = null;
    }
  },
});
