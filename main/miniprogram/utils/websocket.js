/**
 * utils/websocket.js
 * --------------------------------------------------------------------------
 * WebSocketManager — 与 xiaozhi-server 之间的实时双向通道。
 *
 * 设计要点：
 *   - 使用 wx.connectSocket 返回的 SocketTask（避免全局回调串扰）。
 *   - 鉴权信息经由 URL query（小程序无法自定义 WebSocket Header）。
 *   - 状态机：disconnected → connecting → connected → disconnected。
 *   - 30s 心跳 + 60s pong 超时检测；连接被动断开后自动指数退避重连（最多 5 次）。
 *   - 文本消息按 type 分发，二进制消息直通 onMessage 回调。
 *
 * options:
 *   onMessage(msg)      —— 收到一条消息，msg = { type, ...payload }
 *                          其中 type 可能为：'audio' | 'stt' | 'tts' | 'llm'
 *                          | 'hello' | 'goodbye' | 'iot' | 'mcp' | 'unknown'
 *   onStateChange(state)—— 'connecting' | 'connected' | 'disconnected'
 *   onError(err, scope) —— scope: 'connect' | 'send' | 'message' | 'parse'
 * --------------------------------------------------------------------------
 */

const PING_INTERVAL_MS = 30 * 1000;
const PONG_TIMEOUT_MS = 60 * 1000;
const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 15000];
const HANDSHAKE_TIMEOUT_MS = 10 * 1000;

class WebSocketManager {
  constructor(options) {
    this.options = options || {};

    this.socket = null;          // SocketTask
    this.sessionId = null;
    this.state = 'disconnected';

    this._url = null;
    this._deviceId = null;
    this._token = null;

    this._pingTimer = null;
    this._pongTimeoutTimer = null;
    this._reconnectTimer = null;
    this._reconnectAttempts = 0;
    this._handshakeTimer = null;

    this._destroyed = false;
    this._manualClose = false;   // disconnect() 主动断开时为 true
    this._helloSent = false;
    this._connectionGeneration = 0;
    this._voiceTurns = new Map();

    this._stateListeners = new Set();
  }

  // -------------------------------------------------------------------------
  // 公开方法
  // -------------------------------------------------------------------------

  onStateChange(callback) {
    if (typeof callback !== 'function') return;
    this._stateListeners.add(callback);
  }

  offStateChange(callback) {
    this._stateListeners.delete(callback);
  }

  /**
   * 建立连接。重复调用会先断开再重连。
   * @param {string} wsUrl     形如 ws://host:8000/xiaozhi/v1/
   * @param {string} deviceId  openid 作为设备标识
   * @param {string} [token]   鉴权 token（可选）
   */
  connect(wsUrl, deviceId, token) {
    if (this._destroyed) return;
    if (!wsUrl || !deviceId) {
      this._emitError(new Error('wsUrl/deviceId required'), 'connect');
      return;
    }

    this._url = wsUrl;
    this._deviceId = deviceId;
    this._token = token || null;

    // 已有连接：先优雅关闭再重连。
    if (this.socket) {
      this._teardownSocket(true);
    }

    this._setState('connecting');
    this._helloSent = false;
    this.sessionId = null;

    const fullUrl = this._buildUrl();

    let task;
    try {
      task = wx.connectSocket({
        url: fullUrl,
        // header 在小程序里仅对 https 生效，token 已放进 URL，留空即可。
        success: () => {},
        fail: (err) => {
          this._emitError(new Error('connectSocket fail: ' + (err && err.errMsg)), 'connect');
          this._scheduleReconnect();
        },
      });
    } catch (e) {
      this._emitError(e, 'connect');
      this._scheduleReconnect();
      return;
    }

    this.socket = task;
    const generation = ++this._connectionGeneration;
    this._bindSocketHandlers(task, generation);

    // 握手超时保护：若 10s 内服务端无响应，强制重连。
    this._handshakeTimer = setTimeout(() => {
      if (this._isCurrentSocket(task, generation) && this.state !== 'connected') {
        this._emitError(new Error('handshake timeout'), 'connect');
        this._teardownSocket(false);
        this._scheduleReconnect();
      }
    }, HANDSHAKE_TIMEOUT_MS);
  }

  /**
   * 主动断开。不会触发自动重连。
   */
  disconnect() {
    this._manualClose = true;
    this._cancelReconnect();
    this._teardownSocket(true);
    this._setState('disconnected');
  }

  /**
   * 完全销毁，释放回调引用。
   */
  destroy() {
    this._destroyed = true;
    this.disconnect();
    this._stateListeners.clear();
    this.options = {};
  }

