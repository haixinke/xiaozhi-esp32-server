const petStore = require('../../utils/pet-store');
const petApi = require('../../utils/pet-api');
const ota = require('../../utils/ota');
const ageRangeApi = require('../../utils/age-range-api');
const WebSocketManager = require('../../utils/websocket');
const AudioManager = require('../../utils/audio');

const STATE_IDLE = 'idle';
const STATE_THINKING = 'thinking';
const STATE_SPEAKING = 'speaking';

// 两条消息间隔超过该阈值才显示居中时间分隔条
const TIME_GAP_MS = 5 * 60 * 1000;

// 连接状态文案（导航栏下方胶囊，仅非连接态显示）
const CONN_LABELS = {
  connected: '已连接',
  connecting: '连接中',
  disconnected: '未连接',
};

// 当前窗口高度（px），失败时返回 0
function windowHeight() {
  try {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    const height = Number(info && info.windowHeight);
    return Number.isFinite(height) && height > 0 ? Math.round(height) : 0;
  } catch (error) {
    return 0;
  }
}

// 页面根节点内联高度：窗口高减去键盘高，键盘弹出时压缩页面而非被顶起
function viewportStyle(windowHeightValue, keyboardHeight) {
  const availableHeight = Math.round(Number(windowHeightValue) || 0) - Math.max(0, Math.round(Number(keyboardHeight) || 0));
  return availableHeight > 0 ? `height:${availableHeight}px;` : '';
}

// 系统「减弱动画」开关
function reducedMotionEnabled() {
  try {
    const system = wx.getSystemSetting
      ? wx.getSystemSetting()
      : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : {});
    return Boolean(system.reducedMotion || system.enableReduceMotion);
  } catch (error) {
    return false;
  }
}

