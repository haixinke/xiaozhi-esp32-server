const assert = require('assert');
const Module = require('module');

let pageConfig = null;
let setInnerAudioOptionCalls = [];
let toastMessages = [];
let managerState = { isSpeakerOn: true };
let toggleCount = 0;
let resetContextCount = 0;
let setInnerAudioOptionShouldFail = false;

const originalWx = {
  setInnerAudioOption(options) {
    setInnerAudioOptionCalls.push(options);
    if (setInnerAudioOptionShouldFail) {
      if (options.fail) {
        options.fail({ errMsg: 'setInnerAudioOption:fail' });
      }
      if (options.complete) {
        options.complete();
      }
      return;
    }
    if (options.success) {
      options.success();
    }
    if (options.complete) {
      options.complete();
    }
  },
  showToast(options) {
    toastMessages.push(options);
  },
  navigateBack() {},
};

global.Page = function(config) {
  pageConfig = config;
};

global.getApp = function() {
  return {
    globalData: {
      agentName: '女友',
      companionAvatar: '',
      wsUrl: 'wss://test',
      virtualMAC: '00:00:00:00:00:00',
      wsToken: 'token',
    },
  };
};

global.wx = { ...originalWx };

const originalLoad = Module._load;
Module._load = function(request, _parent, _isMain) {
  if (request === '../../utils/voice-call-manager') {
    return function() {
      return {
        getState() { return managerState; },
        toggleSpeaker() {
          managerState.isSpeakerOn = !managerState.isSpeakerOn;
          toggleCount += 1;
        },
        onStateChange() {},
        offStateChange() {},
        setOnRecordRestart() {},
        setMedia() {},
        hangup() {},
      };
    };
  }
  if (request === '../../utils/audio') {
    return class MockAudioManager {
      constructor() {}
      ready() { return Promise.resolve(this); }
      resetAudioContext() { resetContextCount += 1; }
      destroy() {}
    };
  }
  if (request === '../../utils/websocket') {
    return class MockWebSocketManager {
      constructor() {}
      connect() {}
      onStateChange() {}
      offStateChange() {}
      isConnected() { return false; }
      destroy() {}
    };
  }
  if (request === '../../utils/logger') {
    return { log() {}, warn() {}, error() {} };
  }
  if (request === '../../utils/theme') {
    return {
      getTheme() { return false; },
      applyTheme() {},
    };
  }
  return originalLoad.apply(this, arguments);
};

require('./voice-call');

(function runTests() {
  assert.ok(pageConfig, 'Page should be registered');
  const page = Object.create(pageConfig);
  page.setData = function(data) {
    Object.assign(this.data || (this.data = {}), data);
  };

  // Wire up the manager and audio manager directly to test onToggleSpeaker.
  page._mgr = {
    getState() { return managerState; },
    toggleSpeaker() {
      managerState.isSpeakerOn = !managerState.isSpeakerOn;
      toggleCount += 1;
    },
  };
  page.audioManager = { resetAudioContext() { resetContextCount += 1; } };

  // 1. First toggle: speaker -> earpiece.
  setInnerAudioOptionCalls.length = 0;
  toastMessages.length = 0;
  toggleCount = 0;
  resetContextCount = 0;
  setInnerAudioOptionShouldFail = false;
  managerState.isSpeakerOn = true;

  page.onToggleSpeaker();
  assert.strictEqual(setInnerAudioOptionCalls.length, 1, 'setInnerAudioOption should be called once');
  assert.strictEqual(setInnerAudioOptionCalls[0].speakerOn, false, 'should request earpiece');
  assert.strictEqual(toggleCount, 1, 'toggleSpeaker should be called on success');
  assert.strictEqual(managerState.isSpeakerOn, false, 'state should be earpiece');
  assert.strictEqual(resetContextCount, 1, 'resetAudioContext should be called on success');
  assert.strictEqual(page._speakerTogglePending, false, 'pending flag should be cleared');

  // 2. Second toggle: earpiece -> speaker.
  page.onToggleSpeaker();
  assert.strictEqual(setInnerAudioOptionCalls.length, 2, 'setInnerAudioOption should be called again');
  assert.strictEqual(setInnerAudioOptionCalls[1].speakerOn, true, 'should request speaker');
  assert.strictEqual(toggleCount, 2, 'toggleSpeaker should be called again');
  assert.strictEqual(managerState.isSpeakerOn, true, 'state should be speaker');
  assert.strictEqual(resetContextCount, 2, 'resetAudioContext should be called again');

  // 3. Pending toggle is ignored.
  page._speakerTogglePending = true;
  page.onToggleSpeaker();
  assert.strictEqual(setInnerAudioOptionCalls.length, 2, 'no new setInnerAudioOption call while pending');
  page._speakerTogglePending = false;

  // 4. Failure path: state does not change, toast is shown, pending flag cleared.
  setInnerAudioOptionShouldFail = true;
  managerState.isSpeakerOn = true;
  toggleCount = 0;
  resetContextCount = 0;

  page.onToggleSpeaker();
  assert.strictEqual(setInnerAudioOptionCalls.length, 3, 'setInnerAudioOption should be called on failure test');
  assert.strictEqual(toggleCount, 0, 'toggleSpeaker should not be called on failure');
  assert.strictEqual(managerState.isSpeakerOn, true, 'state should remain unchanged on failure');
  assert.strictEqual(resetContextCount, 0, 'resetAudioContext should not be called on failure');
  assert.strictEqual(toastMessages.length, 1, 'toast should be shown on failure');
  assert.strictEqual(page._speakerTogglePending, false, 'pending flag should be cleared on failure');

  // 5. Fallback path when wx.setInnerAudioOption is unavailable.
  setInnerAudioOptionShouldFail = false;
  const savedSetInnerAudioOption = global.wx.setInnerAudioOption;
  delete global.wx.setInnerAudioOption;
  managerState.isSpeakerOn = true;
  toggleCount = 0;
  resetContextCount = 0;

  page.onToggleSpeaker();
  assert.strictEqual(toggleCount, 1, 'toggleSpeaker should be called in fallback');
  assert.strictEqual(managerState.isSpeakerOn, false, 'state should toggle in fallback');
  assert.strictEqual(resetContextCount, 1, 'resetAudioContext should be called in fallback');
  assert.strictEqual(page._speakerTogglePending, false, 'pending flag should be cleared in fallback');

  global.wx.setInnerAudioOption = savedSetInnerAudioOption;

  console.log('voice-call.test.js: ALL PASS');
})();
