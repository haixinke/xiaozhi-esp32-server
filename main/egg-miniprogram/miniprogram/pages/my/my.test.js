const assert = require('assert');
const Module = require('module');

const pagePath = require.resolve('./my');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalWx = global.wx;

let pageConfig;
let inviteMineResult = null;
let inviteMineError = null;
let inviteMineQueue = [];
let hiddenMenus = 0;
let shownMenus = 0;

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/pet-store') {
      return { getUser: () => null, getPet: () => null, syncUserProfile() {} };
    }
    if (request === '../../utils/request') return { get: () => Promise.resolve(null) };
    if (request === '../../utils/invite-api') {
      return {
        getMine: async () => {
          if (inviteMineQueue.length > 0) return inviteMineQueue.shift();
          if (inviteMineError) throw inviteMineError;
          return inviteMineResult;
        }
      };
    }
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.wx = {
  hideShareMenu() { hiddenMenus += 1; },
  showShareMenu() { shownMenus += 1; },
  navigateTo() {}
};

function makePage() {
  return {
    ...Object.fromEntries(Object.entries(pageConfig).filter(([, value]) => typeof value === 'function')),
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

(async () => {
  require('./my');
  assert.ok(pageConfig, 'my page should be registered');

  const loadingPage = makePage();
  assert.strictEqual(loadingPage.onShareAppMessage(), false,
    'friend sharing must be unavailable while the invitation lookup is pending');
  assert.strictEqual(loadingPage.onShareTimeline(), false,
    'timeline sharing must be unavailable while the invitation lookup is pending');

  inviteMineResult = { code: 'EGG-ABCD', remaining: 3, status: 1 };
  inviteMineError = null;
  const activeInvitePage = makePage();
  await activeInvitePage.loadShareInviteCode();
  assert.deepStrictEqual(
    activeInvitePage.onShareAppMessage(),
    {
      title: '一起来养蛋宝宝吧',
      path: '/pages/home/home?v=1&source=home_share&inviteCode=EGG-ABCD'
    },
    'friend sharing should include an active personal invitation code'
  );
  assert.deepStrictEqual(
    activeInvitePage.onShareTimeline(),
    { title: '一起来养蛋宝宝吧' },
    'timeline sharing must not carry or prefill an invitation code'
  );

  inviteMineResult = { code: 'EGG-USED', remaining: 0, status: 1 };
  const exhaustedInvitePage = makePage();
  await exhaustedInvitePage.loadShareInviteCode();
  assert.deepStrictEqual(
    exhaustedInvitePage.onShareAppMessage(),
    {
      title: '一起来养蛋宝宝吧',
      path: '/pages/home/home?v=1&source=home_share'
    },
    'friend sharing should not include an exhausted invitation code'
  );

  let resolveFirstInvite;
  let resolveSecondInvite;
  inviteMineQueue = [
    new Promise((resolve) => { resolveFirstInvite = resolve; }),
    new Promise((resolve) => { resolveSecondInvite = resolve; })
  ];
  const concurrentPage = makePage();
  concurrentPage.prepareShare();
  concurrentPage.prepareShare();
  resolveFirstInvite({ code: 'EGG-OLD', remaining: 3, status: 1 });
  await new Promise((resolve) => setImmediate(resolve));
  assert.strictEqual(concurrentPage.data.shareReady, false,
    'an older invitation request must not reopen sharing while a newer request is pending');
  resolveSecondInvite({ code: 'EGG-NEW', remaining: 3, status: 1 });
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepStrictEqual(
    concurrentPage.onShareAppMessage(),
    {
      title: '一起来养蛋宝宝吧',
      path: '/pages/home/home?v=1&source=home_share&inviteCode=EGG-NEW'
    },
    'only the newest invitation request may determine the friend share path'
  );

  inviteMineResult = null;
  inviteMineError = new Error('network unavailable');
  const failedInvitePage = makePage();
  await failedInvitePage.loadShareInviteCode();
  assert.strictEqual(failedInvitePage.data.shareReady, true,
    'a failed invitation lookup should still allow ordinary sharing');
  assert.deepStrictEqual(
    failedInvitePage.onShareAppMessage(),
    {
      title: '一起来养蛋宝宝吧',
      path: '/pages/home/home?v=1&source=home_share'
    },
    'failed invitation lookups should fall back to a normal home share'
  );

  const onShowPage = makePage();
  const hiddenBeforeOnShow = hiddenMenus;
  onShowPage.onShow();
  assert.strictEqual(hiddenMenus, hiddenBeforeOnShow + 1,
    'my page should hide sharing until the invitation lookup completes');
  await Promise.resolve();
  await Promise.resolve();
  assert.ok(shownMenus >= 1,
    'my page should restore sharing after the invitation lookup completes');

  console.log('my.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.wx = originalWx;
  delete require.cache[pagePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
