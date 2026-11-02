const assert = require('assert');
const path = require('path');
const Module = require('module');

// Exercise real pages and pet-store together; replace only platform I/O and transport.
const root = path.join(__dirname, '..', 'miniprogram');
const requestPath = require.resolve(path.join(root, 'utils/request'));
const originalLoad = Module._load;
const originals = Object.fromEntries(['wx', 'Page', 'getApp', 'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval']
  .map((key) => [key, global[key]]));
const storage = new Map();
let listResponse;
let renameResponse;
let pageDefinition;
let timerId = 0;
const copy = (value) => value === undefined ? undefined : JSON.parse(JSON.stringify(value));

global.wx = {
  getStorageSync(key) { return copy(storage.get(key)) || ''; },
  setStorageSync(key, value) { storage.set(key, copy(value)); },
  removeStorageSync(key) { storage.delete(key); },
  showToast() {},
  navigateTo() {},
  vibrateShort() {}
};
global.getApp = () => ({ globalData: {}, silentLogin: async () => {} });
global.Page = (definition) => { pageDefinition = definition; };
global.setTimeout = () => ++timerId;
global.clearTimeout = () => {};
global.setInterval = () => ++timerId;
global.clearInterval = () => {};

const transport = {
  get(url) {
    assert.strictEqual(url, '/pet/list', 'unexpected transport read in pet consistency test');
    return listResponse();
  },
  put(url, payload) {
    assert.strictEqual(url, '/pet/update');
    return renameResponse(payload);
  },
  post() { throw new Error('Unexpected transport write'); }
};
Module._load = function (request, parent, isMain) {
  if (Module._resolveFilename(request, parent) === requestPath) return transport;
  return originalLoad.call(this, request, parent, isMain);
};

const petStore = require(path.join(root, 'utils/pet-store'));
require(path.join(root, 'pages/home/home'));
const homeDefinition = pageDefinition;
require(path.join(root, 'pages/album/album'));
const albumDefinition = pageDefinition;

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function vo(id, date, nickname = id) {
  return {
    id, nickname, prototype: id === 'a' ? '锦鲤' : '玉兔',
    hatchStatus: 'HATCHED', createDate: date,
    hatchStartTime: date, expectedHatchTime: date,
    collectionCards: [{ id: `card-${id}`, createDate: date, source: 'HATCH' }]
  };
}

const a = vo('a', '2026-10-01 00:00:00', '宝宝A');
const b = vo('b', '2026-10-02 00:00:00', '宝宝B');
const c = vo('c', '2026-10-03 00:00:00', '宝宝C');

function seed(list = [a, b], activeId = 'a') {
  petStore.clearAccountData();
  storage.clear();
  petStore.saveUser({ id: 42 });
  list.forEach((pet) => petStore.savePetFromVO(copy(pet)));
  petStore.setActivePet(petStore.getPetById(activeId));
  listResponse = async () => copy(list);
  renameResponse = async ({ id, nickname }) => ({ ...copy(list.find((pet) => pet.id === id)), nickname });
}

function makePage(definition) {
  return {
    ...definition,
    data: copy(definition.data),
    setData(changes, callback) {
      this.data = { ...this.data, ...changes };
      if (callback) callback();
    }
  };
}

function makeHome() {
  const page = makePage(homeDefinition);
  // These visual paths do not participate in pet selection or cache writes.
  ['syncTabBar', 'configureLayoutMetrics', 'refreshEnvironment', 'refreshStoryState',
    'maybePromptPetName', 'loadStarBalance', 'loadDailyNote', 'showFeedback']
    .forEach((method) => { page[method] = () => {}; });
  page.restoreDoodleArt = () => Promise.resolve();
  page.prefetchPetAssets = () => Promise.resolve();
  const pets = petStore.getAllPets().sort((left, right) => left.createdAt - right.createdAt);
  const pet = petStore.getPet();
  page.setData({
    pets: copy(pets), currentPetIndex: pets.findIndex((item) => item.id === pet.id),
    authChecked: true
  });
  page.renderPet(copy(pet));
  return page;
}

async function flush() {
  for (let index = 0; index < 12; index += 1) await Promise.resolve();
}

async function switchTo(page, id) {
  const index = page.data.pets.findIndex((pet) => pet.id === id);
  assert.ok(index >= 0, `pet ${id} must be visible before switching`);
  page.switchCurrentPet(index);
  await flush();
}

