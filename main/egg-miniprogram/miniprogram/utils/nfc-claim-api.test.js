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

const nfcClaimApi = require('./nfc-claim-api');

function enqueue(data) {
  responses.push({ statusCode: 200, data: { code: 0, data } });
}

function assertUrlSuffix(suffix) {
  assert.ok(calls.at(-1).url.endsWith(suffix), `expected url ending with ${suffix}, got ${calls.at(-1).url}`);
}

(async () => {
  // preview
  const previewData = { productName: '蛋宝宝NFC实物', prototype: '锦鲤', claimStatus: 'CLAIMABLE', pet: null };
  enqueue(previewData);
  const previewResult = await nfcClaimApi.preview('ABCDEFGHIJ1234567890_-');
  assertUrlSuffix('/pdc/nfc/claim/preview');
  assert.strictEqual(calls.at(-1).method, 'GET');
  assert.deepStrictEqual(calls.at(-1).data, { claimRef: 'ABCDEFGHIJ1234567890_-' });
  assert.deepStrictEqual(previewResult, previewData);

  // confirm
  const confirmData = { claimStatus: 'CLAIMED', pet: { id: 99, prototype: '锦鲤', name: '' } };
  enqueue(confirmData);
  const confirmResult = await nfcClaimApi.confirm('ABCDEFGHIJ1234567890_-', 'uuid-123');
  assertUrlSuffix('/pdc/nfc/claim/confirm');
  assert.strictEqual(calls.at(-1).method, 'POST');
  assert.deepStrictEqual(calls.at(-1).data, { claimRef: 'ABCDEFGHIJ1234567890_-', requestId: 'uuid-123' });
  assert.deepStrictEqual(confirmResult, confirmData);

  console.log('nfc-claim-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
