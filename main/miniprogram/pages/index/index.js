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
const { get } = require('../../utils/request');
const { getTheme, applyTheme } = require('../../utils/theme');

const app = getApp();

const STATE_IDLE = 'idle';
const STATE_THINKING = 'thinking';
const STATE_SPEAKING = 'speaking';

Page({
  data: {
    // 主题
    darkMode: getTheme(),

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

    // 历史记录加载状态
    historyLoading: false,
    historyNoMore: false,

    // 启动加载态：初始为 true，确认不需要跳转后才显示 UI
    booting: true,

    // 设备绑定失败，显示重试UI
    bindFailed: false,

    // 文字输入
    inputText: '',

    // 输入模式：text / voice
    inputMode: 'text',

    // 录音状态
    recording: false,
    recordCancelled: false,

    // scroll-view 动态高度（px）
    scrollViewHeight: 0,

    // 订阅权益灰度
    hasVoiceInput: false,        // 是否有语音输入权限
    hasLongTermMemory: false,    // 是否有聊天历史权限
  },

  // 非响应式资源，挂在 this 上以避免 setData 开销
  audioManager: null,
  wsManager: null,
  _bootTimer: null,
  _appShowHandler: null,
  _msgIdSeed: 1,
  _streamingIdx: -1,
  _streamingBuffer: '',
  _flushTimer: null,
  _historyPage: 1,
  _historyLoading: false,
  _historyNoMore: false,
  _sessionStartTime: null,
  _voiceStartY: 0,

  // 生成本地时间的 ISO 格式字符串（与后端 LocalDateTime 对齐）
  _formatLocalDateTime(date) {
    const pad = (n, len) => String(n).padStart(len || 2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
      + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds())
      + '.' + pad(date.getMilliseconds(), 3);
  },

  // -------------------------------------------------------------------------
  // 生命周期
  // -------------------------------------------------------------------------

  onLoad() {
    this._waitForAppReady()
      .then(() => this._bootstrap())
      .catch((err) => {
        if (err.message === 'redirecting to destiny') return;
        console.error('启动失败:', err);
        if (err.message === 'device not bound') {
          this.setData({ booting: false, bindFailed: true });
        } else {
          this.setData({ booting: false });
          wx.showToast({ title: '启动失败，请重试', icon: 'none' });
        }
      });

    // 监听小程序切回前台 → 触发重连
    this._appShowHandler = () => this._handleAppShow();
    if (wx.onAppShow) wx.onAppShow(this._appShowHandler);
  },

  onShow() {
    applyTheme(this);

    // 订阅档位变更后，需要断开当前连接，由用户手动「召唤」重新拉取最新 agent 配置
    const g0 = app.globalData;
    if (g0 && g0.needReconnectAfterSub) {
      g0.needReconnectAfterSub = false;
      if (this.wsManager && this.data.connectionState === 'connected') {
        this.wsManager.disconnect();
      }
    }

    // 每次返回页面（例如从设置页面返回）刷新数据
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
      this._applyFeatures();
    }
  },

  onRetryBind() {
    this.setData({ booting: true, bindFailed: false });
    if (this._bootTimer) { clearInterval(this._bootTimer); this._bootTimer = null; }
    app.globalData.isDeviceBound = undefined;
    app.checkDeviceStatus()
      .then(() => this._waitForAppReady())
      .then(() => this._bootstrap())
      .catch((err) => {
        if (err.message === 'redirecting to destiny') return;
        console.error('重试绑定失败:', err);
        if (err.message === 'device not bound') {
          this.setData({ booting: false, bindFailed: true });
        } else {
          this.setData({ booting: false });
          wx.showToast({ title: '启动失败，请重试', icon: 'none' });
        }
      });
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
          wx.redirectTo({ url: '/pages/welcome/welcome' });
          reject(new Error('redirecting to destiny'));
          return true;
        }

        if (g.token && g.virtualMAC && g.isDeviceBound && g.wsUrl && g.companionDataLoaded) {
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
    this._applyFeatures();
    this.setData({
      agentName: g.agentName || '',
      companionAvatar: g.companionAvatar || '',
      companionBgImage: g.companionBgImage || '',
      booting: false,
    });

    // 确保订阅权益已加载，再刷新一次 features（fetchSubscription 可能还没返回）
    if (app.fetchSubscription) {
      app.fetchSubscription().then(() => this._applyFeatures());
    }

    // 记录页面启动时间，用于下拉加载历史的 createdBefore 过滤
    this._sessionStartTime = this._formatLocalDateTime(new Date());

    this._initAudio();
    // 不再自动连接 WebSocket，等待用户点击"召唤"按钮
    this._initWebSocketManager();

    // 等待视图渲染完毕后计算 scroll-view 高度
    setTimeout(() => {
      this._calcScrollViewHeight();
    }, 100);
  },

  _calcScrollViewHeight() {
    const query = wx.createSelectorQuery();
    query.select('.top-bar').boundingClientRect();
    query.select('.input-bar').boundingClientRect();
    query.exec((res) => {
      const windowHeight = wx.getWindowInfo().windowHeight;
      const topBarHeight = (res[0] && res[0].height) || 0;
      const inputBarHeight = (res[1] && res[1].height) || 0;
      const scrollViewHeight = windowHeight - topBarHeight - inputBarHeight;
      if (scrollViewHeight > 0) {
        this.setData({ scrollViewHeight });
      } else {
        // 兜底：使用窗口高度的70%作为默认值
        this.setData({ scrollViewHeight: windowHeight * 0.7 });
      }
    });
  },

  _initAudio() {
    this.audioManager = new AudioManager({
      onAudioFrame: (frame) => {
        // 录音产生的 Opus 帧 → 通过 WebSocket 发送
        if (this.wsManager) {
          this.wsManager.sendAudioFrame(frame);
        }
      },
      onRecordStart: () => {
        this.setData({ recording: true });
      },
      onRecordStop: () => {
        this.setData({ recording: false, recordCancelled: false });
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
          this.setData({ chatState: STATE_IDLE });
          this._streamingIdx = -1;
          this._streamingBuffer = '';
          if (this._flushTimer) { clearTimeout(this._flushTimer); this._flushTimer = null; }
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
        // 用连接时间替换页面启动时间，更精确避免当前会话消息与历史重复
        this._sessionStartTime = this._formatLocalDateTime(new Date());
        break;

      case 'audio':
        // 二进制 Opus 帧 → 解码播放
        if (this.audioManager) this.audioManager.appendOpusFrame(msg.data);
        break;

      case 'stt':
        if (msg.text && !msg.text.startsWith('% ')) this._addMessage('user', msg.text);
        // STT 抵达后通常进入思考阶段
        this.setData({ chatState: STATE_THINKING });
        break;

      case 'llm':
        // 流式文本：就地更新 messages 中的流式消息
        if (msg.text) {
          this._appendStreamingText(msg.text, STATE_THINKING);
        }
        break;

      case 'tts':
        this._handleTtsState(msg);
        break;

      case 'goodbye':
        this._finalizeStreaming(STATE_IDLE);
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
      if (msg.text) {
        this._appendStreamingText(msg.text, STATE_SPEAKING);
      }
    } else if (msg.state === 'stop') {
      this._finalizeStreaming(STATE_IDLE);
    }
  },

  _addMessage(role, content) {
    const id = 'msg-' + (this._msgIdSeed++);
    const messages = this.data.messages.concat([{ id, role, content }]);
    this.setData({ messages, scrollToView: id });
  },

  // 下拉加载历史消息
  _onScrollToUpper() {
    if (this._historyLoading || this._historyNoMore) return;
    if (!this._sessionStartTime) return;
    this._loadHistoryMessages();
  },

  _loadHistoryMessages() {
    if (this._historyLoading || this._historyNoMore) return;
    if (!this.data.hasLongTermMemory) {
      wx.showModal({
        title: '甜蜜回忆',
        content: '签订契约后即可查看聊天回忆',
        showCancel: false,
        confirmText: '知道了',
        confirmColor: '#864e5a'
      });
      this._historyNoMore = true;
      this.setData({ historyNoMore: true });
      return;
    }
    this._historyLoading = true;
    this.setData({ historyLoading: true });

    const g = app.globalData;
    const params = {
      agentId: g.agentId,
      macAddress: g.virtualMAC,
      page: this._historyPage,
      limit: 4,
      createdBefore: this._sessionStartTime,
    };

    get('/agent/chat-history/list', params).then((res) => {
      const list = (res.data && res.data.list) || [];
      if (list.length === 0) {
        this._historyNoMore = true;
        this._historyLoading = false;
        this.setData({ historyNoMore: true, historyLoading: false });
        return;
      }

      // 记录当前滚动位置
      const anchorId = this.data.scrollToView;

      // 映射为消息格式，API 返回按 created_at DESC，需要反转为时间正序
      const historyMsgs = list.reverse().map((item, idx) => ({
        id: 'hist-' + this._historyPage + '-' + idx,
        role: item.chatType === 1 ? 'user' : 'assistant',
        content: item.content,
        audioId: item.audioId || null,
      }));

      // 前置插入到消息数组
      const messages = historyMsgs.concat(this.data.messages);
      this._historyPage++;
      this._historyLoading = false;

      if (list.length < 4) {
        this._historyNoMore = true;
        this.setData({ messages, historyNoMore: true, historyLoading: false });
      } else {
        this.setData({ messages, historyLoading: false });
      }

      // 恢复滚动位置到之前锚定的消息
      if (anchorId) {
        setTimeout(() => { this.setData({ scrollToView: anchorId }); }, 50);
      }
    }).catch(() => {
      this._historyLoading = false;
      this.setData({ historyLoading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  // 就地追加流式文本到 messages 中的流式消息，不销毁重建组件
  _appendStreamingText(text, chatState) {
    if (!text) return;
    if (this._streamingIdx !== -1) {
      // 后续 chunk：累积到本地 buffer，80ms 后批量刷到视图
      this._streamingBuffer += text;
      if (!this._flushTimer) {
        this._flushTimer = setTimeout(() => this._flushStreaming(), 80);
      }
    } else {
      // 第一个 chunk：立即创建气泡
      const id = 'msg-' + (this._msgIdSeed++);
      const messages = this.data.messages.concat([{ id, role: 'assistant', content: text, typing: true }]);
      this._streamingIdx = messages.length - 1;
      this._streamingBuffer = text;
      this.setData({ messages, scrollToView: id, chatState });
    }
  },

  // 将缓冲区的文本批量刷到 messages
  _flushStreaming() {
    this._flushTimer = null;
    if (this._streamingIdx === -1) return;
    const idx = this._streamingIdx;
    const messages = this.data.messages.slice();
    messages[idx] = Object.assign({}, messages[idx], { content: this._streamingBuffer });
    this.setData({ messages });
    this._scrollToBottom();
  },

  // 流式完成：先刷完残余 buffer，再关闭 typing
  _finalizeStreaming(chatState) {
    if (this._flushTimer) {
      clearTimeout(this._flushTimer);
      this._flushTimer = null;
    }
    if (this._streamingIdx === -1) {
      this.setData({ chatState });
      return;
    }
    const idx = this._streamingIdx;
    const msg = this.data.messages[idx];
    const content = (this._streamingBuffer || (msg && msg.content) || '').trim();
    this._streamingBuffer = '';
    if (!msg || !content) {
      const messages = !msg
        ? this.data.messages
        : this.data.messages.slice(0, idx).concat(this.data.messages.slice(idx + 1));
      this.setData({ messages, chatState });
    } else {
      const messages = this.data.messages.slice();
      messages[idx] = { id: msg.id, role: msg.role, content: content };
      this.setData({ messages, chatState });
    }
    this._streamingIdx = -1;
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

  // 下拉刷新事件处理（scroll-view refresher 触发）
  onPullDownRefresh() {
    this._onScrollToUpper();
  },

  onAvatarTap() {
    wx.showToast({ title: '女友私密空间即将上线', icon: 'none', duration: 2000 });
  },

  onInputTap() {
    if (this.data.connectionState !== 'connected') {
      wx.showToast({ title: '请先召唤您的女友', icon: 'none', duration: 2000 });
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
    });
  },

  onToggleInputMode() {
    const next = this.data.inputMode === 'text' ? 'voice' : 'text';
    this.setData({ inputMode: next });
  },

  onVoiceTouchStart(e) {
    if (!this._isReadyForAction()) return;
    if (this.data.chatState !== STATE_IDLE) return;

    const touch = (e.touches && e.touches[0]) || {};
    this._voiceStartY = touch.clientY || 0;
    this.setData({ recording: true, recordCancelled: false });

    // 开始录音并通知服务端开始监听
    if (this.audioManager) this.audioManager.startRecord();
    if (this.wsManager) this.wsManager.sendListenStart();
  },

  onVoiceTouchMove(e) {
    if (!this.data.recording) return;
    const touch = (e.touches && e.touches[0]) || {};
    const dy = this._voiceStartY - (touch.clientY || 0);
    const cancelled = dy > 80;
    if (cancelled !== this.data.recordCancelled) {
      this.setData({ recordCancelled: cancelled });
    }
  },

  onVoiceTouchEnd() {
    if (!this.data.recording) return;
    const cancelled = this.data.recordCancelled;

    // 停止录音
    if (this.audioManager) this.audioManager.stopRecord();

    if (cancelled) {
      // 取消：中断服务端处理
      if (this.wsManager) this.wsManager.sendAbort();
      this.setData({ recording: false, recordCancelled: false });
    } else {
      // 正常发送：通知服务端停止监听
      if (this.wsManager) this.wsManager.sendListenStop();
      this.setData({ chatState: STATE_THINKING });
    }
  },

  onVoiceTouchCancel() {
    if (!this.data.recording) return;
    if (this.audioManager) this.audioManager.stopRecord();
    if (this.wsManager) this.wsManager.sendAbort();
    this.setData({ recording: false, recordCancelled: false });
  },

  // -------------------------------------------------------------------------
  // 工具
  // -------------------------------------------------------------------------

  _sendAbort() {
    try { this.wsManager && this.wsManager.sendAbort(); } catch (_) {}
  },

  _isReadyForAction() {
    if (this.data.connectionState !== 'connected') {
      wx.showToast({ title: '请先召唤您的女友', icon: 'none' });
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

  _applyFeatures() {
    var features = (app.globalData && app.globalData.subscriptionFeatures) || [];
    var newHasLongTermMemory = features.indexOf('long_term_memory') !== -1;
    // 从无权限变为有权限时，重置历史加载状态，允许重新下拉
    if (!this.data.hasLongTermMemory && newHasLongTermMemory) {
      this._historyNoMore = false;
      this._historyPage = 1;
    }
    this.setData({
      hasVoiceInput: features.indexOf('voice_input') !== -1,
      hasLongTermMemory: newHasLongTermMemory,
      historyNoMore: this._historyNoMore,
    });
  },

  _teardown() {
    if (this._flushTimer) { clearTimeout(this._flushTimer); this._flushTimer = null; }
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
