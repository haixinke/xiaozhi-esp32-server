const assert = require('assert');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const homePath = require.resolve('./home');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalGetApp = global.getApp;
const originalWx = global.wx;

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
  getStage: (pet) => (pet.hatchStatus === 'HATCHED' ? 'hatched' : 'waiting'),
  getStagePresentation: () => ({ homeText: '', actionLabel: '' }),
  getCountdown: () => '',
  getDailyStatus: () => null
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
  if (parent && parent.filename === homePath) {
    if (request === '../../utils/auth') return authMock;
    if (request === '../../utils/pet-store') return petStoreMock;
    if (request === '../../utils/wechat-api') return wechatApiMock;
    if (request === '../../utils/request') return requestMock;
    if (request === '../../utils/invite-api') return inviteApiMock;
    if (request === '../../utils/share-invite') return shareInviteMock;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  navigateTo({ url }) { navigatedTo = url; },
  reLaunch({ url }) { relaunchedTo = url; },
  showTabBar() { showTabBarCalls += 1; },
  showToast({ title }) { toastTitle = title; }
};

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

  require('./home');
  assert.ok(pageConfig, 'home page should be registered');

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
  assert.strictEqual(showTabBarCalls, 1,
    'native tab bar appears only after the server pet state is restored');

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

  console.log('home.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  delete require.cache[homePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
