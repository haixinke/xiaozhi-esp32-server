// 用真实认证、请求、OTA 与门槛模块验证旧响应不污染新的绑定或账号状态。
const assert = require('node:assert/strict');
const Module = require('module');
const originalLoad = Module._load;
const originals = Object.fromEntries(['wx', 'getApp', 'getCurrentPages'].map((key) => [key, global[key]]));
const storage = new Map();
const requests = [];
let restorePromise;
let restoredPets = [];
const app = { globalData: {}, ensureLogin: async () => auth.getSession() };
Module._load = function (request, parent, isMain) {
  if (request.endsWith('/config/api')) return { API_BASE_URL: 'https://fixture.example' };
  if (request.endsWith('/pet-store')) return {
    refreshPets: async () => restorePromise || { pets: restoredPets },
    getPetSnapshot: () => ({ pets: restoredPets })
  };
  return originalLoad.call(this, request, parent, isMain);
};
global.wx = {
  getStorageSync: (key) => storage.get(key) || '',
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  request: (options) => requests.push(options)
};
global.getApp = () => app;
global.getCurrentPages = () => [{ route: 'pages/home/home', syncPhoneAuthorization() {} }];
const auth = require('../miniprogram/utils/auth');
const api = require('../miniprogram/utils/request');
const ota = require('../miniprogram/utils/ota');
function login(userId, hasPhone) {
  auth.saveSession({ token: 'fixture-session', userId, openid: 'fixture-openid', expire: 7200, hasPhone });
  app.globalData.phoneBindingRequired = false;
}
function respond(options, code, data = null) {
  options.success({ statusCode: 200, data: { code, data } });
}
async function bindPhone() {
  const binding = api.post('/wechat/bindPhone', { phoneCode: 'fixture-code' });
  respond(requests.at(-1), 0);
  await binding;
  auth.markPhoneBound();
  app.globalData.phoneBindingRequired = false;
}

(async () => {
  login('account-a', false);
  const staleRequest = api.get('/invite/mine');
  const staleOptions = requests.at(-1);
  await bindPhone();
  respond(staleOptions, 10228);
  await assert.rejects(staleRequest, (error) => error.code === 10228);
  assert.equal(app.globalData.phoneBindingRequired, false, 'an old 10228 cannot re-lock a successfully bound account');

  const currentRequest = api.get('/star/balance');
  respond(requests.at(-1), 10228);
  await assert.rejects(currentRequest, (error) => error.code === 10228);
  assert.equal(app.globalData.phoneBindingRequired, true, 'current server rejection still overrides a stale local bound flag');

  login('account-a', false);
  const otherAccountRequest = api.get('/invite/mine');
  const otherAccountOptions = requests.at(-1);
  auth.clearSession();
  login('account-b', false);
  respond(otherAccountOptions, 10228);
  await assert.rejects(otherAccountRequest, (error) => error.code === 10228);
  assert.equal(app.globalData.phoneBindingRequired, false, 'an old account response cannot lock the new account');

  const staleOta = ota.checkOrRegisterDevice('fixture-device');
  const staleOtaOptions = requests.at(-1);
  await bindPhone();
  respond(staleOtaOptions, 10228);
  await assert.rejects(staleOta, (error) => error.code === 10228);
  assert.equal(app.globalData.phoneBindingRequired, false, 'old OTA rejection cannot undo a confirmed binding');

  const currentOta = ota.checkOrRegisterDevice('fixture-device');
  respond(requests.at(-1), 10228);
  await assert.rejects(currentOta, (error) => error.code === 10228);
  assert.equal(app.globalData.phoneBindingRequired, true, 'current OTA rejection still enforces the server gate');

  login('account-c', false);
  let resolveRestore;
  restorePromise = new Promise((resolve) => { resolveRestore = resolve; });
  let pageLoads = 0;
  const gate = require('../miniprogram/utils/phone-gate');
  const waitingPage = gate.withPhoneGate({ onLoad() { pageLoads += 1; } });
  const waitingLoad = waitingPage.onLoad();
  await Promise.resolve();
  await bindPhone();
  resolveRestore({ pets: [{ id: 'old-hatched', hatchStatus: 'HATCHED' }] });
  await waitingLoad;
  assert.equal(app.globalData.phoneBindingRequired, false, 'an old pet restoration cannot undo a confirmed binding');
  assert.equal(pageLoads, 1, 'the pending gate re-evaluates the confirmed bound session');

  login('account-c', false);
  restorePromise = new Promise((resolve) => { resolveRestore = resolve; });
  const oldAccountPage = gate.withPhoneGate({ onLoad() { pageLoads += 1; } });
  const oldAccountLoad = oldAccountPage.onLoad();
  await Promise.resolve();
  auth.clearSession();
  login('account-d', false);
  restoredPets = [];
  const resolveOldAccount = resolveRestore;
  restorePromise = null;
  resolveOldAccount({ pets: [{ id: 'old-account-hatched', hatchStatus: 'HATCHED' }] });
  await oldAccountLoad;
  assert.equal(app.globalData.phoneBindingRequired, false, 'an old account pet snapshot cannot lock the new account');
  console.log('phone-gate-races.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  Object.entries(originals).forEach(([key, value]) => { global[key] = value; });
}).catch((error) => { console.error(error); process.exitCode = 1; });
