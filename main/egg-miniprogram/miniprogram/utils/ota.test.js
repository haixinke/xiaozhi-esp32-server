const assert = require('assert');
const Module = require('module');
const originalLoad = Module._load;
const originalWx = global.wx;
const originalGetApp = global.getApp;
let response;
let phoneRequirements = 0;
Module._load = function (request, parent, isMain) {
  if (request === '../config/api') return { API_BASE_URL: 'https://api.example' };
  if (request === './auth') return { getPhoneBindingContext: () => ({ userId: 'fixture-user', generation: 0 }) };
  if (request === './phone-gate') return { requirePhoneBinding: () => { phoneRequirements += 1; } };
  return originalLoad.call(this, request, parent, isMain);
};
global.wx = { request(options) { options.success(response); } };
global.getApp = () => ({ globalData: {} });
const ota = require('./ota');

(async () => {
  response = { statusCode: 200, data: { code: 10228, msg: '请先绑定手机号' } };
  await assert.rejects(ota.checkOrRegisterDevice('fixture-device'), (error) => error.code === 10228);
  assert.strictEqual(phoneRequirements, 1, 'OTA phone requirement returns to the binding flow');
  response = { statusCode: 403, data: { code: 10228, msg: '请先绑定手机号' } };
  await assert.rejects(ota.checkOrRegisterDevice('fixture-device'), (error) => error.code === 10228);
  assert.strictEqual(phoneRequirements, 2, 'non-2xx phone requirement remains distinguishable');
  response = { statusCode: 200, data: { websocket: { url: 'wss://example.test', token: 'fixture' } } };
  assert.deepStrictEqual(await ota.checkOrRegisterDevice('fixture-device'), { wsUrl: 'wss://example.test', wsToken: 'fixture' });
  await assert.rejects(ota.checkOrRegisterDevice(''), (error) => /缺少/.test(error.userMessage));
  response = { statusCode: 500, data: {} };
  await assert.rejects(ota.checkOrRegisterDevice('fixture-device'), (error) => /获取聊天配置失败/.test(error.userMessage));
  response = { statusCode: 200, data: {} };
  await assert.rejects(ota.checkOrRegisterDevice('fixture-device'), (error) => /聊天配置不可用/.test(error.userMessage));
  console.log('ota.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  global.wx = originalWx;
  global.getApp = originalGetApp;
}).catch((error) => { console.error(error); process.exitCode = 1; });
