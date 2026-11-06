const assert = require('assert');
const Module = require('module');

const gatePath = require.resolve('./phone-gate');
const originalLoad = Module._load;
const originalGetApp = global.getApp;
const originalWx = global.wx;
let session = { userId: 42, hasPhone: false };
let pets = [];
let refreshError = null;
let refreshPromise = null;
let refreshCalls = 0;
let homeRedirects = 0;
let pageLoads = 0;
let pageShows = 0;
let pageActions = 0;
let cleanupCalls = 0;
let gate;
const app = { globalData: {}, ensureLogin: async () => session };

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === gatePath && request === './auth') return {
    getPhoneBindingContext: () => ({ userId: session.userId, generation: 0 }),
    isPhoneBindingContextCurrent: () => true
  };
  if (parent && parent.filename === gatePath && request === './pet-store') return {
    refreshPets: async () => {
      refreshCalls += 1;
      if (refreshPromise) return refreshPromise;
      if (refreshError) throw refreshError;
      return { pets };
    },
    getPetSnapshot: () => ({ pets })
  };
  return originalLoad.call(this, request, parent, isMain);
};
global.getApp = () => app;
global.wx = { reLaunch({ url }) { assert.strictEqual(url, '/pages/home/home'); homeRedirects += 1; } };

function makePage() {
  return gate.withPhoneGate({
    onLoad() { pageLoads += 1; },
    onShow() { pageShows += 1; },
    onTap() { pageActions += 1; },
    release() { cleanupCalls += 1; },
    onHide() { this.release(); },
    onUnload() { this.release(); }
  });
}

async function run() {
  gate = require('./phone-gate');
  pets = [{ id: 'hatched-pet', hatchStatus: 'HATCHED' }];
  const blocked = makePage();
  await blocked.onLoad();
  await blocked.onShow();
  blocked.onTap();
  assert.strictEqual(pageLoads, 0, 'blocked pages never request business data from onLoad');
  assert.strictEqual(pageShows, 0, 'blocked pages never request business data from onShow');
  assert.strictEqual(pageActions, 0, 'events cannot bypass a pending or rejected gate');
  assert.strictEqual(homeRedirects, 1, 'a blocked page returns home only once');
  assert.strictEqual(app.globalData.phoneBindingRequired, true);

  app.globalData.phoneBindingRequired = false;
  session = { userId: 42, hasPhone: true };
  const bound = makePage();
  await bound.onLoad();
  await bound.onShow();
  bound.onTap();
  assert.deepStrictEqual([pageLoads, pageShows, pageActions], [1, 1, 1]);
  bound.onHide();
  bound.onUnload();
  assert.strictEqual(cleanupCalls, 2, 'unload still runs cleanup helpers after the page was hidden');

  session = { userId: 42, hasPhone: false };
  pets = [{ id: 'unhatched-pet', hatchStatus: 'EGG' }];
  let resolveRefresh;
  refreshPromise = new Promise((resolve) => { resolveRefresh = resolve; });
  const waiting = makePage();
  const callsBefore = refreshCalls;
  const load = waiting.onLoad();
  const show = waiting.onShow();
  waiting.onTap();
  assert.strictEqual(pageActions, 1, 'page actions wait until the server state is known');
  await Promise.resolve();
  resolveRefresh({ pets });
  await Promise.all([load, show]);
  assert.strictEqual(refreshCalls, callsBefore + 1, 'initial load/show share one pet restoration');
  waiting.onTap();
  assert.strictEqual(pageActions, 2, 'unhatched users retain access to existing basic pages');

  refreshPromise = null;
  waiting.onHide();
  pets = [{ id: 'egg', hatchStatus: 'EGG' }, { id: 'other', hatchStatus: 'HATCHED' }];
  await waiting.onShow();
  waiting.onTap();
  assert.strictEqual(pageActions, 2, 'returning to a page rechecks every pet in the account');

  app.globalData.phoneBindingRequired = false;
  refreshError = new Error('offline');
  const offline = makePage();
  await offline.onLoad();
  assert.strictEqual(pageLoads, 2, 'a failed restoration never releases a protected page');

  app.globalData.phoneBindingRequired = false;
  session = { userId: 42, hasPhone: true };
  gate.requirePhoneBinding();
  const staleBound = makePage();
  await staleBound.onLoad();
  assert.strictEqual(pageLoads, 2, 'server PHONE_REQUIRED overrides a stale local bound flag');

  session = { userId: 42, hasPhone: false };
  refreshError = null;
  pets = [{ id: 'replacement-nfc-egg', hatchStatus: 'EGG' }];
  const replacedInvite = makePage();
  await replacedInvite.onLoad();
  assert.strictEqual(pageLoads, 3, 'authoritative all-egg state restores pre-hatch access after channel replacement');
  assert.strictEqual(app.globalData.phoneBindingRequired, false, 'a prior gate flag cannot outlive its hatched pets');

  console.log('phone-gate.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  delete require.cache[gatePath];
}).catch((error) => { console.error(error); process.exitCode = 1; });
