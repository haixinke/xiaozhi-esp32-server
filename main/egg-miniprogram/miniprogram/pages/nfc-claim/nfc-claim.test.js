const assert = require('assert');
const Module = require('module');

const pagePath = require.resolve('./nfc-claim');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalGetApp = global.getApp;
const originalWx = global.wx;

let pageConfig;
let switchedTo = null;
let toastCalls = [];
let intentResult = null;
let cleared = false;
let sessionResult = null;
let sessionExpired = false;
let previewResult = null;
let previewError = null;
let confirmResult = null;
let confirmError = null;
let savedPetVO = null;
let markedPhoneBound = false;
let ensureLoginResult = null;

const app = {
  globalData: { welcomeCompleted: false },
  applySession(s) {
    this.globalData.userId = s ? s.userId : null;
    this.globalData.hasPhone = s ? s.hasPhone : null;
  },
  async ensureLogin() {
    if (ensureLoginResult) return ensureLoginResult;
    return sessionResult;
  }
};

const authMock = {
  getSession: () => sessionResult,
  isExpired: () => sessionExpired,
  markPhoneBound: () => {
    markedPhoneBound = true;
    sessionResult = { ...sessionResult, hasPhone: true };
    return sessionResult;
  }
};

const nfcIntentMock = {
  getPendingNfcClaimIntent: () => intentResult,
  clearPendingNfcClaimIntent: () => { cleared = true; }
};

const nfcClaimApiMock = {
  preview: async () => {
    if (previewError) throw previewError;
    return previewResult;
  },
  confirm: async () => {
    if (confirmError) throw confirmError;
    return confirmResult;
  }
};

const petStoreMock = {
  savePetFromVO: (vo) => { savedPetVO = vo; return vo; }
};

