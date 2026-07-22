/**
 * utils/audio.js
 * --------------------------------------------------------------------------
 * AudioManager — bridges WeChat's RecorderManager and WebAudioContext with
 * the Opus encoder / decoder facades.
 *
 * Responsibilities:
 *   - Capture mic audio at 24 kHz mono PCM via RecorderManager.
 *   - Re-frame the incoming PCM stream into exact 60 ms (1440-sample) chunks.
 *   - Encode each chunk with OpusEncoder and emit it through `onAudioFrame`.
 *   - Receive Opus frames from the network, decode them, and play them
 *     gaplessly through wx.createWebAudioContext().
 *
 * Lifecycle:
 *   const audio = new AudioManager({ onAudioFrame, onRecordStart, onRecordStop, onPlayEnd });
 *   await audio.ready();
 *   audio.startRecord();
 *   audio.appendOpusFrame(frame);
 *   audio.stopRecord();
 *   audio.stopPlayback();
 *   audio.destroy();
 *
 * Notes:
 *   - WeChat's RecorderManager reports `frameSize` in KB, not ms. We pick a
 *     small KB chunk and re-frame in JS so the Opus payload stays at exactly
 *     1440 samples.
 *   - WebAudioContext requires base library >= 2.19.0. If it's missing we
 *     log and refuse to start playback rather than crash.
 * --------------------------------------------------------------------------
 */

const OpusEncoder = require('../libs/opus/opus-encoder');
const OpusDecoder = require('../libs/opus/opus-decoder');

const SAMPLE_RATE = 24000;
const CHANNELS = 1;
const FRAME_DURATION_MS = 60;
const FRAME_SAMPLES = SAMPLE_RATE * FRAME_DURATION_MS / 1000; // 1440
// 60 ms of int16 PCM is 2880 bytes. Tell the recorder to emit ~3 KB chunks
// and we'll re-frame precisely in JS.
const RECORDER_FRAME_KB = 3;
const RECORD_STOP_TIMEOUT_MS = 2000;

class AudioManager {
  /**
   * @param {Object} options
   * @param {(frame: ArrayBuffer) => void} [options.onAudioFrame]
   *        Called for every encoded Opus frame produced from the mic.
   * @param {() => void} [options.onRecordStart]
   * @param {(reason?: string) => void} [options.onRecordStop]
   * @param {() => void} [options.onPlayEnd]
   *        Fires when the playback queue drains.
   * @param {(err: Error, scope: string) => void} [options.onError]
   */
  constructor(options) {
    this.options = options || {};

    this._destroyed = false;
    this._readySettled = false;
    this._readyError = null;

    this.encoder = new OpusEncoder({
      sampleRate: SAMPLE_RATE,
      channels: CHANNELS,
      frameDuration: FRAME_DURATION_MS,
    });
    this.decoder = new OpusDecoder({
      sampleRate: SAMPLE_RATE,
      channels: CHANNELS,
      frameDuration: FRAME_DURATION_MS,
    });

    this._readyPromise = Promise.all([this.encoder.ready(), this.decoder.ready()])
      .then(() => {
        if (this._destroyed || !this.encoder) return this;
        // Opus WASM 加载失败时会回退到 stub，stub 产出的假帧服务端无法识别。
        // 记录 codec 模式，供录音链路拦截并告警，避免静默失效（Android 尤易触发）。
        this._codecMode = this.encoder.mode;
        this._readySettled = true;
        if (this._codecMode === 'stub') {
          this._emitError(new Error('Opus 编码器回退到 stub，真 Opus 不可用'), 'codec');
        }
        return this;
      })
      .catch((err) => {
        this._readySettled = true;
        this._readyError = err;
        throw err;
      });

    // Recording state
    this._recorder = null;
    this._isRecording = false;
    this._pcmBacklog = new Int16Array(0);
    this._recordState = 'idle';
    this._stopRequested = null;
    this._stopDeferred = null;
    this._recordStopTimer = null;
    this._recordStopTimeoutMs = this.options.recordStopTimeoutMs || RECORD_STOP_TIMEOUT_MS;
    this._recordFaulted = false;

    // Playback state
    this._audioCtx = null;
    this._playQueue = [];          // Array<Int16Array> waiting to be scheduled
    this._isPlaying = false;
    this._nextStartTime = 0;       // AudioContext currentTime cursor
    this._activeSources = new Set();

    // 录音 file error 自愈：标记本轮是否已重试过，避免无限重试。
    this._recordRetried = false;
    // 自愈重试定时器句柄：destroy/stopRecord 时需清除，避免对已销毁实例触发 startRecord。
    this._recordRetryTimer = null;
    this._retryAfterStop = false;
    // Opus 编解码运行模式：'wasm'（真 Opus）| 'stub'（假帧，服务端无法识别）| null（未就绪）。
    this._codecMode = null;
  }

