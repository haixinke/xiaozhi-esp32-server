/**
 * opus-decoder.js
 * --------------------------------------------------------------------------
 * Public Opus decoder facade. Mirrors the encoder's lifecycle:
 *
 *   const dec = new OpusDecoder({ sampleRate: 24000, channels: 1 });
 *   await dec.ready();
 *   const pcm = dec.decode(opusFrame);   // -> Int16Array
 *   dec.destroy();
 *
 * The decoder is forgiving: passing in an ArrayBuffer, a Uint8Array, or any
 * typed-array view works. On the stub runtime, frames lacking the magic
 * header decode to silence so playback pipelines keep ticking.
 * --------------------------------------------------------------------------
 */

const { getOpusRuntime } = require('./opus-runtime');

class OpusDecoder {
  /**
   * @param {Object} options
   * @param {number} [options.sampleRate=24000]
   * @param {number} [options.channels=1]
   * @param {number} [options.frameDuration=60] expected frame length in ms;
   *                used only for stub-mode silence frames.
   */
  constructor(options) {
    const opts = options || {};
    this.sampleRate = opts.sampleRate || 24000;
    this.channels = opts.channels || 1;
    this.frameDuration = opts.frameDuration || 60;
    this.frameSize = Math.round(this.sampleRate * this.frameDuration / 1000);

    this._decoder = null;
    this._mode = null;

    this._readyPromise = getOpusRuntime().then((rt) => {
      this._decoder = rt.createDecoder({
        sampleRate: this.sampleRate,
        channels: this.channels,
        frameSize: this.frameSize,
      });
      this._mode = rt.mode;
      return this;
    });
  }

  ready() {
    return this._readyPromise;
  }

  get mode() {
    return this._mode;
  }

  /**
   * Decode a single Opus frame.
   * @param {ArrayBuffer|Uint8Array} opusFrame
   * @returns {Int16Array} PCM samples (interleaved if multi-channel).
   */
  decode(opusFrame) {
    if (!this._decoder) {
      throw new Error('OpusDecoder not ready; await decoder.ready() first');
    }
    let bytes;
    if (opusFrame instanceof Uint8Array) {
      bytes = opusFrame;
    } else if (opusFrame instanceof ArrayBuffer) {
      bytes = new Uint8Array(opusFrame);
    } else if (opusFrame && opusFrame.buffer instanceof ArrayBuffer) {
      bytes = new Uint8Array(opusFrame.buffer, opusFrame.byteOffset || 0, opusFrame.byteLength);
    } else {
      throw new TypeError('decode() expects ArrayBuffer or typed array');
    }
    return this._decoder.decode(bytes);
  }

  destroy() {
    if (this._decoder) {
      try { this._decoder.destroy(); } catch (_) {}
      this._decoder = null;
    }
  }
}

module.exports = OpusDecoder;
module.exports.OpusDecoder = OpusDecoder;
