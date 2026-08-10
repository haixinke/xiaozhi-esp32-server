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
let ensureSession = null;
let ensureError = null;
let markPhoneResult = null;
let bindPhoneError = null;
let bindPhoneCalls = 0;
let navigatedTo = null;
let relaunchedTo = null;
let toastTitle = null;
let applySessionArgument = null;
let bindPhonePromise = null;
let requestGetCalls = 0;
let requestGetResult = [];
let requestGetPromise = null;
let requestGetError = null;
let savedPetVO = null;
let showTabBarCalls = 0;
let storedPet = null;
let inviteMineResult = null;
let inviteMineError = null;
let inviteMineCalls = 0;
let pendingInvite = null;
let clearPendingCalls = 0;

let stageValue = 'empty';
let cuddleResult = { ok: true, alreadyDone: false };
let createCollectionCardResult = { ok: true };
let lastCuddleCall = null;
let lastRecordTouchCall = null;
let resolveForPetCalls = [];
let nextEnvironmentBoundaryMs = 3600000;
let timerDelay = null;
let timerCallback = null;
let timerIdSequence = 12345;
let clearedTimers = [];
let vibrateCalls = [];
let doodleArtUrlResult = '';
let doodleArtUrlError = null;
let uploadDoodleResult = '';
let uploadDoodleError = null;
let uploadDoodleCalls = 0;
let saveDoodleCalls = 0;
let lastSaveDoodleArtUrl = null;
let saveDoodleResult = { ok: true, alreadyDone: false };
let updateNicknameCalls = 0;
let lastNicknameValue = null;
let updateNicknameResult = { ok: true, alreadyDone: false, pet: null };
let getLatestDoodleArtUrlCalls = 0;

const FIXED_TIMESTAMP = 1754880000000;

const WEATHER_LABELS = { sunny: '晴朗', cloudy: '多云', rain: '下雨', storm: '雷雨', snow: '降雪', postSnow: '雪后' };

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired,
  markPhoneBound: () => markPhoneResult
};

const petStoreMock = {
  getPet: () => storedPet,
  savePetFromVO: (pet) => {
    savedPetVO = pet;
    return pet;
  },
  getStage: (pet) => (pet && pet.hatchStatus === 'HATCHED' ? 'hatched' : stageValue),
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
  createCollectionCard: async () => createCollectionCardResult,
  saveDoodle: async (artUrl) => {
    saveDoodleCalls += 1;
    lastSaveDoodleArtUrl = artUrl;
    return saveDoodleResult;
  },
  updateNickname: async (name) => {
    updateNicknameCalls += 1;
    lastNicknameValue = name;
    return updateNicknameResult;
  }
};

