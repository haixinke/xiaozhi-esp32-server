const assert = require('assert');
const Module = require('module');

let calls = [];
let responses = [];
global.wx = { request(options) { calls.push(options); options.success(responses.shift()); } };
global.getApp = () => ({ silentLogin: async () => {} });

const originalLoad = Module._load;
Module._load = function (request) {
  if (request === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
  if (request === './auth') return { getSession: () => ({ token: 't' }), clearSession: () => {} };
  return originalLoad.apply(this, arguments);
};

const storyApi = require('./story-api');

function enqueue(data) { responses.push({ statusCode: 200, data: { code: 0, data } }); }

(async () => {
  enqueue({ bigSceneName: '在家', smallSceneName: '卧室', imageUrl: 'u', tagImageUrl: 't' });
  const state = await storyApi.getStoryState('pet-1');
  assert.ok(calls.at(-1).url.endsWith('/pet/pet-1/story-state'),
    `expected url ending with /pet/pet-1/story-state, got ${calls.at(-1).url}`);
  assert.strictEqual(calls.at(-1).method, 'GET');
  assert.strictEqual(state.bigSceneName, '在家');

  console.log('story-api.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
}).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
