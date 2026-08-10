const assert = require('assert');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const pagePath = require.resolve('./home');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalWx = global.wx;
const originalSetTimeout = global.setTimeout;
const originalClearTimeout = global.clearTimeout;
const originalGetApp = global.getApp;

let pageConfig;
let cachedSession = null;
let cachedExpired = false;
let cachedPet = null;
let stageValue = 'empty';
let cuddleResult = { ok: true, alreadyDone: false };
let createCollectionCardResult = { ok: true };
let lastCuddleCall = null;
let lastRecordTouchCall = null;
let resolveForPetCalls = [];
let nextEnvironmentBoundaryMs = 3600000;
let timerDelay = null;
let timerCallback = null;
let timerId = 12345;
let clearedTimers = [];
let relaunchedTo = null;
let showToastCalls = [];
let vibrateCalls = [];

const FIXED_TIMESTAMP = 1754880000000;

const WEATHER_LABELS = { sunny: '晴朗', cloudy: '多云', rain: '下雨', storm: '雷雨', snow: '降雪', postSnow: '雪后' };

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired
};

const petStoreMock = {
  getPet: () => cachedPet,
  savePetFromVO: (pet) => pet,
  getStage: () => stageValue,
  getStagePresentation: (stage) => {
    const map = {
      waiting: { homeText: '它还在睡觉，试着叫醒它吧', actionLabel: '孵化修炼手册' },
      hatching: { homeText: '它正在慢慢长大', actionLabel: '孵化修炼手册' },
      soon: { homeText: '蛋壳里传来了动静', actionLabel: '孵化修炼手册' },
      ready: { homeText: '它准备好见你了', actionLabel: '查看破壳结果' },
      hatched: { homeText: '它终于来到你身边了', actionLabel: '和它说说话' }
    };
    return map[stage] || map.waiting;
  },
  getCountdown: () => '还剩 5 天 3 小时',
  getDailyStatus: () => null,
  recordTouch: () => { lastRecordTouchCall = Date.now(); },
  completeCuddle: async () => {
    lastCuddleCall = Date.now();
    return cuddleResult;
  },
  createCollectionCard: async () => createCollectionCardResult
};

const incubationEnvMock = {
  resolveForPet: (pet, timestamp) => {
    resolveForPetCalls.push({ pet, timestamp });
    return {
      valid: true,
      season: 'summer',
      weather: 'sunny',
      period: 'day',
      lightPhase: 'midday',
      dateKey: '2026-08-11',
      incubationDay: 3,
      sceneKey: 'summer_sunny_day',
      fullSceneImage: 'https://oss.eggbabe.com/scene/summer_sunny_day.png',
      nestImage: 'https://oss.eggbabe.com/nest/day.png',
      eggImage: 'https://oss.eggbabe.com/egg/summer.png',
      windowImage: 'https://oss.eggbabe.com/window/sunny_day.png',
      className: 'season-summer weather-sunny period-day light-midday'
    };
  }
};

const environmentStateMock = {
  millisecondsUntilNextEnvironmentBoundary: () => {
    timerDelay = nextEnvironmentBoundaryMs;
    return nextEnvironmentBoundaryMs;
  }
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/pet-store') return petStoreMock;
    if (request === '../../utils/auth') return authMock;
    if (request === '../../utils/incubation-environment') return incubationEnvMock;
    if (request === '../../utils/environment-state') return environmentStateMock;
    if (request === '../../utils/request') return { get: async () => [] };
    if (request === '../../utils/wechat-api') return { bindPhone: async () => ({}) };
    if (request === '../../utils/life-scenes') return { getSceneKeyFromUrl: () => '' };
    if (request === '../../utils/invite-api') return { getMine: async () => null };
    if (request === '../../utils/share-invite') return { getPending: () => null, clearPending: () => {} };
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => ({
  globalData: { authReady: null },
  ensureLogin: async () => cachedSession
});
global.wx = {
  reLaunch({ url }) { relaunchedTo = url; },
  switchTab() {},
  showToast(opts) { showToastCalls.push(opts); },
  vibrateShort(opts) { vibrateCalls.push(opts); },
  showTabBar() {},
  hideTabBar() {},
  getSystemInfoSync() { return { windowWidth: 375, statusBarHeight: 44, pixelRatio: 2 }; },
  getWindowInfo() { return { windowWidth: 375, statusBarHeight: 44, pixelRatio: 2 }; },
  getMenuButtonBoundingClientRect() { return { bottom: 88 }; }
};
global.setTimeout = (callback, delay) => {
  timerCallback = callback;
  timerDelay = delay;
  return timerId;
};
global.clearTimeout = (id) => { clearedTimers.push(id); };

function resetScenario() {
  cachedSession = null;
  cachedExpired = false;
  cachedPet = null;
  stageValue = 'empty';
  cuddleResult = { ok: true, alreadyDone: false };
  createCollectionCardResult = { ok: true };
  lastCuddleCall = null;
  lastRecordTouchCall = null;
  resolveForPetCalls = [];
  nextEnvironmentBoundaryMs = 3600000;
  timerDelay = null;
  timerCallback = null;
  clearedTimers = [];
  relaunchedTo = null;
  showToastCalls = [];
  vibrateCalls = [];
}

function makePage() {
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes, callback) {
      this.data = { ...this.data, ...changes };
      if (callback) callback();
    }
  };
}