  /** 是否已连接并完成握手。 */
  isConnected() {
    return this.state === 'connected';
  }

  getConnectionGeneration() {
    return this._connectionGeneration;
  }

  // -------------------------------------------------------------------------
  // 协议消息封装
  // -------------------------------------------------------------------------

  sendHello() {
    return this.send({
      type: 'hello',
      version: 1,
      transport: 'websocket',
      audio_params: {
        format: 'opus',
        sample_rate: 24000,
        channels: 1,
        frame_duration: 60,
      },
    });
  }

  sendListenStart(mode = 'manual') {
    return this.send({ type: 'listen', mode, state: 'start' });
  }

  sendListenStop() {
    return this.send({ type: 'listen', mode: 'manual', state: 'stop' });
  }

  sendAbort() {
    return this.send({ type: 'abort' });
  }

  sendPing() {
    return this.send({ type: 'ping' });
  }

  sendText(text) {
    // 使用服务端已支持的 listen+detect 模式发送文本
    return this.send({ type: 'listen', mode: 'manual', state: 'detect', text: text });
  }

  /**
   * 发送 Opus 二进制帧。AudioManager 输出的 ArrayBuffer 直接喂进来即可。
   */
  sendAudioFrame(frame) {
    if (!this.socket || this.state !== 'connected') return false;
    if (!frame) return false;
    try {
      this.socket.send({ data: frame });
      return true;
    } catch (e) {
      this._emitError(e, 'send');
      return false;
    }
  }

  beginVoiceTurn(turnId) {
    try {
      const session = this._createVoiceSession(turnId);
      return this._sendOnSession(session, JSON.stringify({
        type: 'listen', mode: 'manual', state: 'start', turn_id: turnId,
      }), 'start');
    } catch (err) {
      return this._rejectVoicePromise(err);
    }
  }

  sendVoiceFrame(turnId, frame) {
    const session = this._voiceTurns.get(turnId);
    if (!session || !frame) return this._rejectVoicePromise(new Error('voice turn unavailable'));
    if (session.terminal) return this._rejectVoicePromise(new Error('voice turn ending'));
    return this._sendOnSession(session, frame, 'frame');
  }

  finishVoiceTurn(turnId) {
    const session = this._voiceTurns.get(turnId);
    if (!session) return this._rejectVoicePromise(new Error('voice turn unavailable'));
    if (session.terminal) return this._rejectVoicePromise(new Error('voice turn ending'));
    session.terminal = 'end';
    session.phase = 'end-queued';
    const done = this._sendOnSession(session, JSON.stringify({
      type: 'listen', mode: 'manual', state: 'stop', turn_id: turnId,
    }), 'end');
    const result = done.finally(() => {
      session.closed = true;
      this._voiceTurns.delete(turnId);
    });
    result.catch(() => {});
    return result;
  }

  abortVoiceTurn(turnId) {
    const session = this._voiceTurns.get(turnId);
    if (!session) return this._rejectVoicePromise(new Error('voice turn unavailable'));
    if (session.terminal === 'end' && session.phase === 'end-inflight') {
      return this._rejectVoicePromise(new Error('voice turn end already in flight'));
    }
    if (session.terminal === 'abort') {
      return this._rejectVoicePromise(new Error('voice turn unavailable'));
    }

    this._voiceTurns.delete(turnId);
    session.terminal = 'abort';
    session.phase = 'abort-inflight';
    session.closed = true;
    if (!this._isCurrentSocket(session.task, session.generation) || this.state !== 'connected') {
      return this._rejectVoicePromise(new Error('stale voice socket generation'));
    }
    const result = this._sendOnTaskAsync(session.task, JSON.stringify({
      type: 'abort', turn_id: turnId,
    }));
    result.catch(() => {});
    return result;
  }

  /**
   * 发送 JSON 或字符串。对象会自动序列化。
   */
  send(data) {
    if (!this.socket || this.state !== 'connected') return false;
    const payload = typeof data === 'string' ? data : JSON.stringify(data);
    try {
      this.socket.send({ data: payload });
      return true;
    } catch (e) {
      this._emitError(e, 'send');
      return false;
    }
  }

  // -------------------------------------------------------------------------
  // 内部
  // -------------------------------------------------------------------------

  _buildUrl() {
    const sep = this._url.indexOf('?') >= 0 ? '&' : '?';
    let url = this._url + sep
      + 'device-id=' + encodeURIComponent(this._deviceId)
      + '&client-id=wechat-miniprogram';
    if (this._token) {
      url += '&authorization=' + encodeURIComponent('Bearer ' + this._token);
    }
    return url;
  }