  /** Resolves when both encoder and decoder runtimes are loaded. */
  ready() {
    return this._readyPromise;
  }

  isReady() {
    return this._readySettled && !this._readyError;
  }

  isUsable() {
    return this.isReady() && this._codecMode === 'wasm' && !this._destroyed && !this._recordFaulted;
  }

  getRecordState() {
    return this._recordState;
  }

  // -------------------------------------------------------------------------
  // Recording
  // -------------------------------------------------------------------------

  startRecord() {
    if (this._destroyed || this._recordState !== 'idle' || !this.isUsable()) return false;

    // 录音前先停止播放并释放播放资源：iOS 全局只有一套音频会话，
    // 播放（WebAudioContext / InnerAudio）占用会话时启动录音会触发 file error。
    this.stopPlayback();

    if (typeof wx === 'undefined' || !wx.getRecorderManager) {
      this._emitError(new Error('wx.getRecorderManager unavailable'), 'record');
      return false;
    }

    if (!this._recorder) {
      const recorder = wx.getRecorderManager();
      this._recorder = recorder;
      recorder.onStart(() => {
        if (this._destroyed || this._recordFaulted) return;
        this._isRecording = true;
        this._recordRetried = false;
        if (this._recordRetryTimer) {
          clearTimeout(this._recordRetryTimer);
          this._recordRetryTimer = null;
        }
        this._pcmBacklog = new Int16Array(0);
        if (this._stopRequested) {
          this._recordState = 'stopping';
          try { recorder.stop(); } catch (err) { this._failRecordStop(err, true); }
          return;
        }
        if (this._recordState !== 'starting') {
          this._isRecording = false;
          try { recorder.stop(); } catch (err) { this._emitError(err, 'record'); }
          return;
        }
        this._recordState = 'recording';
        if (this.options.onRecordStart) this.options.onRecordStart();
      });
      recorder.onStop((res) => {
        if (this._destroyed || this._recordFaulted) return;
        this._isRecording = false;
        const stopOptions = this._stopRequested;
        if (!stopOptions && this._recordState === 'idle') return;
        let flushedFrames = 0;
        let flushError = null;
        try {
          // Flush any remaining samples by zero-padding to a full frame only when requested.
          if (stopOptions && stopOptions.flush && this._pcmBacklog.length > 0) {
            const padded = new Int16Array(FRAME_SAMPLES);
            padded.set(this._pcmBacklog.subarray(0, Math.min(this._pcmBacklog.length, FRAME_SAMPLES)));
            this._encodeAndEmit(padded);
            flushedFrames = 1;
          }
        } catch (err) {
          flushError = err;
          this._emitError(err, 'encode');
        } finally {
          this._pcmBacklog = new Int16Array(0);
          this._clearRecordStopTimer();
          this._recordState = 'idle';
          this._stopRequested = null;
          const deferred = this._stopDeferred;
          this._stopDeferred = null;
          if (deferred) {
            if (flushError) {
              deferred.reject(flushError);
            } else {
              deferred.resolve({
                reason: stopOptions.reason,
                flushedFrames,
                timedOut: false,
              });
            }
          }
        }
        if ((!stopOptions || stopOptions.reason !== 'internal-retry') && this.options.onRecordStop) {
          this.options.onRecordStop(res && res.tempFilePath);
        }
      });
      recorder.onError((err) => {
        if (this._destroyed || this._recordFaulted) return;
        this._isRecording = false;
        const errMsg = (err && err.errMsg) || '';
        // iOS 音频会话偶发被占用会报 "file error"，自动重试一次自愈：
        // 先停掉旧会话、短延时后重新拉起录音；仍失败才向上抛错。
        if (/file error/i.test(errMsg) && this._hasExternalStopRequest()) return;
        if (!this._recordRetried && /file error/i.test(errMsg)) {
          this._recordRetried = true;
          this._recordState = 'retrying';
          this._retryAfterStop = true;
          this._requestRecordStop({ flush: false, reason: 'internal-retry' })
            .then(() => {
              if (!this._retryAfterStop || this._destroyed) return;
              this._recordRetryTimer = setTimeout(() => {
                this._recordRetryTimer = null;
                if (this._retryAfterStop && !this._destroyed) this.startRecord();
              }, 300);
            })
            .catch(() => {});
          return;
        }
        this._emitError(new Error('recorder error: ' + errMsg), 'record');
      });
      recorder.onFrameRecorded((res) => {
        if (this._recordFaulted || !this._isRecording || !res || !res.frameBuffer) return;
        try {
          this._handleRecordedFrame(res.frameBuffer);
        } catch (e) {
          this._emitError(e, 'encode');
        }
      });
    }

    this._recordState = 'starting';
    try {
      this._recorder.start({
        duration: 600000,                 // 10 min cap (RecorderManager max)
        sampleRate: SAMPLE_RATE,
        numberOfChannels: CHANNELS,
        encodeBitRate: 48000,             // ignored for PCM, but required field
        format: 'PCM',
        frameSize: RECORDER_FRAME_KB,
      });
      return true;
    } catch (err) {
      this._recordState = 'idle';
      this._emitError(err, 'record');
      return false;
    }
  }

