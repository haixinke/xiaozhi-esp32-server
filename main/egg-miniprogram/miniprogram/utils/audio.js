/**
 * utils/audio.js
 * --------------------------------------------------------------------------
 * AudioManager — playback-only variant for the egg-miniprogram chat.
 *
 * Receives Opus frames from the xiaozhi-server WebSocket, decodes them,
 * and plays them gaplessly through wx.createWebAudioContext().
 *
 * Lifecycle:
 *   const audio = new AudioManager({ onPlayEnd, onError });
 *   await audio.ready();
 *   audio.appendOpusFrame(frame);
 *   audio.stopPlayback();
 *   audio.destroy();
 *
 * Notes:
 *   - WebAudioContext requires base library >= 2.19.0.
 *   - Recording/encoding code is intentionally omitted; this file only handles
 *     inbound audio playback.
 * --------------------------------------------------------------------------
 */

const OpusDecoder = require('../libs/opus/opus-decoder');

const SAMPLE_RATE = 24000;
const CHANNELS = 1;
const FRAME_DURATION_MS = 60;
const FRAME_SAMPLES = SAMPLE_RATE * FRAME_DURATION_MS / 1000; // 1440

class AudioManager {
  /**
   * @param {Object} options
   * @param {() => void} [options.onPlayEnd] Fires when playback queue drains.
   * @param {(err: Error, scope: string) => void} [options.onError]
   */
  constructor(options) {
    this.options = options || {};

    this.decoder = new OpusDecoder({
      sampleRate: SAMPLE_RATE,
      channels: CHANNELS,
      frameDuration: FRAME_DURATION_MS,
    });

    this._readyPromise = this.decoder.ready()
      .then(() => {
        this._codecMode = this.decoder.mode;
        if (this._codecMode === 'stub') {
          this._emitError(new Error('Opus 解码器回退到 stub，真 Opus 不可用'), 'codec');
        }
        return this;
      });

    // Playback state
    this._audioCtx = null;
    this._playQueue = [];
    this._isPlaying = false;
    this._nextStartTime = 0;
    this._activeSources = new Set();

    this._destroyed = false;
    this._codecMode = null;
  }

  /** Resolves when the decoder runtime is loaded. */
  ready() {
    return this._readyPromise;
  }

  // -------------------------------------------------------------------------
  // Playback
  // -------------------------------------------------------------------------

  /**
   * Decode and enqueue an array of opus frames.
   */
  playOpusFrames(frames) {
    if (!frames || !frames.length) return;
    for (let i = 0; i < frames.length; i++) {
      this.appendOpusFrame(frames[i]);
    }
  }

  /**
   * Decode one frame and append it to the play queue.
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
   * Recreate the WebAudioContext so the next playback starts cleanly.
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
    try { this.stopPlayback(); } catch (_) {}
    if (this._audioCtx && typeof this._audioCtx.close === 'function') {
      try { this._audioCtx.close(); } catch (_) {}
    }
    this._audioCtx = null;
    if (this.decoder) this.decoder.destroy();
    this.decoder = null;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  _emitError(err, scope) {
    if (this.options && this.options.onError) {
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
