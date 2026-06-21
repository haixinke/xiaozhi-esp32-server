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

class VoiceCallManager {
  constructor() {
    this._state = STATE_IDLE;
    this._durationSeconds = 0;
    this._isMuted = false;
    this._isSpeakerOn = true;
    this._startTime = null;
    this._listeners = new Set();
  }

  getState() {
    return {
      state: this._state,
      durationSeconds: this._durationSeconds,
      isMuted: this._isMuted,
      isSpeakerOn: this._isSpeakerOn,
      startTime: this._startTime,
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
    this._setState(STATE_CONNECTED);
  }

  hangup() {
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