Page({
  data: {
    pet: null,
    title: '和蛋宝宝说说话',
    dailyStatus: null,
    messages: [],
    draft: '',
    canSend: false,
    booting: true,
    connectionState: 'disconnected',
    connLabel: CONN_LABELS.disconnected,
    chatState: STATE_IDLE,
    scrollAnchor: '',
    scrollTop: 0,
    scrollWithAnimation: true,
    historyLoading: false,
    historyNoMore: false,
    chatViewportStyle: '',
    keyboardHeight: 0,
    inputFocused: false,
    reducedMotion: false,
  },

  _msgIdSeed: 1,
  _streamingIdx: -1,
  _streamingBuffer: '',
  _flushTimer: null,
  _pendingText: '',
  _historyPage: 1,
  _historyLoading: false,
  _historyNoMore: false,
  _scrollTop: 0,
  _suspendedByHide: false,
  wsManager: null,
  audioManager: null,

  onLoad() {
    const pet = petStore.getPet();
    if (!pet || pet.hatchStatus !== 'HATCHED' || !pet.deviceId) {
      wx.showToast({ title: '破壳后才可以对话', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }

    const allMessages = pet.messages || [];
    if (!allMessages.length) {
      petStore.saveMessage({
        id: 'hello',
        role: 'assistant',
        content: '你来啦。我已经等你好一会儿了。',
        time: Date.now(),
      });
    }

    this.setData({
      pet,
      title: `和${pet.name || '蛋宝宝'}说说话`,
      dailyStatus: petStore.getDailyStatus(),
    });

    // 年龄区间强制门槛：未设置的用户先去选择，通过后才初始化聊天
    this._ensureAgeRange().then((ok) => {
      if (ok) this._startChat();
    });

    // 键盘适配基准：记录未弹键盘时的窗口高度，配合 chatViewportStyle 压缩页面
    this.chatWindowHeight = windowHeight();
    this.updateChatViewport(0);
    this.windowResizeHandler = (event) => {
      const resizedHeight = Number(event && event.size && event.size.windowHeight);
      if (!Number.isFinite(resizedHeight) || resizedHeight <= 0) return;
      // 输入框聚焦时，部分机型会先回报「键盘缩小后的窗口」，不能当作新基准，否则会双重缩短页面
      if (this.data.inputFocused || this.data.keyboardHeight) return;
      this.chatWindowHeight = Math.round(resizedHeight);
      this.updateChatViewport();
    };
    if (wx.onWindowResize) wx.onWindowResize(this.windowResizeHandler);
  },

  // 年龄区间强制门槛（合规要求）：未设置年龄区间的用户无法使用聊天功能。
  // 缓存优先，缓存缺失时实时拉取资料兜底；接口失败阻断并提供重试。
  _ensureAgeRange() {
    const cached = petStore.getUser();
    if (cached && cached.ageRange) return Promise.resolve(true);
    return ageRangeApi.getProfile().then((profile) => {
      petStore.syncUserProfile(profile);
      if (profile && profile.ageRange) return true;
      wx.redirectTo({ url: '/pages/age-range/age-range?force=1' });
      return false;
    }).catch(() => {
      wx.showModal({
        title: '提示',
        content: '网络异常，无法读取账号设置',
        confirmText: '重试',
        showCancel: false,
        success: () => {
          this._ensureAgeRange().then((ok) => {
            if (ok) this._startChat();
          });
        }
      });
      return false;
    });
  },

  // 年龄门槛通过后的聊天初始化入口
  _startChat() {
    if (this._chatStarted) return;
    this._chatStarted = true;
    this._loadHistoryMessages(1, true);
    this._initAudio();
    this._initWebSocketManager();
    this._otaAndConnect();
  },

  onShow() {
    this.setData({ reducedMotion: reducedMotionEnabled() });
    if (this.audioManager && this.data.connectionState === 'connected') {
      this.audioManager.resetAudioContext();
    }
    // onHide 主动断开后回到页面时重连（首次进入由 onLoad 负责连接，不走这里）
    if (this._suspendedByHide) {
      this._suspendedByHide = false;
      if (this.wsManager && !this.wsManager.isConnected()) {
        this._otaAndConnect();
      }
    }
  },

  onHide() {
    // 页面不可见（切后台或跳转其他页面）时主动断开，
    // 避免僵尸连接占用服务端连接名额（否则要等服务端约3分钟超时回收）。
    // chatState 重置与停止播放由 onStateChange('disconnected') 回调统一处理。
    this._suspendedByHide = true;
    if (this.wsManager) {
      this.wsManager.disconnect();
    }
  },

  onUnload() {
    if (this.wsManager) {
      this.wsManager.disconnect();
      this.wsManager.destroy();
      this.wsManager = null;
    }
    if (this.audioManager) {
      this.audioManager.destroy();
      this.audioManager = null;
    }
    if (this._flushTimer) {
      clearTimeout(this._flushTimer);
      this._flushTimer = null;
    }
    if (wx.offWindowResize && this.windowResizeHandler) {
      wx.offWindowResize(this.windowResizeHandler);
      this.windowResizeHandler = null;
    }
  },

  // -------------------------------------------------------------------------
  // Init
  // -------------------------------------------------------------------------

  _initAudio() {
    this.audioManager = new AudioManager({
      onPlayEnd: () => {
        if (this.data.chatState === STATE_SPEAKING) {
          this.setData({ chatState: STATE_IDLE });
        }
      },
      onError: (err, scope) => {
        if (scope === 'codec') {
          wx.showToast({ title: '音频引擎加载失败，请重启微信后重试', icon: 'none' });
        }
      },
    });
  },

  _initWebSocketManager() {
    this.wsManager = new WebSocketManager({
      onStateChange: (state) => {
        this.setData({ connectionState: state, connLabel: CONN_LABELS[state] || state });
        if (state === 'disconnected' && this.data.chatState !== STATE_IDLE) {
          this.setData({ chatState: STATE_IDLE });
          try {
            if (this.audioManager) this.audioManager.stopPlayback();
          } catch (_) {}
        }
      },
      onMessage: (msg) => this._handleWSMessage(msg),
      onError: () => {},
    });
  },

  async _otaAndConnect() {
    const pet = this.data.pet;
    if (!pet || !pet.deviceId) return;

    try {
      const { wsUrl, wsToken } = await ota.checkOrRegisterDevice(pet.deviceId);
      this.setData({ booting: false });
      this.wsManager.connect(wsUrl, pet.deviceId, wsToken);
    } catch (error) {
      this.setData({ booting: false });
      const message = (error && error.userMessage) || '获取聊天配置失败';
      wx.showToast({ title: message, icon: 'none' });
    }
  },

  // -------------------------------------------------------------------------
  // History pagination (local store; replace with HTTP when backend is ready)
  // -------------------------------------------------------------------------

  _onConversationScroll(e) {
    this._scrollTop = (e && e.detail && e.detail.scrollTop) || 0;
  },

  _onScrollToUpper() {
    if (this._historyLoading || this._historyNoMore) return;
    this._loadHistoryMessages(this._historyPage + 1, false);
  },

  // 键盘弹出时用「窗口高 - 键盘高」压缩页面根节点，输入栏随之上移且消息区不被遮挡
  updateChatViewport(keyboardHeight, afterUpdate) {
    const currentWindowHeight = this.chatWindowHeight || windowHeight();
    if (!this.chatWindowHeight && currentWindowHeight) this.chatWindowHeight = currentWindowHeight;
    const nextKeyboardHeight = keyboardHeight === undefined ? this.data.keyboardHeight : keyboardHeight;
    this.setData({
      keyboardHeight: Math.max(0, Number(nextKeyboardHeight) || 0),
      chatViewportStyle: viewportStyle(currentWindowHeight, nextKeyboardHeight),
    }, afterUpdate);
  },

  resetChatViewport() {
    this.updateChatViewport(0);
  },

  onInputFocus() {
    this.setData({ inputFocused: true });
  },

  onInputBlur() {
    this.setData({ inputFocused: false }, () => {
      // 键盘仍在收起动画时等待高度回调，避免页面先于键盘突然拉伸
      if (!this.data.keyboardHeight) this.resetChatViewport();
    });
  },

  onKeyboardHeightChange(event) {
    const keyboardHeight = Math.max(0, Number(event && event.detail && event.detail.height) || 0);
    const openingKeyboard = keyboardHeight > 0 && !this.data.keyboardHeight;
    if (!keyboardHeight) {
      const measuredHeight = windowHeight();
      // 某些机型先回报高度 0，再恢复完整窗口；不得用尚未恢复的小窗口覆盖基准
      if (measuredHeight >= (this.chatWindowHeight || 0)) this.chatWindowHeight = measuredHeight;
    }
    this.updateChatViewport(keyboardHeight, () => {
      // 键盘首次出现时滚到对话末尾；收起键盘时不动用户正在阅读的历史位置
      if (openingKeyboard) this._scrollToBottom();
    });
  },

  _queryMessageAnchor(id, callback) {
    if (!id) {
      callback({ offsetTop: 0, viewportOffset: 0 });
      return;
    }
    const query = wx.createSelectorQuery().in(this);
    query.select('.messages').boundingClientRect();
    query.select('#msg-' + id).boundingClientRect();
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

  _queryMessageOffset(id, callback) {
    if (!id) {
      callback(0);
      return;
    }
    const query = wx.createSelectorQuery().in(this);
    query.select('.messages').boundingClientRect();
    query.select('#msg-' + id).boundingClientRect();
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

  _loadHistoryMessages(page, initial) {
    if (this._historyLoading || this._historyNoMore) return;

    if (initial) {
      this._historyLoading = true;
      this.setData({ historyLoading: true });

      petApi.listChatHistory(this.data.pet.agentId, this.data.pet.deviceId, page, 20).then((pageData) => {
        const list = (pageData && pageData.list) || [];

        if (list.length === 0) {
          this._historyNoMore = true;
          this._historyLoading = false;
          this.setData({ historyNoMore: true, historyLoading: false });
          return;
        }

        const historyMsgs = list.reverse().map((item, idx) => ({
          id: `hist-${page}-${idx}`,
          messageId: item.id,
          role: item.chatType === 1 ? 'user' : 'assistant',
          content: item.content || '',
          audioId: item.audioId || '',
          time: this._parseTime(item.createdAt),
        }));

        const messages = this._stampSeparators(historyMsgs.concat(this.data.messages));
        this._historyPage = page;
        this._historyLoading = false;

        const noMore = list.length < 20;
        const nextData = {
          messages,
          historyLoading: false,
          scrollAnchor: '',
        };
        if (noMore) {
          this._historyNoMore = true;
          nextData.historyNoMore = true;
        }

        this.setData(nextData, () => {
          this._scrollToBottom();
          setTimeout(() => {
            const last = this.data.messages[this.data.messages.length - 1];
            if (last && last.id) {
              this._queryMessageOffset(last.id, (offset) => {
                this.setData({ scrollWithAnimation: false, scrollTop: offset }, () => {
                  this.setData({ scrollWithAnimation: true });
                });
              });
            }
          }, 100);
        });
      }).catch(() => {
        this._historyLoading = false;
        this.setData({ historyLoading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
      return;
    }

    const anchorId = (this.data.messages[0] && this.data.messages[0].id) || '';

    this._queryMessageAnchor(anchorId, (anchor) => {
      const anchorViewportOffset = anchor.viewportOffset;

      this._historyLoading = true;
      this.setData({ historyLoading: true });

      petApi.listChatHistory(this.data.pet.agentId, this.data.pet.deviceId, page, 20).then((pageData) => {
        const list = (pageData && pageData.list) || [];

        if (list.length === 0) {
          this._historyNoMore = true;
          this._historyLoading = false;
          this.setData({ historyNoMore: true, historyLoading: false });
          return;
        }

        const historyMsgs = list.reverse().map((item, idx) => ({
          id: `hist-${page}-${idx}`,
          messageId: item.id,
          role: item.chatType === 1 ? 'user' : 'assistant',
          content: item.content || '',
          audioId: item.audioId || '',
          time: this._parseTime(item.createdAt),
        }));

        const messages = this._stampSeparators(historyMsgs.concat(this.data.messages));
        this._historyPage = page;
        this._historyLoading = false;

        const noMore = list.length < 20;
        const nextData = {
          messages,
          historyLoading: false,
          scrollAnchor: '',
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
          }
        });
      }).catch(() => {
        this._historyLoading = false;
        this.setData({ historyLoading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
    });
  },

  _parseTime(value) {
    if (!value) return Date.now();
    if (typeof value === 'number') return value;
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

  // 时间分隔条文案：今天 HH:mm；昨天「昨天 HH:mm」；同年更早「M月D日 HH:mm」；否则带年份
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
      return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + hm;
    }
    return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + hm;
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

  // 新消息只与最后一条比较，O(1) 打标后追加
  _appendWithSeparator(messages, msg) {
    const prev = messages.length ? messages[messages.length - 1] : null;
    const t = msg.time || 0;
    const show = !prev
      || (t - (prev.time || 0) >= TIME_GAP_MS)
      || !this._isSameDay(t, prev.time || 0);
    const stamped = show
      ? Object.assign({}, msg, { showTime: true, timeLabel: this._formatTimeLabel(t) })
      : Object.assign({}, msg, { showTime: false, timeLabel: '' });
    return messages.concat([stamped]);
  },

  // -------------------------------------------------------------------------
  // WebSocket message dispatch
  // -------------------------------------------------------------------------

  _handleWSMessage(msg) {
    switch (msg.type) {
      case 'hello':
        if (this._pendingText) {
          const pending = this._pendingText;
          this._pendingText = '';
          try {
            this.wsManager.sendText(pending);
            this.setData({ chatState: STATE_THINKING });
          } catch (err) {
            this.setData({ draft: pending, chatState: STATE_IDLE });
            wx.showToast({ title: '发送失败，请重试', icon: 'none' });
          }
        }
        break;

      case 'audio':
        if (this.audioManager) this.audioManager.appendOpusFrame(msg.data);
        break;

      case 'stt':
        if (msg.text && !msg.text.startsWith('% ')) {
          this._addMessage('user', msg.text);
        }
        this.setData({ chatState: STATE_THINKING });
        break;

      case 'llm':
        // Drop emoji prefix to comply with egg design (no emoji in UI).
        break;

      case 'tts':
        this._handleTtsState(msg);
        break;

      case 'goodbye':
        this._finalizeStreaming(STATE_IDLE);
        break;

      default:
        break;
    }
  },

  _handleTtsState(msg) {
    if (msg.state === 'start') {
      this.setData({ chatState: STATE_SPEAKING });
    } else if (msg.state === 'sentence_start') {
      if (msg.text) this._pushSentenceBubble(msg.text);
    } else if (msg.state === 'stop') {
      this._finalizeStreaming(STATE_IDLE);
    }
  },

  _pushSentenceBubble(text) {
    this._finalizeStreaming(STATE_SPEAKING);
    this._appendStreamingText(text, STATE_SPEAKING);
  },

  // -------------------------------------------------------------------------
  // Message list helpers
  // -------------------------------------------------------------------------

  _addMessage(role, content) {
    const id = `msg-${this._msgIdSeed++}`;
    const messages = this._appendWithSeparator(this.data.messages, {
      id,
      role,
      content,
      time: Date.now(),
    });
    this._persistAndSetMessages(messages, id);
  },

  _appendStreamingText(text, chatState) {
    if (!text) return;
    if (this._streamingIdx !== -1) {
      this._streamingBuffer += text;
      if (!this._flushTimer) {
        this._flushTimer = setTimeout(() => this._flushStreaming(), 80);
      }
    } else {
      const id = `msg-${this._msgIdSeed++}`;
      const messages = this._appendWithSeparator(this.data.messages, {
        id,
        role: 'assistant',
        content: text,
        typing: true,
        time: Date.now(),
      });
      this._streamingIdx = messages.length - 1;
      this._streamingBuffer = text;
      this._persistAndSetMessages(messages, id, { chatState });
    }
  },

  _flushStreaming() {
    this._flushTimer = null;
    if (this._streamingIdx === -1) return;
    const idx = this._streamingIdx;
    const messages = this.data.messages.slice();
    messages[idx] = { ...messages[idx], content: this._streamingBuffer };
    this.setData({ messages }, () => {
      this._scrollToBottom();
    });
  },

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

    let messages;
    if (!msg || !content) {
      messages = !msg
        ? this.data.messages
        : this.data.messages.slice(0, idx).concat(this.data.messages.slice(idx + 1));
    } else {
      messages = this.data.messages.slice();
      messages[idx] = {
        id: msg.id,
        role: msg.role,
        content,
        time: msg.time,
        showTime: msg.showTime,
        timeLabel: msg.timeLabel,
      };
    }

    this._streamingIdx = -1;
    this._persistAndSetMessages(messages, null, { chatState }, { upsert: true });
  },

  _persistAndSetMessages(messages, scrollAnchor, extraData, persistOptions) {
    this.setData({ messages, ...(extraData || {}) }, () => {
      this._scrollToBottom();
    });
    const last = messages[messages.length - 1];
    if (last) petStore.saveMessage(last, persistOptions || {});
  },

  _scrollToBottom() {
    const query = wx.createSelectorQuery().in(this);
    query.select('.messages').scrollOffset();
    query.exec((res) => {
      if (!res || !res[0]) return;
      // 清空 scrollAnchor 避免与 scroll-top 冲突；
      // 用 scroll-top 设置到内容底部，确保流式刷新（相同目标 id）时也能触发滚动。
      this.setData({
        scrollAnchor: '',
        scrollTop: res[0].scrollHeight,
      });
    });
  },

  // -------------------------------------------------------------------------
  // User interaction
  // -------------------------------------------------------------------------

  onInput(e) {
    const draft = e.detail.value;
    this.setData({ draft, canSend: Boolean(draft.trim()) });
  },

  onSend() {
    const text = (this.data.draft || '').trim();
    if (!text) return;

    if (this.data.connectionState !== 'connected') {
      this._pendingText = text;
      this.setData({ draft: '', canSend: false, chatState: STATE_THINKING }, () => {
        this._scrollToBottom();
      });
      if (this.data.connectionState === 'disconnected') {
        this._otaAndConnect();
        wx.showToast({ title: '它正在赶来…', icon: 'none' });
      }
      return;
    }

    try {
      this.wsManager.sendText(text);
    } catch (err) {
      wx.showToast({ title: '发送失败，请重试', icon: 'none' });
      return;
    }

    this.setData({ draft: '', canSend: false, chatState: STATE_THINKING }, () => {
      this._scrollToBottom();
    });
  },

  /**
   * 长按消息气泡复制单条消息（交互照搬蛋宝宝UI静态项目）。
   * 打字中的 AI 消息与空内容消息不可复制，避免复制到半截文本；
   * 复制内容为消息原文，不加署名；成功反馈依赖 wx.setClipboardData 系统提示。
   */
  onMessageLongPress(e) {
    const messageId = e.currentTarget.dataset.messageId;
    const target = this.data.messages.find((m) => m.id === messageId);
    if (!target || target.typing || !target.content) return;

    wx.showActionSheet({
      itemList: ['复制'],
      success: (res) => {
        if (res.tapIndex !== 0) return;
        wx.setClipboardData({ data: target.content });
      },
    });
  },

  noop() {},
});
