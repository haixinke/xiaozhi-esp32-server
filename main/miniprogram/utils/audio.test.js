const assert = require('assert');
const Module = require('module');

let createdContexts = [];
let recorderManagerStub = null;

function createMockAudioContext() {
  const sources = new Set();
  const ctx = {
    state: 'running',
    currentTime: 0,
    closed: false,
    createBuffer(_channels, length, sampleRate) {
      return {
        duration: length / sampleRate,
        getChannelData: () => new Float32Array(length),
      };
    },
    createBufferSource() {
      const source = {
        buffer: null,
        onended: null,
        started: false,
        stopped: false,
        disconnected: false,
        connectedNode: null,
        start() { this.started = true; },
        stop() { this.stopped = true; },
        connect(node) { this.connectedNode = node; },
        disconnect() { this.disconnected = true; },
      };
      sources.add(source);
      return source;
    },
    getSources() { return sources; },
    destination: {},
    close() { this.closed = true; },
  };
  createdContexts.push(ctx);
  return ctx;
}

global.wx = {
  createWebAudioContext: createMockAudioContext,
  getRecorderManager() {
    return recorderManagerStub;
  },
};

recorderManagerStub = {
  start() {},
  stop() {},
  onStart() {},
  onStop() {},
  onError() {},
  onFrameRecorded() {},
};

const originalLoad = Module._load;
Module._load = function(request, _parent, _isMain) {
  if (request === '../libs/opus/opus-encoder' || request.endsWith('/opus-encoder')) {
    return class MockOpusEncoder {
      constructor() {}
      ready() { return Promise.resolve(this); }
      get mode() { return global.__MOCK_OPUS_MODE || 'wasm'; }
      encode() { return new ArrayBuffer(1); }
      destroy() {}
    };
  }
  if (request === '../libs/opus/opus-decoder' || request.endsWith('/opus-decoder')) {
    return class MockOpusDecoder {
      constructor() {}
      ready() { return Promise.resolve(this); }
      decode() { return new Int16Array(10); }
      destroy() {}
    };
  }
  return originalLoad.apply(this, arguments);
};

const AudioManager = require('./audio');

// 可控的 RecorderManager 桩：捕获回调，便于在测试中主动触发 onStart/onError。
function makeRecorderStub() {
  const stub = {
    startCalls: 0,
    stopCalls: 0,
    _onStart: null,
    _onStop: null,
    _onError: null,
    _onFrameRecorded: null,
    start() { this.startCalls++; },
    stop() { this.stopCalls++; },
    onStart(cb) { this._onStart = cb; },
    onStop(cb) { this._onStop = cb; },
    onError(cb) { this._onError = cb; },
    onFrameRecorded(cb) { this._onFrameRecorded = cb; },
  };
  return stub;
}

