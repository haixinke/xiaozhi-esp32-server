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
let pendingInvite = null;

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired
};

const shareInvite = {
  getPending: () => pendingInvite
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
    if (request === '../../utils/share-invite') return shareInvite;
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
  pendingInvite = null;
  app.globalData = { userId: null, hasPhone: null, welcomeCompleted: false };
}

async function run() {
  require('./welcome');
  assert.ok(pageConfig, 'welcome page should be registered');

  // 同步路径：本地已有有效未绑定会话停留欢迎页，不调用 ensureLogin
  resetScenario();
  cachedSession = { userId: 42, hasPhone: false };
  cachedExpired = false;
  const syncPage = makePage();
  await syncPage.onLoad();
  assert.strictEqual(switchedTo, null, 'cached valid unbound session stays on welcome');
  assert.strictEqual(ensureCalls, 0, 'synchronous path should not call ensureLogin');
  assert.strictEqual(syncPage.data.ready, true, 'ready is rendered for valid unbound session');
  assert.strictEqual(syncPage.data.hasPendingInvite, false,
    'welcome does not show an invite message without a pending share');

  // 异步路径：本地无 session，静默登录后未绑定手机号 → 停留欢迎页
  resetScenario();
  ensureSession = { userId: 42, hasPhone: false };
  pendingInvite = { code: 'ABCDE', source: 'home_share', version: 1, receivedAt: 1000 };
  const asyncUnboundPage = makePage();
  await asyncUnboundPage.onLoad();
  assert.strictEqual(switchedTo, null, 'user after silent login without phone stays on welcome');
  assert.strictEqual(asyncUnboundPage.data.ready, true, 'ready set to true for async login');
  assert.strictEqual(asyncUnboundPage.data.hasPendingInvite, true,
    'welcome shows its invite message when a pending share exists');

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
  assert.strictEqual(app.globalData.welcomeCompleted, true, 'entry button marks welcome as completed');

  console.log('welcome.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  Date.now = originalNow;
});