function requirePetStage(stage, options = {}) {
  cachedPet = {
    id: 'pet-001',
    prototype: '玉兔',
    name: '小白',
    hatchStartTime: FIXED_TIMESTAMP - 2 * 24 * 60 * 60 * 1000,
    hatchAt: FIXED_TIMESTAMP + 5 * 24 * 60 * 60 * 1000,
    progress: 20,
    hatchStatus: stage === 'hatched' ? 'HATCHED' : 'EGG',
    ...options
  };
  stageValue = stage;
}

async function run() {
  const template = fs.readFileSync(path.join(__dirname, 'home.wxml'), 'utf8');
  assert.ok(template.includes('<incubation-scene'),
    'home.wxml must render incubation-scene component for pre-hatch stages');
  assert.ok(template.includes('<daily-window-detail'),
    'home.wxml must render daily-window-detail component');
  require('./home');
  assert.ok(pageConfig, 'home page should be registered');

  // 1. 孵化期：environment 被解析且非空
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageHatching = makePage();
  pageHatching.onLoad();
  pageHatching.onShow();
  assert.strictEqual(resolveForPetCalls.length, 1, 'pre-hatch stage resolves environment once on show');
  assert.strictEqual(pageHatching.data.environment && pageHatching.data.environment.weather, 'sunny', 'environment is set');
  assert.strictEqual(timerDelay, nextEnvironmentBoundaryMs, 'environment refresh scheduled');

  // 2. hatched：不解析环境（environment 保持 null）
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatched', { sceneUrl: 'https://oss.eggbabe.com/scene/home.png' });
  const pageHatched = makePage();
  pageHatched.onLoad();
  pageHatched.onShow();
  assert.strictEqual(resolveForPetCalls.length, 0, 'hatched stage does not resolve environment');
  assert.strictEqual(pageHatched.data.environment, null, 'hatched environment stays null');

  // 3. onEggTap 事件 -> 调 petStore.recordTouch（沿用现有业务逻辑）
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageTap = makePage();
  pageTap.onLoad();
  pageTap.onShow();
  pageTap.onEggTap();
  assert.ok(lastRecordTouchCall, 'onEggTap records touch via petStore');
  assert.ok(vibrateCalls.length > 0, 'onEggTap triggers vibration');

  // 4. onWindowTap：windowImage 非空 -> dailyWindowVisible=true；空串 -> 不打开
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageWindow = makePage();
  pageWindow.onLoad();
  pageWindow.onShow();
  pageWindow.onWindowTap({ detail: { left: 100, top: 120, width: 120, height: 160 } });
  assert.strictEqual(pageWindow.data.dailyWindowVisible, true, 'window tap opens detail when image exists');
  assert.ok(pageWindow.data.dailyWindowOriginStyle.includes('--daily-window-origin-left:100px'),
    'origin style captures left position');
  assert.strictEqual(pageWindow.data.dailyWindowWeatherLabel, WEATHER_LABELS.sunny, 'weather label mapped');
  assert.strictEqual(pageWindow.data.dailyWindowPeriodLabel, '日间', 'period label is daytime');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageWindowEmpty = makePage();
  pageWindowEmpty.onLoad();
  pageWindowEmpty.onShow();
  pageWindowEmpty.setData({ environment: { ...pageWindowEmpty.data.environment, windowImage: '' } });
  pageWindowEmpty.onWindowTap({ detail: { left: 100, top: 120, width: 120, height: 160 } });
  assert.notStrictEqual(pageWindowEmpty.data.dailyWindowVisible, true,
    'window tap does nothing when windowImage is empty');

  // 5. onDailyWindowClosed -> dailyWindowVisible=false
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageClose = makePage();
  pageClose.onLoad();
  pageClose.onShow();
  pageClose.onWindowTap({ detail: { left: 0, top: 0, width: 1, height: 1 } });
  assert.strictEqual(pageClose.data.dailyWindowVisible, true);
  pageClose.onDailyWindowClosed();
  assert.strictEqual(pageClose.data.dailyWindowVisible, false, 'close sets visible false');

  // 6. 时段定时器：setTimeout 延迟等于 millisecondsUntilNextEnvironmentBoundary 返回值
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('soon');
  nextEnvironmentBoundaryMs = 45000;
  const pageTimer = makePage();
  pageTimer.onLoad();
  pageTimer.onShow();
  assert.strictEqual(timerDelay, 45000, 'timer delay matches next boundary');

  // 7. onHide/onUnload 清 environmentTimer
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageLifecycle = makePage();
  pageLifecycle.onLoad();
  pageLifecycle.onShow();
  const lifecycleTimerId = pageLifecycle.environmentTimer;
  assert.ok(typeof lifecycleTimerId !== 'undefined' && lifecycleTimerId !== null, 'environment timer is set');
  pageLifecycle.onHide();
  assert.ok(clearedTimers.includes(lifecycleTimerId), 'onHide clears environment timer');
  clearedTimers = [];
  pageLifecycle.onUnload();
  assert.strictEqual(pageLifecycle.environmentTimer, null, 'onUnload leaves environment timer cleared');

  // 8. onLampTap 切换 lampOn
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageLamp = makePage();
  pageLamp.onLoad();
  pageLamp.onShow();
  assert.strictEqual(pageLamp.data.lampOn, false, 'lamp defaults off');
  pageLamp.onLampTap();
  assert.strictEqual(pageLamp.data.lampOn, true, 'lamp toggles on');
  pageLamp.onLampTap();
  assert.strictEqual(pageLamp.data.lampOn, false, 'lamp toggles off');

  console.log('home.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.wx = originalWx;
  global.setTimeout = originalSetTimeout;
  global.clearTimeout = originalClearTimeout;
  global.getApp = originalGetApp;
  delete require.cache[pagePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