const wechatApiMock = {
  bindPhone: async () => {}
};

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === pagePath) {
    if (request === '../../utils/auth') return authMock;
    if (request === '../../utils/nfc-claim-intent') return nfcIntentMock;
    if (request === '../../utils/nfc-claim-api') return nfcClaimApiMock;
    if (request === '../../utils/pet-store') return petStoreMock;
    if (request === '../../utils/wechat-api') return wechatApiMock;
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => app;
global.wx = {
  switchTab({ url }) { switchedTo = url; },
  showToast(opts) { toastCalls.push(opts); }
};

function makePage() {
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

function reset() {
  switchedTo = null;
  toastCalls = [];
  intentResult = null;
  cleared = false;
  sessionResult = null;
  sessionExpired = false;
  previewResult = null;
  previewError = null;
  confirmResult = null;
  confirmError = null;
  savedPetVO = null;
  markedPhoneBound = false;
  ensureLoginResult = null;
  app.globalData = { welcomeCompleted: false };
}

async function run() {
  require('./nfc-claim');
  assert.ok(pageConfig, 'nfc-claim page should be registered');

  // 1. No intent → UNAVAILABLE
  reset();
  const p1 = makePage();
  await p1.onLoad();
  assert.strictEqual(p1.data.state, 'UNAVAILABLE', 'no intent shows UNAVAILABLE');

  // 2. Intent + no session + ensureLogin fails → UNAVAILABLE
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  ensureLoginResult = null;
  const p2 = makePage();
  await p2.onLoad();
  await new Promise((r) => setTimeout(r, 10));
  assert.strictEqual(p2.data.state, 'UNAVAILABLE', 'failed login shows UNAVAILABLE');

  // 3. Intent + session without phone + CLAIMABLE preview → NEED_PHONE
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: false, token: 't' };
  sessionExpired = false;
  previewResult = { productName: '蛋宝宝NFC', prototype: '锦鲤', claimStatus: 'CLAIMABLE', pet: null };
  const p3 = makePage();
  await p3.onLoad();
  assert.strictEqual(p3.data.state, 'NEED_PHONE', 'no phone shows NEED_PHONE');

  // 4. Intent + session with phone + CLAIMABLE preview → READY
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: true, token: 't' };
  sessionExpired = false;
  previewResult = { productName: '蛋宝宝NFC', prototype: '锦鲤', claimStatus: 'CLAIMABLE', pet: null };
  const p4 = makePage();
  await p4.onLoad();
  assert.strictEqual(p4.data.state, 'READY', 'claimable preview shows READY');
  assert.strictEqual(p4.data.productName, '蛋宝宝NFC');
  assert.strictEqual(p4.data.petType, '锦鲤');

  // 5. Confirm claim SUCCESS → SUCCESS + saves pet + clears intent
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: true, token: 't' };
  sessionExpired = false;
  previewResult = { productName: '蛋宝宝NFC', prototype: '锦鲤', claimStatus: 'CLAIMABLE', pet: null };
  confirmResult = { claimStatus: 'CLAIMED', pet: { id: 99, prototype: '锦鲤', name: '' } };
  const p5 = makePage();
  await p5.onLoad();
  assert.strictEqual(p5.data.state, 'READY');
  await p5.onConfirmClaim();
  assert.strictEqual(p5.data.state, 'SUCCESS', 'confirm success shows SUCCESS');
  assert.deepStrictEqual(savedPetVO, { id: 99, prototype: '锦鲤', name: '' }, 'pet saved from VO');
  assert.strictEqual(cleared, true, 'intent cleared after success');
  assert.strictEqual(app.globalData.welcomeCompleted, true, 'welcome marked completed');

  // 6. Preview CLAIMED_BY_SELF → 直接回首页（无中间页）+ 存 pet + 清 intent
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: true, token: 't' };
  sessionExpired = false;
  previewResult = { productName: '蛋宝宝NFC', prototype: '玉兔', claimStatus: 'CLAIMED_BY_SELF', pet: { id: 88, prototype: '玉兔' } };
  const p6 = makePage();
  await p6.onLoad();
  assert.strictEqual(switchedTo, '/pages/home/home', 'self-claimed goes straight home');
  assert.deepStrictEqual(savedPetVO, { id: 88, prototype: '玉兔' }, 'pet saved from VO');
  assert.strictEqual(cleared, true, 'intent cleared on direct home');
  assert.strictEqual(app.globalData.welcomeCompleted, true, 'welcome marked completed');

  // 6b. Confirm 竞态 CLAIMED_BY_SELF → 同样直接回首页
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: true, token: 't' };
  sessionExpired = false;
  previewResult = { productName: '蛋宝宝NFC', prototype: '锦鲤', claimStatus: 'CLAIMABLE', pet: null };
  confirmResult = { claimStatus: 'CLAIMED_BY_SELF', pet: { id: 66, prototype: '锦鲤' } };
  const p6b = makePage();
  await p6b.onLoad();
  assert.strictEqual(p6b.data.state, 'READY');
  await p6b.onConfirmClaim();
  assert.strictEqual(switchedTo, '/pages/home/home', 'self-claimed race goes straight home');
  assert.deepStrictEqual(savedPetVO, { id: 66, prototype: '锦鲤' }, 'pet saved from VO on race');
  assert.strictEqual(cleared, true, 'intent cleared on race');
  assert.strictEqual(app.globalData.welcomeCompleted, true, 'welcome marked completed on race');

  // 7. Preview CLAIMED_BY_OTHER → CLAIMED_BY_OTHER
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: true, token: 't' };
  sessionExpired = false;
  previewResult = { productName: '蛋宝宝NFC', prototype: '锦鲤', claimStatus: 'CLAIMED_BY_OTHER', pet: null };
  const p7 = makePage();
  await p7.onLoad();
  assert.strictEqual(p7.data.state, 'CLAIMED_BY_OTHER', 'other-claimed shows CLAIMED_BY_OTHER');

  // 8. Preview network error → NETWORK_ERROR
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: true, token: 't' };
  sessionExpired = false;
  previewError = { userMessage: '网络异常' };
  const p8 = makePage();
  await p8.onLoad();
  assert.strictEqual(p8.data.state, 'NETWORK_ERROR', 'preview error shows NETWORK_ERROR');
  assert.strictEqual(p8.data.errorMessage, '网络异常');

  // 9. Confirm network error → NETWORK_ERROR, retry reuses requestId
  reset();
  intentResult = { type: 'NFC_CLAIM', claimRef: 'ABCDEFGHIJ1234567890_-' };
  sessionResult = { userId: 42, hasPhone: true, token: 't' };
  sessionExpired = false;
  previewResult = { productName: '蛋宝宝NFC', prototype: '锦鲤', claimStatus: 'CLAIMABLE', pet: null };
  confirmError = { userMessage: '请求超时' };
  const p9 = makePage();
  await p9.onLoad();
  assert.strictEqual(p9.data.state, 'READY');
  await p9.onConfirmClaim();
  assert.strictEqual(p9.data.state, 'NETWORK_ERROR', 'confirm error shows NETWORK_ERROR');
  const firstRequestId = p9._requestId;
  assert.ok(firstRequestId, 'requestId was generated');
  // Retry with success
  confirmError = null;
  confirmResult = { claimStatus: 'CLAIMED', pet: { id: 77, prototype: '锦鲤', name: '' } };
  await p9.onRetry();
  assert.strictEqual(p9.data.state, 'SUCCESS', 'retry succeeds');
  assert.strictEqual(p9._requestId, firstRequestId, 'requestId reused on retry');

  // 10. onGoHome → clears intent and navigates home
  reset();
  const p10 = makePage();
  p10.onGoHome();
  assert.strictEqual(switchedTo, '/pages/home/home', 'go home navigates');
  assert.strictEqual(cleared, true, 'intent cleared on go home');
  assert.strictEqual(app.globalData.welcomeCompleted, true, 'welcome completed on go home');

  console.log('nfc-claim.test.js: ALL PASS');
}

run().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  delete require.cache[pagePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
