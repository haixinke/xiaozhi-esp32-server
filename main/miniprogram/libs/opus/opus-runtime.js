/**
 * opus-runtime.js
 * --------------------------------------------------------------------------
 * Shared loader for the Opus codec runtime inside a WeChat Mini Program.
 *
 * Strategy (priority order):
 *   1. Try to load a libopus WebAssembly module via WXWebAssembly. The host
 *      app should ship `libs/opus/libopus.wasm` (compiled from the upstream
 *      libopus / opus-recorder Emscripten target). When available, we get
 *      real, server-compatible Opus frames.
 *   2. Fall back to a JS-only stub runtime that keeps the encoder/decoder
 *      API contract intact. The stub does NOT produce true Opus packets;
 *      it tags the PCM payload with a 4-byte header so callers can detect
 *      the fallback mode and so server-side bridges can ignore the frames.
 *
 * The mini program sandbox does not expose `window`, `document`, Workers,
 * or `WebAssembly.compileStreaming`, so we deliberately use only:
 *   - `wx.getFileSystemManager()` for reading the bundled wasm file
 *   - Global `WXWebAssembly` (>= base library 2.13.0) for instantiation
 *   - Plain `ArrayBuffer` / `Int16Array` math
 *
 * NOTE FOR FOLLOW-UP: drop a real `libs/opus/libopus.wasm` (and the matching
 * exports below) into the package to switch from stub to real codec. No
 * caller code needs to change.
 * --------------------------------------------------------------------------
 */

// Magic bytes used by the JS stub so encoder/decoder can round-trip PCM
// without pretending to be a real Opus frame on the wire. 'XZPC' = XiaoZhi PCM.
const STUB_MAGIC = 0x58_5a_50_43; // big-endian view: 0x58 0x5A 0x50 0x43

// Resolved runtime is cached; the loader is idempotent.
let runtimePromise = null;
let cachedRuntime = null;

/**
 * Public entry point. Returns a resolved runtime descriptor:
 *   {
 *     mode: 'wasm' | 'stub',
 *     createEncoder({ sampleRate, channels, frameSize, application, bitrate }),
 *     createDecoder({ sampleRate, channels, frameSize })
 *   }
 *
 * Encoder objects expose:  encode(Int16Array) -> Uint8Array, destroy()
 * Decoder objects expose:  decode(Uint8Array) -> Int16Array, destroy()
 */
function getOpusRuntime() {
  if (cachedRuntime) return Promise.resolve(cachedRuntime);
  if (runtimePromise) return runtimePromise;

  runtimePromise = loadWasmRuntime()
    .catch((err) => {
      console.warn('[opus-runtime] WASM unavailable, using JS stub:', err && err.message);
      return buildStubRuntime();
    })
    .then((rt) => {
      cachedRuntime = rt;
      return rt;
    });

  return runtimePromise;
}

/**
 * Force-refresh the runtime (mainly for tests / hot-reload during dev).
 */
function resetOpusRuntime() {
  cachedRuntime = null;
  runtimePromise = null;
}

// ---------------------------------------------------------------------------
// WASM path
// ---------------------------------------------------------------------------

function loadWasmRuntime() {
  return new Promise((resolve, reject) => {
    // WXWebAssembly is the mini-program-only WASM API. If it's missing we
    // can't load a libopus build, so we bail to the stub immediately.
    if (typeof WXWebAssembly === 'undefined' || !WXWebAssembly.instantiate) {
      reject(new Error('WXWebAssembly not available'));
      return;
    }

    // The wasm binary is expected to live alongside this file. WXWebAssembly
    // accepts a package-relative path (string), so we pass that directly.
    const wasmPath = '/libs/opus/libopus.wasm';

    // Best-effort existence check via the file system manager. If the file
    // is not bundled, fail fast and let the caller fall back.
    let fs = null;
    try {
      // eslint-disable-next-line no-undef
      fs = wx.getFileSystemManager();
    } catch (_) {
      // wx may be missing in non-mini-program runtimes (e.g. unit tests).
    }
    if (fs && typeof fs.accessSync === 'function') {
      try {
        fs.accessSync(wasmPath);
      } catch (e) {
        reject(new Error('libopus.wasm not bundled'));
        return;
      }
    }

    const importObject = createWasmImports();

    WXWebAssembly.instantiate(wasmPath, importObject)
      .then((result) => {
        const instance = result && (result.instance || result);
        const rawExports = instance && instance.exports;
        if (!rawExports) {
          reject(new Error('libopus exports missing'));
          return;
        }
        // Normalize export names: Emscripten 5.x exports without underscore
        // prefix (e.g. 'opus_encode'), older versions use '_opus_encode'.
        // We normalize to always use underscore-prefixed names internally.
        const exports = normalizeExports(rawExports);
        if (typeof exports._opus_encoder_get_size !== 'function') {
          reject(new Error('libopus exports missing required functions'));
          return;
        }
        resolve(buildWasmRuntime(exports, importObject.env));
      })
      .catch((err) => reject(err));
  });
}

