const assert = require('assert');
const Module = require('module');

const inviteCodesPath = require.resolve('./invite-codes');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalGetApp = global.getApp;
const originalWx = global.wx;

let pageConfig;
let apiCalls = [];
let clipboardData = null;
let shouldReject = false;
let rejectValue = null;

const inviteApi = {
  getMine: async () => {
    apiCalls.push('getMine');
    if (shouldReject) throw rejectValue;
    return { code: 'EGG-ABCD', quota: 5, usedCount: 2, remaining: 3, status: 1 };
  }
};

Module._load = function (requestName, parent, isMain) {
  if (parent && parent.filename === inviteCodesPath && requestName === '../../utils/invite-api') {
    return inviteApi;
  }
  return originalLoad.call(this, requestName, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => ({ silentLogin: async () => {} });
global.wx = {
  setClipboardData: (options) => { clipboardData = options.data; }
};

function makePage() {
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

(async () => {
  require('./invite-codes');
  assert.ok(pageConfig, 'invite-codes page should be registered');
  assert.deepStrictEqual(
    pageConfig.data,
    { inviteCode: null, exhausted: false, loading: true, error: '' },
    'initial data should be set'
  );

  // 1. 正常加载：激活码 + 未用完
  shouldReject = false;
  apiCalls = [];
  const page = makePage();
  await page.onShow();
  assert.strictEqual(apiCalls.length, 1, 'onShow should call getMine');
  assert.strictEqual(page.data.loading, false, 'loading should be false after success');
  assert.strictEqual(page.data.error, '', 'error should be empty after success');
  assert.strictEqual(page.data.inviteCode.code, 'EGG-ABCD', 'code should be loaded');
  assert.strictEqual(page.data.inviteCode.quota, 5, 'quota should be loaded');
  assert.strictEqual(page.data.inviteCode.usedCount, 2, 'usedCount should be loaded');
  assert.strictEqual(page.data.inviteCode.remaining, 3, 'remaining should be loaded');
  assert.strictEqual(page.data.exhausted, false, 'remaining > 0 and status == 1 should not be exhausted');

  // 2. 已用完：remaining 为 0
  let usedPage = makePage();
  usedPage.loadInviteCode = async function () {
    this.setData({ loading: true, error: '' });
    const code = { code: 'EGG-USED', quota: 5, usedCount: 5, remaining: 0, status: 1 };
    this.setData({ inviteCode: code, exhausted: pageConfig.isExhausted(code), loading: false });
  };
  await usedPage.loadInviteCode();
  assert.strictEqual(usedPage.data.exhausted, true, 'remaining <= 0 should be exhausted');

  // 3. 失效：status 不为 1
  let pausedPage = makePage();
  pausedPage.loadInviteCode = async function () {
    this.setData({ loading: true, error: '' });
    const code = { code: 'EGG-PAUSED', quota: 5, usedCount: 1, remaining: 4, status: 0 };
    this.setData({ inviteCode: code, exhausted: pageConfig.isExhausted(code), loading: false });
  };
  await pausedPage.loadInviteCode();
  assert.strictEqual(pausedPage.data.exhausted, true, 'status !== 1 should be exhausted');

  // 4. 加载失败
  shouldReject = true;
  rejectValue = { userMessage: '登录状态已失效，请重新登录' };
  const errorPage = makePage();
  apiCalls = [];
  await errorPage.onShow();
  assert.strictEqual(errorPage.data.loading, false, 'loading should be false after error');
  assert.strictEqual(errorPage.data.inviteCode, null, 'inviteCode should be null on error');
  assert.strictEqual(errorPage.data.error, '登录状态已失效，请重新登录', 'error message should surface');

  // 5. 重试
  shouldReject = false;
  await errorPage.onRetry();
  assert.strictEqual(errorPage.data.error, '', 'error should clear after retry success');
  assert.strictEqual(errorPage.data.inviteCode.code, 'EGG-ABCD', 'retry should reload code');

  // 6. 复制
  const copyPage = makePage();
  shouldReject = false;
  await copyPage.onShow();
  clipboardData = null;
  copyPage.onCopy();
  assert.strictEqual(clipboardData, 'EGG-ABCD', 'copy should set clipboard to code');

  // 已用完不可复制
  usedPage = makePage();
  usedPage.loadInviteCode = async function () {
    this.setData({ loading: true, error: '' });
    const code = { code: 'EGG-USED', quota: 5, usedCount: 5, remaining: 0, status: 1 };
    this.setData({ inviteCode: code, exhausted: pageConfig.isExhausted(code), loading: false });
  };
  await usedPage.loadInviteCode();
  clipboardData = null;
  usedPage.onCopy();
  assert.strictEqual(clipboardData, null, 'exhausted code should not be copied');

  console.log('invite-codes.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  delete require.cache[inviteCodesPath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
