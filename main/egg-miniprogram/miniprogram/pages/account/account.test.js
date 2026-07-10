const assert = require('assert');
const Module = require('module');

const accountPath = require.resolve('./account');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalGetApp = global.getApp;
const originalWx = global.wx;

let pageConfig;
let callOrder = [];
let relaunchTo = null;
let modalResult = { confirm: false };

const petStore = {
  clearUser() { callOrder.push('clearUser'); },
  clearAccountData() { callOrder.push('clearAccountData'); },
  resetDemo() { callOrder.push('resetDemo'); }
};

const app = {
  clearLoginState() { callOrder.push('clearLoginState'); }
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === accountPath && request === '../../utils/pet-store') {
    return petStore;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  showModal(options) {
    if (options && options.success) options.success(modalResult);
  },
  reLaunch({ url }) { relaunchTo = url; callOrder.push(`reLaunch:${url}`); },
  switchTab() {},
  showToast() {},
  navigateTo() {}
};

function makePage() {
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

async function run() {
  require('./account');
  assert.ok(pageConfig, 'account page should be registered');

  modalResult = { confirm: true };
  callOrder = [];
  relaunchTo = null;
  const page = makePage();
  page.onLogout();
  assert.ok(callOrder.indexOf('clearLoginState') < callOrder.indexOf('clearAccountData'),
    'auth cleanup must precede account data cleanup');
  assert.ok(callOrder.indexOf('clearAccountData') < callOrder.indexOf('reLaunch:/pages/welcome/welcome'),
    'account data cleanup must precede relaunch');
  assert.strictEqual(relaunchTo, '/pages/welcome/welcome');
  assert.ok(!callOrder.includes('clearUser'), 'logout must not call legacy clearUser');

  modalResult = { confirm: false };
  callOrder = [];
  relaunchTo = null;
  const cancelPage = makePage();
  cancelPage.onLogout();
  assert.deepStrictEqual(callOrder, [], 'cancel must perform no cleanup');
  assert.strictEqual(relaunchTo, null, 'cancel must not relaunch');

  console.log('account.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  delete require.cache[accountPath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
