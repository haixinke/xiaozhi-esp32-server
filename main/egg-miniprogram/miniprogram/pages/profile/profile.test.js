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
    return Promise.resolve({ nickname: 'Test', city: '上海', mbti: 'INTJ', zodiac: 'aquarius' });
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
    if (options.success) options.success({ confirm: true, content: 'custom' });
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

  assert.strictEqual(
    pageConfig.data.cityList,
    undefined,
    'cityList should not be exposed after removing picker'
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
  assert.strictEqual(page.data.zodiac, '水瓶座', 'zodiac english code should translate to chinese');

  putCalls = [];
  modalCalls = [];
  page.onEditCity();
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.strictEqual(modalCalls.length, 1, 'onEditCity should prompt city input modal');
  assert.strictEqual(modalCalls[0].editable, true, 'city modal should be editable');
  assert.strictEqual(putCalls.length, 1, 'city modal confirm should call PUT');
  assert.strictEqual(putCalls[0].data.city, 'custom', 'city value should be saved');

  putCalls = [];
  global.wx.showModal = (options) => {
    modalCalls.push(options);
    if (options.success) options.success({ confirm: true, content: '  custom-city  ' });
  };
  page.onEditCity();
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.strictEqual(putCalls[0].data.city, 'custom-cit', 'city value should be trimmed and truncated to 10 characters');

  putCalls = [];
  global.wx.showModal = (options) => {
    modalCalls.push(options);
    if (options.success) options.success({ confirm: true, content: '   ' });
  };
  page.onEditCity();
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.strictEqual(putCalls.length, 0, 'empty city value after trim should not save');

  putCalls = [];
  global.wx.showModal = (options) => {
    modalCalls.push(options);
    if (options.success) options.success({ confirm: false, content: 'ignored' });
  };
  page.onEditCity();
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.strictEqual(putCalls.length, 0, 'city modal cancel should not save');

  putCalls = [];
  global.wx.showModal = (options) => {
    modalCalls.push(options);
    if (options.success) options.success({ confirm: true, content: 'custom-city' });
  };
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
