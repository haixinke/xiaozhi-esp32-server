/**
 * pages/index/index.js
 *
 * 主页：语音对话控制器。
 * 负责：
 *   1. 等待 app 启动完成（登录 + 设备绑定）。
 *   2. 初始化 AudioManager 与 WebSocketManager 并桥接二者。
 *   3. 维护会话状态机：idle → listening → thinking → speaking → idle。
 *   4. 切后台/前台时按需重连。
 */

const AudioManager = require('../../utils/audio');
const WebSocketManager = require('../../utils/websocket');

const app = getApp();

const STATE_IDLE = 'idle';
const STATE_LISTENING = 'listening';
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

    // 启动加载态
    booting: true,

    // 文字输入
    inputText: '',

    // 语音按钮状态
    voicePressed: false,
    voiceCancelled: false,
    voiceStartY: 0,
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
    // 切后台时主动停录音（避免误录），但保留连接交给系统调度
    if (this.audioManager && this.data.chatState === STATE_LISTENING) {
      try { this.audioManager.stopRecord(); } catch (_) {}
      try { this.wsManager && this.wsManager.sendListenStop(); } catch (_) {}
      this.setData({ chatState: STATE_IDLE });
    }
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

    this._initAudio();
    this._initWebSocket();
  },

  _initAudio() {
    this.audioManager = new AudioManager({
      onAudioFrame: (frame) => {
        // 录音回调：把 Opus 帧推到 WebSocket
        if (this.wsManager && this.wsManager.isConnected()) {
          this.wsManager.sendAudioFrame(frame);
        }
      },
      onRecordStart: () => {
        // 录音真正启动后再切到 listening（避免提前显示）
      },
      onRecordStop: () => {
        // 录音器停止；不在这里切状态，因为状态由用户手势 + 服务端响应驱动
      },
      onPlayEnd: () => {
        // 播放队列清空：若还在 speaking 状态则可视为补完
      },
      onError: (err, scope) => {
        console.warn('[Audio:' + scope + ']', err);
        if (scope === 'record') {
          wx.showToast({ title: '麦克风启动失败', icon: 'none' });
          this.setData({ chatState: STATE_IDLE });
        }
      },
    });

    this.audioManager.ready().catch((err) => {
      console.error('Opus runtime not ready:', err);
      wx.showToast({ title: '音频引擎加载失败', icon: 'none' });
    });
  },

  _initWebSocket() {
    const g = app.globalData;
    this.wsManager = new WebSocketManager({
      onStateChange: (state) => {
        this.setData({ connectionState: state });
        if (state === 'disconnected' && this.data.chatState !== STATE_IDLE) {
          // 连接断开时把会话状态拉回 idle
          this.setData({ chatState: STATE_IDLE, currentReply: '' });
          if (this.audioManager) {
            try { this.audioManager.stopRecord(); } catch (_) {}
            try { this.audioManager.stopPlayback(); } catch (_) {}
          }
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

      case 'audio':
        // 二进制 Opus 帧 → 解码播放
        if (this.audioManager) this.audioManager.appendOpusFrame(msg.data);
        break;

      case 'stt':
        if (msg.text) this._addMessage('user', msg.text);
        // STT 抵达后通常进入思考阶段
        if (this.data.chatState !== STATE_SPEAKING) {
          this.setData({ chatState: STATE_THINKING });
        }
        break;

      case 'llm':
        // 流式文本：累加到 currentReply
        if (msg.text) {
          this.setData({
            currentReply: (this.data.currentReply || '') + msg.text,
            chatState: this.data.chatState === STATE_LISTENING
              ? this.data.chatState
              : STATE_SPEAKING,
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
      this.setData({ chatState: STATE_SPEAKING });
    } else if (msg.state === 'sentence_start' || msg.state === 'sentence_end') {
      // 服务端通过 tts 消息发送文本内容，累加到 currentReply
      if (msg.text) {
        this.setData({
          currentReply: (this.data.currentReply || '') + msg.text,
          chatState: STATE_SPEAKING,
        });
        this._scrollToBottom();
      } else {
        this.setData({ chatState: STATE_SPEAKING });
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

  onVoiceStart(e) {
    if (!this._isReadyForAction()) return;

    const touch = (e.touches && e.touches[0]) || {};
    this.setData({
      voicePressed: true,
      voiceCancelled: false,
      voiceStartY: touch.clientY || 0,
    });

    // 若 AI 正在说话，则先打断
    if (this.data.chatState === STATE_SPEAKING) {
      this._sendAbort();
    }

    this.wsManager.sendListenStart();
    this.audioManager.startRecord();
    this.setData({
      chatState: STATE_LISTENING,
      currentReply: '',
    });
  },

  onVoiceMove(e) {
    if (!this.data.voicePressed) return;
    const touch = (e.touches && e.touches[0]) || {};
    const dy = (this.data.voiceStartY || 0) - (touch.clientY || 0);
    // 上滑超过 80px 视为取消
    const cancelled = dy > 80;
    if (cancelled !== this.data.voiceCancelled) {
      this.setData({ voiceCancelled: cancelled });
      // 可以在这里添加震动反馈
    }
  },

  onVoiceEnd() {
    if (!this.data.voicePressed) return;
    const cancelled = this.data.voiceCancelled;
    this.setData({ voicePressed: false, voiceCancelled: false });

    try { this.audioManager.stopRecord(); } catch (_) {}
    try { this.wsManager.sendListenStop(); } catch (_) {}

    if (cancelled) {
      // 取消录音
      this._sendAbort();
      this.setData({ chatState: STATE_IDLE, currentReply: '' });
      wx.showToast({ title: '已取消', icon: 'none' });
    } else {
      // 正常结束
      if (this.data.chatState !== STATE_SPEAKING) {
        this.setData({ chatState: STATE_THINKING });
      }
    }
  },

  onVoiceCancel() {
    if (!this.data.voicePressed) return;
    this.setData({ voicePressed: false, voiceCancelled: false });

    try { this.audioManager.stopRecord(); } catch (_) {}
    try { this.wsManager.sendListenStop(); } catch (_) {}

    this._sendAbort();
    this.setData({ chatState: STATE_IDLE, currentReply: '' });
  },

  onAbort() {
    if (this.data.chatState !== STATE_SPEAKING) return;
    this._sendAbort();
    this.setData({ chatState: STATE_IDLE, currentReply: '' });
  },

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

    // 若 AI 正在说话，则先打断
    if (this.data.chatState === STATE_SPEAKING) {
      this._sendAbort();
    }

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
    try { this.audioManager && this.audioManager.stopPlayback(); } catch (_) {}
  },

  _isReadyForAction() {
    if (this.data.connectionState !== 'connected') {
      wx.showToast({ title: '正在连接服务...', icon: 'none' });
      this._reconnect();
      return false;
    }
    if (!this.audioManager) return false;
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
    if (this.audioManager) {
      try { this.audioManager.destroy(); } catch (_) {}
      this.audioManager = null;
    }
  },
});
