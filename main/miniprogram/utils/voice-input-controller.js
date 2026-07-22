const STATE_IDLE = 'idle';
const STATE_STARTING = 'starting';
const STATE_RECORDING = 'recording';
const STATE_STOPPING = 'stopping';
const STATE_CANCELLING = 'cancelling';
const STATE_WAITING = 'waiting';
const ACTIVE_AUDIO_STATES = new Set([STATE_RECORDING, STATE_STOPPING]);

class VoiceInputController {
  constructor(options) {
    this.options = options || {};
    this.audio = this.options.audio;
    this.socket = this.options.socket;
    this.state = STATE_IDLE;
    this.turn = null;
    this._turnSeed = 0;
    this._ackTimer = null;
    this._responseTimer = null;
    this._destroyed = false;
    this._cancelPromise = null;
  }

  getState() {
    return this.state;
  }

  press() {
    if (this._destroyed || this.state !== STATE_IDLE) return false;
    if (!this.audio || !this.audio.isReady()) {
      this._userError('语音引擎准备中，请稍后重试');
      return false;
    }
    if (!this.audio.isUsable()) {
      this._userError('语音引擎加载失败，请重启微信后重试');
      return false;
    }

    const id = 'm-' + (++this._turnSeed);
    this.turn = Object.freeze({
      id,
      generation: this.socket && this.socket.getConnectionGeneration(),
      serverStarted: false,
      frameCount: 0,
      cancelled: false,
      cancelling: false,
      ackReceived: false,
      pendingTerminal: null,
      terminalAction: null,
    });
    this._setState(STATE_STARTING);
    if (!this.audio.startRecord()) {
      this._finishLocal('error', 'record_start_rejected');
      return false;
    }
    return true;
  }

  setCancelled(cancelled) {
    if (!this.turn || this._destroyed) return;
    this._patchTurn({ cancelled: Boolean(cancelled) });
  }

  handleRecordStart() {
    const turn = this.turn;
    if (!turn || this.state !== STATE_STARTING) return;
    const id = turn.id;
    this._patchTurn({ serverStarted: true });
    this._setState(STATE_RECORDING);
    this._callSocket('beginVoiceTurn', [id], id, 'send_failed');
  }

  handleAudioFrame(frame) {
    const turn = this.turn;
    if (!turn || !frame || turn.cancelled || !ACTIVE_AUDIO_STATES.has(this.state)) return;
    const id = turn.id;
    this._patchTurn({ frameCount: turn.frameCount + 1 });
    this._callSocket('sendVoiceFrame', [id, frame], id, 'send_failed');
  }

  handleAudioFailure(scope) {
    const turn = this.turn;
    if (!turn || (scope !== 'record' && scope !== 'encode')) return Promise.resolve();
    return this._failTurn(turn.id, scope === 'encode' ? 'encode_failed' : 'record_failed');
  }

  async release() {
    const turn = this.turn;
    if (!turn || ![STATE_STARTING, STATE_RECORDING].includes(this.state)) return;
    if (turn.cancelled) return this.cancel('slide-cancel');
    const id = turn.id;
    this._setState(STATE_STOPPING);
    let stop;
    try {
      stop = await this.audio.stopRecord({
        flush: turn.serverStarted,
        reason: turn.serverStarted ? 'release' : 'released-before-start',
      });
    } catch (_) {
      await this._failTurn(id, 'record_stop_failed');
      return;
    }
    if (!this._isCurrent(id)) return;
    const current = this.turn;
    if (!current.serverStarted) {
      this._finishLocal('cancelled', 'released-before-start');
      return;
    }
    if (current.cancelled || current.cancelling) {
      this._abortCurrent(id, 'cancelled');
      return;
    }
    if (current.frameCount === 0 || (stop && stop.timedOut)) {
      this._abortCurrent(id, 'no_audio');
      return;
    }

    this._patchTurn({ terminalAction: 'finish-pending' });
    try {
      await this.socket.finishVoiceTurn(id);
    } catch (_) {
      await this._failTurn(id, 'send_failed');
      return;
    }
    if (!this._isCurrent(id)) return;
    const finished = this._patchTurn({ terminalAction: 'finish-confirmed' });
    if (finished.pendingTerminal) {
      this._finishLocal(finished.pendingTerminal, 'server_terminal');
      return;
    }
    this._setState(STATE_WAITING);
    if (finished.ackReceived) this._startResponseTimer(id);
    else this._startAckTimer(id);
    if (typeof this.options.onWaiting === 'function') this.options.onWaiting(id);
  }

