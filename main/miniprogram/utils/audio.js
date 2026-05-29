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
      .then(() => this);

    // Recording state
    this._recorder = null;
    this._isRecording = false;
    this._pcmBacklog = new Int16Array(0);

    // Playback state
    this._audioCtx = null;
    this._playQueue = [];          // Array<Int16Array> waiting to be scheduled
    this._isPlaying = false;
    this._nextStartTime = 0;       // AudioContext currentTime cursor
    this._activeSources = new Set();

    this._destroyed = false;
  }

  /** Resolves when both encoder and decoder runtimes are loaded. */
  ready() {
    return this._readyPromise;
  }

  // -------------------------------------------------------------------------
  // Recording
  // -------------------------------------------------------------------------

  startRecord() {
    if (this._destroyed) throw new Error('AudioManager destroyed');
    if (this._isRecording) return;

    if (typeof wx === 'undefined' || !wx.getRecorderManager) {
      this._emitError(new Error('wx.getRecorderManager unavailable'), 'record');
      return;
    }

    if (!this._recorder) {
      this._recorder = wx.getRecorderManager();
      this._recorder.onStart(() => {
        this._isRecording = true;
        this._pcmBacklog = new Int16Array(0);
        if (this.options.onRecordStart) this.options.onRecordStart();
      });
      this._recorder.onStop((res) => {
        this._isRecording = false;
        // Flush any remaining samples by zero-padding to a full frame.
        if (this._pcmBacklog.length > 0) {
          const padded = new Int16Array(FRAME_SAMPLES);
          padded.set(this._pcmBacklog.subarray(0, Math.min(this._pcmBacklog.length, FRAME_SAMPLES)));
          this._encodeAndEmit(padded);
          this._pcmBacklog = new Int16Array(0);
        }
        if (this.options.onRecordStop) this.options.onRecordStop(res && res.tempFilePath);
      });
      this._recorder.onError((err) => {
        this._isRecording = false;
        this._emitError(new Error('recorder error: ' + (err && err.errMsg)), 'record');
      });
      this._recorder.onFrameRecorded((res) => {
        if (!this._isRecording || !res || !res.frameBuffer) return;
        try {
          this._handleRecordedFrame(res.frameBuffer);
        } catch (e) {
          this._emitError(e, 'encode');
        }
      });
    }

    this._recorder.start({
      duration: 600000,                 // 10 min cap (RecorderManager max)
      sampleRate: SAMPLE_RATE,
      numberOfChannels: CHANNELS,
      encodeBitRate: 48000,             // ignored for PCM, but required field
      format: 'PCM',
      frameSize: RECORDER_FRAME_KB,
    });
  }

  stopRecord() {
    if (!this._recorder || !this._isRecording) return;
    try {
      this._recorder.stop();
    } catch (e) {
      this._emitError(e, 'record');
    }
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
    if (this._activeSources.size > 0) {
      this._activeSources.forEach((src) => {
        try { src.stop(0); } catch (_) {}
        try { src.disconnect(); } catch (_) {}
      });
      this._activeSources.clear();
    }
    this._isPlaying = false;
    this._nextStartTime = 0;
  }

  _ensureAudioContext() {
    if (this._audioCtx) return this._audioCtx;
    if (typeof wx === 'undefined' || !wx.createWebAudioContext) {
      this._emitError(new Error('wx.createWebAudioContext requires base lib >= 2.19.0'), 'play');
      return null;
    }
    this._audioCtx = wx.createWebAudioContext();
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
    try { this.stopRecord(); } catch (_) {}
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