  stopRecord(options) {
    // 即使正在自愈重试窗口内（_isRecording 已被 onError 置 false），也要清掉重试定时器，
    // 否则用户停止后仍会被定时器拉起一次录音。
    if (this._recordRetryTimer) {
      clearTimeout(this._recordRetryTimer);
      this._recordRetryTimer = null;
    }
    this._retryAfterStop = false;
    return this._requestRecordStop(Object.assign({ flush: true, reason: 'stop' }, options || {}));
  }

  _requestRecordStop(options) {
    if (this._recordState === 'idle') {
      return Promise.resolve({ reason: options.reason, flushedFrames: 0, timedOut: false });
    }
    if (this._stopDeferred) return this._stopDeferred.promise;

    let resolveStop;
    let rejectStop;
    const promise = new Promise((resolve, reject) => {
      resolveStop = resolve;
      rejectStop = reject;
    });
    this._stopRequested = options;
    this._stopDeferred = { promise, resolve: resolveStop, reject: rejectStop };
    // Existing voice-call callers intentionally ignore this Promise.
    promise.catch(() => {});

    if (this._recordState === 'recording' || this._recordState === 'retrying') {
      this._recordState = 'stopping';
      try { this._recorder.stop(); } catch (err) { this._failRecordStop(err, true); }
    }
    if (this._stopDeferred) {
      const deferred = this._stopDeferred;
      this._recordStopTimer = setTimeout(() => {
        if (this._stopDeferred === deferred) {
          this._failRecordStop(new Error('recorder onStop timeout'), true);
        }
      }, this._recordStopTimeoutMs);
    }
    return promise;
  }

