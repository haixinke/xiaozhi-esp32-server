const assert = require('assert');
const Module = require('module');

let pageConfig = null;
let setInnerAudioOptionCalls = [];
let navigateBackCount = 0;
let hangupCount = 0;
let mutedState = false;
let toggleMuteCount = 0;

const originalWx = {
  setInnerAudioOption(options) {
    setInnerAudioOptionCalls.push(options);
    if (options.success) options.success();
  },
  showToast() {},
  navigateBack() {
    navigateBackCount += 1;
  },
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
        getState() {
          return { state: 'calling', durationSeconds: 0, isMuted: mutedState };
        },
        onStateChange() {},
        offStateChange() {},
        setOnRecordRestart() {},
        setMedia() {},
        hangup() { hangupCount += 1; },
        toggleMute() {
          mutedState = !mutedState;
          toggleMuteCount += 1;
        },
      };
    };
  }
  if (request === '../../utils/audio') {
    return class MockAudioManager {
      constructor() {}
      ready() { return Promise.resolve(this); }
      startRecord() {}
      destroy() {}
    };
  }
  if (request === '../../utils/websocket') {
    return class MockWebSocketManager {
      constructor() {}
      connect() {}
      onStateChange() {}
      offStateChange() {}
      isConnected() { return true; }
      sendListenStart() {}
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

  // 1. onLoad sets companion data and starts the call timer.
  page.onLoad();
  assert.strictEqual(page.data.companionName, '女友', 'companionName should be set');
  assert.ok(page._callTimer, 'call timer should be started');

  // 2. onCancelCall clears the timer, hangs up and navigates back.
  page.onCancelCall();
  assert.strictEqual(page._callTimer, null, 'call timer should be cleared');
  assert.strictEqual(hangupCount, 1, 'hangup should be called on cancel');
  assert.strictEqual(navigateBackCount, 1, 'navigateBack should be called on cancel');

  // 3. onHangup hangs up and navigates back.
  hangupCount = 0;
  navigateBackCount = 0;
  page.onHangup();
  assert.strictEqual(hangupCount, 1, 'hangup should be called on hangup');
  assert.strictEqual(navigateBackCount, 1, 'navigateBack should be called on hangup');

  // 4. Mute toggle works.
  mutedState = false;
  toggleMuteCount = 0;
  page.onToggleMute();
  assert.strictEqual(toggleMuteCount, 1, 'toggleMute should be called');
  assert.strictEqual(mutedState, true, 'mute state should toggle on');

  page.onToggleMute();
  assert.strictEqual(toggleMuteCount, 2, 'toggleMute should be called again');
  assert.strictEqual(mutedState, false, 'mute state should toggle off');

  // 5. _startMedia defaults to speaker mode.
  setInnerAudioOptionCalls.length = 0;
  page._startMedia();
  assert.strictEqual(setInnerAudioOptionCalls.length, 1, 'setInnerAudioOption should be called on media start');
  assert.strictEqual(setInnerAudioOptionCalls[0].speakerOn, true, 'speakerOn should default to true');

  console.log('voice-call.test.js: ALL PASS');
})();
