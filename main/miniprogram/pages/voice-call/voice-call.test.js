const assert = require('assert');
const Module = require('module');

let pageConfig = null;
let setInnerAudioOptionCalls = [];
let navigateBackCount = 0;
let hangupCount = 0;
let mutedState = false;
let toggleMuteCount = 0;
let innerAudioContextRecords = [];

function createMockInnerAudioContext() {
  const ctx = {
    src: '',
    loop: false,
    playCount: 0,
    stopCount: 0,
    destroyCount: 0,
    paused: true,
    _errorHandler: null,
    play() {
      this.playCount += 1;
      this.paused = false;
    },
    stop() {
      this.stopCount += 1;
      this.paused = true;
    },
    destroy() {
      this.destroyCount += 1;
      this.paused = true;
    },
    onError(handler) {
      this._errorHandler = handler;
    },
  };
  innerAudioContextRecords.push(ctx);
  return ctx;
}

const originalWx = {
  setInnerAudioOption(options) {
    setInnerAudioOptionCalls.push(options);
    if (options.success) options.success();
  },
  showToast() {},
  navigateBack() {
    navigateBackCount += 1;
  },
  createInnerAudioContext() {
    return createMockInnerAudioContext();
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
        connect() {},
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
  if (request === '../../config/companion-codes') {
    return { RINGBACK_TONE_URL: 'https://cdn.example.com/ringback.mp3' };
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

  // 1. onLoad sets companion data and starts the call timer and ringback.
  page.onLoad();
  assert.strictEqual(page.data.companionName, '女友', 'companionName should be set');
  assert.ok(page._callTimer, 'call timer should be started');
  assert.strictEqual(innerAudioContextRecords.length, 1, 'ringback audio context should be created');
  const ringback = innerAudioContextRecords[0];
  assert.strictEqual(ringback.src, 'https://cdn.example.com/ringback.mp3', 'ringback src should be set');
  assert.strictEqual(ringback.loop, true, 'ringback should loop');
  assert.strictEqual(ringback.playCount, 1, 'ringback should start playing');

  // 2. onCancelCall clears the timer, stops ringback, hangs up and navigates back.
  page.onCancelCall();
  assert.strictEqual(page._callTimer, null, 'call timer should be cleared');
  assert.strictEqual(ringback.stopCount, 1, 'ringback should stop on cancel');
  assert.strictEqual(ringback.destroyCount, 1, 'ringback should be destroyed on cancel');
  assert.strictEqual(hangupCount, 1, 'hangup should be called on cancel');
  assert.strictEqual(navigateBackCount, 1, 'navigateBack should be called on cancel');

  // 3. onHangup stops ringback, hangs up and navigates back.
  hangupCount = 0;
  navigateBackCount = 0;
  innerAudioContextRecords.length = 0;
  const hangupPage = Object.create(pageConfig);
  hangupPage.setData = function(data) {
    Object.assign(this.data || (this.data = {}), data);
  };
  hangupPage.onLoad();
  const hangupRingback = innerAudioContextRecords[0];
  assert.ok(hangupRingback, 'ringback should be created for hangup test');
  hangupPage.onHangup();
  assert.strictEqual(hangupRingback.stopCount, 1, 'ringback should stop on hangup');
  assert.strictEqual(hangupRingback.destroyCount, 1, 'ringback should be destroyed on hangup');
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

  // 6. Ringback stops when call connects.
  innerAudioContextRecords.length = 0;
  const connectPage = Object.create(pageConfig);
  connectPage.setData = function(data) {
    Object.assign(this.data || (this.data = {}), data);
  };
  connectPage.onLoad();
  const connectRingback = innerAudioContextRecords[0];
  assert.ok(connectRingback, 'ringback should be created for connect test');
  assert.strictEqual(connectRingback.playCount, 1, 'ringback should be playing before connect');
  connectPage._syncState({ state: 'connected', durationSeconds: 0, isMuted: false });
  assert.strictEqual(connectRingback.stopCount, 1, 'ringback should stop on connect');
  assert.strictEqual(connectRingback.destroyCount, 1, 'ringback should be destroyed on connect');

  console.log('voice-call.test.js: ALL PASS');
})();
