const assert = require('assert');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const pagePath = require.resolve('./add-device');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalWx = global.wx;
const originalSetTimeout = global.setTimeout;

let pageConfig;
let cachedSession = null;
let cachedExpired = false;
let relaunchedTo = null;
let pendingInvite = null;
let postCalls = 0;
let postData = null;
let postError = null;
let clearPendingCalls = 0;

const authMock = {
  getSession: () => cachedSession,
  isExpired: () => cachedExpired
};
const shareInviteMock = {
  getPending: () => pendingInvite,
  clearPending: () => { clearPendingCalls += 1; }
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/auth') return authMock;
    if (request === '../../utils/request') return {
      post: async (url, data) => {
        postCalls += 1;
        postData = data;
        if (postError) throw postError;
        return { prototype: '玉兔' };
      }
    };
    if (request === '../../utils/pet-store') return { savePetFromVO() {} };
    if (request === '../../utils/share-invite') return shareInviteMock;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.wx = {
  reLaunch({ url }) { relaunchedTo = url; },
  switchTab() {}
};
global.setTimeout = (callback) => {
  callback();
  return 0;
};

function resetScenario() {
  cachedSession = null;
  cachedExpired = false;
  relaunchedTo = null;
  pendingInvite = null;
  postCalls = 0;
  postData = null;
  postError = null;
  clearPendingCalls = 0;
}

function makePage() {
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

async function run() {
  const template = fs.readFileSync(path.join(__dirname, 'add-device.wxml'), 'utf8');
  assert.ok(template.includes('wx:if="{{sharedInvite}}"'),
    'shared invite users see an explicit confirmation message');
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

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  pendingInvite = { code: 'ABCDE', source: 'home_share', version: 1, receivedAt: 1 };
  const invitePage = makePage();
  invitePage.onLoad();
  assert.strictEqual(invitePage.data.code, 'ABCDE', 'shared invite code is prefilled');
  assert.strictEqual(invitePage.data.canSubmit, true, 'prefilled shared invite can be submitted');
  assert.strictEqual(postCalls, 0, 'prefilling never automatically adopts a pet');
  assert.strictEqual(clearPendingCalls, 0, 'prefilling keeps the invite until successful adoption');

  await invitePage.onValidate();
  assert.strictEqual(postCalls, 1, 'explicit confirmation adopts the pet once');
  assert.deepStrictEqual(postData, { inviteCode: 'ABCDE' });
  assert.strictEqual(clearPendingCalls, 1, 'successful shared-code adoption clears the pending invite');

  resetScenario();
  cachedSession = { userId: 42, hasPhone: true };
  pendingInvite = { code: 'ABCDE', source: 'home_share', version: 1, receivedAt: 1 };
  const editedInvitePage = makePage();
  editedInvitePage.onLoad();
  editedInvitePage.onCodeInput({ detail: { value: 'MANUAL999' } });
  assert.strictEqual(clearPendingCalls, 1, 'editing a shared code discards the pending invite');
  assert.strictEqual(editedInvitePage.data.sharedInvite, false, 'edited codes are treated as manual input');

  console.log('add-device.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.wx = originalWx;
  global.setTimeout = originalSetTimeout;
  delete require.cache[pagePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
