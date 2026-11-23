const assert = require('assert');
const Module = require('module');

let calls = [];
let responses = [];
global.wx = { request(options) { calls.push(options); options.success(responses.shift()); } };
global.getApp = () => ({ silentLogin: async () => {} });

const originalLoad = Module._load;
Module._load = function (request) {
  if (request === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
  if (request === './auth') return { getPhoneBindingContext: () => ({ userId: 'fixture-user', generation: 0 }), getSession: () => ({ token: 't' }), clearSession: () => {} };
  return originalLoad.apply(this, arguments);
};

const starApi = require('./star-api');

function enqueue(data) {
  responses.push({ statusCode: 200, data: { code: 0, data } });
}

(async () => {
  // 余额查询：GET /star/balance，透传 StarBalanceVO
  enqueue({ balance: 128 });
  const result = await starApi.getBalance();
  assert.ok(calls.at(-1).url.endsWith('/star/balance'), `expected url ending with /star/balance, got ${calls.at(-1).url}`);
  assert.strictEqual(calls.at(-1).method, 'GET', 'getBalance uses GET');
  assert.deepStrictEqual(result, { balance: 128 }, 'getBalance returns StarBalanceVO');

  console.log('star-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