async function runTests() {
  const mgr = new AudioManager({});
  await mgr.ready();

  // 1. Playback creates the first WebAudioContext.
  mgr.appendOpusFrame(new ArrayBuffer(1));
  assert.strictEqual(createdContexts.length, 1, 'first context should be created');
  assert.strictEqual(createdContexts[0].closed, false, 'first context should be open');

  // 2. resetAudioContext immediately creates a fresh context and closes the old one.
  mgr.resetAudioContext();
  assert.strictEqual(createdContexts[0].closed, true, 'old context should be closed');
  assert.strictEqual(createdContexts.length, 2, 'fresh context should be created immediately');
  assert.strictEqual(createdContexts[1].closed, false, 'fresh context should be open');

  // 3. Active sources from the old context are stopped and disconnected.
  const firstSource = createdContexts[0].getSources().values().next().value;
  assert.ok(firstSource, 'an active source should exist on the old context');
  assert.strictEqual(firstSource.stopped, true, 'old source should be stopped');
  assert.strictEqual(firstSource.disconnected, true, 'old source should be disconnected');

  // 4. resetAudioContext clears any queued frames to avoid stale playback.
  // The mock drains frames immediately, so push directly to verify clearing.
  mgr._playQueue.push(new Int16Array(10));
  mgr.resetAudioContext();
  assert.strictEqual(mgr._playQueue.length, 0, 'play queue should be cleared');

  // 5. Playback after reset schedules on the latest context.
  mgr.appendOpusFrame(new ArrayBuffer(1));
  assert.strictEqual(createdContexts.length, 3, 'no additional context should be created');
  assert.strictEqual(createdContexts[2].getSources().size, 1, 'latest context should have one source');

  // 6. Multiple consecutive resets are safe and do not leak contexts.
  mgr.resetAudioContext();
  mgr.resetAudioContext();
  assert.strictEqual(createdContexts.length, 5, 'each reset creates a fresh context');
  assert.strictEqual(createdContexts[3].closed, true, 'first reset context should be closed');
  assert.strictEqual(createdContexts[4].closed, false, 'latest context should be open');

  // 7. destroy closes the active context.
  mgr.destroy();
  assert.strictEqual(createdContexts[4].closed, true, 'active context should be closed on destroy');

  console.log('audio.test.js: playback suite ALL PASS');
}

