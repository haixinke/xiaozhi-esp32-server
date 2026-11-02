const assert = require('assert');
const Module = require('module');
const storage = new Map();
const originalLoad = Module._load;
const originals = { wx: global.wx, Page: global.Page };
let actionsResponse;
let listResponse = [];
let definition;
const clone = (value) => value === undefined ? undefined : JSON.parse(JSON.stringify(value));
global.wx = {
  getStorageSync: (key) => clone(storage.get(key)) || '',
  setStorageSync: (key, value) => storage.set(key, clone(value)),
  removeStorageSync: (key) => storage.delete(key),
  switchTab() {}
};
global.Page = (value) => { definition = value; };
Module._load = function (request, parent, isMain) {
  if (request === './pet-api' || request === '../../utils/pet-api') {
    return { listPets: async () => listResponse, listHatchActions: () => actionsResponse };
  }
  return originalLoad.call(this, request, parent, isMain);
};
const store = require('../../utils/pet-store');
require('./hatch-guide');
function page() {
  return { ...definition, data: clone(definition.data), setData(changes) { this.data = { ...this.data, ...changes }; } };
}
function seed() {
  store.clearAccountData();
  store.saveUser({ id: 42 });
  store.savePetFromVO({ id: 'a', nickname: 'A', hatchStatus: 'EGG' });
}
function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}
async function run() {
  seed();
  actionsResponse = Promise.resolve([{ actionType: 'CUDDLE', actionDate: store.todayKey() }]);
  const first = page();
  await first.onShow();
  assert.strictEqual(first.data.tasks.find((task) => task.key === 'cuddle').done, true,
    'fresh action results must appear on the first render');

  seed();
  storage.delete('eggbaby_mvp_pets_v1');
  actionsResponse = Promise.resolve([{ actionType: 'NICKNAME' }]);
  const legacy = page();
  await legacy.onShow();
  assert.strictEqual(legacy.data.tasks.find((task) => task.key === 'nickname').done, true,
    'legacy single-pet cache still accepts fresh action results');

  seed();
  const deleted = page();
  const pending = deferred();
  actionsResponse = pending.promise;
  const loading = deleted.onShow();
  listResponse = [];
  await store.refreshPets();
  pending.resolve([{ actionType: 'NICKNAME' }]);
  await loading;
  assert.strictEqual(store.getPetById('a'), null, 'late action read must not resurrect a deleted pet');
  assert.strictEqual(store.getPet(), null);
  assert.strictEqual(deleted.data.pet, null, 'deleted pet must not be rendered after the response');

  seed();
  const switching = page();
  const late = deferred();
  actionsResponse = late.promise;
  const reading = switching.onShow();
  store.savePetFromVO({ id: 'b', nickname: 'B', hatchStatus: 'EGG' });
  late.resolve([{ actionType: 'NICKNAME' }]);
  await reading;
  assert.strictEqual(store.getPet().id, 'b');
  assert.ok(!switching.data.pet || switching.data.pet.id === 'b', 'late A results must not render A as current');
  console.log('hatch-guide.test.js: ALL PASS');
}
run().catch((error) => { console.error(error); process.exitCode = 1; }).finally(() => {
  Module._load = originalLoad;
  global.wx = originals.wx;
  global.Page = originals.Page;
});
