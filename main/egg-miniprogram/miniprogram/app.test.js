const assert = require('assert');
const Module = require('module');

const appPath = require.resolve('./app');
const originalLoad = Module._load;
const originalApp = global.App;
const originalWx = global.wx;

let appConfig;
let savedSession;
let storedSession;
let expired = true;
let expiringSoon = false;
let clearCalls = 0;
let wxLoginCalls = 0;
let postCalls = 0;
let wxLoginResult = { code: 'first-code' };

const auth = {
  getSession: () => storedSession,
  saveSession: (session) => {
    savedSession = { ...session };
    storedSession = savedSession;
    return savedSession;
  },
  clearSession: () => { clearCalls += 1; storedSession = null; },
  isExpired: () => expired,
  isExpiringSoon: () => expiringSoon
};

const request = {
  post: async (url, data, options) => {
    postCalls += 1;
    assert.strictEqual(url, '/wechat/login');
    assert.deepStrictEqual(data, { code: wxLoginResult.code });
    assert.deepStrictEqual(options, { anonymous: true });
    return {
      token: 'test-token', userId: 42, openid: 'test-openid',
      isNewUser: true, hasPhone: false, agentId: 7, expire: 7200
    };
  }
};

Module._load = function (requestPath, parent, isMain) {
  if (parent && parent.filename === appPath && requestPath === './utils/auth') return auth;
  if (parent && parent.filename === appPath && requestPath === './utils/request') return request;
  return originalLoad.call(this, requestPath, parent, isMain);
};

global.App = (config) => { appConfig = config; };
global.wx = {
  login(options) {
    wxLoginCalls += 1;
    if (wxLoginResult.fail) options.fail(wxLoginResult.fail);
    else options.success(wxLoginResult);
  }
};

async function run() {
  require('./app');
  assert.ok(appConfig, 'App should be registered');

  const first = appConfig.silentLogin.call(appConfig);
  const second = appConfig.silentLogin.call(appConfig);
  assert.strictEqual(first, second, 'concurrent login must share one Promise');
  const session = await first;
  assert.strictEqual(wxLoginCalls, 1);
  assert.strictEqual(postCalls, 1);
  assert.strictEqual(session.userId, 42);
  assert.strictEqual(appConfig.globalData.userId, 42);
  assert.strictEqual(savedSession.userId, 42);

  wxLoginResult = { fail: new Error('denied') };
  await assert.rejects(appConfig.silentLogin.call(appConfig), /微信登录失败/);
  wxLoginResult = { code: 'second-code' };
  await appConfig.silentLogin.call(appConfig);
  assert.strictEqual(wxLoginCalls, 3, 'lock must release after failure');

  const restored = {
    token: 'saved-token', userId: 9, openid: 'saved-openid',
    isNewUser: false, hasPhone: true, agentId: null, expire: 7200
  };
  storedSession = restored;
  expired = false;
  const callsBeforeRestore = wxLoginCalls;
  assert.strictEqual(await appConfig.ensureLogin.call(appConfig), restored);
  assert.strictEqual(wxLoginCalls, callsBeforeRestore, 'valid session should not call wx.login');
  assert.strictEqual(appConfig.globalData.token, 'saved-token');

  expiringSoon = true;
  wxLoginResult = { code: 'expiring-ensure-code' };
  const callsBeforeExpiringEnsure = wxLoginCalls;
  const refreshed = await appConfig.ensureLogin.call(appConfig);
  assert.strictEqual(wxLoginCalls, callsBeforeExpiringEnsure + 1,
    'expiring session should use silentLogin instead of restoring');
  assert.strictEqual(refreshed.userId, 42);

  expiringSoon = false;
  appConfig.onShow.call(appConfig);
  await Promise.resolve();
  assert.strictEqual(wxLoginCalls, callsBeforeExpiringEnsure + 1, 'fresh session should not refresh');
  expiringSoon = true;
  appConfig.onShow.call(appConfig);
  await appConfig._loginPromise;
  assert.strictEqual(wxLoginCalls, callsBeforeExpiringEnsure + 2, 'expiring session should refresh');

  storedSession = restored;
  expired = true;
  expiringSoon = false;
  appConfig.applySession.call(appConfig, restored);
  const clearsBeforeExpiredShow = clearCalls;
  const callsBeforeExpiredShow = wxLoginCalls;
  appConfig.onShow.call(appConfig);
  await Promise.resolve();
  assert.strictEqual(clearCalls, clearsBeforeExpiredShow + 1,
    'expired session should be removed from storage on show');
  assert.strictEqual(wxLoginCalls, callsBeforeExpiredShow,
    'expired session should not automatically log in on show');
  ['token', 'userId', 'openid', 'isNewUser', 'hasPhone', 'agentId'].forEach((key) => {
    assert.strictEqual(appConfig.globalData[key], null, `${key} should be cleared for expired session`);
  });

  storedSession = null;
  wxLoginResult = { fail: new Error('launch denied') };
  appConfig.applySession.call(appConfig, restored);
  const unhandledRejections = [];
  const onUnhandledRejection = (reason) => { unhandledRejections.push(reason); };
  process.on('unhandledRejection', onUnhandledRejection);
  appConfig.onLaunch.call(appConfig);
  assert.strictEqual(await appConfig.globalData.authReady, null,
    'failed launch login should resolve authReady to null');
  await new Promise((resolve) => setImmediate(resolve));
  process.removeListener('unhandledRejection', onUnhandledRejection);
  assert.deepStrictEqual(unhandledRejections, [], 'launch failure should not be unhandled');
  ['token', 'userId', 'openid', 'isNewUser', 'hasPhone', 'agentId'].forEach((key) => {
    assert.strictEqual(appConfig.globalData[key], null, `${key} should be cleared after launch failure`);
  });

  appConfig.clearLoginState.call(appConfig);
  assert.strictEqual(clearCalls, clearsBeforeExpiredShow + 2);
  ['token', 'userId', 'openid', 'isNewUser', 'hasPhone', 'agentId'].forEach((key) => {
    assert.strictEqual(appConfig.globalData[key], null, `${key} should be cleared`);
  });

  console.log('app.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.App = originalApp;
  global.wx = originalWx;
  delete require.cache[appPath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