  cancel(reason) {
    const turn = this.turn;
    if (!turn) return Promise.resolve();
    if (turn.cancelling) return this._cancelPromise || Promise.resolve();
    const id = turn.id;
    this._patchTurn({ cancelled: true, cancelling: true });
    this._setState(STATE_CANCELLING);
    this._cancelPromise = this._cancelCurrent(id, reason || 'cancelled');
    return this._cancelPromise;
  }

  async _cancelCurrent(id, reason) {
    try {
      await this.audio.stopRecord({ flush: false, reason });
    } catch (_) {
      // Cancellation still has to close the remote turn when native stop fails.
    }
    if (!this._isCurrent(id)) return;
    this._abortCurrent(id, 'cancelled');
  }

  handleSocketState(state, generation) {
    const turn = this.turn;
    if (!turn || this._destroyed) return;
    if (generation !== undefined && generation !== turn.generation) {
      this._failTurn(turn.id, 'socket_generation_changed');
      return;
    }
    if (state === 'disconnected') this._failTurn(turn.id, 'socket_disconnected');
  }

  handleMessage(message) {
    const turn = this.turn;
    if (!turn || !message || this._destroyed) return;
    const type = message.type;
    const state = message.state;
    const turnId = message.turnId === undefined
      ? (message.turn_id === undefined ? null : String(message.turn_id))
      : String(message.turnId);
    const matchingTurn = turnId === turn.id;

    if (type === 'listen') {
      if (!matchingTurn) return;
      if (state === 'stopped') {
        if (turn.ackReceived) return;
        this._patchTurn({ ackReceived: true });
        if (this.state === STATE_WAITING) this._startResponseTimer(turn.id);
        return;
      }
      if (['no_speech', 'error', 'cancelled'].includes(state)) {
        if (this.state === STATE_STOPPING) {
          this._patchTurn({ pendingTerminal: state });
        } else if (this.state === STATE_WAITING) {
          this._finishLocal(state, 'server_terminal');
        }
      }
      return;
    }

    const isRecognitionStart = type === 'stt' || (type === 'tts' && state === 'start');
    if (!isRecognitionStart) return;
    if (turnId && !matchingTurn) return;
    if (!turnId && (!this._sameGeneration(turn) || this.state !== STATE_WAITING)) return;
    if (this.state === STATE_STOPPING) {
      this._patchTurn({ pendingTerminal: 'recognized' });
    } else if (this.state === STATE_WAITING) {
      this._finishLocal('recognized', 'recognized');
    }
  }

  destroy() {
    if (this._destroyed) return;
    this._destroyed = true;
    this._clearTimers();
    const turn = this.turn;
    if (!turn) return;
    this._patchTurn({ cancelled: true, cancelling: true });
    try {
      const result = this.audio.stopRecord({ flush: false, reason: 'destroy' });
      Promise.resolve(result).catch(() => {});
    } catch (_) {}
    if (turn.serverStarted && turn.terminalAction !== 'finish-confirmed' && turn.terminalAction !== 'abort') {
      this._sendAbort(turn.id);
    }
    this.turn = null;
    this._setState(STATE_IDLE);
  }

  _callSocket(method, args, id, failure) {
    let result;
    try {
      result = this.socket[method].apply(this.socket, args);
    } catch (_) {
      this._failTurn(id, failure);
      return;
    }
    Promise.resolve(result).catch(() => this._failTurn(id, failure));
  }

  _abortCurrent(id, outcome) {
    if (!this._isCurrent(id)) return;
    const turn = this.turn;
    if (turn.terminalAction === 'finish-confirmed' || turn.terminalAction === 'abort') {
      this._finishLocal(outcome, 'terminal_already_sent');
      return;
    }
    this._patchTurn({ terminalAction: 'abort' });
    this._sendAbort(id);
    this._finishLocal(outcome, 'abort');
  }