// ---------------------------------------------------------------------------
// Codec-mode + recording self-healing suite (M1/M2)
// ---------------------------------------------------------------------------

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function runCodecAndRetryTests() {
  // --- M1.a: stub codec mode emits a 'codec'-scoped error and drops frames ---
  global.__MOCK_OPUS_MODE = 'stub';
  let codecError = null;
  const mgrStub = new AudioManager({
    onError: (err, scope) => { if (scope === 'codec') codecError = err; },
    onAudioFrame: () => { throw new Error('onAudioFrame must NOT be called in stub mode'); },
  });
  await mgrStub.ready();
  assert.strictEqual(mgrStub._codecMode, 'stub', 'codecMode should be stub');
  assert.ok(codecError, 'codec error should be emitted when mode is stub');

  // _encodeAndEmit must drop frames in stub mode (no call to onAudioFrame).
  assert.doesNotThrow(() => mgrStub._encodeAndEmit(new Int16Array(1440)));
  mgrStub.destroy();

  // --- M1.a cont: wasm codec mode does NOT emit a codec error ---
  global.__MOCK_OPUS_MODE = 'wasm';
  let wasmCodecError = null;
  const mgrWasm = new AudioManager({
    onError: (_err, scope) => { if (scope === 'codec') wasmCodecError = true; },
    onAudioFrame: () => {},
  });
  await mgrWasm.ready();
  assert.strictEqual(mgrWasm._codecMode, 'wasm', 'codecMode should be wasm');
  assert.strictEqual(wasmCodecError, null, 'codec error must NOT fire for wasm mode');
  mgrWasm.destroy();

  // --- M1.b + M2: file error triggers exactly one retry, then surfaces error ---
  global.__MOCK_OPUS_MODE = 'wasm';
  const recorder = makeRecorderStub();
  global.wx.getRecorderManager = () => recorder;

  const errors = [];
  const recordStarts = [];
  const mgrRetry = new AudioManager({
    onError: (err, scope) => errors.push({ msg: err.message, scope }),
    onRecordStart: () => recordStarts.push(true),
  });
  await mgrRetry.ready();
  mgrRetry.startRecord();            // first start attempt
  assert.strictEqual(recorder.startCalls, 1, 'initial start should call recorder.start once');

  // First file error -> should schedule a retry, NOT surface to onError yet.
  recorder._onError({ errMsg: 'record: file error' });
  assert.strictEqual(errors.length, 0, 'first file error should be swallowed for retry');
  assert.strictEqual(mgrRetry._recordRetried, true, 'retry flag should be set after first file error');
  assert.ok(mgrRetry._recordRetryTimer, 'retry timer handle should be tracked');

  await wait(320);                   // retry timer fires (300ms) -> startRecord() again
  assert.strictEqual(recorder.startCalls, 2, 'retry should call recorder.start a second time');
  assert.strictEqual(mgrRetry._recordRetryTimer, null, 'timer handle should clear after firing');

  // Second file error -> retry already used, must surface to onError.
  recorder._onError({ errMsg: 'record: file error' });
  assert.strictEqual(errors.length, 1, 'second file error must surface');
  assert.strictEqual(errors[0].scope, 'record', 'surfaced error should be scoped record');
  mgrRetry.destroy();

  // --- M2: destroy() during retry window clears the timer and does not start ---
  const recorder2 = makeRecorderStub();
  global.wx.getRecorderManager = () => recorder2;
  const mgrDestroy = new AudioManager({ onError: () => {} });
  await mgrDestroy.ready();
  mgrDestroy.startRecord();
  recorder2._onError({ errMsg: 'record: file error' });
  assert.ok(mgrDestroy._recordRetryTimer, 'retry timer should be pending');
  mgrDestroy.destroy();              // destroy mid-window
  assert.strictEqual(mgrDestroy._recordRetryTimer, null, 'destroy must clear the retry timer');
  const startCountBefore = recorder2.startCalls;
  await wait(320);                   // would-have-fired window
  assert.strictEqual(recorder2.startCalls, startCountBefore, 'no startRecord after destroy');
  assert.strictEqual(mgrDestroy._destroyed, true, 'manager stays destroyed');

  // --- M2: stopRecord() during retry window clears the timer ---
  const recorder3 = makeRecorderStub();
  global.wx.getRecorderManager = () => recorder3;
  const mgrStop = new AudioManager({ onError: () => {} });
  await mgrStop.ready();
  mgrStop.startRecord();
  recorder3._onError({ errMsg: 'record: file error' });
  assert.ok(mgrStop._recordRetryTimer, 'retry timer should be pending before stopRecord');
  mgrStop.stopRecord();
  assert.strictEqual(mgrStop._recordRetryTimer, null, 'stopRecord must clear the retry timer');
  const before = recorder3.startCalls;
  await wait(320);
  assert.strictEqual(recorder3.startCalls, before, 'no startRecord after stopRecord cleared timer');
  mgrStop.destroy();

  // --- M1.c: successful onStart resets _recordRetried so a later file error can retry again ---
  const recorder4 = makeRecorderStub();
  global.wx.getRecorderManager = () => recorder4;
  const mgrReset = new AudioManager({ onError: () => {} });
  await mgrReset.ready();
  mgrReset.startRecord();
  recorder4._onError({ errMsg: 'record: file error' });  // retry scheduled
  assert.strictEqual(mgrReset._recordRetried, true, 'flag set after first file error');
  await wait(320);                                         // retry fires startRecord
  recorder4._onStart();                                    // retried start succeeds
  assert.strictEqual(mgrReset._recordRetried, false, 'onStart should reset retry flag');
  // Now a new file error should be retried, not surfaced.
  let surfaced = false;
  mgrReset.options.onError = () => { surfaced = true; };
  recorder4._onError({ errMsg: 'record: file error' });
  assert.strictEqual(surfaced, false, 'after onStart reset, file error should retry not surface');
  mgrReset.destroy();

  global.__MOCK_OPUS_MODE = undefined;
  console.log('audio.test.js: codec+retry suite ALL PASS');
}

// Run suites sequentially: both are async and share `global.__MOCK_OPUS_MODE`,
// so interleaving would let the stub flag leak into the playback suite.
const runTestsPromise = runTests();
runTestsPromise
  .catch((err) => {
    console.error('audio.test.js: playback suite FAILED', err);
    process.exit(1);
  })
  .then(runCodecAndRetryTests)
  .catch((err) => {
    console.error('audio.test.js: codec+retry suite FAILED', err);
    process.exit(1);
  });
