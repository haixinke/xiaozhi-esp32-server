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
const logger = require('../../utils/logger');
const { get, del } = require('../../utils/request');
const { getTheme, applyTheme } = require('../../utils/theme');

const app = getApp();

const STATE_IDLE = 'idle';
const STATE_THINKING = 'thinking';
const STATE_SPEAKING = 'speaking';

// 两条消息间隔超过该阈值才显示居中时间分隔条
const TIME_GAP_MS = 5 * 60 * 1000;

// 空闲多久无交互自动断开连接（控制后端会话成本，对用户无感，下次发消息秒级重连）
const IDLE_TIMEOUT_MS = 5 * 60 * 1000;

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
    scrollTop: 0,
    scrollWithAnimation: true,

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
    hasMessageDelete: false,     // 是否有消息撤回权限

    // 聊天配额
    chatRemaining: -1,           // 剩余次数：-1=不限, >=0 显示剩余
    showQuotaExceeded: false,    // 是否显示配额耗尽弹窗

    // 多功能浮窗
    showToolPanel: false,

    // 悬浮通话小球位置
    floatingBallTop: 400,
  },

  // 非响应式资源，挂在 this 上以避免 setData 开销
  audioManager: null,
  wsManager: null,
  _bootTimer: null,
  _idleTimer: null,
  _pendingText: '',
  _appShowHandler: null,
  _msgIdSeed: 1,
  _streamingIdx: -1,
  _streamingBuffer: '',
  _pendingEmoji: '',
  _flushTimer: null,
  _historyPage: 1,
  _historyLoading: false,
  _historyNoMore: false,
  _historyNonce: 0,
  _sessionStartTime: null,
  _voiceStartY: 0,
  _scrollTop: 0,

  // 生成本地时间的 ISO 格式字符串（与后端 LocalDateTime 对齐）
  _formatLocalDateTime(date) {
    const pad = (n, len) => String(n).padStart(len || 2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
      + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds())
      + '.' + pad(date.getMilliseconds(), 3);
  },

  // 解析服务端 createdAt（ISO-8601 带时区）或数字时间戳为毫秒
  _parseTime(value) {
    if (!value) return Date.now();
    if (typeof value === 'number') return value;
    // iOS 不支持 "yyyy-MM-dd HH:mm:ss" 格式，需要替换成 ISO 8601 的 "T" 分隔格式
    const normalized = value.replace(' ', 'T');
    const t = new Date(normalized).getTime();
    return isNaN(t) ? Date.now() : t;
  },

  // 判断两个时间戳是否落在同一天
  _isSameDay(a, b) {
    if (!a || !b) return false;
    const da = new Date(this._parseTime(a));
    const db = new Date(this._parseTime(b));
    return da.getFullYear() === db.getFullYear()
      && da.getMonth() === db.getMonth()
      && da.getDate() === db.getDate();
  },

  // 时间分隔条文案：今天 HH:mm；昨天「昨天 HH:mm」；同年更早「M月D日」；否则带年份
  _formatTimeLabel(ms) {
    const d = new Date(ms);
    const now = new Date();
    const pad = (n) => (n < 10 ? '0' + n : '' + n);
    const hm = pad(d.getHours()) + ':' + pad(d.getMinutes());
    if (this._isSameDay(ms, now.getTime())) return hm;
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (this._isSameDay(ms, yesterday.getTime())) return '昨天 ' + hm;
    if (d.getFullYear() === now.getFullYear()) {
      return (d.getMonth() + 1) + '月' + d.getDate() + '日';
    }
    return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日';
  },

  // 给消息数组打 showTime / timeLabel：首条，或与上一条间隔≥5分钟，或跨天，则显示分隔条
  _stampSeparators(messages) {
    const n = messages.length;
    if (n === 0) return messages;
    const out = [];
    let prevTime = 0;
    for (let i = 0; i < n; i++) {
      const m = messages[i];
      const t = m.time || 0;
      const show = i === 0 || (t - prevTime >= TIME_GAP_MS) || !this._isSameDay(t, prevTime);
      out.push(show
        ? Object.assign({}, m, { showTime: true, timeLabel: this._formatTimeLabel(t) })
        : Object.assign({}, m, { showTime: false, timeLabel: '' }));
      prevTime = t;
    }
    return out;
  },

  // -------------------------------------------------------------------------
  // 生命周期
  // -------------------------------------------------------------------------

  onLoad() {
    this._waitForAppReady()
      .then(() => this._bootstrap())
      .catch((err) => {
        if (err.message === 'redirecting to destiny') return;
        logger.error('启动失败:', err);
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

    // 切后台时自动挂断语音通话
    this._appHideHandler = () => this._handleAppHide();
    if (wx.onAppHide) wx.onAppHide(this._appHideHandler);
  },

  onShow() {
    applyTheme(this);

    // 订阅档位变更或重塑命运（换职业/性格/声音）后，自动以最新 agent 配置重连（connect 会先断开旧会话）
    const g0 = app.globalData;
    const needFreshConfig = g0 && (g0.needReconnectAfterSub || g0.needReconnectAfterReshape);
    if (g0) {
      g0.needReconnectAfterSub = false;
      g0.needReconnectAfterReshape = false;
    }
    if (needFreshConfig && this.wsManager) {
      this._pendingText = '';
      this._connectToChat();
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
        logger.error('重试绑定失败:', err);
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
    this._initWebSocketManager();
    // 进入页面即自动连接，呈现「在场」；空闲超时会自动断开以控成本
    this._connectToChat();

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
      const finalHeight = scrollViewHeight > 0 ? scrollViewHeight : windowHeight * 0.7;
      this.setData({ scrollViewHeight: finalHeight, floatingBallTop: windowHeight * 0.55 }, () => {
        // 有长期记忆权限时，进入页面自动加载一批历史消息，让列表可滚动
        if (this.data.hasLongTermMemory) {
          this._loadHistoryMessages();
        }
      });
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
        logger.warn('[Audio:' + scope + ']', err);
        if (scope === 'record') {
          // 录音启动失败（常见于麦克风权限未授予，RecorderManager 报 file error）：
          // 复位录音状态并明确提示，避免用户面对“按住无反应”无法定位。
          this.setData({ recording: false, recordCancelled: false });
          wx.showToast({ title: '录音失败，请在设置中开启麦克风权限', icon: 'none' });
        }
      },
    });

    this.audioManager.ready().catch((err) => {
      logger.error('Opus runtime not ready:', err);
      wx.showToast({ title: '音频引擎加载失败', icon: 'none' });
    });
  },

  _initWebSocketManager() {
    this.wsManager = new WebSocketManager({
      onStateChange: (state) => {
        this.setData({ connectionState: state });
        if (state === 'connected') {
          // 连接建立后开始空闲计时
          this._resetIdleTimer();
        }
        if (state === 'disconnected') {
          // 网络抖动断开交给 websocket.js 的退避重连自愈；主动断开（退下/空闲）不会走到这里触发重连
          // 若有待补发消息但连接最终断开，回填输入框并提示
          if (this._pendingText) {
            const pending = this._pendingText;
            this._pendingText = '';
            this.setData({ inputText: pending });
            wx.showToast({ title: '发送失败，请重试', icon: 'none' });
          }
        }
        if (state === 'disconnected' && this.data.chatState !== STATE_IDLE) {
          // 连接断开时把会话状态拉回 idle
          this.setData({ chatState: STATE_IDLE });
          this._streamingIdx = -1;
          this._streamingBuffer = '';
          this._pendingEmoji = '';
          if (this._flushTimer) { clearTimeout(this._flushTimer); this._flushTimer = null; }
          if (this.audioManager) {
            try { this.audioManager.stopPlayback(); } catch (_) {}
          }
        }
      },
      onMessage: (msg) => this._handleWSMessage(msg),
      onError: (err, scope) => {
        logger.warn('[WS:' + scope + ']', err && err.message ? err.message : err);
      },
    });
  },

  _connectToChat() {
    if (!this.wsManager) return false;
    const g = app.globalData;
    if (!g || !g.wsUrl || !g.virtualMAC) {
      wx.showToast({ title: '连接信息未就绪', icon: 'none' });
      return false;
    }
    this.wsManager.connect(g.wsUrl, g.virtualMAC, g.wsToken);
    return true;
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
        // 握手完成：补发空闲断开期间排队的消息
        if (this._pendingText) {
          const pending = this._pendingText;
          this._pendingText = '';
          try {
            this.wsManager.sendText(pending);
            // 补发成功后递减本地配额计数
            var newRemaining = this.data.chatRemaining;
            if (newRemaining > 0) {
              newRemaining = newRemaining - 1;
            }
            this.setData({ chatState: STATE_THINKING, chatRemaining: newRemaining });
          } catch (err) {
            logger.error('补发文本失败:', err);
            this.setData({ inputText: pending, chatState: STATE_IDLE });
            wx.showToast({ title: '发送失败，请重试', icon: 'none' });
          }
        }
        this._resetIdleTimer();
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
        // 本产品 llm 仅承载一个情绪 emoji（服务端 get_emotion），作为整条回复首个气泡的前缀，
        // 不再单独成泡，正文由后续 tts sentence_start 逐句下发
        if (msg.text) this._pendingEmoji = msg.text;
        break;

      case 'tts':
        this._handleTtsState(msg);
        break;

      case 'goodbye':
        this._finalizeStreaming(STATE_IDLE);
        break;

      case 'quota_exceeded':
        // 配额耗尽：显示升级引导弹窗，不在聊天流中插入消息
        this.setData({
          chatRemaining: 0,
          showQuotaExceeded: true,
          chatState: STATE_IDLE,
        });
        break;

      default:
        // 未知消息：忽略
        break;
    }
  },

  _handleTtsState(msg) {
    if (msg.state === 'start') {
      this.setData({ chatState: STATE_SPEAKING });
    } else if (msg.state === 'sentence_start') {
      // 一句一气泡：像真人发微信一样陆续冒出，节奏由 TTS 逐句下发天然决定
      if (msg.text) this._pushSentenceBubble(msg.text);
    } else if (msg.state === 'stop') {
      this._finalizeStreaming(STATE_IDLE);
      this._pendingEmoji = '';
    }
  },

  // 把一整句作为独立气泡推入：先定格上一句，再开新气泡（首句带上情绪 emoji 前缀）
  _pushSentenceBubble(text) {
    this._finalizeStreaming(STATE_SPEAKING);
    const emoji = this._pendingEmoji || '';
    this._pendingEmoji = '';
    const content = (emoji ? emoji + ' ' : '') + text;
    this._appendStreamingText(content, STATE_SPEAKING);
  },

  _addMessage(role, content) {
    const id = 'msg-' + (this._msgIdSeed++);
    const messages = this.data.messages.concat([{ id, role, content, audioId: '', time: Date.now() }]);
    this.setData({ messages: this._stampSeparators(messages) }, () => {
      this.setData({ scrollToView: id });
    });
  },

  _onConversationScroll(e) {
    this._scrollTop = (e && e.detail && e.detail.scrollTop) || 0;
  },

  _onScrollToUpper() {
    if (this._historyLoading || this._historyNoMore) return;
    if (!this._sessionStartTime) return;
    this._loadHistoryMessages();
  },

  _queryMessageOffset(id, callback) {
    if (!id) {
      callback(0);
      return;
    }
    const safeId = (typeof CSS !== 'undefined' && CSS.escape) ? CSS.escape(id) : id;
    const query = wx.createSelectorQuery().in(this);
    query.select('.conversation').boundingClientRect();
    query.select('#' + safeId).boundingClientRect();
    query.exec((res) => {
      const conversationRect = res[0];
      const msgRect = res[1];
      if (!conversationRect || !msgRect) {
        callback(0);
        return;
      }
      callback(msgRect.top - conversationRect.top + this._scrollTop);
    });
  },

  _queryMessageAnchor(id, callback) {
    if (!id) {
      callback({ offsetTop: 0, viewportOffset: 0 });
      return;
    }
    const safeId = (typeof CSS !== 'undefined' && CSS.escape) ? CSS.escape(id) : id;
    const query = wx.createSelectorQuery().in(this);
    query.select('.conversation').boundingClientRect();
    query.select('#' + safeId).boundingClientRect();
    query.exec((res) => {
      const conversationRect = res[0];
      const msgRect = res[1];
      if (!conversationRect || !msgRect) {
        callback({ offsetTop: 0, viewportOffset: 0 });
        return;
      }
      const viewportOffset = msgRect.top - conversationRect.top;
      callback({
        offsetTop: viewportOffset + this._scrollTop,
        viewportOffset,
      });
    });
  },

  _loadHistoryMessages() {
    if (this._historyLoading || this._historyNoMore) return;
    if (!this.data.hasLongTermMemory) {
      this._showContractPopup('签订契约后即可查看和女友的文字、音频聊天回忆');
      this._historyNoMore = true;
      this.setData({ historyNoMore: true });
      return;
    }

    // 选取当前最顶部消息作为视觉锚点，保持加载前后视觉位置不变
    const anchorId = (this.data.messages[0] && this.data.messages[0].id) || '';

    this._queryMessageAnchor(anchorId, (anchor) => {
      const anchorViewportOffset = anchor.viewportOffset;

      this._historyLoading = true;
      this.setData({ historyLoading: true });

      const g = app.globalData;
      const params = {
        agentId: g.agentId,
        macAddress: g.virtualMAC,
        page: this._historyPage,
        limit: 15,
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

        // 映射为消息格式，API 返回按 created_at DESC，需要反转为时间正序
        const historyMsgs = list.reverse().map((item, idx) => ({
          id: 'hist-' + this._historyPage + '-' + idx + '-' + (this._historyNonce++),
          messageId: item.id,           // 后端消息ID，用于撤回
          role: item.chatType === 1 ? 'user' : 'assistant',
          content: item.content,
          audioId: item.audioId || '',
          time: this._parseTime(item.createdAt),
        }));

        // 前置插入到消息数组
        const messages = historyMsgs.concat(this.data.messages);
        this._historyPage++;
        this._historyLoading = false;

        const noMore = list.length < 15;
        const nextData = {
          messages: this._stampSeparators(messages),
          historyLoading: false,
          scrollToView: '',
        };
        if (noMore) {
          this._historyNoMore = true;
          nextData.historyNoMore = true;
        }

        this.setData(nextData, () => {
          if (anchorId) {
            this._queryMessageOffset(anchorId, (newOffset) => {
              const newScrollTop = newOffset - anchorViewportOffset;
              this.setData({ scrollWithAnimation: false, scrollTop: newScrollTop }, () => {
                this.setData({ scrollWithAnimation: true });
              });
            });
          } else {
            // 初始进入页面加载历史时，滚动到最新消息（最底部）
            this._scrollToBottom();
          }
        });
      }).catch(() => {
        this._historyLoading = false;
        this.setData({ historyLoading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
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
      const messages = this.data.messages.concat([{ id, role: 'assistant', content: text, typing: true, audioId: '', time: Date.now() }]);
      this._streamingIdx = messages.length - 1;
      this._streamingBuffer = text;
      this.setData({ messages: this._stampSeparators(messages), chatState }, () => {
        this.setData({ scrollToView: id });
      });
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
      this.setData({ messages: this._stampSeparators(messages), chatState });
    } else {
      const messages = this.data.messages.slice();
      messages[idx] = { id: msg.id, role: msg.role, content: content, time: msg.time };
      this.setData({ messages: this._stampSeparators(messages), chatState });
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
    return this._connectToChat();
  },

  onDisconnect() {
    if (this.wsManager && this.data.connectionState === 'connected') {
      try {
        this.wsManager.disconnect();
      } catch (_) {}
    }
  },

  onAvatarTap() {
    wx.showToast({ title: '女友私密空间即将上线', icon: 'none', duration: 2000 });
  },

  onInputTap() {
    // 已进入页面即自动连接；空闲断开后由发送消息触发重连，无需手动召唤
  },

  onTextInput(e) {
    this.setData({ inputText: e.detail.value });
  },

  onTextSend() {
    const text = (this.data.inputText || '').trim();
    if (!text) return;

    // 配额检查：本地计数达零时客户端直接拦截
    if (this.data.chatRemaining === 0) {
      this.setData({ showQuotaExceeded: true });
      return;
    }

    // 未连接（空闲断开/连接中）：排队该消息，触发重连，握手完成后自动补发
    if (this.data.connectionState !== 'connected') {
      this._pendingText = text;
      this.setData({ inputText: '', chatState: STATE_THINKING });
      if (this.data.connectionState === 'disconnected') {
        this._connectToChat();
        wx.showToast({ title: '她正在赶来…', icon: 'none' });
      }
      return;
    }

    // 发送文本消息到服务器
    try {
      this.wsManager.sendText(text);
    } catch (err) {
      logger.error('发送文本失败:', err);
      wx.showToast({ title: '发送失败，请重试', icon: 'none' });
      return;
    }

    this._resetIdleTimer();

    // 清空输入框并更新状态
    // 注意：不在这里添加用户消息，等待服务端的 stt 消息来触发显示
    // 发送成功后递减本地配额计数
    var newRemaining = this.data.chatRemaining;
    if (newRemaining > 0) {
      newRemaining = newRemaining - 1;
    }
    this.setData({
      inputText: '',
      chatState: STATE_THINKING,
      chatRemaining: newRemaining,
    });
  },

  async onToggleInputMode() {
    if (!this.data.hasVoiceInput) {
      this._showContractPopup('签订契约后即可使用语音输入与女友聊天');
      return;
    }
    const next = this.data.inputMode === 'text' ? 'voice' : 'text';
    // 切到语音模式前先确保麦克风权限，否则清缓存后授权丢失会导致
    // RecorderManager.start 报 file error，录不出任何音频。
    if (next === 'voice') {
      const permitted = await this._ensureRecordPermission();
      if (!permitted) return;
    }
    this.setData({ inputMode: next });
  },

  _showContractPopup(content) {
    wx.showModal({
      title: '甜蜜契约',
      content,
      showCancel: true,
      cancelText: '知道了',
      confirmText: '去订阅',
      confirmColor: '#864e5a',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/subscription/subscription?from=voiceCall&tab=gold' });
        }
      },
    });
  },

  // 长按消息：仅 Gold 套餐（message_delete 权益）可撤回，且仅历史消息带后端 messageId
  onMsgLongPress(e) {
    const localId = e.currentTarget.dataset.id;
    const idx = this.data.messages.findIndex((m) => m.id === localId);
    if (idx < 0) { return; }
    const msg = this.data.messages[idx];

    // 仅用户自己发送的消息可撤回，女友消息不弹出任何入口
    if (msg.role !== 'user') { return; }

    if (!this.data.hasMessageDelete) {
      this._showContractPopup('签订契约后即可撤回与女友的历史消息');
      return;
    }
    if (!msg.messageId) {
      // 实时消息由服务端 stt 回显，无后端 messageId，当次会话内不支持撤回
      wx.showToast({ title: '该消息暂不支持撤回', icon: 'none' });
      return;
    }
    wx.showActionSheet({
      itemList: ['撤回'],
      success: (r) => {
        if (r.tapIndex === 0) {
          this.onRecall(msg, idx);
        }
      },
    });
  },

  onRecall(msg, idx) {
    del('/agent/chat-history/' + msg.messageId).then((res) => {
      if (res && res.code === 0) {
        this.setData({ [`messages[${idx}].recalled`]: true });
        return;
      }
      // 后端权益校验兜底（订阅过期/被降级）：10312 = ErrorCode.SUBSCRIPTION_FEATURE_DENIED
      if (res && res.code === 10312) {
        this._showContractPopup('签订契约后即可撤回与女友的历史消息');
      } else {
        wx.showToast({ title: (res && res.msg) || '撤回失败', icon: 'none' });
      }
    }).catch(() => {
      wx.showToast({ title: '撤回失败，请稍后重试', icon: 'none' });
    });
  },

  onQuotaUpgrade() {
    this.setData({ showQuotaExceeded: false });
    wx.navigateTo({ url: '/pages/subscription/subscription?from=quotaUpgrade' });
  },

  onQuotaDismiss() {
    this.setData({ showQuotaExceeded: false });
  },

  onToolPanelToggle() {
    this.setData({ showToolPanel: !this.data.showToolPanel });
  },

  onToolPanelMaskTap() {
    this.setData({ showToolPanel: false });
  },

  onToolPanelCatch() {
    // 阻止冒泡，避免点击面板自身关闭浮窗
  },

  async onVoiceCallTap() {
    this.setData({ showToolPanel: false });

    // 1. 权益检查
    if (!this._hasVoiceCallFeature()) {
      this._showContractPopup('签订契约后即可与女友语音通话');
      return;
    }

    // 2. 权限检查
    const permitted = await this._ensureRecordPermission();
    if (!permitted) return;

    // 3. 确保 WebSocket 已连接
    if (this.data.connectionState !== 'connected') {
      const summoned = this.onSummon();
      if (!summoned) {
        wx.showToast({ title: '连接失败，请重试', icon: 'none' });
        return;
      }
      const connected = await this._waitForConnection(10000);
      if (!connected) {
        wx.showToast({ title: '连接失败，请重试', icon: 'none' });
        return;
      }
    }

    // 4. 初始化通话状态并跳转
    const VoiceCallManager = require('../../utils/voice-call-manager');
    VoiceCallManager().startCall();
    wx.navigateTo({ url: '/pages/voice-call/voice-call' });
  },

  _waitForConnection(timeoutMs) {
    return new Promise((resolve) => {
      if (this.data.connectionState === 'connected') {
        resolve(true);
        return;
      }
      if (!this.wsManager) {
        resolve(false);
        return;
      }

      // connect() 可能已在注册监听器前同步切换到 connecting
      let sawConnecting = this.wsManager.state === 'connecting' || this.data.connectionState === 'connecting';

      const timer = setTimeout(() => {
        if (this.wsManager) this.wsManager.offStateChange(handler);
        resolve(false);
      }, timeoutMs);

      const handler = (state) => {
        if (state === 'connecting') {
          sawConnecting = true;
          return;
        }
        if (state === 'connected') {
          clearTimeout(timer);
          if (this.wsManager) this.wsManager.offStateChange(handler);
          resolve(true);
          return;
        }
        // 连接失败后立即退出，避免空等到超时
        if (sawConnecting && state === 'disconnected') {
          clearTimeout(timer);
          if (this.wsManager) this.wsManager.offStateChange(handler);
          resolve(false);
        }
      };

      this.wsManager.onStateChange(handler);
    });
  },

  _hasVoiceCallFeature() {
    const features = (app.globalData && app.globalData.subscriptionFeatures) || [];
    return features.indexOf('voice_call') !== -1;
  },

  _ensureRecordPermission() {
    return new Promise((resolve) => {
      wx.getSetting({
        success: (res) => this._handleRecordSetting(res, resolve),
        fail: () => resolve(false),
      });
    });
  },

  _handleRecordSetting(res, resolve) {
    const auth = res.authSetting && res.authSetting['scope.record'];
    if (auth === true) {
      resolve(true);
      return;
    }
    if (auth === false) {
      this._openRecordSettings(resolve);
      return;
    }
    this._requestRecordAuthorize(resolve);
  },

  _openRecordSettings(resolve) {
    wx.showModal({
      title: '需要麦克风权限',
      content: '语音通话需要访问您的麦克风',
      confirmText: '去设置',
      cancelText: '取消',
      success: (modalRes) => {
        if (!modalRes.confirm) {
          resolve(false);
          return;
        }
        wx.openSetting({
          success: (settingRes) => {
            resolve(!!(settingRes.authSetting && settingRes.authSetting['scope.record']));
          },
          fail: () => resolve(false),
        });
      },
    });
  },

  _requestRecordAuthorize(resolve) {
    wx.authorize({
      scope: 'scope.record',
      success: () => resolve(true),
      fail: () => {
        wx.showToast({ title: '需要麦克风权限', icon: 'none' });
        resolve(false);
      },
    });
  },

  onVoiceTouchStart(e) {
    if (!this._isReadyForAction()) return;
    if (this.data.chatState !== STATE_IDLE) return;

    // 兜底校验：已进入语音模式但无权益时，按住说话触发购买引导。
    // 提前 return 可防止 recording 被置为 true，因此 onVoiceTouchEnd 会自然跳过。
    if (!this.data.hasVoiceInput) {
      this._showContractPopup('签订契约后即可使用语音输入与女友聊天');
      return;
    }

    // 配额检查：免费用户配额耗尽时拦截语音输入
    if (this.data.chatRemaining === 0) {
      this.setData({ showQuotaExceeded: true });
      return;
    }

    const touch = (e.touches && e.touches[0]) || {};
    this._voiceStartY = touch.clientY || 0;
    this.setData({ recording: true, recordCancelled: false });
    this._resetIdleTimer();

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
      // 必须复位 recording，否则后续 touchcancel 会通过其守卫误发 abort，
      // 中断刚由 listen stop 启动的 ASR，导致用户说话无回应。
      this.setData({ recording: false, recordCancelled: false, chatState: STATE_THINKING });
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

  // 语音模式专用就绪校验：语音不做音频缓冲补发，未连接时触发重连并提示用户重按
  _isReadyForAction() {
    if (this.data.connectionState !== 'connected') {
      if (this.data.connectionState === 'disconnected') {
        this._connectToChat();
      }
      wx.showToast({ title: '她正在赶来，稍候再按住说话', icon: 'none' });
      return false;
    }
    return true;
  },

  // 空闲计时：N 分钟无交互且会话空闲时主动断开，控制后端会话成本；下次交互秒级重连
  _resetIdleTimer() {
    if (this._idleTimer) {
      clearTimeout(this._idleTimer);
      this._idleTimer = null;
    }
    this._idleTimer = setTimeout(() => {
      this._idleTimer = null;
      if (this.data.connectionState !== 'connected') return;
      if (this.data.chatState !== STATE_IDLE) return;
      // 语音通话激活时跳过（其连接独立，但避免任何意外）
      try {
        const VoiceCallManager = require('../../utils/voice-call-manager');
        const callState = VoiceCallManager().getState().state;
        if (callState === 'connected' || callState === 'calling') return;
      } catch (_) {}
      if (this.wsManager) {
        try { this.wsManager.disconnect(); } catch (_) {}
      }
    }, IDLE_TIMEOUT_MS);
  },

  _reconnect() {
    this._connectToChat();
  },

  _handleAppShow() {
    // 从后台恢复：若连接已断开则自动重连，恢复「在场」
    if (this.wsManager && this.data.connectionState === 'disconnected') {
      this._connectToChat();
      this._resetIdleTimer();
    }
  },

  _handleAppHide() {
    // 切后台时自动挂断语音通话
    const VoiceCallManager = require('../../utils/voice-call-manager');
    const mgr = VoiceCallManager();
    const state = mgr.getState().state;
    if (state === 'connected' || state === 'calling') {
      mgr.hangup();
    }
  },

  _applyFeatures() {
    var features = (app.globalData && app.globalData.subscriptionFeatures) || [];
    var newHasLongTermMemory = features.indexOf('long_term_memory') !== -1;
    // 从无权限变为有权限时，重置历史加载状态，允许重新下拉
    if (!this.data.hasLongTermMemory && newHasLongTermMemory) {
      this._historyNoMore = false;
      this._historyPage = 1;
    }

    // 配额信息
    var quota = (app.globalData && app.globalData.chatQuota) || null;
    var chatRemaining = -1;
    if (quota && quota.remaining !== undefined && quota.remaining !== null) {
      chatRemaining = quota.remaining; // -1=无限, >=0 显示剩余
    }

    this.setData({
      hasVoiceInput: features.indexOf('voice_input') !== -1,
      hasLongTermMemory: newHasLongTermMemory,
      hasMessageDelete: features.indexOf('message_delete') !== -1,
      historyNoMore: this._historyNoMore,
      chatRemaining: chatRemaining,
    });
  },

  _teardown() {
    if (this._flushTimer) { clearTimeout(this._flushTimer); this._flushTimer = null; }
    if (this._idleTimer) { clearTimeout(this._idleTimer); this._idleTimer = null; }
    if (this._bootTimer) {
      clearInterval(this._bootTimer);
      this._bootTimer = null;
    }
    if (this._appShowHandler && wx.offAppShow) {
      try { wx.offAppShow(this._appShowHandler); } catch (_) {}
      this._appShowHandler = null;
    }
    if (this._appHideHandler && wx.offAppHide) {
      try { wx.offAppHide(this._appHideHandler); } catch (_) {}
      this._appHideHandler = null;
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