  _bindSocketHandlers(task, generation) {
    task.onOpen(() => {
      if (!this._isCurrentSocket(task, generation)) return;
      // onOpen 后 state 仍为 connecting，等 hello 回来才升级为 connected。
      this._reconnectAttempts = 0;
      this._manualClose = false;
      // 此处直接发 hello（send() 会校验 state，需要先放行）。
      try {
        const helloPayload = {
          type: 'hello',
          version: 1,
          transport: 'websocket',
          audio_params: {
            format: 'opus',
            sample_rate: 24000,
            channels: 1,
            frame_duration: 60,
          },
        };
        task.send({ data: JSON.stringify(helloPayload) });
        this._helloSent = true;
      } catch (e) {
        this._emitError(e, 'send');
      }
    });

    task.onMessage((res) => {
      if (!this._isCurrentSocket(task, generation)) return;
      this._handleMessage(res);
    });

    task.onError((err) => {
      if (!this._isCurrentSocket(task, generation)) return;
      this._emitError(new Error('socket error: ' + (err && err.errMsg)), 'connect');
    });

    task.onClose((res) => {
      if (!this._isCurrentSocket(task, generation)) return;
      this._clearTimers();
      this._failVoiceSessions(task, generation, new Error('stale voice socket generation'));
      this.socket = null;
      this._setState('disconnected');
      if (!this._manualClose && !this._destroyed) {
        this._scheduleReconnect();
      }
    });
  }

  _handleMessage(res) {
    if (!res) return;
    const data = res.data;

    // 二进制：TTS 音频帧。
    if (data instanceof ArrayBuffer) {
      this._dispatch({ type: 'audio', data });
      return;
    }

    let msg;
    try {
      msg = JSON.parse(data);
    } catch (e) {
      this._emitError(e, 'parse');
      return;
    }

    if (!msg || typeof msg !== 'object') return;

    switch (msg.type) {
      case 'hello':
        // 服务端确认握手 → 升级为 connected。
        this.sessionId = msg.session_id || msg.sessionId || null;
        if (this._handshakeTimer) {
          clearTimeout(this._handshakeTimer);
          this._handshakeTimer = null;
        }
        this._setState('connected');
        this._startPing();
        this._dispatch({ type: 'hello', sessionId: this.sessionId, raw: msg });
        break;

      case 'pong':
        this._clearPongTimeout();
        break;

      case 'listen':
        this._dispatch({
          type: 'listen',
          state: msg.state || '',
          turnId: msg.turn_id === undefined ? null : String(msg.turn_id),
          reason: msg.reason || '',
          raw: msg,
        });
        break;

      case 'stt':
        this._dispatch({ type: 'stt', text: msg.text || '', raw: msg });
        break;

      case 'tts':
        this._dispatch({
          type: 'tts',
          state: msg.state,
          text: msg.text || '',
          raw: msg,
        });
        break;

      case 'llm':
        this._dispatch({
          type: 'llm',
          text: msg.text || '',
          emotion: msg.emotion || null,
          raw: msg,
        });
        break;

      case 'goodbye':
        this._dispatch({ type: 'goodbye', raw: msg });
        break;

      default:
        this._dispatch({ type: msg.type || 'unknown', raw: msg });
        break;
    }
  }

  _dispatch(msg) {
    if (this.options && this.options.onMessage) {
      try { this.options.onMessage(msg); } catch (e) { this._emitError(e, 'message'); }
    }
  }

  _setState(next) {
    if (this.state === next) return;
    this.state = next;
    this._stateListeners.forEach((fn) => {
      try { fn(next); } catch (_) {}
    });
    if (this.options && this.options.onStateChange) {
      try { this.options.onStateChange(next); } catch (_) {}
    }
  }

  _startPing() {
    this._stopPing();
    this._pingTimer = setInterval(() => {
      if (this.state === 'connected') {
        this.sendPing();
        // 仅在没有活跃 pong 超时计时器时才启动新的，
        // 避免每次 ping 都重置计时器导致超时永远不触发。
        // 正确逻辑：发 ping → 等 pong → 收到 pong 清除计时器 → 下次 ping 再启动
        if (!this._pongTimeoutTimer) {
          this._startPongTimeout();
        }
      }
    }, PING_INTERVAL_MS);
  }

  _stopPing() {
    if (this._pingTimer) {
      clearInterval(this._pingTimer);
      this._pingTimer = null;
    }
    this._clearPongTimeout();
  }

