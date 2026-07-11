const assert = require('assert');
const Module = require('module');

const welcomePath = require.resolve('./welcome');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalGetApp = global.getApp;
const originalWx = global.wx;
const originalNow = Date.now;

let pageConfig;
let savedUser;
let ensureCalls = 0;
let switchedTo = null;
let toasts = [];
let bindCalls = [];
let bindError = null;
let bindResolver = null;
let ensureSession = null;
let markedSession = null;

const petStore = {
  saveUser: (user) => { savedUser = user; }
};

const auth = {
  markPhoneBound() {
    markedSession = { ...ensureSession, hasPhone: true };
    return markedSession;
  }
};

const wechatApi = {
  bindPhone(phoneCode) {
    bindCalls.push(phoneCode);
    if (bindError) return Promise.reject(bindError);
    if (bindResolver) return new Promise((resolve) => { bindResolver = resolve; });
    return Promise.resolve({ phone: '138****8000' });
  }
};

const app = {
  globalData: { userId: null, hasPhone: null },
  async ensureLogin() {
    ensureCalls += 1;
    return ensureSession;
  },
  applySession(session) {
    this.globalData.userId = session ? session.userId : null;
    this.globalData.hasPhone = session ? session.hasPhone : null;
  }
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === welcomePath) {
    if (request === '../../utils/pet-store') return petStore;
    if (request === '../../utils/auth') return auth;
    if (request === '../../utils/wechat-api') return wechatApi;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  switchTab({ url }) { switchedTo = url; },
  showToast({ title }) { toasts.push(title); },
  navigateTo() {}
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
  savedUser = undefined;
  ensureCalls = 0;
  switchedTo = null;
  toasts = [];
  bindCalls = [];
  bindError = null;
  bindResolver = null;
  ensureSession = null;
  markedSession = null;
  app.globalData = { userId: null, hasPhone: null };
}

async function run() {
  require('./welcome');
  assert.ok(pageConfig, 'welcome page should be registered');

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  await makePage().onLoad();
  assert.strictEqual(switchedTo, null, 'unbound user must remain on welcome');

  resetScenario();
  ensureSession = { userId: 42, hasPhone: true };
  await makePage().onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'bound returning user enters home');

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  const uncheckedPage = makePage();
  await uncheckedPage.onAuthorize({ detail: { code: 'phone-code' } });
  assert.deepStrictEqual(bindCalls, []);
  assert.strictEqual(switchedTo, null);
  assert.strictEqual(toasts.at(-1), '请先阅读并同意隐私政策');

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  const rejectedPage = makePage();
  rejectedPage.setData({ agreed: true });
  await rejectedPage.onAuthorize({ detail: { errMsg: 'getPhoneNumber:fail user deny' } });
  assert.deepStrictEqual(bindCalls, []);
  assert.strictEqual(switchedTo, null);
  assert.strictEqual(toasts.at(-1), '需要授权手机号后才能使用蛋宝宝');

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  const fixedNow = 1_725_000_000_000;
  Date.now = () => fixedNow;
  const successPage = makePage();
  successPage.setData({ agreed: true });
  await successPage.onAuthorize({ detail: { code: 'phone-code' } });
  assert.deepStrictEqual(bindCalls, ['phone-code']);
  assert.strictEqual(ensureCalls, 1);
  assert.strictEqual(markedSession.hasPhone, true);
  assert.strictEqual(app.globalData.hasPhone, true);
  assert.deepStrictEqual(savedUser, {
    id: 42,
    nickname: '蛋友',
    avatarUrl: '',
    authorizedAt: fixedNow
  });
  assert.strictEqual(switchedTo, '/pages/home/home');
  assert.strictEqual(successPage.data.authorizing, false);

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  bindError = { userMessage: '手机号绑定失败，请重试' };
  const failedPage = makePage();
  failedPage.setData({ agreed: true });
  await failedPage.onAuthorize({ detail: { code: 'failed-code' } });
  assert.strictEqual(switchedTo, null);
  assert.strictEqual(failedPage.data.authorizing, false);
  assert.strictEqual(toasts.at(-1), '手机号绑定失败，请重试');

  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  const concurrentPage = makePage();
  concurrentPage.setData({ agreed: true });
  let finishBind;
  wechatApi.bindPhone = (phoneCode) => {
    bindCalls.push(phoneCode);
    return new Promise((resolve) => { finishBind = resolve; });
  };
  const first = concurrentPage.onAuthorize({ detail: { code: 'once' } });
  const second = concurrentPage.onAuthorize({ detail: { code: 'twice' } });
  await Promise.resolve();
  await Promise.resolve();
  assert.deepStrictEqual(bindCalls, ['once'], 'concurrent taps bind only once');
  finishBind({ phone: '138****8000' });
  await Promise.all([first, second]);

  console.log('welcome.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  Date.now = originalNow;
});