const cases = [
  ['explicit NFC selection survives the first home refresh', async () => {
    seed();
    petStore.savePetFromVO(copy(b));
    const page = makeHome();
    await page.loadPetFromServer();
    assert.strictEqual(page.data.pet.id, 'b', 'first home refresh must honor an explicit go-see-this-pet choice');
    assert.strictEqual(petStore.getPet().id, 'b');
  }],
  ['home removes deleted pets and selects the earliest remaining pet', async () => {
    seed([a, b, c]);
    const page = makeHome();
    listResponse = async () => copy([c, b]);
    await page.loadPetFromServer();
    assert.strictEqual(page.data.pet.id, 'b');
    assert.strictEqual(petStore.getPet().id, 'b');
    assert.deepStrictEqual(page.data.pets.map((pet) => pet.id), ['b', 'c']);
    assert.strictEqual(petStore.getPetById('a'), null, 'deleted pet must not remain in real cache');
  }],
  ['home successful empty list clears the visible and cached current pet', async () => {
    seed();
    const page = makeHome();
    listResponse = async () => [];
    await page.loadPetFromServer();
    assert.strictEqual(page.data.pet, null, 'empty successful response must clear visible pet');
    assert.strictEqual(page.data.stage, 'empty');
    assert.deepStrictEqual(page.data.pets, []);
    assert.strictEqual(petStore.getPet(), null);
    assert.strictEqual(petStore.getActivePetId(), null);
    assert.deepStrictEqual(petStore.getAllPets(), []);
  }],
  ['late failed refresh cannot replace a newer successful empty state', async () => {
    seed();
    const page = makeHome();
    const old = deferred();
    listResponse = () => old.promise;
    const first = page.loadPetFromServer();
    listResponse = async () => [];
    await page.loadPetFromServer();
    old.reject(new Error('old request failed'));
    await first;
    assert.strictEqual(page.data.pet, null);
    assert.strictEqual(page.data.petRestoreError, '', 'stale error must not replace successful empty state');
    assert.deepStrictEqual(petStore.getAllPets(), []);
  }],
  ['home failed refresh keeps the previous list and current selection', async () => {
    seed([a, b], 'b');
    const page = makeHome();
    listResponse = async () => { throw new Error('network unavailable'); };
    await page.loadPetFromServer();
    assert.strictEqual(page.data.pet.id, 'b');
    assert.strictEqual(petStore.getPet().id, 'b');
    assert.deepStrictEqual(page.data.pets.map((pet) => pet.id), ['a', 'b']);
    assert.strictEqual(petStore.getAllPets().length, 2);
  }],
  ['latest user selection wins over an in-flight home refresh', async () => {
    seed();
    const page = makeHome();
    const pending = deferred();
    listResponse = () => pending.promise;
    const refresh = page.loadPetFromServer();
    await switchTo(page, 'b');
    pending.resolve(copy([a, b]));
    await refresh;
    assert.strictEqual(page.data.pet.id, 'b', 'refresh must not restore the selection captured before the request');
    assert.strictEqual(petStore.getPet().id, 'b');
    assert.strictEqual(page.data.currentPetIndex, 1);
  }],
  ['switching via stale page objects cannot overwrite a newer cached nickname', async () => {
    seed();
    const page = makeHome();
    const result = await petStore.updateNickname('新版名字');
    assert.strictEqual(result.ok, true);
    assert.strictEqual(page.data.pets[0].name, '宝宝A', 'test retains the old page list snapshot');
    await switchTo(page, 'b');
    await switchTo(page, 'a');
    assert.strictEqual(page.data.pet.name, '新版名字');
    assert.strictEqual(petStore.getPetById('a').name, '新版名字');
  }],
  ['late nickname response updates A without changing current pet B', async () => {
    seed();
    const page = makeHome();
    const pending = deferred();
    renameResponse = () => pending.promise;
    page.setData({ nameDraft: 'A的新名字', showNameSheet: true });
    const saving = page.onSaveName();
    await switchTo(page, 'b');
    pending.resolve({ ...copy(a), nickname: 'A的新名字' });
    await saving;
    assert.strictEqual(page.data.pet.id, 'b');
    assert.strictEqual(page.data.pet.name, '宝宝B', 'A response must not rename B in the visible page');
    assert.strictEqual(petStore.getPet().id, 'b');
    assert.strictEqual(petStore.getPetById('a').name, 'A的新名字');
  }],
  ['album authoritative refresh also repairs real current selection', async () => {
    seed([a, b, c]);
    listResponse = async () => copy([c, b]);
    const page = makePage(albumDefinition);
    await page.onShow();
    assert.strictEqual(petStore.getPet().id, 'b', 'album must not leave a deleted current pet in cache');
    assert.strictEqual(petStore.getPetById('a'), null);
    assert.deepStrictEqual(page.data.cards.map((card) => card.petId), ['b', 'c']);
  }]
];

(async () => {
  let failures = 0;
  for (const [name, run] of cases) {
    try {
      await run();
      console.log(`PASS ${name}`);
    } catch (error) {
      failures += 1;
      console.error(`FAIL ${name}: ${error.message}`);
    }
  }
  assert.strictEqual(failures, 0, `${failures} pet/page consistency cases failed`);
  console.log('pet-consistency.test.js: ALL PASS');
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
}).finally(() => {
  Module._load = originalLoad;
  Object.entries(originals).forEach(([key, value]) => {
    if (value === undefined) delete global[key];
    else global[key] = value;
  });
});
