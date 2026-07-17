const assert = require('assert');
const Module = require('module');

const welcomePath = require.resolve('./welcome');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalGetApp = global.getApp;
const originalWx = global.wx;
const originalNow = Date.now;

let pageConfig;
let ensureCalls = 0;
let switchedTo = null;
let ensureSession = null;
let cachedSession = null;
let cachedExpired = false;

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired
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
    if (request === '../../utils/auth') return authMock;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  switchTab({ url }) { switchedTo = url; }
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
  ensureCalls = 0;
  switchedTo = null;
  ensureSession = null;
  cachedSession = null;
  cachedExpired = false;
  app.globalData = { userId: null, hasPhone: null };
}

async function run() {
  require('./welcome');
  assert.ok(pageConfig, 'welcome page should be registered');

  // 同步路径：本地已有有效未绑定会话也直接跳转首页，不调用 ensureLogin
  // ready 保持 false，欢迎页内容不渲染，避免闪烁
  resetScenario();
  cachedSession = { userId: 42, hasPhone: false };
  cachedExpired = false;
  const syncPage = makePage();
  await syncPage.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'cached valid unbound session skips welcome synchronously');
  assert.strictEqual(ensureCalls, 0, 'synchronous path should not call ensureLogin');
  assert.strictEqual(syncPage.data.ready, false, 'ready stays false for valid session');

  // 异步路径：本地无 session，静默登录后未绑定手机号 → 跳转首页
  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  const asyncUnboundPage = makePage();
  await asyncUnboundPage.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'user after silent login without phone enters home');
  assert.strictEqual(asyncUnboundPage.data.ready, true, 'ready set to true for async login');

  // 异步路径：本地无 session，静默登录后已绑定手机号 → 跳转首页
  resetScenario();
  ensureSession = { userId: 42, hasPhone: true };
  const asyncPhonePage = makePage();
  await asyncPhonePage.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'user after silent login with phone enters home');
  assert.strictEqual(asyncPhonePage.data.ready, true, 'ready set to true for async login');

  // 点击入口直接进入首页
  resetScenario();
  const entered = makePage();
  entered.onEnterIsland();
  assert.strictEqual(switchedTo, '/pages/home/home', 'entry button enters home');

  console.log('welcome.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  Date.now = originalNow;
});