const inviteApiMock = {
  getMine: async () => {
    inviteMineCalls += 1;
    if (inviteMineError) throw inviteMineError;
    return inviteMineResult;
  }
};
const shareInviteMock = {
  getPending: () => pendingInvite,
  clearPending: () => {
    clearPendingCalls += 1;
    pendingInvite = null;
  }
};
const requestMock = {
  get: async () => {
    requestGetCalls += 1;
    if (requestGetPromise) return requestGetPromise;
    if (requestGetError) throw requestGetError;
    return requestGetResult;
  }
};
const wechatApiMock = {
  bindPhone: async (phoneCode) => {
    bindPhoneCalls += 1;
    assert.strictEqual(phoneCode, 'test-phone-code');
    if (bindPhonePromise) return bindPhonePromise;
    if (bindPhoneError) throw bindPhoneError;
  }
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

const preHatchAssetsMock = {
  INTERACTION_ICONS: {
    wish: '/assets/ui/3d-actions/ui_3d_wishing_fountain_two_tier_simple_256_v04.webp',
    learn: '/assets/ui/3d-actions/ui_3d_early_learning_picture_book_simple_256_v03.webp',
    draw: '/assets/ui/3d-actions/ui_3d_drawing_palette_256_v02.webp'
  }
};

const environmentStateMock = {
  millisecondsUntilNextEnvironmentBoundary: () => {
    timerDelay = nextEnvironmentBoundaryMs;
    return nextEnvironmentBoundaryMs;
  }
};

const doodleApiMock = {
  uploadDoodleImage: async (tempFilePath) => {
    uploadDoodleCalls += 1;
    if (uploadDoodleError) throw uploadDoodleError;
    return uploadDoodleResult;
  },
  getLatestDoodleArtUrl: async () => {
    getLatestDoodleArtUrlCalls += 1;
    if (doodleArtUrlError) throw doodleArtUrlError;
    return doodleArtUrlResult;
  }
};

const app = {
  globalData: { authReady: null },
  async ensureLogin() {
    if (ensureError) throw ensureError;
    return ensureSession;
  },
  applySession(session) {
    applySessionArgument = session;
  }
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/auth') return authMock;
    if (request === '../../utils/pet-store') return petStoreMock;
    if (request === '../../utils/wechat-api') return wechatApiMock;
    if (request === '../../utils/request') return requestMock;
    if (request === '../../utils/invite-api') return inviteApiMock;
    if (request === '../../utils/share-invite') return shareInviteMock;
    if (request === '../../utils/incubation-environment') return incubationEnvMock;
    if (request === '../../utils/environment-state') return environmentStateMock;
    if (request === '../../utils/doodle-api') return doodleApiMock;
    if (request === '../../utils/life-scenes') return { getSceneKeyFromUrl: () => '' };
    if (request === '../../config/pre-hatch-assets') return preHatchAssetsMock;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  navigateTo({ url }) { navigatedTo = url; },
  reLaunch({ url }) { relaunchedTo = url; },
  showTabBar() { showTabBarCalls += 1; },
  showToast({ title }) { toastTitle = title; },
  vibrateShort(opts) { vibrateCalls.push(opts); },
  hideTabBar() {},
  createVideoContext() { return { play: () => {} }; },
  getSystemInfoSync() { return { windowWidth: 375, statusBarHeight: 44, pixelRatio: 2 }; },
  getWindowInfo() { return { windowWidth: 375, statusBarHeight: 44, pixelRatio: 2 }; },
  getMenuButtonBoundingClientRect() { return { bottom: 88 }; }
};
global.setTimeout = (callback, delay) => {
  timerCallback = callback;
  timerDelay = delay;
  const id = timerIdSequence++;
  return id;
};
global.clearTimeout = (id) => { clearedTimers.push(id); };

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

function resetScenario() {
  cachedSession = null;
  cachedExpired = false;
  ensureSession = null;
  ensureError = null;
  markPhoneResult = null;
  bindPhoneError = null;
  bindPhoneCalls = 0;
  navigatedTo = null;
  relaunchedTo = null;
  toastTitle = null;
  applySessionArgument = null;
  bindPhonePromise = null;
  requestGetCalls = 0;
  requestGetResult = [];
  requestGetPromise = null;
  requestGetError = null;
  savedPetVO = null;
  showTabBarCalls = 0;
  storedPet = null;
  inviteMineResult = null;
  inviteMineError = null;
  inviteMineCalls = 0;
  pendingInvite = null;
  clearPendingCalls = 0;
  app.globalData = { authReady: null };

  stageValue = 'empty';
  cuddleResult = { ok: true, alreadyDone: false };
  createCollectionCardResult = { ok: true };
  lastCuddleCall = null;
  lastRecordTouchCall = null;
  resolveForPetCalls = [];
  nextEnvironmentBoundaryMs = 3600000;
  timerDelay = null;
  timerCallback = null;
  timerIdSequence = 12345;
  clearedTimers = [];
  vibrateCalls = [];
  doodleArtUrlResult = '';
  doodleArtUrlError = null;
  uploadDoodleResult = '';
  uploadDoodleError = null;
  uploadDoodleCalls = 0;
  saveDoodleCalls = 0;
  lastSaveDoodleArtUrl = null;
  saveDoodleResult = { ok: true, alreadyDone: false };
  updateNicknameCalls = 0;
  lastNicknameValue = null;
  updateNicknameResult = { ok: true, alreadyDone: false, pet: null };
  getLatestDoodleArtUrlCalls = 0;
}

function requirePetStage(stage, options = {}) {
  storedPet = {
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
  const homeTemplate = fs.readFileSync(path.join(__dirname, 'home.wxml'), 'utf8');
  assert.ok(!homeTemplate.includes('<text class="state-time">{{countdown}}</text>'),
    'home must not reveal the hatching countdown');
  assert.ok(homeTemplate.includes('<view wx:elif="{{!pet}}" class="empty-state">'),
    'home must preserve the empty state after restoration completes');
  assert.ok(homeTemplate.includes('<block wx:if="{{authChecked && !petRestoreLoading}}">'),
    'home must keep all page content hidden while restoring the pet');
  assert.ok(homeTemplate.includes('wx:if="{{hasPendingInvite}}"'),
    'home should offer a dedicated CTA when a friend invitation is pending');
  assert.ok(!homeTemplate.includes('open-type="share"'),
    'home should rely on the native top-right menu instead of inline share buttons');
  assert.ok(homeTemplate.includes('wx:if="{{petRestoreError}}"'),
    'home should render a dedicated pet restoration error state');
  assert.ok(homeTemplate.includes('<incubation-scene'),
    'home.wxml must render incubation-scene component for pre-hatch stages');
  assert.ok(homeTemplate.includes('<daily-window-detail'),
    'home.wxml must render daily-window-detail component');
  assert.ok(homeTemplate.includes('class="companion-section companion-section--incubating"'),
    'home.wxml must render the floating companion-section dock');
  assert.ok(homeTemplate.includes('companion-primary-dock'),
    'companion entries must sit inside the static project primary dock');
  assert.ok(homeTemplate.includes('wx:for="{{companionActions}}"'),
    'companion-section must iterate companionActions');
  assert.ok(homeTemplate.includes('data-key="{{item.key}}"'),
    'companion items must carry data-key');
  assert.ok(homeTemplate.includes('bindtap="onCompanionTap"'),
    'companion items must bind onCompanionTap');
  assert.ok(homeTemplate.includes('class="hatch-entry"'),
    'home.wxml must render hatch-entry for ready stage');
  assert.ok(homeTemplate.includes('wx:if="{{stage === \'ready\'}}"'),
    'hatch-entry must be conditionally rendered for ready stage');
  assert.ok(homeTemplate.includes('content--incubating'),
    'incubating stages must render the full-screen content variant');
  assert.ok(homeTemplate.includes('pet-view--incubating'),
    'incubating stages must render the full-screen pet view variant');
  assert.ok(homeTemplate.includes('<nav-bar wx:if="{{!pet}}"'),
    'nav-bar only renders before a pet is claimed, so the scene stays full screen');
  assert.ok(homeTemplate.includes('bindtap="onPetNameTap"'),
    'the pet name pill must open the naming sheet');
  assert.ok(homeTemplate.includes('class="name-sheet"'),
    'home.wxml must render the naming sheet');
  assert.ok(homeTemplate.includes('maxlength="10"'),
    'the naming input keeps the 10 character limit');

  require('./home');
  assert.ok(pageConfig, 'home page should be registered');

  // ===== 既有业务回归断言（分享、邀请、设备绑定、冷启动） =====
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  inviteMineResult = { code: 'EGG-ABCD', remaining: 3, status: 1 };
  const sharePage = makePage();
  sharePage.onLoad();
  await Promise.resolve();
  assert.strictEqual(inviteMineCalls, 1, 'home load should preload the personal invitation code');
  assert.deepStrictEqual(
    sharePage.onShareAppMessage(),
    {
      title: '一起来养蛋宝宝吧',
      path: '/pages/home/home?v=1&source=home_share&inviteCode=EGG-ABCD'
    },
    'an active personal invitation code should be included in the synchronous share path'
  );
  assert.deepStrictEqual(
    sharePage.onShareTimeline(),
    {
      title: '一起来养蛋宝宝吧'
    },
    'timeline sharing must not carry or prefill a personal invitation code'
  );

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  inviteMineError = new Error('network unavailable');
  const fallbackSharePage = makePage();
  fallbackSharePage.onLoad();
  await Promise.resolve();
  assert.deepStrictEqual(
    fallbackSharePage.onShareAppMessage(),
    {
      title: '一起来养蛋宝宝吧',
      path: '/pages/home/home?v=1&source=home_share'
    },
    'a failed invitation lookup should still provide a privacy-safe base share'
  );
  assert.deepStrictEqual(
    fallbackSharePage.onShareTimeline(),
    {
      title: '一起来养蛋宝宝吧'
    },
    'timeline sharing should remain a plain home share without an invitation query'
  );

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  inviteMineResult = { code: 'EGG-USED', remaining: 0, status: 1 };
  const exhaustedSharePage = makePage();
  exhaustedSharePage.onLoad();
  await Promise.resolve();
  assert.deepStrictEqual(
    exhaustedSharePage.onShareAppMessage(),
    {
      title: '一起来养蛋宝宝吧',
      path: '/pages/home/home?v=1&source=home_share'
    },
    'an exhausted code should not be exposed in the share path'
  );

  resetScenario();
  pendingInvite = { code: 'ABCDE', source: 'home_share', version: 1, receivedAt: Date.now() };
  const pendingInvitePage = makePage();
  await pendingInvitePage.loadPetFromServer();
  assert.strictEqual(pendingInvitePage.data.hasPendingInvite, true,
    'a petless user with a pending friend invite should see the invitation CTA');
  assert.strictEqual(clearPendingCalls, 0, 'a petless user should retain the friend invitation');

  resetScenario();
  pendingInvite = { code: 'ABCDE', source: 'home_share', version: 1, receivedAt: Date.now() };
  requestGetError = new Error('network unavailable');
  const failedRestorePage = makePage();
  await failedRestorePage.loadPetFromServer();
  assert.strictEqual(failedRestorePage.data.hasPendingInvite, false,
    'a failed pet restoration must not expose the pending invitation CTA');
  assert.strictEqual(failedRestorePage.data.petRestoreError, '宠物状态加载失败，请重试',
    'a failed pet restoration should surface a retryable error state');
  assert.strictEqual(clearPendingCalls, 0,
    'a failed pet restoration must retain the pending invitation for a later retry');
  requestGetError = null;
  await failedRestorePage.onRetryPetRestore();
  assert.strictEqual(failedRestorePage.data.petRestoreError, '',
    'a successful retry should clear the pet restoration error');
  assert.strictEqual(failedRestorePage.data.hasPendingInvite, true,
    'a successful petless retry should restore the pending invitation CTA');

  resetScenario();
  storedPet = { id: 'pet-1', hatchStatus: 'HATCHED', prototype: '玉兔' };
  pendingInvite = { code: 'ABCDE', source: 'home_share', version: 1, receivedAt: Date.now() };
  const existingPetPage = makePage();
  existingPetPage.setData({ authChecked: true });
  existingPetPage.onShow();
  assert.strictEqual(clearPendingCalls, 1, 'an existing pet should clear a pending friend invitation');
  assert.strictEqual(existingPetPage.data.hasPendingInvite, false,
    'an existing pet should not show the invitation CTA');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  pendingInvite = { code: 'ABCDE', source: 'home_share', version: 1, receivedAt: Date.now() };
  inviteMineResult = { code: 'ABCDE', remaining: 3, status: 1 };
  const selfInvitePage = makePage();
  selfInvitePage.onLoad();
  await Promise.resolve();
  assert.strictEqual(clearPendingCalls, 1, 'opening a personal invitation should clear the self-invite context');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  const boundPage = makePage();
  boundPage.onAddDevice();
  assert.strictEqual(navigatedTo, '/pages/add-device/add-device');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: false };
  const unboundPage = makePage();
  unboundPage.onAddDevice();
  assert.strictEqual(unboundPage.data.showPhoneAuthorization, true);
  assert.strictEqual(navigatedTo, null);

  resetScenario();
  cachedSession = { userId: 42, hasPhone: false };
  app.globalData.authReady = Promise.resolve(cachedSession);
  const browsingPage = makePage();
  browsingPage.onLoad();
  await Promise.resolve();
  assert.strictEqual(browsingPage.data.authChecked, true, 'valid unbound users can browse home');
  assert.strictEqual(relaunchedTo, null, 'valid unbound users are not redirected to welcome');

  resetScenario();
  const hatchedPet = { id: 'pet-1', hatchStatus: 'HATCHED', prototype: '玉兔' };
  let resolvePetList;
  app.globalData.authReady = Promise.resolve({ userId: 42, hasPhone: true });
  requestGetPromise = new Promise((resolve) => { resolvePetList = resolve; });
  const coldStartPage = makePage();
  coldStartPage.onLoad();
  await Promise.resolve();
  assert.strictEqual(requestGetCalls, 1, 'cold start restores the server pet after login');
  assert.strictEqual(coldStartPage.data.petRestoreLoading, true,
    'empty state remains hidden while the server pet is restoring');
  resolvePetList([hatchedPet]);
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepStrictEqual(savedPetVO, hatchedPet, 'server pet is saved to local cache');
  assert.strictEqual(coldStartPage.data.petRestoreLoading, false,
    'restoration completes after the server pet is returned');
  assert.strictEqual(coldStartPage.data.stage, 'hatched', 'hatched server pet renders the success state');
  assert.strictEqual(showTabBarCalls, 0,
    'custom floating tab bar replaces the native tab bar after restoration');

  // ===== 手机号授权断言 =====
  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  markPhoneResult = { userId: 42, hasPhone: true };
  const authorizationPage = makePage();
  authorizationPage.setData({ showPhoneAuthorization: true });
  await authorizationPage.onAuthorizePhone({ detail: { code: 'test-phone-code' } });
  assert.strictEqual(bindPhoneCalls, 1);
  assert.deepStrictEqual(applySessionArgument, markPhoneResult, 'bound session syncs with app state');
  assert.strictEqual(authorizationPage.data.showPhoneAuthorization, false);
  assert.strictEqual(authorizationPage.data.authorizingPhone, false);
  assert.strictEqual(navigatedTo, '/pages/add-device/add-device');

  resetScenario();
  const rejectedPage = makePage();
  await rejectedPage.onAuthorizePhone({ detail: {} });
  assert.strictEqual(bindPhoneCalls, 0, 'rejected phone authorization does not bind');
  assert.strictEqual(navigatedTo, null, 'rejected phone authorization does not navigate');
  assert.strictEqual(rejectedPage.data.authorizingPhone, false);
  assert.strictEqual(toastTitle, '需要授权手机号后才能领取蛋宝宝');

  resetScenario();
  const missingCodePage = makePage();
  await missingCodePage.onAuthorizePhone();
  assert.strictEqual(bindPhoneCalls, 0, 'missing phone code does not bind');
  assert.strictEqual(navigatedTo, null, 'missing phone code does not navigate');
  assert.strictEqual(missingCodePage.data.authorizingPhone, false);

  resetScenario();
  ensureSession = null;
  const invalidSessionPage = makePage();
  await invalidSessionPage.onAuthorizePhone({ detail: { code: 'test-phone-code' } });
  assert.strictEqual(bindPhoneCalls, 0, 'invalid login session does not bind');
  assert.strictEqual(navigatedTo, null, 'invalid login session does not navigate');
  assert.strictEqual(invalidSessionPage.data.authorizingPhone, false);

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  bindPhoneError = new Error('bind failed');
  const failedBindPage = makePage();
  await failedBindPage.onAuthorizePhone({ detail: { code: 'test-phone-code' } });
  assert.strictEqual(bindPhoneCalls, 1, 'binding failure still attempts exactly once');
  assert.strictEqual(navigatedTo, null, 'binding failure does not navigate');
  assert.strictEqual(failedBindPage.data.authorizingPhone, false);

  resetScenario();
  const closePage = makePage();
  closePage.setData({ showPhoneAuthorization: true });
  closePage.onClosePhoneAuthorization();
  assert.strictEqual(closePage.data.showPhoneAuthorization, false, 'close hides the authorization dialog');
  closePage.setData({ showPhoneAuthorization: true, authorizingPhone: true });
  closePage.onClosePhoneAuthorization();
  assert.strictEqual(closePage.data.showPhoneAuthorization, true, 'close is disabled while authorizing');

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  markPhoneResult = { userId: 42, hasPhone: true };
  let resolveBind;
  bindPhonePromise = new Promise((resolve) => { resolveBind = resolve; });
  const concurrentPage = makePage();
  const firstAuthorization = concurrentPage.onAuthorizePhone({ detail: { code: 'test-phone-code' } });
  await Promise.resolve();
  const secondAuthorization = concurrentPage.onAuthorizePhone({ detail: { code: 'test-phone-code' } });
  assert.strictEqual(bindPhoneCalls, 1, 'concurrent taps start only one phone bind');
  resolveBind();
  await Promise.all([firstAuthorization, secondAuthorization]);
  assert.strictEqual(bindPhoneCalls, 1, 'concurrent taps bind only once');
  assert.strictEqual(navigatedTo, '/pages/add-device/add-device');
  assert.strictEqual(concurrentPage.data.authorizingPhone, false);

  // ===== Task 7 新增断言：破壳前场景集成 =====
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

  // ===== Task 8 新增断言：companion 图标入口 =====
  // 1. companionActions 包含 wish/learn/draw 且携带图标与锁定状态
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageCompanion = makePage();
  pageCompanion.onLoad();
  pageCompanion.onShow();
  const actions = pageCompanion.data.companionActions;
  assert.ok(Array.isArray(actions), 'companionActions is array');
  assert.strictEqual(actions.length, 3, 'companionActions has three entries');
  assert.deepStrictEqual(actions.map(a => a.key), ['wish', 'learn', 'draw'], 'companionActions keys order');
  actions.forEach((a) => {
    assert.ok(a.icon, `companion action ${a.key} has icon`);
    assert.ok(a.title, `companion action ${a.key} has title`);
    assert.ok('locked' in a, `companion action ${a.key} has locked field`);
  });

  // 2. 解锁 wish 时点击跳转 /pages/wish/wish（300ms setTimeout 后触发 wx.navigateTo）
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageWish = makePage();
  pageWish.onLoad();
  pageWish.onShow();
  pageWish.setData({ wishUnlocked: true, learnUnlocked: true });
  pageWish.onCompanionTap({ currentTarget: { dataset: { key: 'wish' } } });
  assert.strictEqual(timerDelay, 300, 'wish navigation delayed 300ms');
  assert.ok(typeof timerCallback === 'function', 'wish navigation scheduled');
  timerCallback();
  assert.strictEqual(navigatedTo, '/pages/wish/wish', 'wish tap navigates to wish page');

  // 3. learn 未解锁时点击给出 feedback，不跳转
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageLearnLocked = makePage();
  pageLearnLocked.onLoad();
  pageLearnLocked.onShow();
  pageLearnLocked.setData({ wishUnlocked: true, learnUnlocked: false });
  pageLearnLocked.onCompanionTap({ currentTarget: { dataset: { key: 'learn' } } });
  assert.strictEqual(pageLearnLocked.data.feedback, '蛋宝宝还没到早教的年龄，明天来试试吧。', 'locked learn shows feedback');
  assert.strictEqual(navigatedTo, null, 'locked learn does not navigate');

  // 4. draw 点击打开涂鸦编辑器（不再显示占位 toast）
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageDraw = makePage();
  pageDraw.onLoad();
  pageDraw.onShow();
  pageDraw.onCompanionTap({ currentTarget: { dataset: { key: 'draw' } } });
  assert.strictEqual(pageDraw.data.doodleEditorVisible, true, 'draw entry opens the doodle editor');
  assert.strictEqual(navigatedTo, null, 'draw entry does not navigate away');
  assert.strictEqual(toastTitle, null, 'draw entry no longer shows the placeholder toast');

  // 5. wish 未解锁时点击给出 feedback，不跳转
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageWishLocked = makePage();
  pageWishLocked.onLoad();
  pageWishLocked.onShow();
  pageWishLocked.setData({ wishUnlocked: false, learnUnlocked: true });
  pageWishLocked.onCompanionTap({ currentTarget: { dataset: { key: 'wish' } } });
  assert.strictEqual(pageWishLocked.data.feedback, '许愿池还在准备中。', 'locked wish shows feedback');
  assert.strictEqual(navigatedTo, null, 'locked wish does not navigate');

  // 6. ready 态渲染破壳入口按钮并显示「查看破壳结果」文案
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('ready', { hatchAt: FIXED_TIMESTAMP });
  const pageReady = makePage();
  pageReady.onLoad();
  pageReady.onShow();
  assert.strictEqual(pageReady.data.stage, 'ready', 'ready stage is recognized');
  assert.strictEqual(pageReady.data.actionLabel, '查看破壳结果', 'ready stage action label invites hatch');

  // 7. ready 态点击破壳入口调 doHatch（启动破壳视频遮罩）
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('ready', { hatchAt: FIXED_TIMESTAMP });
  const pageReadyHatch = makePage();
  pageReadyHatch.onLoad();
  pageReadyHatch.onShow();
  pageReadyHatch.onPrimaryAction();
  assert.strictEqual(pageReadyHatch.data.hatching, true, 'ready onPrimaryAction starts hatching overlay');

  // 8. 涂鸦保存：上传成功 → 记录动作 → 回显 artUrl → 关闭编辑器
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  uploadDoodleResult = 'https://oss.eggbabe.com/doodle/pet-001.png';
  const pageSave = makePage();
  pageSave.onLoad();
  pageSave.onShow();
  saveDoodleCalls = 0;
  await pageSave.onDoodleSaved({ detail: { tempFilePath: 'wxfile://tmp/doodle.png' } });
  assert.strictEqual(uploadDoodleCalls, 1, 'doodle image uploaded once');
  assert.strictEqual(saveDoodleCalls, 1, 'saveDoodle called after upload succeeds');
  assert.strictEqual(lastSaveDoodleArtUrl, 'https://oss.eggbabe.com/doodle/pet-001.png', 'saveDoodle received the artUrl');
  assert.strictEqual(pageSave.data.eggArtUrl, 'https://oss.eggbabe.com/doodle/pet-001.png', 'eggArtUrl updated for scene art layer');
  assert.strictEqual(pageSave.data.doodleEditorVisible, false, 'editor closes after a successful save');

  // 10. 涂鸦上传失败 → 不记录动作 → 编辑器保持打开
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  uploadDoodleError = { userMessage: '画作上传失败' };
  const pageSaveFail = makePage();
  pageSaveFail.onLoad();
  pageSaveFail.onShow();
  pageSaveFail.setData({ doodleEditorVisible: true });
  saveDoodleCalls = 0;
  await pageSaveFail.onDoodleSaved({ detail: { tempFilePath: 'wxfile://tmp/doodle.png' } });
  assert.strictEqual(saveDoodleCalls, 0, 'saveDoodle is not called when upload fails');
  assert.strictEqual(pageSaveFail.data.doodleEditorVisible, true, 'editor stays open so the artwork can be retried');
  assert.ok(toastTitle, 'upload failure shows a toast');

  // 11. onShow 孵化期回显最新涂鸦 artUrl
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  doodleArtUrlResult = 'https://oss.eggbabe.com/doodle/existing.png';
  const pageRestore = makePage();
  pageRestore.onLoad();
  pageRestore.onShow();
  // getLatestDoodleArtUrl 是异步的，等待微任务落定后再断言
  await Promise.resolve();
  await Promise.resolve();
  assert.strictEqual(pageRestore.data.eggArtUrl, 'https://oss.eggbabe.com/doodle/existing.png', 'onShow restores the latest doodle artUrl onto the scene');

  // 12. 命名弹层：打开回填当前名字，输入限 10 字符
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  const pageName = makePage();
  pageName.onLoad();
  pageName.onShow();
  pageName.onPetNameTap();
  assert.strictEqual(pageName.data.showNameSheet, true, 'tapping the name pill opens the naming sheet');
  assert.strictEqual(pageName.data.nameDraft, '小白', 'the sheet pre-fills the current pet name');
  pageName.onNameInput({ detail: { value: '一二三四五六七八九十十一' } });
  assert.strictEqual(pageName.data.nameCount, 10, 'the naming input is capped at 10 characters');

  // 13. 命名保存成功：调用 pet-store 并关闭弹层
  updateNicknameResult = { ok: true, alreadyDone: false, pet: { name: '小白白' } };
  pageName.onNameInput({ detail: { value: '小白白' } });
  await pageName.onSaveName();
  assert.strictEqual(updateNicknameCalls, 1, 'saving delegates to petStore.updateNickname');
  assert.strictEqual(lastNicknameValue, '小白白', 'the trimmed draft reaches pet-store');
  assert.strictEqual(pageName.data.showNameSheet, false, 'sheet closes after a successful save');
  assert.strictEqual(pageName.data.pet.name, '小白白', 'the new name renders on the page');

  // 14. 命名保存失败：弹层保留并展示错误
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  requirePetStage('hatching');
  updateNicknameResult = { ok: false, message: '昵称含有不适合的内容，请换一个' };
  const pageNameFail = makePage();
  pageNameFail.onLoad();
  pageNameFail.onShow();
  pageNameFail.onPetNameTap();
  pageNameFail.onNameInput({ detail: { value: '诈骗蛋' } });
  await pageNameFail.onSaveName();
  assert.strictEqual(pageNameFail.data.showNameSheet, true, 'sheet stays open when saving fails');
  assert.strictEqual(pageNameFail.data.nameError, '昵称含有不适合的内容，请换一个', 'the failure message surfaces inline');
  assert.strictEqual(pageNameFail.data.savingName, false, 'saving state resets after failure');

  console.log('home.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  global.setTimeout = originalSetTimeout;
  global.clearTimeout = originalClearTimeout;
  delete require.cache[pagePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
