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
let cachedUser = null;
let loginCalls = 0;
let ensureCalls = 0;
let profileCalls = 0;
let switchedTo = null;
let toasts = [];
let loginError = null;
let loginSession = { userId: 42 };
let ensureSession = null;

const petStore = {
  getUser: () => cachedUser,
  saveUser: (user) => { savedUser = user; }
};

const app = {
  globalData: { userId: null },
  async ensureLogin() {
    ensureCalls += 1;
    return ensureSession;
  },
  async silentLogin() {
    loginCalls += 1;
    if (loginError) throw loginError;
    this.globalData.userId = loginSession.userId;
    return loginSession;
  }
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === welcomePath && request === '../../utils/pet-store') {
    return petStore;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  getUserProfile() { profileCalls += 1; },
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

async function run() {
  require('./welcome');
  assert.ok(pageConfig, 'welcome page should be registered');

  const fixedNow = 1_725_000_000_000;
  Date.now = () => fixedNow;
  const page = makePage();

  page.onAuthorize();
  assert.strictEqual(loginCalls, 0);
  assert.strictEqual(toasts.at(-1), '请先阅读并同意隐私政策');

  page.setData({ agreed: true });
  await page.onAuthorize();
  assert.deepStrictEqual(savedUser, {
    id: 42,
    nickname: '蛋友',
    avatarUrl: '',
    authorizedAt: fixedNow
  });
  assert.strictEqual(switchedTo, '/pages/home/home');
  assert.strictEqual(profileCalls, 0);

  loginError = new Error('network');
  switchedTo = null;
  await page.onAuthorize();
  assert.strictEqual(switchedTo, null);
  assert.strictEqual(page.data.authorizing, false);
  assert.strictEqual(toasts.at(-1), '暂时无法连接服务，请稍后重试');

  loginError = null;
  loginSession = {};
  switchedTo = null;
  await page.onAuthorize();
  assert.strictEqual(switchedTo, null, 'session without userId must not enter home');
  assert.strictEqual(toasts.at(-1), '暂时无法连接服务，请稍后重试');

  cachedUser = { id: 'stale-user' };
  ensureSession = null;
  switchedTo = null;
  const stalePage = makePage();
  await stalePage.onLoad();
  assert.strictEqual(ensureCalls, 1);
  assert.strictEqual(switchedTo, null, 'cached profile alone must not enter home');

  ensureSession = { userId: 42 };
  await stalePage.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home');

  console.log('welcome.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  Date.now = originalNow;
});
