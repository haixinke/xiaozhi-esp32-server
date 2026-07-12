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

const inviteApi = require('./invite-api');

function enqueue(data) {
  responses.push({ statusCode: 200, data: { code: 0, data } });
}

function assertUrl(suffix) {
  assert.ok(calls.at(-1).url.endsWith(suffix), `expected url ending with ${suffix}, got ${calls.at(-1).url}`);
}

(async () => {
  const mine = { code: 'EGG-ABCD', quota: 5, usedCount: 2, remaining: 3, status: 1 };
  enqueue(mine);
  const result = await inviteApi.getMine();
  assertUrl('/invite/mine');
  assert.strictEqual(calls.at(-1).method, 'GET');
  assert.deepStrictEqual(result, mine);

  console.log('invite-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
