/**
 * utils/voice-call-manager.js
 * --------------------------------------------------------------------------
 * VoiceCallManager — 小程序语音通话状态单例。
 *
 * Responsibilities:
 *   - 维护通话状态机：idle → calling → connected → ended。
 *   - 维护通话时长、静音、免提状态。
 *   - 提供状态变更订阅，供页面和组件同步。
 *
 * Lifecycle:
 *   const mgr = VoiceCallManager();
 *   mgr.startCall();
 *   // 2-8s later
 *   mgr.connect();
 *   mgr.toggleMute();
 *   mgr.toggleSpeaker();
 *   mgr.hangup();
 * --------------------------------------------------------------------------
 */

const STATE_IDLE = 'idle';
const STATE_CALLING = 'calling';
const STATE_CONNECTED = 'connected';
const STATE_ENDING = 'ending';
const STATE_ENDED = 'ended';

const RECORD_RESTART_INTERVAL_MS = 10 * 60 * 1000;

class VoiceCallManager {
  constructor() {
    this._state = STATE_IDLE;
    this._durationSeconds = 0;
    this._isMuted = false;
    this._isSpeakerOn = true;
    this._startTime = null;
    this._recordRestartAt = null;
    this._listeners = new Set();

    this._durationTimer = null;
    this._recordRestartTimer = null;
    this._recordRestartCallback = null;

    this._audioManager = null;
    this._wsManager = null;
  }

  setMedia(audioManager, wsManager) {
    this._audioManager = audioManager || null;
    this._wsManager = wsManager || null;
  }

  clearMedia() {
    this._audioManager = null;
    this._wsManager = null;
  }

  _stopMedia() {
    if (this._audioManager) {
      try { this._audioManager.stopRecord(); } catch (_) {}
      try { this._audioManager.stopPlayback(); } catch (_) {}
    }
    if (this._wsManager) {
      try { this._wsManager.sendListenStop(); } catch (_) {}
      try { this._wsManager.disconnect(); } catch (_) {}
    }
  }

  getState() {
    return {
      state: this._state,
      durationSeconds: this._durationSeconds,
      isMuted: this._isMuted,
      isSpeakerOn: this._isSpeakerOn,
      startTime: this._startTime,
      recordRestartAt: this._recordRestartAt,
    };
  }

  _setState(next) {
    if (this._state === next) return;
    this._state = next;
    this._emit();
  }

  _emit() {
    const state = this.getState();
    this._listeners.forEach((fn) => {
      try { fn(state); } catch (_) {}
    });
  }

  onStateChange(callback) {
    if (typeof callback !== 'function') return;
    this._listeners.add(callback);
  }

  offStateChange(callback) {
    this._listeners.delete(callback);
  }

  startCall() {
    this._durationSeconds = 0;
    this._isMuted = false;
    this._isSpeakerOn = true;
    this._startTime = null;
    this._setState(STATE_CALLING);
  }

  connect() {
    this._startTime = Date.now();
    this._startDurationTimer();
    this._scheduleRecordRestart();
    this._setState(STATE_CONNECTED);
  }

  hangup() {
    this._stopDurationTimer();
    this._stopRecordRestartTimer();
    this._stopMedia();
    this.clearMedia();
    this._setState(STATE_ENDED);
  }

  toggleMute() {
    this._isMuted = !this._isMuted;
    this._emit();
  }

  toggleSpeaker() {
    this._isSpeakerOn = !this._isSpeakerOn;
    this._emit();
  }

  setOnRecordRestart(callback) {
    this._recordRestartCallback = callback;
  }

  destroy() {
    this._stopDurationTimer();
    this._stopRecordRestartTimer();
    this._listeners.clear();
    this._recordRestartCallback = null;
    this._audioManager = null;
    this._wsManager = null;
  }

  _startDurationTimer() {
    this._stopDurationTimer();
    this._durationTimer = setInterval(() => {
      this._durationSeconds += 1;
      this._emit();
    }, 1000);
  }

  _stopDurationTimer() {
    if (this._durationTimer) {
      clearInterval(this._durationTimer);
      this._durationTimer = null;
    }
  }

  _scheduleRecordRestart() {
    this._stopRecordRestartTimer();
    this._recordRestartAt = Date.now() + RECORD_RESTART_INTERVAL_MS;
    this._recordRestartTimer = setTimeout(() => {
      this._recordRestartTimer = null;
      this._recordRestartAt = null;
      if (this._state === STATE_CONNECTED && this._recordRestartCallback) {
        this._recordRestartCallback();
      }
      if (this._state === STATE_CONNECTED) {
        this._scheduleRecordRestart();
      }
    }, RECORD_RESTART_INTERVAL_MS);
  }

  _stopRecordRestartTimer() {
    if (this._recordRestartTimer) {
      clearTimeout(this._recordRestartTimer);
      this._recordRestartTimer = null;
    }
    this._recordRestartAt = null;
  }

  _triggerRecordRestartForTest() {
    if (this._recordRestartCallback) this._recordRestartCallback();
  }
}

let instance = null;

module.exports = function getInstance() {
  if (!instance) instance = new VoiceCallManager();
  return instance;
};
module.exports.VoiceCallManager = VoiceCallManager;
module.exports.STATE_IDLE = STATE_IDLE;
module.exports.STATE_CALLING = STATE_CALLING;
module.exports.STATE_CONNECTED = STATE_CONNECTED;
module.exports.STATE_ENDING = STATE_ENDING;
module.exports.STATE_ENDED = STATE_ENDED;
