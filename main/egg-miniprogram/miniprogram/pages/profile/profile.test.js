const assert = require('assert');
const Module = require('module');

const profilePath = require.resolve('./profile');
const originalLoad = Module._load;
const originalPage = global.Page;
const originalGetApp = global.getApp;
const originalWx = global.wx;

let pageConfig;
let putCalls = [];
let modalCalls = [];
let requestCalls = [];

const request = {
  get: (url) => {
    requestCalls.push({ method: 'GET', url });
    return Promise.resolve({ nickname: 'Test', city: '上海', mbti: 'INTJ' });
  },
  put: (url, data) => {
    putCalls.push({ url, data });
    return Promise.resolve();
  }
};
const petStore = {
  getUser: () => ({ id: 'U123456789', nickname: 'Test' }),
  syncUserProfile: () => {}
};
const auth = { getSession: () => ({ token: 'T' }) };
const api = { API_BASE_URL: 'http://test' };

Module._load = function (requestName, parent, isMain) {
  if (parent && parent.filename === profilePath) {
    if (requestName === '../../utils/pet-store') return petStore;
    if (requestName === '../../utils/request') return request;
    if (requestName === '../../utils/auth') return auth;
    if (requestName === '../../config/api') return api;
  }
  return originalLoad.call(this, requestName, parent, isMain);
};

global.Page = (config) => { pageConfig = config; };
global.getApp = () => ({ silentLogin: async () => {} });
global.wx = {
  setStorageSync: () => {},
  getStorageSync: () => ({}),
  showToast: () => {},
  showModal: (options) => {
    modalCalls.push(options);
    if (options.success) options.success({ confirm: true, content: 'custom-city' });
  },
  uploadFile: () => {}
};

function makePage() {
  return {
    ...pageConfig,
    data: { ...pageConfig.data },
    setData(changes) { this.data = { ...this.data, ...changes }; }
  };
}

(async () => {
  require('./profile');
  assert.ok(pageConfig, 'profile page should be registered');

  assert.deepStrictEqual(
    pageConfig.data.cityList,
    ['上海', '北京', '深圳', '杭州', '成都', '广州', '武汉', '西安', '其他'],
    'cityList should be exposed for picker range'
  );
  assert.deepStrictEqual(
    pageConfig.data.mbtiList,
    ['INFP', 'INFJ', 'INTJ', 'INTP', 'ENFP', 'ENFJ', 'ENTJ', 'ENTP',
     'ISFP', 'ISFJ', 'ISTJ', 'ISTP', 'ESFP', 'ESFJ', 'ESTJ', 'ESTP'],
    'mbtiList should be exposed for picker range'
  );

  const page = makePage();
  await page.onLoad();
  assert.strictEqual(requestCalls[0].url, '/wechat/profile', 'page should load profile on mount');

  putCalls = [];
  page.onCityChange({ detail: { value: 2 } });
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.strictEqual(putCalls.length, 1, 'city picker change should call PUT');
  assert.strictEqual(putCalls[0].data.city, '深圳', 'city should match picker selection');

  putCalls = [];
  modalCalls = [];
  page.onCityChange({ detail: { value: 8 } });
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.strictEqual(modalCalls.length, 1, 'selecting "其他" should prompt custom city');
  assert.strictEqual(putCalls.length, 1, 'custom city should be saved');
  assert.strictEqual(putCalls[0].data.city, 'custom-city', 'custom city value should be saved');

  putCalls = [];
  page.onMbtiChange({ detail: { value: 4 } });
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.strictEqual(putCalls.length, 1, 'MBTI picker change should call PUT');
  assert.strictEqual(putCalls[0].data.mbti, 'ENFP', 'MBTI should match picker selection');

  console.log('profile.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  global.Page = originalPage;
  global.getApp = originalGetApp;
  global.wx = originalWx;
  delete require.cache[profilePath];
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