  _clearRecordStopTimer() {
    if (this._recordStopTimer) {
      clearTimeout(this._recordStopTimer);
      this._recordStopTimer = null;
    }
  }

  _hasExternalStopRequest() {
    return Boolean(this._stopDeferred && this._stopRequested && this._stopRequested.reason !== 'internal-retry');
  }

  _failRecordStop(err, faultRecorder) {
    this._clearRecordStopTimer();
    this._pcmBacklog = new Int16Array(0);
    this._isRecording = false;
    this._recordState = 'idle';
    this._stopRequested = null;
    const deferred = this._stopDeferred;
    this._stopDeferred = null;
    if (faultRecorder) {
      this._recordFaulted = true;
      this._retryAfterStop = false;
    }
    if (deferred) deferred.reject(err);
    this._emitError(err, 'record');
  }

  /**
   * Append a chunk of raw PCM bytes to the backlog and drain as many full
   * 60 ms frames as possible.
   */
  _handleRecordedFrame(frameBuffer) {
    const incoming = new Int16Array(frameBuffer);
    if (incoming.length === 0) return;

    // Concatenate backlog + incoming.
    const merged = new Int16Array(this._pcmBacklog.length + incoming.length);
    merged.set(this._pcmBacklog);
    merged.set(incoming, this._pcmBacklog.length);

    let offset = 0;
    while (merged.length - offset >= FRAME_SAMPLES) {
      const frame = merged.subarray(offset, offset + FRAME_SAMPLES);
      // subarray shares the same buffer; encoder will copy it internally.
      this._encodeAndEmit(new Int16Array(frame));
      offset += FRAME_SAMPLES;
    }
    this._pcmBacklog = merged.slice(offset);
  }

  _encodeAndEmit(pcmFrame) {
    if (!this.options.onAudioFrame) return;
    // Opus 回退到 stub 时产出的并非真 Opus 帧，服务端无法识别，直接丢弃避免污染 ASR；
    // 用户提示已在 ready() 阶段通过 'codec' 错误发出。
    if (this._codecMode === 'stub') return;
    const encoded = this.encoder.encode(pcmFrame);
    this.options.onAudioFrame(encoded);
  }

  // -------------------------------------------------------------------------
  // Playback
  // -------------------------------------------------------------------------

  /**
   * Decode and enqueue an array of opus frames (ArrayBuffer / Uint8Array).
   */
  playOpusFrames(frames) {
    if (!frames || !frames.length) return;
    for (let i = 0; i < frames.length; i++) {
      this.appendOpusFrame(frames[i]);
    }
  }

  /**
   * Stream-friendly variant: decode one frame and append it to the play queue.
   * Starts the scheduler the first time data arrives.
   */
  appendOpusFrame(frame) {
    if (this._destroyed || !frame) return;
    let pcm;
    try {
      pcm = this.decoder.decode(frame);
    } catch (e) {
      this._emitError(e, 'decode');
      return;
    }
    if (!pcm || pcm.length === 0) return;

    this._playQueue.push(pcm);
    this._scheduleQueued();
  }

  stopPlayback() {
    this._playQueue.length = 0;
    this._stopActiveSources();
    this._isPlaying = false;
    this._nextStartTime = 0;
  }

  /**
   * Recreate the WebAudioContext so the next playback schedules on a fresh
   * context. This gives the runtime a chance to pick up a changed system audio
   * route (e.g. after wx.setInnerAudioOption), because WebAudioContext does not
   * observe route changes while it is alive.
   *
   * Active sources are stopped and queued frames are discarded so playback on
   * the new context starts cleanly.
   */
  resetAudioContext() {
    if (this._destroyed) return;
    this._playQueue.length = 0;
    this._stopActiveSources();
    this._isPlaying = false;
    this._nextStartTime = 0;

    const oldCtx = this._audioCtx;
    this._audioCtx = this._createAudioContext();

    if (oldCtx && typeof oldCtx.close === 'function') {
      try { oldCtx.close(); } catch (_) {}
    }
  }

