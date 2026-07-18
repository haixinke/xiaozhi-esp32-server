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

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired,
  markPhoneBound: () => markPhoneResult
};
const petStoreMock = {};
const requestMock = { get: async () => [] };
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
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  navigateTo({ url }) { navigatedTo = url; },
  reLaunch({ url }) { relaunchedTo = url; },
  showToast({ title }) { toastTitle = title; }
};

function makePage() {
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) {
      this.data = { ...this.data, ...changes };
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
  app.globalData = { authReady: null };
}

async function run() {
  const homeTemplate = fs.readFileSync(path.join(__dirname, 'home.wxml'), 'utf8');
  assert.ok(!homeTemplate.includes('<text class="state-time">{{countdown}}</text>'),
    'home must not reveal the hatching countdown');

  require('./home');
  assert.ok(pageConfig, 'home page should be registered');

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
