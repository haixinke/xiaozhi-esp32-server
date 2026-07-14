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
let ensureSession = null;
let cachedSession = null;
let cachedExpired = false;
let markPhoneResult = null;
let bindPhoneCalled = false;

const petStore = {
  saveUser: (user) => { savedUser = user; }
};

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired,
  markPhoneBound: () => markPhoneResult
};

const wechatApiMock = {
  bindPhone: async () => { bindPhoneCalled = true; }
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
    if (request === '../../utils/auth') return authMock;
    if (request === '../../utils/wechat-api') return wechatApiMock;
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
  ensureSession = null;
  cachedSession = null;
  cachedExpired = false;
  markPhoneResult = null;
  bindPhoneCalled = false;
  app.globalData = { userId: null, hasPhone: null };
}

async function run() {
  require('./welcome');
  assert.ok(pageConfig, 'welcome page should be registered');

  // 同步路径：本地已有有效登录态且已绑定手机号，直接跳转首页，不调用 ensureLogin
  // ready 保持 false，欢迎页内容不渲染，避免闪烁
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  cachedExpired = false;
  const syncPage = makePage();
  await syncPage.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'cached valid session skips welcome synchronously');
  assert.strictEqual(ensureCalls, 0, 'synchronous path should not call ensureLogin');
  assert.strictEqual(syncPage.data.ready, false, 'ready stays false for registered user');

  // 同步路径：本地有 session 但已过期，降级到异步登录，ready 设为 true
  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  cachedExpired = true;
  ensureSession = { userId: 42, hasPhone: true };
  const expiredPage = makePage();
  await expiredPage.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'expired cached session falls through to async login');
  assert.strictEqual(ensureCalls, 1, 'expired session should call ensureLogin');
  assert.strictEqual(expiredPage.data.ready, true, 'ready set to true for expired session');

  // 异步路径：本地无 session，静默登录后已绑定手机号 → 跳转首页
  resetScenario();
  cachedSession = null;
  ensureSession = { userId: 42, hasPhone: true };
  const asyncPhonePage = makePage();
  await asyncPhonePage.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'user after silent login with phone enters home');
  assert.strictEqual(asyncPhonePage.data.ready, true, 'ready set to true for async login');

  // 异步路径：本地无 session，静默登录后未绑定手机号 → 留在欢迎页
  resetScenario();
  cachedSession = null;
  ensureSession = { userId: 42, hasPhone: false };
  const noPhonePage = makePage();
  await noPhonePage.onLoad();
  assert.strictEqual(switchedTo, null, 'user without phone binding stays on welcome');
  assert.strictEqual(noPhonePage.data.ready, true, 'ready set to true for user without phone');

  // onAuthorize: 未勾选隐私协议
  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  const uncheckedPage = makePage();
  await uncheckedPage.onAuthorize();
  assert.strictEqual(switchedTo, null);
  assert.strictEqual(toasts.at(-1), '请先阅读并同意隐私政策');

  // onAuthorize: 成功授权手机号
  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  markPhoneResult = { userId: 42, hasPhone: true };
  const fixedNow = 1_725_000_000_000;
  Date.now = () => fixedNow;
  const successPage = makePage();
  successPage.setData({ agreed: true });
  await successPage.onAuthorize({ detail: { code: 'test-phone-code' } });
  assert.strictEqual(bindPhoneCalled, true, 'bindPhone should be called');
  assert.deepStrictEqual(savedUser, {
    id: 42,
    nickname: '蛋友',
    avatarUrl: '',
    authorizedAt: fixedNow
  });
  assert.strictEqual(switchedTo, '/pages/home/home');
  assert.strictEqual(successPage.data.authorizing, false);

  // onAuthorize: ensureLogin 返回 null
  resetScenario();
  ensureSession = null;
  const noSessionPage = makePage();
  noSessionPage.setData({ agreed: true });
  await noSessionPage.onAuthorize({ detail: { code: 'test-code' } });
  assert.strictEqual(switchedTo, null);
  assert.strictEqual(toasts.at(-1), '暂时无法连接服务，请稍后重试');

  // onAuthorize: 并发调用只触发一次 ensureLogin
  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  markPhoneResult = { userId: 42, hasPhone: true };
  const concurrentPage = makePage();
  concurrentPage.setData({ agreed: true });
  const first = concurrentPage.onAuthorize({ detail: { code: 'c1' } });
  const second = concurrentPage.onAuthorize({ detail: { code: 'c2' } });
  await Promise.all([first, second]);
  assert.strictEqual(ensureCalls, 1, 'concurrent taps trigger login only once');

  console.log('welcome.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  Date.now = originalNow;
});
