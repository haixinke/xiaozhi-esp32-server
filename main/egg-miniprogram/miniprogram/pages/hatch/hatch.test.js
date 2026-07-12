/**
 * hatch.test.js — pages/hatch/hatch.js 页面级单元测试
 *
 * 验证：快速连续点击「揭晓破壳收藏卡」时，onReveal 只发起一次破壳请求。
 */
const assert = require('assert');

// 模拟 wx 全局 API
const wxCalls = [];
global.wx = {
  redirectTo(options) { wxCalls.push({ type: 'redirectTo', options }); },
  showToast(options) { wxCalls.push({ type: 'showToast', options }); },
  navigateBack() { wxCalls.push({ type: 'navigateBack' }); },
  getStorageSync() { return null; },
  setStorageSync() {},
  removeStorageSync() {}
};

let hatchCalls = 0;
const hatchResponse = { ok: true, created: true, card: { serial: 'EGG-TEST-001' }, pet: { hatchStatus: 'HATCHED' } };

const originalLoad = require('module')._load;
require('module')._load = function (request) {
  if (request === '../../utils/pet-store') {
    return {
      getPet() {
        return {
          id: 'pet-test-1',
          prototype: '玉兔',
          shell: { color: '#EDE78E' },
          hatchStatus: 'EGG',
          hatchAt: Date.now() - 1000,
          acceleratedMinutes: 0,
          expectedHatchTime: Date.now() - 1000,
          hatchStartTime: Date.now() - 7 * 24 * 60 * 60 * 1000
        };
      },
      getStage() { return 'ready'; },
      createCollectionCard() {
        hatchCalls += 1;
        return Promise.resolve(hatchResponse);
      }
    };
  }
  return originalLoad.apply(this, arguments);
};

let capturedPage = null;
global.Page = function (config) {
  config.setData = function (updates) {
    Object.assign(this.data, updates);
  };
  capturedPage = config;
};

require('./hatch.js');
const HatchPage = capturedPage;

(async () => {
  hatchCalls = 0;
  wxCalls.length = 0;

  // 构造两个并发的 onReveal 调用
  HatchPage.onReveal();
  HatchPage.onReveal();
  HatchPage.onReveal();

  // 等待 setTimeout + Promise
  await new Promise((resolve) => setTimeout(resolve, 2000));

  assert.strictEqual(hatchCalls, 1, 'rapid onReveal taps should call createCollectionCard only once');
  assert.strictEqual(wxCalls.filter((c) => c.type === 'redirectTo').length, 1, 'should redirect once');

  console.log('hatch.test.js: ALL PASS');
})().finally(() => { require('module')._load = originalLoad; });
