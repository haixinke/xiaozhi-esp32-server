const petStore = require('../../utils/pet-store');
const ota = require('../../utils/ota');
const WebSocketManager = require('../../utils/websocket');
const AudioManager = require('../../utils/audio');

const STATE_IDLE = 'idle';
const STATE_THINKING = 'thinking';
const STATE_SPEAKING = 'speaking';

Page({
  data: {
    pet: null,
    card: null,
    dailyStatus: null,
    messages: [],
    draft: '',
    booting: true,
    connectionState: 'disconnected',
    chatState: STATE_IDLE,
    scrollAnchor: '',
  },

  _msgIdSeed: 1,
  _streamingIdx: -1,
  _streamingBuffer: '',
  _flushTimer: null,
  _pendingText: '',
  wsManager: null,
  audioManager: null,

  onLoad() {
    const pet = petStore.getPet();
    if (!pet || !pet.collectionCard || !pet.deviceId) {
      wx.showToast({ title: '破壳后才可以对话', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 600);
      return;
    }

    const messages = pet.messages || [];
    if (!messages.length) {
      messages.push({
        id: 'hello',
        role: 'assistant',
        content: '你来啦。我已经等你好一会儿了。',
        time: Date.now(),
      });
    }

    this.setData({
      pet,
      card: pet.collectionCard,
      dailyStatus: petStore.getDailyStatus(),
      messages,
      scrollAnchor: `msg-${messages[messages.length - 1].id}`,
    });

    this._initAudio();
    this._initWebSocketManager();
    this._otaAndConnect();
  },

  onShow() {
    if (this.audioManager && this.data.connectionState === 'connected') {
      this.audioManager.resetAudioContext();
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
    const messages = this.data.messages.concat([{
      id,
      role,
      content,
      time: Date.now(),
    }]);
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
      const messages = this.data.messages.concat([{
        id,
        role: 'assistant',
        content: text,
        typing: true,
        time: Date.now(),
      }]);
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
    this.setData({ messages });
    this._scrollToBottom();
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
      };
    }

    this._streamingIdx = -1;
    this._persistAndSetMessages(messages, null, { chatState });
  },

  _persistAndSetMessages(messages, scrollAnchor, extraData) {
    this.setData({ messages, ...(extraData || {}) }, () => {
      if (scrollAnchor) this.setData({ scrollAnchor });
      else this._scrollToBottom();
    });
    const last = messages[messages.length - 1];
    if (last) petStore.saveMessage(last);
  },

  _scrollToBottom() {
    if (this.data.messages.length === 0) return;
    const last = this.data.messages[this.data.messages.length - 1];
    if (last && last.id) this.setData({ scrollAnchor: `msg-${last.id}` });
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
      this.setData({ draft: '', chatState: STATE_THINKING });
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

    this.setData({ draft: '', chatState: STATE_THINKING });
  },

  noop() {},
});
