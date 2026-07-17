const assert = require('assert');
const Module = require('module');

const pagePath = require.resolve('./add-device');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalWx = global.wx;

let pageConfig;
let cachedSession = null;
let cachedExpired = false;
let relaunchedTo = null;

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/auth') return authMock;
    if (request === '../../utils/request') return { post: async () => ({}) };
    if (request === '../../utils/pet-store') return {};
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.wx = {
  reLaunch({ url }) { relaunchedTo = url; }
};

function resetScenario() {
  cachedSession = null;
  cachedExpired = false;
  relaunchedTo = null;
}

function makePage() {
  return { ...pageConfig, data: { ...pageConfig.data } };
}

function run() {
  require('./add-device');
  assert.ok(pageConfig, 'add-device page should be registered');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  makePage().onLoad();
  assert.strictEqual(relaunchedTo, null, 'bound users can enter add-device directly');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: false };
  makePage().onLoad();
  assert.strictEqual(relaunchedTo, '/pages/home/home', 'unbound users return home for phone authorization');

  resetScenario();
  makePage().onLoad();
  assert.strictEqual(relaunchedTo, '/pages/home/home', 'users without a session return home');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  cachedExpired = true;
  makePage().onLoad();
  assert.strictEqual(relaunchedTo, '/pages/home/home', 'expired sessions return home');

  console.log('add-device.test.js: ALL PASS');
}

try {
  run();
} finally {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.wx = originalWx;
  delete require.cache[pagePath];
}
