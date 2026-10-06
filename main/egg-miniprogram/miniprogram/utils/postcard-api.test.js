const assert = require('assert');
const Module = require('module');

let calls = [];
let responses = [];
global.wx = { request(options) { calls.push(options); options.success(responses.shift()); } };
global.getApp = () => ({ silentLogin: async () => {} });

const originalLoad = Module._load;
Module._load = function (request) {
  if (request === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
  if (request === './auth') return { getSession: () => null, clearSession: () => {} };
  return originalLoad.apply(this, arguments);
};

const { getPublicPostcard } = require('./postcard-api');

function enqueue(data) {
  responses.push({ statusCode: 200, data: { code: 0, data } });
}

(async () => {
  // 免授权请求：匿名会话下不携带 Authorization，也能发出
  const vo = { petName: '小金鱼', prototype: '锦鲤', imageUrl: 'https://oss/x.png', caption: '想你', createDate: '2026-10-06 10:00:00' };
  enqueue(vo);
  const result = await getPublicPostcard('A2B3C4D5E6F7');
  const last = calls.at(-1);
  assert.ok(last.url.endsWith('/pet/postcard/public/A2B3C4D5E6F7'), `unexpected url: ${last.url}`);
  assert.strictEqual(last.method, 'GET');
  assert.strictEqual(last.header.Authorization, undefined, 'anonymous request must not carry Bearer token');
  assert.deepStrictEqual(result, vo);

  // shareId 特殊字符需编码
  enqueue(vo);
  await getPublicPostcard('AB CD');
  assert.ok(calls.at(-1).url.includes('AB%20CD'), 'shareId must be url-encoded');

  console.log('postcard-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
