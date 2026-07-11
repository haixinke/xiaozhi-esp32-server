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

const petApi = require('./pet-api');

function enqueue(data) { responses.push({ statusCode: 200, data: { code: 0, data } }); }

// request.js prepends API_BASE_URL before calling wx.request, so the captured
// options.url is the full URL. Assert the suffix to verify each wrapper passes
// the correct relative path while keeping the mock's base URL intact.
function assertUrl(suffix) {
  assert.ok(calls.at(-1).url.endsWith(suffix), `expected url ending with ${suffix}, got ${calls.at(-1).url}`);
}

(async () => {
  enqueue({ id: 'pet-1', hatchStatus: 'EGG' });
  const adopted = await petApi.adoptPet('CODE-1');
  assert.strictEqual(adopted.id, 'pet-1');
  assertUrl('/pet/adopt');
  assert.strictEqual(calls.at(-1).method, 'POST');
  assert.deepStrictEqual(calls.at(-1).data, { inviteCode: 'CODE-1' });

  enqueue({ addedMinutes: 60, alreadyDone: false, readyToHatch: false, pet: { id: 'pet-1' } });
  const action = await petApi.submitHatchAction('pet-1', 'LESSON', { value: '学会勇敢' });
  assertUrl('/pet/pet-1/hatch-action');
  assert.strictEqual(calls.at(-1).method, 'POST');
  assert.deepStrictEqual(calls.at(-1).data, { type: 'LESSON', payload: { value: '学会勇敢' } });
  assert.strictEqual(action.addedMinutes, 60);
  assert.strictEqual(action.alreadyDone, false);

  enqueue([{ actionType: 'LESSON', payload: '{}' }]);
  const actions = await petApi.listHatchActions('pet-1');
  assertUrl('/pet/pet-1/hatch-actions');
  assert.strictEqual(calls.at(-1).method, 'GET');
  assert.strictEqual(Array.isArray(actions), true);

  enqueue({ id: 'pet-1', hatchStatus: 'HATCHED' });
  const hatched = await petApi.hatchPet('pet-1');
  assertUrl('/pet/pet-1/hatch');
  assert.strictEqual(calls.at(-1).method, 'POST');
  assert.strictEqual(hatched.hatchStatus, 'HATCHED');

  enqueue({ id: 'pet-1' });
  await petApi.getPet('pet-1');
  assertUrl('/pet/pet-1');
  assert.strictEqual(calls.at(-1).method, 'GET');

  enqueue([{ id: 'pet-1' }, { id: 'pet-2' }]);
  const list = await petApi.listPets();
  assertUrl('/pet/list');
  assert.strictEqual(list.length, 2);

  enqueue({ id: 'pet-1', nickname: '小金' });
  await petApi.updateNickname('pet-1', '小金');
  assertUrl('/pet/update');
  assert.strictEqual(calls.at(-1).method, 'PUT');
  assert.deepStrictEqual(calls.at(-1).data, { id: 'pet-1', nickname: '小金' });

  console.log('pet-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