/**
 * Normalize WASM exports to always use underscore-prefixed names.
 * Emscripten 5.x exports symbols without the leading underscore
 * (e.g. 'opus_encode' instead of '_opus_encode'), while older
 * versions include it. This function ensures consistent access.
 */
function normalizeExports(rawExports) {
  const normalized = {};
  const requiredNames = [
    'opus_encoder_get_size', 'opus_encoder_init', 'opus_encoder_ctl', 'opus_encode',
    'opus_decoder_get_size', 'opus_decoder_init', 'opus_decode',
    'malloc', 'free'
  ];
  for (const name of requiredNames) {
    // Try underscore-prefixed first (older Emscripten), then bare name (5.x+)
    const fn = rawExports['_' + name] || rawExports[name];
    if (fn) normalized['_' + name] = fn;
  }
  // Copy any other exports as-is
  for (const key of Object.keys(rawExports)) {
    if (!(key in normalized) && !('_' + key in normalized)) {
      normalized[key] = rawExports[key];
    }
  }
  return normalized;
}

/**
 * Construct a minimal Emscripten-style import object. The libopus build is
 * expected to be compiled with `-s STANDALONE_WASM=0 -s ENVIRONMENT=web`
 * and to import its memory from `env.memory`. Tweak as needed for the
 * specific wasm artifact you ship.
 */
function createWasmImports() {
  const memory = new WXWebAssembly.Memory({ initial: 256, maximum: 256 });
  const env = {
    memory,
    abort: (msg) => { throw new Error('opus wasm abort: ' + msg); },
    emscripten_notify_memory_growth: () => {},
  };
  return { env, wasi_snapshot_preview1: { proc_exit: () => {} } };
}

function buildWasmRuntime(exp, env) {
  const memory = env.memory;

  // Helpers that mirror Emscripten's HEAP views, refreshed lazily because
  // memory.grow() can invalidate the underlying ArrayBuffer.
  const view = {
    HEAPU8: () => new Uint8Array(memory.buffer),
    HEAP16: () => new Int16Array(memory.buffer),
  };

  function malloc(bytes) {
    const ptr = exp._malloc(bytes);
    if (!ptr) throw new Error('opus malloc failed');
    return ptr;
  }
  function free(ptr) {
    if (ptr) exp._free(ptr);
  }

  return {
    mode: 'wasm',
    createEncoder(opts) {
      const channels = opts.channels || 1;
      const sampleRate = opts.sampleRate || 24000;
      const frameSize = opts.frameSize || (sampleRate * 0.06) | 0;
      const application = opts.application || 2048; // OPUS_APPLICATION_VOIP
      const bitrate = opts.bitrate || 24000;
      const maxPacketSize = 4000;

      const encSize = exp._opus_encoder_get_size(channels);
      const encPtr = malloc(encSize);
      const initErr = exp._opus_encoder_init(encPtr, sampleRate, channels, application);
      if (initErr < 0) {
        free(encPtr);
        throw new Error('opus_encoder_init failed: ' + initErr);
      }
      // OPUS_SET_BITRATE = 4002, OPUS_SET_COMPLEXITY = 4010, OPUS_SET_DTX = 4016
      exp._opus_encoder_ctl(encPtr, 4002, bitrate);
      exp._opus_encoder_ctl(encPtr, 4010, 5);
      exp._opus_encoder_ctl(encPtr, 4016, 1);

      let destroyed = false;
      return {
        mode: 'wasm',
        sampleRate, channels, frameSize,
        encode(pcm) {
          if (destroyed) throw new Error('encoder destroyed');
          if (!pcm || pcm.length !== frameSize) {
            throw new Error('encoder expects ' + frameSize + ' samples, got ' + (pcm ? pcm.length : 0));
          }
          const pcmPtr = malloc(pcm.length * 2);
          const outPtr = malloc(maxPacketSize);
          try {
            view.HEAP16().set(pcm, pcmPtr >> 1);
            const len = exp._opus_encode(encPtr, pcmPtr, frameSize, outPtr, maxPacketSize);
            if (len < 0) throw new Error('opus_encode failed: ' + len);
            return view.HEAPU8().slice(outPtr, outPtr + len);
          } finally {
            free(pcmPtr);
            free(outPtr);
          }
        },
        destroy() {
          if (destroyed) return;
          destroyed = true;
          free(encPtr);
        },
      };
    },

    createDecoder(opts) {
      const channels = opts.channels || 1;
      const sampleRate = opts.sampleRate || 24000;
      const frameSize = opts.frameSize || (sampleRate * 0.06) | 0;
      // We allocate enough room for the largest realistic frame (120ms).
      const maxFrameSize = (sampleRate * 0.12) | 0;

      const decSize = exp._opus_decoder_get_size(channels);
      const decPtr = malloc(decSize);
      const initErr = exp._opus_decoder_init(decPtr, sampleRate, channels);
      if (initErr < 0) {
        free(decPtr);
        throw new Error('opus_decoder_init failed: ' + initErr);
      }

      let destroyed = false;
      return {
        mode: 'wasm',
        sampleRate, channels, frameSize,
        decode(opus) {
          if (destroyed) throw new Error('decoder destroyed');
          const bytes = opus instanceof Uint8Array ? opus : new Uint8Array(opus);
          const inPtr = malloc(bytes.length);
          const outPtr = malloc(maxFrameSize * channels * 2);
          try {
            view.HEAPU8().set(bytes, inPtr);
            const samples = exp._opus_decode(
              decPtr, inPtr, bytes.length, outPtr, maxFrameSize, 0
            );
            if (samples < 0) throw new Error('opus_decode failed: ' + samples);
            return view.HEAP16().slice(outPtr >> 1, (outPtr >> 1) + samples * channels);
          } finally {
            free(inPtr);
            free(outPtr);
          }
        },
        destroy() {
          if (destroyed) return;
          destroyed = true;
          free(decPtr);
        },
      };
    },
  };
}

