const assert = require('assert');
const Module = require('module');

let token = 'old-token';
let requestCalls = [];
let loginCalls = 0;
let responses = [];
global.wx = { request(options) { requestCalls.push(options); options.success(responses.shift()); } };
global.getApp = () => ({ silentLogin: async () => { loginCalls += 1; token = 'new-token'; } });

const originalLoad = Module._load;
Module._load = function (request) {
  if (request === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
  if (request === './auth') return {
    getSession: () => token ? { token } : null,
    clearSession: () => { token = ''; }
  };
  return originalLoad.apply(this, arguments);
};
const api = require('./request');

(async () => {
  responses = [{ statusCode: 200, data: { code: 0, data: { userId: 42 } } }];
  assert.deepStrictEqual(await api.post('/wechat/login', { code: 'test' }, { anonymous: true }), { userId: 42 });
  assert.strictEqual(requestCalls[0].header.Authorization, undefined);

  responses = [
    { statusCode: 401, data: { code: 10021, msg: 'expired' } },
    { statusCode: 200, data: { code: 0, data: { ok: true } } }
  ];
  assert.deepStrictEqual(await api.get('/pet/list'), { ok: true });
  assert.strictEqual(loginCalls, 1);
  assert.strictEqual(requestCalls.at(-1).header.Authorization, 'Bearer new-token');

  responses = [{ statusCode: 200, data: { code: 10201, msg: 'bad' } }];
  await assert.rejects(api.get('/pet/list'), (error) => error.type === 'business' && error.code === 10201);

  responses = [
    { statusCode: 401, data: {} },
    { statusCode: 401, data: {} }
  ];
  await assert.rejects(api.get('/pet/list'), (error) => error.type === 'unauthorized');
  assert.strictEqual(responses.length, 0);
  console.log('request.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
