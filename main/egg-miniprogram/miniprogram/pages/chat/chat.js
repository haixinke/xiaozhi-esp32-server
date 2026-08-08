const petStore = require('../../utils/pet-store');
const petApi = require('../../utils/pet-api');
const ota = require('../../utils/ota');
const WebSocketManager = require('../../utils/websocket');
const AudioManager = require('../../utils/audio');

const STATE_IDLE = 'idle';
const STATE_THINKING = 'thinking';
const STATE_SPEAKING = 'speaking';

// 两条消息间隔超过该阈值才显示居中时间分隔条
const TIME_GAP_MS = 5 * 60 * 1000;

Page({
  data: {
    pet: null,
    card: null,
    dailyStatus: null,
    messages: [],
    draft: '',
    booting: true,
    avatarUrl: '',
    connectionState: 'disconnected',
    chatState: STATE_IDLE,
    scrollAnchor: '',
    scrollTop: 0,
    scrollWithAnimation: true,
    historyLoading: false,
    historyNoMore: false,
    scrollViewHeight: 0,
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
      card: (pet.collectionCards && pet.collectionCards[0]) || null,
      avatarUrl: pet.avatarUrl || '',
      dailyStatus: petStore.getDailyStatus(),
    });

    this._loadHistoryMessages(1, true);
    this._initAudio();
    this._initWebSocketManager();
    this._otaAndConnect();

    setTimeout(() => this._calcScrollViewHeight(), 100);
  },

  onShow() {
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
        this.setData({ connectionState: state });
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

  _calcScrollViewHeight() {
    const query = wx.createSelectorQuery().in(this);
    query.select('.mood-bar').boundingClientRect();
    query.select('.composer').boundingClientRect();
    query.select('.navbar-root').boundingClientRect();
    query.exec((res) => {
      const windowHeight = wx.getWindowInfo().windowHeight;
      const moodBarHeight = (res[0] && res[0].height) || 0;
      const composerHeight = (res[1] && res[1].height) || 0;
      const navBarHeight = (res[2] && res[2].height) || 0;
      const scrollViewHeight = windowHeight - navBarHeight - moodBarHeight - composerHeight;
      const finalHeight = scrollViewHeight > 0 ? scrollViewHeight : windowHeight * 0.6;
      this.setData({ scrollViewHeight: finalHeight });
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
    this.setData({ draft: e.detail.value });
  },

  onSend() {
    const text = (this.data.draft || '').trim();
    if (!text) return;

    if (this.data.connectionState !== 'connected') {
      this._pendingText = text;
      this.setData({ draft: '', chatState: STATE_THINKING }, () => {
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

    this.setData({ draft: '', chatState: STATE_THINKING }, () => {
      this._scrollToBottom();
    });
  },

  noop() {},
});