// ---------------------------------------------------------------------------
// JS stub path (interface-compatible fallback)
// ---------------------------------------------------------------------------
//
// The stub wraps PCM samples in a tagged container so encode/decode round-trips
// inside the mini program (e.g. monitoring ones own mic locally). It is NOT a
// real Opus codec: a stub frame fed into a server expecting Opus will be
// rejected. Replace this with a real WASM build before going to production.
//
// Frame layout produced by the stub encoder:
//   bytes 0..3   : magic 'XZPC'
//   bytes 4..5   : little-endian uint16 sample count
//   bytes 6..N-1 : little-endian int16 PCM samples
// ---------------------------------------------------------------------------

function buildStubRuntime() {
  return {
    mode: 'stub',
    createEncoder(opts) {
      const channels = opts.channels || 1;
      const sampleRate = opts.sampleRate || 24000;
      const frameSize = opts.frameSize || (sampleRate * 0.06) | 0;
      let destroyed = false;
      return {
        mode: 'stub',
        sampleRate, channels, frameSize,
        encode(pcm) {
          if (destroyed) throw new Error('encoder destroyed');
          if (!pcm || pcm.length !== frameSize) {
            throw new Error('stub encoder expects ' + frameSize + ' samples, got ' + (pcm ? pcm.length : 0));
          }
          const out = new Uint8Array(6 + pcm.length * 2);
          // Magic header
          out[0] = 0x58; out[1] = 0x5A; out[2] = 0x50; out[3] = 0x43;
          out[4] = pcm.length & 0xff;
          out[5] = (pcm.length >> 8) & 0xff;
          // PCM payload (little-endian int16)
          const dst = new DataView(out.buffer);
          for (let i = 0; i < pcm.length; i++) {
            dst.setInt16(6 + i * 2, pcm[i], true);
          }
          return out;
        },
        destroy() { destroyed = true; },
      };
    },
    createDecoder(opts) {
      const channels = opts.channels || 1;
      const sampleRate = opts.sampleRate || 24000;
      const frameSize = opts.frameSize || (sampleRate * 0.06) | 0;
      let destroyed = false;
      return {
        mode: 'stub',
        sampleRate, channels, frameSize,
        decode(opus) {
          if (destroyed) throw new Error('decoder destroyed');
          const bytes = opus instanceof Uint8Array ? opus : new Uint8Array(opus);
          // If frame doesn't carry the stub magic, return silence so the
          // playback pipeline keeps running instead of throwing.
          if (bytes.length < 6 ||
              bytes[0] !== 0x58 || bytes[1] !== 0x5A ||
              bytes[2] !== 0x50 || bytes[3] !== 0x43) {
            return new Int16Array(frameSize);
          }
          const sampleCount = bytes[4] | (bytes[5] << 8);
          const view2 = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
          const out = new Int16Array(sampleCount);
          for (let i = 0; i < sampleCount; i++) {
            out[i] = view2.getInt16(6 + i * 2, true);
          }
          return out;
        },
        destroy() { destroyed = true; },
      };
    },
  };
}

module.exports = {
  getOpusRuntime,
  resetOpusRuntime,
  STUB_MAGIC,
};
