const assert = require('assert');
const Module = require('module');

let pageConfig = null;
let wsSendCalls = [];
let audioStartCalls = 0;
let audioStopCalls = 0;

global.Page = function (config) {
  pageConfig = config;
};

global.getApp = function () {
  return { globalData: {} };
};

global.wx = {
  showToast() {},
  showModal() {},
  setStorageSync() {},
  getStorageSync() { return ''; },
  getSystemInfoSync() {
    return { windowWidth: 375, windowHeight: 667, statusBarHeight: 20 };
  },
};

const originalLoad = Module._load;
Module._load = function (request, _parent, _isMain) {
  if (request === '../../utils/audio') {
    return class MockAudioManager {
      constructor() {}
      ready() { return Promise.resolve(this); }
      startRecord() { audioStartCalls += 1; }
      stopRecord() { audioStopCalls += 1; }
      stopPlayback() {}
      appendOpusFrame() {}
      destroy() {}
    };
  }
  if (request === '../../utils/websocket') {
    return class MockWebSocketManager {
      constructor() {}
      connect() {}
      onStateChange() {}
      offStateChange() {}
      sendAudioFrame() {}
      sendListenStart() { wsSendCalls.push('listenStart'); }
      sendListenStop() { wsSendCalls.push('listenStop'); }
      sendAbort() { wsSendCalls.push('abort'); }
      sendText() {}
      disconnect() {}
    };
  }
  if (request === '../../utils/logger') {
    return { log() {}, warn() {}, error() {}, info() {} };
  }
  if (request === '../../utils/request') {
    return {
      get() { return Promise.resolve({ code: 0, data: {} }); },
      del() { return Promise.resolve({ code: 0 }); },
    };
  }
  if (request === '../../utils/theme') {
    return { getTheme() { return false; }, applyTheme() {} };
  }
  return originalLoad.apply(this, arguments);
};

require('./index');

function makePage() {
  const page = Object.create(pageConfig);
  page.data = Object.assign({}, pageConfig.data);
  page.setData = function (data) {
    Object.assign(this.data, data);
  };
  // Inject mock managers so we can observe send calls.
  page.wsManager = {
    sendListenStart() { wsSendCalls.push('listenStart'); },
    sendListenStop() { wsSendCalls.push('listenStop'); },
    sendAbort() { wsSendCalls.push('abort'); },
  };
  page.audioManager = {
    startRecord() { audioStartCalls += 1; },
    stopRecord() { audioStopCalls += 1; },
  };
  // Pre-conditions for onVoiceTouchStart guards.
  page.data.connectionState = 'connected';
  page.data.chatState = 'idle';
  page.data.hasVoiceInput = true;
  page.data.chatRemaining = 10;
  // Stub side-effectful timer used inside the touch start path.
  page._resetIdleTimer = function () {};
  return page;
}

(function runTests() {
  assert.ok(pageConfig, 'Page should be registered');

  // Regression: onVoiceTouchEnd normal branch must reset `recording` to false.
  // Previously only the cancelled branch reset it, leaving recording=true so a
  // later touchcancel would pass its guard and fire sendAbort, aborting the
  // ASR that listen stop had just initiated (server logs showed
  // listen-stop immediately followed by abort -> no AI response).
  const page = makePage();
  wsSendCalls = [];
  audioStartCalls = 0;
  audioStopCalls = 0;

  // Press: starts recording and sends listen start.
  page.onVoiceTouchStart({ touches: [{ clientY: 100 }] });
  assert.strictEqual(page.data.recording, true, 'recording should be true after touch start');
  assert.strictEqual(audioStartCalls, 1, 'audioManager.startRecord should be called once');
  assert.deepStrictEqual(wsSendCalls, ['listenStart'], 'only listen start should be sent on press');

  // Release without cancelling: sends listen stop, must reset recording.
  page.onVoiceTouchEnd();
  assert.strictEqual(page.data.recording, false, 'recording must be reset to false after normal touch end');
  assert.strictEqual(page.data.recordCancelled, false, 'recordCancelled must be reset after normal touch end');
  assert.deepStrictEqual(wsSendCalls, ['listenStart', 'listenStop'], 'listen stop should be sent, no abort on normal release');

  // A later touchcancel must be a no-op (no abort) because recording is false.
  page.onVoiceTouchCancel();
  assert.deepStrictEqual(wsSendCalls, ['listenStart', 'listenStop'], 'no abort should fire after recording has ended');

  console.log('index.test.js: ALL PASS');
})();
