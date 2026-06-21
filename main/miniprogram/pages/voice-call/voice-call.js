/**
 * pages/voice-call/voice-call.js
 *
 * 语音通话页：呼叫等待 + 通话中控制。
 */

const VoiceCallManager = require('../../utils/voice-call-manager');
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

  onLoad() {
    this._mgr = VoiceCallManager();
    this._unsubscribe = (state) => this._syncState(state);
    this._mgr.onStateChange(this._unsubscribe);

    const g = app.globalData || {};
    this.setData({
      companionName: g.agentName || '女友',
      companionAvatar: g.companionAvatar || '',
    });

    // 模拟 2-8 秒呼叫等待
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
    let statusText = '正在呼叫…';
    if (state.state === 'connected') {
      statusText = '通话中';
    }
    this.setData({
      callState: state.state,
      formattedDuration: formatDuration(state.durationSeconds),
      isMuted: state.isMuted,
      isSpeakerOn: state.isSpeakerOn,
      callStatusText: statusText,
    });
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
  },

  onBackToChat() {
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
  },
});
