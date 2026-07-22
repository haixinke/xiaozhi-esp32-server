const assert = require('assert');
const Module = require('module');

let pageConfig = null;
let controllerCalls = [];
let recordPermissionChecks = 0;
let modalCalls = [];
let audioOptions = null;
let webSocketOptions = null;

class MockVoiceInputController {
  constructor(options) {
    this.options = options;
  }

  press() { controllerCalls.push('press'); return true; }
  setCancelled(cancelled) { controllerCalls.push(['setCancelled', cancelled]); }
  release() { controllerCalls.push('release'); return Promise.resolve(); }
  cancel() { controllerCalls.push('cancel'); return Promise.resolve(); }
  handleRecordStart() { controllerCalls.push('handleRecordStart'); }
  handleAudioFrame(frame) { controllerCalls.push(['handleAudioFrame', frame]); }
  handleAudioFailure(scope) { controllerCalls.push(['handleAudioFailure', scope]); return Promise.resolve(); }
  handleSocketState(state, generation) { controllerCalls.push(['handleSocketState', state, generation]); }
  handleMessage(message) { controllerCalls.push(['handleMessage', message]); }
  destroy() { controllerCalls.push('destroy'); }
}

global.Page = function (config) {
  pageConfig = config;
};

global.getApp = function () {
  return { globalData: {} };
};

global.wx = {
  showToast() {},
  showModal(options) { modalCalls.push(options); },
  getSetting(options) {
    recordPermissionChecks += 1;
    options.success({ authSetting: {} });
  },
  authorize(options) {
    options.fail({ errMsg: 'authorize:fail' });
  },
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
      constructor(options) { audioOptions = options; }
      ready() { return Promise.resolve(this); }
      startRecord() {}
      stopRecord() {}
      stopPlayback() {}
      appendOpusFrame() {}
      destroy() {}
    };
  }
  if (request === '../../utils/websocket') {
    return class MockWebSocketManager {
      constructor(options) { webSocketOptions = options; }
      connect() {}
      onStateChange() {}
      offStateChange() {}
      getConnectionGeneration() { return 7; }
      sendAudioFrame() {}
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
  if (request === '../../utils/voice-input-controller') {
    return MockVoiceInputController;
  }
  if (request === '../../utils/voice-call-manager') {
    return function () {
      return { getState() { return { state: 'idle' }; }, hangup() {} };
    };
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
  // Inject mock managers for page lifecycle methods.
  page.wsManager = {
    sendListenStart() {},
    sendListenStop() {},
    sendAbort() {},
    destroy() {},
  };
  page.audioManager = {
    startRecord() {},
    stopRecord() {},
    destroy() {},
  };
  page.voiceInputController = new MockVoiceInputController({});
  // Pre-conditions for onVoiceTouchStart guards.
  page.data.connectionState = 'connected';
  page.data.chatState = 'idle';
  page.data.hasVoiceInput = true;
  page.data.chatRemaining = 10;
  // Stub side-effectful timer used inside the touch start path.
  page._resetIdleTimer = function () {};
  return page;
}

async function runTests() {
  assert.ok(pageConfig, 'Page should be registered');

  // A recorder failure after a previous denial must direct the user to the
  // Mini Program permission settings instead of repeating an inert toast.
  const recordErrorPage = makePage();
  modalCalls = [];
  global.wx.getSetting = function (options) {
    options.success({ authSetting: { 'scope.record': false } });
  };
  recordErrorPage._handleRecordError(new Error('recorder error: auth deny'));
  assert.strictEqual(modalCalls.length, 1, 'permission denial should open the settings prompt');
  assert.strictEqual(modalCalls[0].confirmText, '去设置');

  // Tapping the input-mode toggle must not preflight the microphone through
  // wx.authorize. That preflight can fail without showing a user prompt;
  // RecorderManager.start, initiated by the later press-and-hold gesture,
  // is the native authorization trigger.
  const inputModePage = makePage();
  recordPermissionChecks = 0;
  global.wx.getSetting = function (options) {
    recordPermissionChecks += 1;
    options.success({ authSetting: {} });
  };
  await inputModePage.onToggleInputMode();
  assert.strictEqual(inputModePage.data.inputMode, 'voice', 'toggle should enter voice mode');
  assert.strictEqual(recordPermissionChecks, 0, 'toggle should not preflight microphone permission');

  // Voice touch and lifecycle entry points delegate all protocol work to the
  // controller. The page only keeps the UI guards and slide-cancel geometry.
  const page = makePage();
  controllerCalls = [];
  page.onVoiceTouchStart({ touches: [{ clientY: 100 }] });
  assert.deepStrictEqual(controllerCalls, ['press']);
  page.onVoiceTouchMove({ touches: [{ clientY: 0 }] });
  assert.deepStrictEqual(controllerCalls, ['press', ['setCancelled', true]]);
  await page.onVoiceTouchEnd();
  assert.deepStrictEqual(controllerCalls, ['press', ['setCancelled', true], 'release']);
  page.onVoiceTouchCancel();
  page._handleAppHide();
  page.onUnload();
  assert.strictEqual(controllerCalls.filter((call) => call === 'cancel').length, 2);
  assert.strictEqual(controllerCalls.includes('destroy'), true);

  // Controller creation waits for both managers, and manager callbacks enter
  // it before the page's existing websocket renderer runs.
  const wiringPage = Object.create(pageConfig);
  wiringPage.data = Object.assign({}, pageConfig.data, { chatState: 'speaking' });
  wiringPage.setData = function (data) { Object.assign(this.data, data); };
  wiringPage._resetIdleTimer = function () {};
  wiringPage._handleWSMessage = function () { controllerCalls.push('pageMessage'); };
  wiringPage._initVoiceInputController();
  assert.strictEqual(wiringPage.voiceInputController, null);
  audioOptions = null;
  webSocketOptions = null;
  wiringPage._initAudio();
  wiringPage._initVoiceInputController();
  assert.strictEqual(wiringPage.voiceInputController, null);
  wiringPage._initWebSocketManager();
  wiringPage._initVoiceInputController();
  assert.ok(wiringPage.voiceInputController instanceof MockVoiceInputController);

  controllerCalls = [];
  const frame = new ArrayBuffer(1);
  audioOptions.onRecordStart();
  audioOptions.onAudioFrame(frame);
  webSocketOptions.onStateChange('connected');
  webSocketOptions.onMessage({ type: 'stt', text: 'heard' });
  assert.deepStrictEqual(controllerCalls, [
    'handleRecordStart',
    ['handleAudioFrame', frame],
    ['handleSocketState', 'connected', 7],
    ['handleMessage', { type: 'stt', text: 'heard' }],
    'pageMessage',
  ]);

  controllerCalls = [];
  audioOptions.onError(new Error('record failed'), 'record');
  audioOptions.onError(new Error('encode failed'), 'encode');
  assert.deepStrictEqual(controllerCalls, [
    ['handleAudioFailure', 'record'],
    ['handleAudioFailure', 'encode'],
  ]);

  wiringPage.voiceInputController.options.onTerminal('no_speech');
  assert.strictEqual(wiringPage.data.chatState, 'idle');
  wiringPage.data.chatState = 'speaking';
  wiringPage.voiceInputController.options.onTerminal('recognized');
  assert.strictEqual(wiringPage.data.chatState, 'speaking');

  const hidePage = makePage();
  controllerCalls = [];
  hidePage.onHide();
  assert.deepStrictEqual(controllerCalls, ['cancel']);

  console.log('index.test.js: ALL PASS');
}

runTests().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