  _startPongTimeout() {
    this._clearPongTimeout();
    this._pongTimeoutTimer = setTimeout(() => {
      this._pongTimeoutTimer = null;
      this._emitError(new Error('pong timeout: server not responding'), 'connect');
      // 心跳超时视为被动断开：拆掉旧 socket 后走退避重连自愈，而非置 _manualClose 永久断开
      this._teardownSocket(false);
      this._setState('disconnected');
      this._scheduleReconnect();
    }, PONG_TIMEOUT_MS);
  }

  _clearPongTimeout() {
    if (this._pongTimeoutTimer) {
      clearTimeout(this._pongTimeoutTimer);
      this._pongTimeoutTimer = null;
    }
  }

  _scheduleReconnect() {
    if (this._destroyed || this._manualClose) return;
    if (this._reconnectTimer) return;
    if (!this._url || !this._deviceId) return;

    const idx = Math.min(this._reconnectAttempts, RECONNECT_DELAYS.length - 1);
    const delay = RECONNECT_DELAYS[idx];
    this._reconnectAttempts += 1;

    this._reconnectTimer = setTimeout(() => {
      this._reconnectTimer = null;
      if (this._destroyed || this._manualClose) return;
      this.connect(this._url, this._deviceId, this._token);
    }, delay);
  }

  _cancelReconnect() {
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    this._reconnectAttempts = 0;
  }

  _clearTimers() {
    this._stopPing();
    if (this._handshakeTimer) {
      clearTimeout(this._handshakeTimer);
      this._handshakeTimer = null;
    }
  }

  _teardownSocket(silent) {
    this._clearTimers();
    if (!this.socket) return;
    const task = this.socket;
    const generation = this._connectionGeneration;
    this.socket = null;
    this._failVoiceSessions(task, generation, new Error('stale voice socket generation'));
    try {
      task.close({ code: 1000, reason: silent ? 'client close' : 'reconnect' });
    } catch (_) {}
  }

  _isCurrentSocket(task, generation) {
    return this.socket === task && this._connectionGeneration === generation;
  }

  _createVoiceSession(turnId) {
    if (!turnId || this.state !== 'connected' || !this.socket) {
      throw new Error('voice socket unavailable');
    }
    if (this._voiceTurns.has(turnId)) throw new Error('duplicate voice turn');
    let rejectInvalidation;
    const invalidation = new Promise((_, reject) => {
      rejectInvalidation = reject;
    });
    invalidation.catch(() => {});
    const session = {
      turnId,
      task: this.socket,
      generation: this._connectionGeneration,
      tail: Promise.resolve(),
      failure: null,
      closed: false,
      hasQueuedSend: false,
      terminal: null,
      phase: 'active',
      invalidation,
      rejectInvalidation,
    };
    this._voiceTurns.set(turnId, session);
    return session;
  }

  _sendOnSession(session, data, operationType) {
    const send = () => {
      if (operationType === 'end' && session.terminal !== 'end') {
        if (session.failure) throw session.failure;
        throw new Error('voice turn terminal cancelled');
      }
      if (session.failure) throw session.failure;
      if (session.closed || !this._isCurrentSocket(session.task, session.generation) ||
          this.state !== 'connected') {
        throw new Error('stale voice socket generation');
      }
      session.phase = operationType + '-inflight';
      return Promise.race([this._sendOnTaskAsync(session.task, data), session.invalidation]);
    };
    let operation;
    if (session.hasQueuedSend) {
      operation = session.tail.then(send);
    } else {
      try {
        operation = Promise.resolve(send());
      } catch (err) {
        operation = Promise.reject(err);
      }
    }
    session.hasQueuedSend = true;
    session.tail = operation.catch((err) => {
      if (!session.failure) session.failure = err;
      throw session.failure;
    });
    session.tail.catch(() => {});
    return session.tail;
  }

  _sendOnTaskAsync(task, data) {
    return new Promise((resolve, reject) => {
      task.send({
        data,
        success: resolve,
        fail: (err) => reject(new Error('voice send fail: ' + ((err && err.errMsg) || 'unknown'))),
      });
    });
  }

  _failVoiceSessions(task, generation, error) {
    this._voiceTurns.forEach((session, turnId) => {
      if (session.task !== task || session.generation !== generation) return;
      session.closed = true;
      if (!session.failure) session.failure = error;
      session.rejectInvalidation(session.failure);
      this._voiceTurns.delete(turnId);
    });
  }

  _rejectVoicePromise(error) {
    const result = Promise.reject(error);
    result.catch(() => {});
    return result;
  }

  _emitError(err, scope) {
    if (this.options && this.options.onError) {
      try { this.options.onError(err, scope); } catch (_) {}
    } else {
      console.error('[WebSocketManager:' + scope + ']', err);
    }
  }
}

module.exports = WebSocketManager;
module.exports.WebSocketManager = WebSocketManager;
