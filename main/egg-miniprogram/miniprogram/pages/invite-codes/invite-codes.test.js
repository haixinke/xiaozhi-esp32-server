const assert = require('assert');
const fs = require('fs');
const path = require('path');
const auth = require('../../utils/auth');

process.env.TZ = 'UTC';

const storage = new Map();
const files = new Map();
const requests = [];
const albumSaves = [];
const toasts = [];
const base64Png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==';
const defaultInvite = { code: 'ABCD2345', quota: 5, usedCount: 2, remaining: 3, status: 1 };
let pageConfig;
let metadata;
let qrResponse;
let onQrRequest;
let finishAlbumSave;

global.Page = (config) => { pageConfig = config; };
global.getApp = () => ({ ensureLogin: async () => auth.getSession(), globalData: {} });
global.wx = {
  env: { USER_DATA_PATH: 'wxfile://usr' },
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  getFileSystemManager: () => ({
    writeFileSync(filePath, data, encoding) {
      assert.strictEqual(encoding, 'base64');
      files.set(filePath, data);
    },
    unlink({ filePath }) { files.delete(filePath); }
  }),
  saveImageToPhotosAlbum(options) {
    assert.ok(files.has(options.filePath), 'the QR file exists when handing it to the album');
    albumSaves.push(options.filePath);
    finishAlbumSave = options.success;
  },
  showToast: ({ title }) => toasts.push(title),
  request(options) {
    const endpoint = new URL(options.url).pathname;
    requests.push(endpoint);
    if (endpoint.endsWith('/invite/mine/qrcode')) {
      if (onQrRequest) onQrRequest(options);
      else options.success({ statusCode: 200, data: qrResponse });
      return;
    }
    assert.ok(endpoint.endsWith('/invite/mine'));
    options.success({ statusCode: 200, data: metadata });
  }
};

function makePage(invite = defaultInvite) {
  files.clear();
  requests.length = 0;
  albumSaves.length = 0;
  toasts.length = 0;
  onQrRequest = null;
  metadata = { code: 0, data: invite };
  qrResponse = { code: 0, data: base64Png };
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

async function run() {
  require('./invite-codes');
  auth.saveSession({ token: 'test-session', userId: 42, openid: 'test-user', hasPhone: true, expire: 3600 });

  const template = fs.readFileSync(path.join(__dirname, 'invite-codes.wxml'), 'utf8');
  assert.ok(!template.includes('{{inviteCode.code}}'));
  assert.ok(!template.includes('复制激活码'));
  assert.ok(template.includes('src="{{qrImage}}"'));
  assert.ok(template.includes('bindtap="onSaveQrImage"'));

  const page = makePage();
  await page.onLoad();
  await page.onShow();
  assert.strictEqual(requests.length, 2);
  assert.strictEqual(files.get(page.data.qrImage), base64Png, 'the PNG is rendered from a mini-program file');
  assert.strictEqual(page.data.inviteCode.usedCount, 2);
  assert.strictEqual(page.data.inviteCode.quota, 5);
  assert.strictEqual(page.data.inviteCode.remaining, 3);
  assert.strictEqual(page.data.statusLabel, '可邀请');
  assert.strictEqual(page.data.inviteCode.code, undefined, 'the visible data does not carry the text code');
  const save = page.onSaveQrImage();
  assert.deepStrictEqual(albumSaves, [page.data.qrImage], 'saving uses the existing local QR file');
  finishAlbumSave();
  await save;
  assert.deepStrictEqual(toasts, ['已保存到相册']);
  const previousImage = page.data.qrImage;
  await page.onShow();
  page.onQrImageError({ currentTarget: { dataset: { path: previousImage } } });
  assert.strictEqual(page.data.error, '', 'a retired image cannot break the current QR display');
  assert.strictEqual(files.get(page.data.qrImage), base64Png);
  page.onUnload();
  assert.strictEqual(files.size, 0, 'leaving the page removes its QR file');

  for (const [invite, label] of [
    [{ ...defaultInvite, remaining: 0, usedCount: 5 }, '已用完'],
    [{ ...defaultInvite, status: 0 }, '已停用'],
    [{ ...defaultInvite, expireTime: '2020-01-01T00:00:00.000+00:00' }, '已过期']
  ]) {
    const unavailable = makePage(invite);
    await unavailable.onShow();
    assert.strictEqual(unavailable.data.statusLabel, label);
    assert.strictEqual(unavailable.data.qrImage, '');
    assert.strictEqual(requests.length, 1, 'an unavailable code only loads metadata');
    await unavailable.onSaveQrImage();
    assert.deepStrictEqual(albumSaves, [], 'unavailable codes cannot be saved');
  }

  const realNow = Date.now;
  const midnightInChina = Date.parse('2026-11-24T16:00:00Z');
  try {
    const invite = { ...defaultInvite, expireTime: '2026-11-25 00:00:00' };
    Date.now = () => midnightInChina - 1000;
    const stillAvailable = makePage(invite);
    await stillAvailable.onShow();
    assert.strictEqual(stillAvailable.data.statusLabel, '可邀请');
    stillAvailable.onUnload();

    Date.now = () => midnightInChina;
    const expired = makePage(invite);
    await expired.onShow();
    assert.strictEqual(expired.data.statusLabel, '已过期', 'server timestamps expire at GMT+8 on a UTC device');
    assert.strictEqual(requests.length, 1, 'an expired code only loads metadata');
  } finally {
    Date.now = realNow;
  }

  const failedPage = makePage();
  qrResponse = { code: 1, msg: '二维码暂不可用' };
  await failedPage.onShow();
  assert.strictEqual(failedPage.data.error, '二维码暂不可用');
  assert.strictEqual(failedPage.data.inviteCode.remaining, 3, 'available usage information remains visible');
  qrResponse = { code: 0, data: base64Png };
  await failedPage.onRetry();
  assert.strictEqual(failedPage.data.error, '');
  assert.strictEqual(files.get(failedPage.data.qrImage), base64Png);
  failedPage.onUnload();

  const albumPage = makePage();
  await albumPage.onShow();
  const savingFile = albumPage.data.qrImage;
  const saving = albumPage.onSaveQrImage();
  albumPage.onHide();
  assert.ok(files.has(savingFile), 'an in-flight album save retains its source file');
  finishAlbumSave();
  await saving;
  assert.strictEqual(files.size, 0, 'the hidden page releases its source file after saving');

  const delayedPage = makePage();
  const requestedQr = new Promise((resolve) => { onQrRequest = resolve; });
  const showing = delayedPage.onShow();
  const request = await requestedQr;
  delayedPage.onHide();
  request.success({ statusCode: 200, data: qrResponse });
  await showing;
  assert.strictEqual(files.size, 0, 'a response after the page hides cannot leave a QR file behind');
  assert.strictEqual(delayedPage.data.qrImage, '');

  console.log('invite-codes.test.js: ALL PASS');
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
