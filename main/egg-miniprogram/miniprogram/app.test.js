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
let relaunchedTo = null;
let relaunchCalls = 0;
let hideTabBarCalls = 0;
let currentRoute = 'pages/home/home';
const parsedEntryOptions = [];
const savedPendingContexts = [];

const shareInvite = {
  parseEntryOptions(options) {
    parsedEntryOptions.push(options);
    if (!options || !options.query || !options.query.inviteCode) return null;
    return { code: 'ABCDE', source: 'home_share', version: 1 };
  },
  savePending(context) {
    savedPendingContexts.push(context);
  }
};

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
  if (parent && parent.filename === appPath && requestPath === './utils/share-invite') return shareInvite;
  return originalLoad.call(this, requestPath, parent, isMain);
};

global.App = (config) => { appConfig = config; };
global.wx = {
  login(options) {
    wxLoginCalls += 1;
    if (wxLoginResult.fail) options.fail(wxLoginResult.fail);
    else options.success(wxLoginResult);
  },
  hideTabBar() { hideTabBarCalls += 1; },
  reLaunch({ url }) { relaunchedTo = url; relaunchCalls += 1; },
  nextTick(callback) { callback(); }
};
global.getCurrentPages = () => (currentRoute ? [{ route: currentRoute }] : []);

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

  const validShareEntry = {
    path: 'pages/home/home',
    query: { v: '1', source: 'home_share', inviteCode: 'ABCDE' }
  };
  appConfig.onLaunch.call(appConfig, validShareEntry);
  await appConfig.globalData.authReady;
  assert.deepStrictEqual(parsedEntryOptions.at(-1), validShareEntry,
    'launch should parse the entry options before login continues');
  assert.deepStrictEqual(savedPendingContexts.at(-1),
    { code: 'ABCDE', source: 'home_share', version: 1 },
    'a valid launch share context should be saved without entering login data');

  appConfig.onShow.call(appConfig, validShareEntry);
  assert.deepStrictEqual(parsedEntryOptions.at(-1), validShareEntry,
    'show should also parse share entry options');
  assert.deepStrictEqual(savedPendingContexts.at(-1),
    { code: 'ABCDE', source: 'home_share', version: 1 },
    'a valid show share context should be saved');

  storedSession = { ...restored, hasPhone: false };
  relaunchedTo = null;
  relaunchCalls = 0;
  currentRoute = 'pages/home/home';
  appConfig.onLaunch.call(appConfig, { path: 'pages/home/home' });
  await appConfig.globalData.authReady;
  assert.strictEqual(hideTabBarCalls, 2,
    'home launch hides the native tab bar before the pet state is restored');
  assert.strictEqual(relaunchedTo, '/pages/welcome/welcome',
    'unbound session should launch into welcome before claiming a pet');
  assert.strictEqual(relaunchCalls, 1,
    'launch should schedule only one welcome redirect');

  appConfig.onShow.call(appConfig);
  assert.strictEqual(relaunchedTo, '/pages/welcome/welcome',
    'in-flight launch redirect remains the welcome destination');
  assert.strictEqual(relaunchCalls, 1,
    'onShow should not duplicate an in-flight welcome redirect');

  relaunchedTo = null;
  currentRoute = 'pages/welcome/welcome';
  appConfig.globalData.welcomeCompleted = false;
  appConfig.onShow.call(appConfig);
  assert.strictEqual(relaunchedTo, null,
    'welcome route should not redirect itself');
  assert.strictEqual(appConfig._welcomeRedirecting, false,
    'welcome route should release the redirect lock');

  appConfig.globalData.welcomeCompleted = true;
  currentRoute = 'pages/home/home';
  appConfig.onShow.call(appConfig);
  assert.strictEqual(relaunchedTo, null,
    'completed welcome should not redirect after entering home');

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
