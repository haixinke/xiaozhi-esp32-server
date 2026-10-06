const assert = require('assert');
const Module = require('module');

// app.js redirectUnboundToWelcome 行为测试：
// 公开页白名单（welcome/postcard）不受"未绑手机号踢回 welcome"约束。
// 好友点开明信片分享卡片时无登录态/未绑手机号，页面必须留在原地。

let reLaunchUrls = [];
global.wx = {
  reLaunch(options) { reLaunchUrls.push(options.url); },
  nextTick(fn) { fn(); }
};

const originalLoad = Module._load;
Module._load = function (request) {
  if (request === './utils/auth') return { getSession: () => ({ token: 't', userId: 1 }) };
  if (request === './utils/request') return { post: () => Promise.resolve({}) };
  if (request === './utils/nfc-claim-intent') return { captureNfcClaimIntent: () => {} };
  if (request === './utils/share-invite') return { parseEntryOptions: () => null, savePending: () => {} };
  return originalLoad.apply(this, arguments);
};

const path = require('path');
const appModulePath = path.join(__dirname, '..', 'app.js');
// 抓取 App({...}) 注册的配置对象：测试环境无 App 全局，注入收集桩
global.App = (config) => { global.__lastAppConfig = config; };
require(appModulePath);
const appConfig = global.__lastAppConfig;
assert.ok(appConfig && typeof appConfig.redirectUnboundToWelcome === 'function', 'app config captured');

const app = {
  globalData: { welcomeCompleted: false, hasPhone: false, launchPath: 'pages/home/home' },
  _welcomeRedirecting: false
};

function bind(method) {
  return appConfig[method].bind(app);
}

(async () => {
  // 未绑手机号 + 普通页 → 踢回 welcome
  reLaunchUrls = [];
  bind('redirectUnboundToWelcome')('pages/home/home');
  assert.strictEqual(reLaunchUrls.length, 1, 'home should be redirected to welcome');
  assert.strictEqual(reLaunchUrls[0], '/pages/welcome/welcome');

  // 未绑手机号 + 明信片页（带不带前导斜杠都要豁免）→ 不劫持
  reLaunchUrls = [];
  app._welcomeRedirecting = false;
  bind('redirectUnboundToWelcome')('pages/postcard/postcard');
  assert.strictEqual(reLaunchUrls.length, 0, 'postcard must not be redirected');
  assert.strictEqual(app._welcomeRedirecting, false, 'flag must be reset for postcard');

  bind('redirectUnboundToWelcome')('/pages/postcard/postcard');
  assert.strictEqual(reLaunchUrls.length, 0, 'postcard with leading slash must not be redirected');

  // 已绑手机号 → 任何页都不动
  app.globalData.hasPhone = true;
  reLaunchUrls = [];
  bind('redirectUnboundToWelcome')('pages/home/home');
  assert.strictEqual(reLaunchUrls.length, 0, 'bound user should stay');

  console.log('app-redirect.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
