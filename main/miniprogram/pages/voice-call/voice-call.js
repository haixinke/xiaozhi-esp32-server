/**
 * pages/voice-call/voice-call.js
 *
 * 语音通话页：呼叫等待 + 通话中控制 + 双向语音。
 */

const VoiceCallManager = require('../../utils/voice-call-manager');
const AudioManager = require('../../utils/audio');
const WebSocketManager = require('../../utils/websocket');
const logger = require('../../utils/logger');
const { getTheme, applyTheme } = require('../../utils/theme');

const app = getApp();

function formatDuration(totalSeconds) {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  const pad = (n) => (n < 10 ? '0' + n : '' + n);
  return pad(m) + ':' + pad(s);
}

Page({
  data: {
    darkMode: getTheme(),
    companionName: '',
    companionAvatar: '',
    callState: 'calling',
    formattedDuration: '00:00',
    callStatusText: '正在呼叫…',
    isMuted: false,
    isSpeakerOn: true,
  },

  _mgr: null,
  _unsubscribe: null,
  _callTimer: null,
  _hasStartedMedia: false,
  _isUserSpeaking: false,
  _isAiSpeaking: false,
  _returningToChat: false,
  _restartingRecord: false,

  audioManager: null,
  wsManager: null,

  onLoad() {
    this._mgr = VoiceCallManager();
    this._unsubscribe = (state) => this._syncState(state);
    this._mgr.onStateChange(this._unsubscribe);

    this._mgr.setOnRecordRestart(() => this._restartRecord());

    const g = app.globalData || {};
    this.setData({
      companionName: g.agentName || '女友',
      companionAvatar: g.companionAvatar || '',
    });

    const state = this._mgr.getState().state;
    if (state === 'connected') {
      // 从悬浮小球返回，媒体已在运行
      this._hasStartedMedia = true;
      this._syncState(this._mgr.getState());
      return;
    }

    // 新通话：模拟 2-8 秒呼叫等待
    const delay = 2000 + Math.floor(Math.random() * 6000);
    this._callTimer = setTimeout(() => {
      this._mgr.connect();
    }, delay);
  },

  onShow() {
    applyTheme(this);
  },

  onUnload() {
    this._cleanup();
  },

  _syncState(state) {
    if (state.state === 'connected' && !this._hasStartedMedia) {
      this._hasStartedMedia = true;
      this._startMedia();
    }

    this.setData({
      callState: state.state,
      formattedDuration: formatDuration(state.durationSeconds),
      isMuted: state.isMuted,
      isSpeakerOn: state.isSpeakerOn,
      callStatusText: this._computeStatusText(state.state),
    });
  },

  _computeStatusText(state) {
    if (state === 'calling') return '正在呼叫…';
    if (this._isAiSpeaking) return '女友正在说…';
    if (this._isUserSpeaking) return '正在听…';
    return '通话中';
  },

  _updateStatusText() {
    this.setData({ callStatusText: this._computeStatusText(this._mgr.getState().state) });
  },

  _startMedia() {
    const g = app.globalData;

    this.audioManager = new AudioManager({
      onAudioFrame: (frame) => {
        if (this.data.isMuted || this._restartingRecord) return;
        if (this.wsManager) this.wsManager.sendAudioFrame(frame);
      },
      onRecordStart: () => {
        this._isUserSpeaking = true;
        this._updateStatusText();
      },
      onRecordStop: () => {
        this._isUserSpeaking = false;
        this._updateStatusText();
      },
      onPlayEnd: () => {
        this._isAiSpeaking = false;
        this._updateStatusText();
        // AI 说完后自动进入下一轮倾听
        if (this.wsManager && this._mgr.getState().state === 'connected') {
          this.wsManager.sendListenStart('auto');
        }
      },
      onError: (err, scope) => {
        logger.warn('[VoiceCall Audio:' + scope + ']', err);
      },
    });

    this.wsManager = new WebSocketManager({
      onStateChange: (wsState) => {
        if (wsState === 'disconnected' && this._mgr.getState().state === 'connected') {
          wx.showToast({ title: '通话已断开', icon: 'none' });
          this._mgr.hangup();
          wx.navigateBack();
        }
      },
      onMessage: (msg) => this._handleWSMessage(msg),
      onError: (err, scope) => {
        logger.warn('[VoiceCall WS:' + scope + ']', err && err.message ? err.message : err);
      },
    });

    this.wsManager.connect(g.wsUrl, g.virtualMAC, g.wsToken);
    this._mgr.setMedia(this.audioManager, this.wsManager);

    this._waitForHelloAndStart();
  },

  _waitForHelloAndStart() {
    if (!this.wsManager) return;
    if (this.wsManager.isConnected()) {
      this._startRecordingAfterReady();
      return;
    }

    const TIMEOUT_MS = 10000;
    let timeoutTimer = null;
    let handler = null;

    const cleanup = () => {
      if (timeoutTimer) {
        clearTimeout(timeoutTimer);
        timeoutTimer = null;
      }
      if (handler && this.wsManager) {
        this.wsManager.offStateChange(handler);
      }
      handler = null;
    };

    handler = (state) => {
      if (state !== 'connected') return;
      cleanup();
      this._startRecordingAfterReady();
    };

    timeoutTimer = setTimeout(() => {
      cleanup();
      wx.showToast({ title: '通话连接超时', icon: 'none' });
      this._mgr.hangup();
      wx.navigateBack();
    }, TIMEOUT_MS);

    this.wsManager.onStateChange(handler);
  },

  _startRecordingAfterReady() {
    if (!this.audioManager || !this.wsManager) return;
    this.audioManager.ready().then(() => {
      if (!this.audioManager || !this.wsManager) return;
      this.audioManager.startRecord();
      this.wsManager.sendListenStart('auto');
    }).catch((err) => {
      logger.error('AudioManager not ready:', err);
      wx.showToast({ title: '音频引擎未就绪', icon: 'none' });
      this._mgr.hangup();
      wx.navigateBack();
    });
  },

  _handleWSMessage(msg) {
    switch (msg.type) {
      case 'audio':
        if (this.audioManager && !this._restartingRecord) {
          this.audioManager.appendOpusFrame(msg.data);
          this._isAiSpeaking = true;
          this._updateStatusText();
        }
        break;
      case 'tts':
        if (msg.state === 'start') {
          this._isAiSpeaking = true;
        } else if (msg.state === 'stop') {
          this._isAiSpeaking = false;
        }
        this._updateStatusText();
        break;
      case 'goodbye':
        this._mgr.hangup();
        wx.navigateBack();
        break;
      default:
        break;
    }
  },

  _restartRecord() {
    if (!this.audioManager || !this.wsManager) return;
    this._restartingRecord = true;
    this.audioManager.stopRecord();
    this.wsManager.sendListenStop();
    setTimeout(() => {
      this._restartingRecord = false;
      if (!this.audioManager || !this.wsManager) return;
      this.audioManager.startRecord();
      this.wsManager.sendListenStart('auto');
    }, 100);
  },

  onCancelCall() {
    if (this._callTimer) {
      clearTimeout(this._callTimer);
      this._callTimer = null;
    }
    this._mgr.hangup();
    wx.navigateBack();
  },

  onHangup() {
    this._mgr.hangup();
    wx.navigateBack();
  },

  onToggleMute() {
    this._mgr.toggleMute();
  },

  onToggleSpeaker() {
    this._mgr.toggleSpeaker();
    const next = this._mgr.getState().isSpeakerOn;
    if (wx.setInnerAudioOption) {
      wx.setInnerAudioOption({
        speakerOn: next,
        success: () => {
          logger.log('audio output switched, speakerOn=' + next);
        },
        fail: (err) => {
          logger.warn('setInnerAudioOption failed:', err);
        },
      });
    }
  },

  onBackToChat() {
    this._returningToChat = true;
    wx.navigateBack();
  },

  _cleanup() {
    if (this._callTimer) {
      clearTimeout(this._callTimer);
      this._callTimer = null;
    }
    if (this._mgr && this._unsubscribe) {
      this._mgr.offStateChange(this._unsubscribe);
    }

    if (this._returningToChat) {
      // 返回聊天页保持通话：媒体由 VoiceCallManager 继续持有
      this._returningToChat = false;
      return;
    }

    this._cleanupResources();
  },

  _cleanupResources() {
    if (this._mgr) this._mgr.clearMedia();
    if (this.audioManager) {
      this.audioManager.destroy();
      this.audioManager = null;
    }
    if (this.wsManager) {
      this.wsManager.destroy();
      this.wsManager = null;
    }
  },
});
