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
const { RINGBACK_TONE_URL } = require('../../config/companion-codes');

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
  },

  _mgr: null,
  _unsubscribe: null,
  _callTimer: null,
  _hasStartedMedia: false,
  _isUserSpeaking: false,
  _isAiSpeaking: false,
  _aiSpeakingTimer: null,
  _restartingRecord: false,
  _mediaFailureHandled: false,
  _isLeaving: false,
  _helloWaitTimer: null,
  _helloWaitHandler: null,
  _helloWaitSocket: null,
  _destroyed: false,

  audioManager: null,
  wsManager: null,
  _innerAudioContext: null,

  onLoad() {
    this._mediaFailureHandled = false;
    this._isLeaving = false;
    this._destroyed = false;
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

    // 新通话：播放彩铃并模拟 3-8 秒呼叫等待
    this._startRingback();
    const delay = 3000 + Math.floor(Math.random() * 6000);
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
      this._stopRingback();
      this._startMedia();
    }

    if (state.state !== 'calling') {
      this._stopRingback();
    }

    this.setData({
      callState: state.state,
      formattedDuration: formatDuration(state.durationSeconds),
      isMuted: state.isMuted,
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

  _startRingback() {
    if (this._innerAudioContext) return;
    if (typeof wx === 'undefined' || !wx.createInnerAudioContext) return;

    const ctx = wx.createInnerAudioContext();
    if (!ctx) return;
    ctx.src = RINGBACK_TONE_URL;
    ctx.loop = true;
    ctx.onError((err) => {
      logger.warn('[VoiceCall Ringback]', err && err.errMsg ? err.errMsg : err);
    });
    ctx.play();
    this._innerAudioContext = ctx;
  },

  _stopRingback() {
    if (!this._innerAudioContext) return;
    try { this._innerAudioContext.stop(); } catch (_) {}
    try { this._innerAudioContext.destroy(); } catch (_) {}
    this._innerAudioContext = null;
  },

  _setAiSpeaking(isSpeaking) {
    if (this._destroyed) return;
    if (this._isAiSpeaking === isSpeaking) return;
    this._isAiSpeaking = isSpeaking;
    // AI 说话时优先显示 AI 状态
    if (isSpeaking) this._isUserSpeaking = false;
    this._updateStatusText();

    if (this._aiSpeakingTimer) {
      clearTimeout(this._aiSpeakingTimer);
      this._aiSpeakingTimer = null;
    }

    // 安全超时：如果 AI 说话状态异常未重置，120 秒后强制恢复
    if (isSpeaking) {
      this._aiSpeakingTimer = setTimeout(() => {
        this._aiSpeakingTimer = null;
        if (this._isAiSpeaking) {
          logger.warn('[VoiceCall] AI 说话状态看门狗超时，强制重置状态');
          this._setAiSpeaking(false);
        }
      }, 120000);
    }
  },

  _startMedia() {
    const g = app.globalData;

    // 默认使用免提（扬声器）播放，禁止用户切换。
    if (wx.setInnerAudioOption) {
      wx.setInnerAudioOption({
        speakerOn: true,
        fail: (err) => {
          logger.warn('[VoiceCall] setInnerAudioOption failed:', err);
        },
      });
    }

    this.audioManager = new AudioManager({
      onAudioFrame: (frame) => {
        if (this.data.isMuted || this._restartingRecord || this._isAiSpeaking) return;
        if (this.wsManager) this.wsManager.sendAudioFrame(frame);
      },
      onRecordStart: () => {
        if (this._destroyed) return;
        this._isUserSpeaking = true;
        this._updateStatusText();
      },
      onRecordStop: () => {
        if (this._destroyed) return;
        this._isUserSpeaking = false;
        this._updateStatusText();
      },
      onPlayEnd: () => {
        if (this._destroyed) return;
        this._setAiSpeaking(false);
        // AI 说完后自动进入下一轮倾听
        if (this.wsManager && this._mgr.getState().state === 'connected') {
          this.wsManager.sendListenStart('auto');
        }
      },
      onError: (err, scope) => {
        logger.warn('[VoiceCall Audio:' + scope + ']', err);
        if (scope === 'codec') {
          wx.showToast({ title: '语音引擎加载失败，请重启微信后重试', icon: 'none' });
        } else if (scope === 'record') {
          this._failActiveCall('录音异常，请重新发起通话');
        }
      },
    });

    this.wsManager = new WebSocketManager({
      onStateChange: (wsState) => {
        if (!this._isLeaving && wsState === 'disconnected' && this._mgr.getState().state === 'connected') {
          wx.showToast({ title: '通话已断开', icon: 'none' });
          this._hangupAndNavigateBack();
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
    this._clearHelloWait();
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
      if (handler && waitSocket) {
        waitSocket.offStateChange(handler);
      }
      if (this._helloWaitTimer) this._helloWaitTimer = null;
      if (this._helloWaitHandler === handler) this._helloWaitHandler = null;
      if (this._helloWaitSocket === waitSocket) this._helloWaitSocket = null;
      handler = null;
    };
    const waitSocket = this.wsManager;

    handler = (state) => {
      if (state !== 'connected') return;
      cleanup();
      if (this._destroyed || this._isLeaving) return;
      this._startRecordingAfterReady();
    };

    timeoutTimer = setTimeout(() => {
      cleanup();
      if (this._destroyed || this._isLeaving) return;
      wx.showToast({ title: '通话连接超时', icon: 'none' });
      this._hangupAndNavigateBack();
    }, TIMEOUT_MS);

    this._helloWaitTimer = timeoutTimer;
    this._helloWaitHandler = handler;
    this._helloWaitSocket = waitSocket;
    waitSocket.onStateChange(handler);
  },

  _clearHelloWait() {
    if (this._helloWaitTimer) clearTimeout(this._helloWaitTimer);
    if (this._helloWaitHandler && this._helloWaitSocket) {
      this._helloWaitSocket.offStateChange(this._helloWaitHandler);
    }
    this._helloWaitTimer = null;
    this._helloWaitHandler = null;
    this._helloWaitSocket = null;
  },

  _startRecordingAfterReady() {
    if (!this.audioManager || !this.wsManager) return;
    this.audioManager.ready().then(() => {
      if (this._destroyed || this._isLeaving || !this.audioManager || !this.wsManager) return;
      if (this.audioManager.startRecord() === false) {
        this._failActiveCall('无法启动录音，请重新发起通话');
        return;
      }
      this.wsManager.sendListenStart('auto');
    }).catch((err) => {
      if (this._destroyed || this._isLeaving) return;
      logger.error('AudioManager not ready:', err);
      wx.showToast({ title: '音频引擎未就绪', icon: 'none' });
      this._hangupAndNavigateBack();
    });
  },

  _handleWSMessage(msg) {
    switch (msg.type) {
      case 'audio':
        if (this.audioManager && !this._restartingRecord) {
          this.audioManager.appendOpusFrame(msg.data);
          this._setAiSpeaking(true);
        }
        break;
      case 'tts':
        if (msg.state === 'start') {
          this._setAiSpeaking(true);
          this._updateStatusText();
        } else if (msg.state === 'stop') {
          // 以实际播放结束为准，不在这里提前清除说话状态，
          // 避免 tts stop 后仍在播放的末尾音频被回采。
        }
        break;
      case 'goodbye':
        this._hangupAndNavigateBack();
        break;
      default:
        break;
    }
  },

  _restartRecord() {
    if (!this.audioManager || !this.wsManager) return;
    const audioManager = this.audioManager;
    const wsManager = this.wsManager;
    this._restartingRecord = true;
    wsManager.sendListenStop();
    Promise.resolve()
      .then(() => audioManager.stopRecord({ flush: false, reason: 'voice-call-restart' }))
      .then(() => {
        this._restartingRecord = false;
        if (this._destroyed || this._isLeaving ||
            this.audioManager !== audioManager || this.wsManager !== wsManager) return;
        if (!audioManager.startRecord()) {
          throw new Error('recorder unavailable after stop');
        }
        wsManager.sendListenStart('auto');
      })
      .catch((err) => {
        this._restartingRecord = false;
        if (this._destroyed || this._isLeaving) return;
        logger.warn('[VoiceCall] restart recorder failed:', err);
        this._failActiveCall('录音异常，请重新发起通话');
      });
  },

  _failActiveCall(message) {
    if (this._destroyed || this._isLeaving || this._mediaFailureHandled) return;
    this._mediaFailureHandled = true;
    wx.showToast({ title: message, icon: 'none' });
    this._hangupAndNavigateBack();
  },

  _hangupAndNavigateBack() {
    if (this._destroyed || this._isLeaving) return;
    this._isLeaving = true;
    this._clearHelloWait();
    if (this._mgr) this._mgr.hangup();
    wx.navigateBack();
  },

  onCancelCall() {
    if (this._callTimer) {
      clearTimeout(this._callTimer);
      this._callTimer = null;
    }
    this._stopRingback();
    this._hangupAndNavigateBack();
  },

  onHangup() {
    this._stopRingback();
    this._hangupAndNavigateBack();
  },

  onToggleMute() {
    this._mgr.toggleMute();
  },

  _cleanup() {
    this._destroyed = true;
    this._clearHelloWait();
    this._stopRingback();
    if (this._callTimer) {
      clearTimeout(this._callTimer);
      this._callTimer = null;
    }
    if (this._aiSpeakingTimer) {
      clearTimeout(this._aiSpeakingTimer);
      this._aiSpeakingTimer = null;
    }
    if (this._mgr && this._unsubscribe) {
      this._mgr.offStateChange(this._unsubscribe);
    }

    // 通话结束恢复默认音频输出模式：避免 _startMedia 里设的 speakerOn:true
    // 残留到全局会话，影响聊天页后续录音/播放。
    if (wx.setInnerAudioOption) {
      wx.setInnerAudioOption({ speakerOn: false, fail: () => {} });
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