  _sendAbort(id) {
    try {
      const result = this.socket.abortVoiceTurn(id);
      Promise.resolve(result).catch(() => {});
    } catch (_) {}
  }

  async _failTurn(id, reason) {
    if (!this._isCurrent(id)) return;
    const turn = this.turn;
    this._clearTimers();
    if ([STATE_STARTING, STATE_RECORDING].includes(this.state)) {
      try {
        const stop = this.audio.stopRecord({ flush: false, reason: 'failure' });
        Promise.resolve(stop).catch(() => {});
      } catch (_) {}
    }
    if (turn.serverStarted && turn.terminalAction !== 'finish-confirmed' && turn.terminalAction !== 'abort') {
      this._patchTurn({ terminalAction: 'abort' });
      this._sendAbort(id);
    }
    this._userErrorFor(reason);
    const outcome = ['ack_timeout', 'response_timeout'].includes(reason) ? reason : 'error';
    this._finishLocal(outcome, reason);
  }

  _finishLocal(outcome) {
    if (!this.turn) return;
    this._clearTimers();
    this.turn = null;
    this._cancelPromise = null;
    this._setState(STATE_IDLE);
    if (typeof this.options.onTerminal === 'function') this.options.onTerminal(outcome);
  }

  _patchTurn(patch) {
    if (!this.turn) return null;
    this.turn = Object.freeze(Object.assign({}, this.turn, patch));
    return this.turn;
  }

  _isCurrent(id) {
    return Boolean(!this._destroyed && this.turn && this.turn.id === id);
  }

  _sameGeneration(turn) {
    return Boolean(this.socket && this.socket.getConnectionGeneration() === turn.generation);
  }

  _startAckTimer(id) {
    this._clearAckTimer();
    const delay = this.options.ackTimeoutMs === undefined ? 5000 : this.options.ackTimeoutMs;
    this._ackTimer = setTimeout(() => this._failTurn(id, 'ack_timeout'), delay);
  }

  _startResponseTimer(id) {
    this._clearAckTimer();
    this._clearResponseTimer();
    const delay = this.options.responseTimeoutMs === undefined ? 30000 : this.options.responseTimeoutMs;
    this._responseTimer = setTimeout(() => this._failTurn(id, 'response_timeout'), delay);
  }

  _clearAckTimer() {
    if (this._ackTimer) clearTimeout(this._ackTimer);
    this._ackTimer = null;
  }

  _clearResponseTimer() {
    if (this._responseTimer) clearTimeout(this._responseTimer);
    this._responseTimer = null;
  }

  _clearTimers() {
    this._clearAckTimer();
    this._clearResponseTimer();
  }

  _setState(next) {
    if (this.state === next) return;
    this.state = next;
    if (typeof this.options.onStateChange === 'function') this.options.onStateChange(next);
  }

  _userErrorFor(reason) {
    const messages = {
      record_start_rejected: '无法启动录音，请重试',
      record_failed: '录音失败，请重试',
      encode_failed: '语音编码失败，请重试',
      record_stop_failed: '录音停止失败，请重试',
      send_failed: '语音发送失败，请重试',
      socket_generation_changed: '语音连接已更新，请重试',
      socket_disconnected: '语音连接已断开，请重试',
      ack_timeout: '语音服务响应超时，请重试',
      response_timeout: '语音响应超时，请重试',
    };
    if (messages[reason]) this._userError(messages[reason]);
  }

  _userError(message) {
    if (typeof this.options.onUserError === 'function') this.options.onUserError(message);
  }
}

module.exports = VoiceInputController;
module.exports.VoiceInputController = VoiceInputController;
module.exports.STATE_IDLE = STATE_IDLE;
module.exports.STATE_STARTING = STATE_STARTING;
module.exports.STATE_RECORDING = STATE_RECORDING;
module.exports.STATE_STOPPING = STATE_STOPPING;
module.exports.STATE_CANCELLING = STATE_CANCELLING;
module.exports.STATE_WAITING = STATE_WAITING;
