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

(async function runTests() {
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

  console.log('audio.test.js: ALL PASS');
})().catch((err) => {
  console.error('audio.test.js: FAILED', err);
  process.exit(1);
});
