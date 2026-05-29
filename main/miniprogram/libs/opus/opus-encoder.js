/**
 * opus-encoder.js
 * --------------------------------------------------------------------------
 * Thin facade over the shared opus runtime, presenting the public API
 * required by callers:
 *
 *   const enc = new OpusEncoder({ sampleRate: 24000, channels: 1, frameDuration: 60 });
 *   await enc.ready();
 *   const frameBytes = enc.encode(pcmInt16);   // -> ArrayBuffer
 *   enc.destroy();
 *
 * The constructor is synchronous, but the underlying WASM runtime resolves
 * asynchronously. Two usage patterns are supported:
 *   - Await `enc.ready()` before the first encode() call (recommended).
 *   - Call encode() eagerly: if the runtime isn't loaded yet a clear
 *     'encoder not ready' error is thrown.
 *
 * Frame size is derived from frameDuration (ms): samples = sampleRate * d/1000.
 * For 24 kHz / 60 ms this is 1440 samples, matching xiaozhi-server's expectation.
 * --------------------------------------------------------------------------
 */

const { getOpusRuntime } = require('./opus-runtime');

class OpusEncoder {
  /**
   * @param {Object} options
   * @param {number} [options.sampleRate=24000]
   * @param {number} [options.channels=1]
   * @param {number} [options.frameDuration=60] frame length in ms
   * @param {number} [options.bitrate=24000]
   * @param {number} [options.application=2048] OPUS_APPLICATION_VOIP
   */
  constructor(options) {
    const opts = options || {};
    this.sampleRate = opts.sampleRate || 24000;
    this.channels = opts.channels || 1;
    this.frameDuration = opts.frameDuration || 60;
    this.bitrate = opts.bitrate || 24000;
    this.application = opts.application || 2048;
    this.frameSize = Math.round(this.sampleRate * this.frameDuration / 1000);

    this._encoder = null;
    this._mode = null;

    this._readyPromise = getOpusRuntime().then((rt) => {
      this._encoder = rt.createEncoder({
        sampleRate: this.sampleRate,
        channels: this.channels,
        frameSize: this.frameSize,
        bitrate: this.bitrate,
        application: this.application,
      });
      this._mode = rt.mode;
      return this;
    });
  }

  /** Resolves once the underlying encoder is allocated. */
  ready() {
    return this._readyPromise;
  }

  /** 'wasm' (real Opus) or 'stub' (PCM-tagged fallback). */
  get mode() {
    return this._mode;
  }

  /**
   * Encode a single PCM frame.
   * @param {Int16Array} pcmData - exactly {@link frameSize} samples.
   * @returns {ArrayBuffer} encoded frame bytes.
   */
  encode(pcmData) {
    if (!this._encoder) {
      throw new Error('OpusEncoder not ready; await encoder.ready() first');
    }
    if (!(pcmData instanceof Int16Array)) {
      // Accept ArrayBuffer too, for convenience with RecorderManager output.
      if (pcmData && pcmData.byteLength !== undefined) {
        pcmData = new Int16Array(
          pcmData.buffer || pcmData,
          pcmData.byteOffset || 0,
          (pcmData.byteLength || pcmData.length) / 2 | 0
        );
      } else {
        throw new TypeError('encode() expects an Int16Array');
      }
    }
    const out = this._encoder.encode(pcmData);
    // Always hand back an ArrayBuffer slice the caller can transfer freely.
    if (out instanceof Uint8Array) {
      // .slice() ensures the returned ArrayBuffer is a standalone copy.
      return out.slice().buffer;
    }
    return out;
  }

  destroy() {
    if (this._encoder) {
      try { this._encoder.destroy(); } catch (_) {}
      this._encoder = null;
    }
  }
}

module.exports = OpusEncoder;
module.exports.OpusEncoder = OpusEncoder;
