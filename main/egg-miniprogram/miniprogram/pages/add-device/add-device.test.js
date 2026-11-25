const assert = require('assert');
const fs = require('fs');
const path = require('path');
const auth = require('../../utils/auth');
const petStore = require('../../utils/pet-store');
const shareInvite = require('../../utils/share-invite');

const storage = new Map();
const requests = [];
const scanners = [];
const redirects = [];
const originalSetTimeout = global.setTimeout;
let pageConfig;
let response;

global.Page = (config) => { pageConfig = config; };
global.wx = {
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  reLaunch: ({ url }) => redirects.push(url),
  switchTab: ({ url }) => redirects.push(url),
  request(options) {
    requests.push(options);
    options.success({ statusCode: 200, data: response });
  },
  scanCode: (options) => scanners.push(options)
};
global.setTimeout = (callback) => { callback(); return 0; };

function makePage({ hasPhone = true, expired = false, guest = false, sharedCode } = {}) {
  storage.clear();
  requests.length = 0;
  scanners.length = 0;
  redirects.length = 0;
  response = { code: 0, data: { id: 'test-egg', prototype: '玉兔' } };
  if (!guest) {
    auth.saveSession({ token: 'test-session', userId: 42, openid: 'test-user', hasPhone, expire: 3600 },
      expired ? Date.now() - 7200000 : Date.now());
  }
  if (sharedCode) shareInvite.savePending({ code: sharedCode, source: 'home_share', version: 1 });
  const page = {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
  page.onLoad();
  return page;
}

async function run() {
  require('./add-device');
  const template = fs.readFileSync(path.join(__dirname, 'add-device.wxml'), 'utf8');
  assert.ok(!template.includes('<input'), 'invite codes are no longer entered manually');
  assert.ok(template.includes('bind:tap="onScanCode"'));
  assert.ok(template.includes('bind:tap="onClaimSharedInvite"'));

  makePage({ guest: true });
  assert.deepStrictEqual(redirects, ['/pages/home/home']);
  makePage({ expired: true });
  assert.deepStrictEqual(redirects, ['/pages/home/home']);

  const scanPage = makePage({ hasPhone: false });
  assert.deepStrictEqual(redirects, [], 'an authenticated account can adopt before phone binding');
  const scan = scanPage.onScanCode();
  scanPage.onScanCode();
  assert.strictEqual(scanners.length, 1, 'repeated taps open one scanner');
  assert.strictEqual(scanners[0].onlyFromCamera, false, 'the scanner can select an image from the album');
  assert.deepStrictEqual(scanners[0].scanType, ['qrCode']);
  scanners[0].success({ result: 'iv1-ABCD2345' });
  await scan;
  assert.strictEqual(requests.length, 1);
  assert.ok(requests[0].url.endsWith('/pet/adopt'));
  assert.strictEqual(requests[0].method, 'POST');
  assert.deepStrictEqual(requests[0].data, { inviteCode: 'ABCD2345' }, 'the full historical code is submitted');
  assert.strictEqual(petStore.getPet().id, 'test-egg');
  assert.strictEqual(scanPage.data.success.petType, '玉兔');
  assert.deepStrictEqual(redirects, ['/pages/home/home']);

  for (const result of ['ABCDE', 'iv2-ABCDE', 'iv1-ABCD', 'iv1-ABCDE\n', 'https://example.com/?code=ABCDE']) {
    const page = makePage();
    const attempt = page.onScanCode();
    scanners[0].success({ result });
    await attempt;
    assert.strictEqual(requests.length, 0, 'unrelated content never reaches the adopt API');
    assert.ok(page.data.error);
  }

  const cancelledPage = makePage();
  const cancelled = cancelledPage.onScanCode();
  scanners[0].fail({ errMsg: 'scanCode:fail cancel' });
  await cancelled;
  assert.strictEqual(requests.length, 0);
  assert.strictEqual(cancelledPage.data.error, '', 'cancelling leaves the page ready to retry');

  const rejectedPage = makePage();
  response = { code: 1, msg: '邀请码已失效' };
  const rejected = rejectedPage.onScanCode();
  scanners[0].success({ result: 'iv1-ABCDE' });
  await rejected;
  assert.strictEqual(rejectedPage.data.error, '邀请码已失效');
  assert.strictEqual(rejectedPage.data.submitting, false);

  const sharedPage = makePage({ sharedCode: 'ABCD2345' });
  assert.strictEqual(sharedPage.data.sharedInvite, true);
  assert.strictEqual(requests.length, 0, 'opening a share never automatically adopts');
  assert.ok(shareInvite.getPending());
  await sharedPage.onClaimSharedInvite();
  assert.strictEqual(requests.length, 1);
  assert.deepStrictEqual(requests[0].data, { inviteCode: 'ABCD2345' });
  assert.strictEqual(shareInvite.getPending(), null, 'successful adoption clears the pending share');

  console.log('add-device.test.js: ALL PASS');
}

run().finally(() => { global.setTimeout = originalSetTimeout; }).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