  _stopActiveSources() {
    if (!this._activeSources || this._activeSources.size === 0) return;
    try {
      this._activeSources.forEach((src) => {
        try { src.stop(0); } catch (_) {}
        try { src.disconnect(); } catch (_) {}
      });
    } finally {
      this._activeSources.clear();
    }
  }

  _createAudioContext() {
    if (typeof wx === 'undefined' || !wx.createWebAudioContext) {
      this._emitError(new Error('wx.createWebAudioContext requires base lib >= 2.19.0'), 'play');
      return null;
    }
    return wx.createWebAudioContext();
  }

  _ensureAudioContext() {
    if (this._audioCtx) return this._audioCtx;
    this._audioCtx = this._createAudioContext();
    return this._audioCtx;
  }

  _scheduleQueued() {
    const ctx = this._ensureAudioContext();
    if (!ctx) return;

    while (this._playQueue.length > 0) {
      const pcm = this._playQueue.shift();
      const buffer = ctx.createBuffer(CHANNELS, pcm.length, SAMPLE_RATE);
      const channelData = buffer.getChannelData(0);
      // Convert int16 [-32768, 32767] → float32 [-1, 1].
      for (let i = 0; i < pcm.length; i++) {
        channelData[i] = pcm[i] / 32768;
      }

      const source = ctx.createBufferSource();
      source.buffer = buffer;
      source.connect(ctx.destination);

      const now = ctx.currentTime;
      const startAt = Math.max(now, this._nextStartTime);
      try {
        source.start(startAt);
      } catch (e) {
        this._emitError(e, 'play');
        continue;
      }
      this._nextStartTime = startAt + buffer.duration;
      this._isPlaying = true;
      this._activeSources.add(source);

      source.onended = () => {
        this._activeSources.delete(source);
        try { source.disconnect(); } catch (_) {}
        if (this._activeSources.size === 0 && this._playQueue.length === 0) {
          this._isPlaying = false;
          this._nextStartTime = 0;
          if (this.options.onPlayEnd) this.options.onPlayEnd();
        }
      };
    }
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  destroy() {
    if (this._destroyed) return;
    this._destroyed = true;
    if (this._recordRetryTimer) {
      clearTimeout(this._recordRetryTimer);
      this._recordRetryTimer = null;
    }
    this._retryAfterStop = false;
    this._clearRecordStopTimer();
    if (this._recorder && this._recordState !== 'idle') {
      try { this._recorder.stop(); } catch (_) {}
    }
    this._recordState = 'idle';
    this._isRecording = false;
    this._pcmBacklog = new Int16Array(0);
    this._stopRequested = null;
    if (this._stopDeferred) {
      this._stopDeferred.reject(new Error('AudioManager destroyed'));
      this._stopDeferred = null;
    }
    try { this.stopPlayback(); } catch (_) {}
    if (this._audioCtx && typeof this._audioCtx.close === 'function') {
      try { this._audioCtx.close(); } catch (_) {}
    }
    this._audioCtx = null;
    this._recorder = null;
    if (this.encoder) this.encoder.destroy();
    if (this.decoder) this.decoder.destroy();
    this.encoder = null;
    this.decoder = null;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  _emitError(err, scope) {
    if (this.options.onError) {
      try { this.options.onError(err, scope); } catch (_) {}
    } else {
      console.error('[AudioManager:' + scope + ']', err);
    }
  }
}

module.exports = AudioManager;
module.exports.AudioManager = AudioManager;
module.exports.SAMPLE_RATE = SAMPLE_RATE;
module.exports.FRAME_SAMPLES = FRAME_SAMPLES;
module.exports.FRAME_DURATION_MS = FRAME_DURATION_MS;
