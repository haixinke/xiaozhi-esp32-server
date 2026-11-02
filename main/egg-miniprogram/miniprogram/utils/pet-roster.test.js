const assert = require('assert');
const Module = require('module');

const clone = (value) => value === undefined ? undefined : JSON.parse(JSON.stringify(value));
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function setup() {
  const storage = new Map();
  global.wx = {
    getStorageSync: (key) => clone(storage.get(key)),
    setStorageSync: (key, value) => storage.set(key, clone(value)),
    removeStorageSync: (key) => storage.delete(key)
  };
  const api = { listPets: async () => [], submitHatchAction: async () => {}, updateNickname: async () => {}, hatchPet: async () => {} };
  const originalLoad = Module._load;
  Module._load = function (request) {
    if (request === './pet-api') return api;
    return originalLoad.apply(this, arguments);
  };
  for (const name of ['./pet-store', './pet-roster']) {
    try { delete require.cache[require.resolve(name)]; } catch (error) {}
  }
  let store;
  try { store = require('./pet-store'); } finally { Module._load = originalLoad; }
  store.saveUser({ id: 'user-1' });
  return { store, api, storage };
}
const vo = (id, date, nickname = id) => ({ id, nickname, prototype: '玉兔', createDate: date, hatchStatus: 'HATCHED' });

(async () => {
  const { store, api } = setup();
  api.listPets = async () => [vo('new', '2026-10-02'), vo('old', '2026-10-01')];
  const snapshot = await store.refreshPets();
  assert.deepStrictEqual(snapshot.pets.map((pet) => pet.id), ['old', 'new']);
  assert.strictEqual(snapshot.pet.id, 'old');
  assert.strictEqual(snapshot.currentPetIndex, 0);
  assert.strictEqual(snapshot.pets[0].petType, '玉兔');
  store.selectPet('new');
  api.listPets = async () => [vo('new', '2026-10-02'), vo('old', '2026-10-01')];
  assert.strictEqual((await store.refreshPets()).pet.id, 'new', 'refresh preserves the current choice');
  api.listPets = async () => [vo('old', '2026-10-01')];
  assert.strictEqual((await store.refreshPets()).pet.id, 'old', 'deleted choice falls back to oldest');
  assert.strictEqual(store.getPetById('new'), null, 'deleted pet disappears from cache');
  api.listPets = async () => [];
  assert.deepStrictEqual(await store.refreshPets(), { pets: [], pet: null, currentPetIndex: 0 });
  assert.strictEqual(store.getPet(), null, 'empty complete list clears legacy current pet');
  assert.strictEqual(store.getActivePetId(), null, 'empty complete list clears selected id');
  {
    const { store: pets, api: server, storage: cache } = setup();
    server.listPets = async () => [vo('a', '2026-10-01'), vo('b', '2026-10-02')];
    await pets.refreshPets();
    cache.set('eggbaby_mvp_pet_v1', pets.getPetById('b'));
    cache.set('eggbaby_active_pet_v1', 'b');
    assert.strictEqual((await pets.refreshPets({ resetSelection: true })).pet.id, 'a', 'cold cached choice defaults to oldest');
    const pending = deferred();
    server.listPets = () => pending.promise;
    const refresh = pets.refreshPets({ resetSelection: true });
    pets.selectPet('b');
    pending.resolve([vo('a', '2026-10-01'), vo('b', '2026-10-02', '服务端新名字')]);
    const choice = await refresh;
    assert.strictEqual(choice.pet.id, 'b', 'explicit choice during reset refresh wins');
    assert.strictEqual(choice.pet.name, '服务端新名字', 'selection alone does not block fresh server data');
    const beforeFailure = pets.getPetSnapshot();
    server.listPets = async () => { throw new Error('offline'); };
    await assert.rejects(pets.refreshPets(), /offline/);
    assert.deepStrictEqual(pets.getPetSnapshot(), beforeFailure, 'offline preserves cached list and choice');
    server.listPets = async () => null;
    await assert.rejects(pets.refreshPets(), /宠物列表/);
    assert.deepStrictEqual(pets.getPetSnapshot(), beforeFailure, 'invalid list is not authoritative empty');
  }
  {
    const { store: pets, api: server } = setup();
    const old = deferred();
    server.listPets = () => old.promise;
    const first = pets.refreshPets();
    server.listPets = async () => [vo('fresh', '2026-10-01')];
    await pets.refreshPets();
    old.resolve([vo('stale', '2026-10-01')]);
    assert.strictEqual(await first, null, 'older global refresh is ignored');
    assert.strictEqual(pets.getPet().id, 'fresh');
    const logout = deferred();
    server.listPets = () => logout.promise;
    const afterLogout = pets.refreshPets();
    pets.clearAccountData();
    pets.saveUser({ id: 'user-1' });
    logout.resolve([vo('ghost', '2026-10-01')]);
    assert.strictEqual(await afterLogout, null, 'logout invalidates even same-account relogin');
    assert.strictEqual(pets.getPet(), null);
    const switchAccount = deferred();
    server.listPets = () => switchAccount.promise;
    const afterSwitch = pets.refreshPets();
    pets.saveUser({ id: 'user-2' });
    switchAccount.resolve([vo('foreign', '2026-10-01')]);
    assert.strictEqual(await afterSwitch, null, 'account switch invalidates list refresh');
    assert.strictEqual(pets.getAllPets().length, 0);
  }
  {
    const { store: pets, api: server, storage: cache } = setup();
    cache.set('eggbaby_mvp_pet_v1', { id: 'legacy', ownerId: 'user-1', prototype: '玉兔', createdAt: 1,
      shell: { color: 'red' }, preferences: { wishes: ['wish'], lessons: [] }, messages: ['message'], _hatchActions: [{ type: 'CUDDLE' }] });
    assert.strictEqual(pets.getPetSnapshot().pet.id, 'legacy', 'old single-pet cache is immediately displayable');
    server.listPets = async () => [vo('legacy', '2026-10-01')];
    const refreshed = (await pets.refreshPets()).pet;
    assert.deepStrictEqual(refreshed.shell, { color: 'red' });
    assert.deepStrictEqual(refreshed.messages, ['message']);
    assert.deepStrictEqual(refreshed._hatchActions, [{ type: 'CUDDLE' }]);
    const oldCopy = refreshed;
    pets.savePet({ ...refreshed, name: 'renamed' });
    assert.strictEqual(pets.selectPet(oldCopy.id).name, 'renamed', 'select by id never writes a stale page copy');
    assert.strictEqual(pets.selectPet('missing'), null);
    assert.strictEqual(pets.getPet().name, 'renamed', 'unknown selection keeps valid current pet');
    pets.savePetFromVO(vo('adopted', '2026-10-02'));
    assert.strictEqual(pets.getPet().id, 'adopted', 'adoption explicitly selects new pet');
    pets.savePet({ ...refreshed, name: 'updated in background' });
    assert.strictEqual(pets.getPet().id, 'adopted', 'ordinary cache update does not select another pet');
    assert.strictEqual(pets.getPetById('legacy').name, 'updated in background');
  }
  for (const action of ['rename', 'lesson', 'hatch']) {
    const { store: pets, api: server } = setup();
    server.listPets = async () => [vo('a', '2026-10-01'), vo('b', '2026-10-02')];
    await pets.refreshPets();
    const pending = deferred();
    server.updateNickname = () => pending.promise;
    server.submitHatchAction = () => pending.promise;
    server.hatchPet = () => pending.promise;
    if (action === 'hatch') pets.savePet({ ...pets.getPet(), hatchStatus: 'EGG', hatchAt: 1 });
    const completion = action === 'rename' ? pets.updateNickname('新名字')
      : action === 'lesson' ? pets.completeLesson('学会勇敢') : pets.createCollectionCard();
    pets.selectPet('b');
    const response = { ...vo('a', '2026-10-01', '新名字'), mbti: 'ENFP' };
    pending.resolve(action === 'lesson' ? { pet: response, alreadyDone: false } : response);
    const result = await completion;
    assert.strictEqual(result.ok, true, action + ' completes for original pet');
    assert.strictEqual(pets.getPet().id, 'b', action + ' does not steal current selection');
    assert.strictEqual(pets.getPetById('a').name, '新名字');
    if (action === 'lesson') assert.strictEqual(pets.getPetById('a').preferences.lessons[0].value, '学会勇敢');
  }
  for (const change of ['delete', 'logout', 'account-switch']) {
    const { store: pets, api: server } = setup();
    server.listPets = async () => [vo('a', '2026-10-01')];
    await pets.refreshPets();
    const pending = deferred();
    server.updateNickname = () => pending.promise;
    const completion = pets.updateNickname('新名字');
    if (change === 'delete') {
      server.listPets = async () => [];
      await pets.refreshPets();
    } else if (change === 'logout') {
      pets.clearAccountData();
      pets.saveUser({ id: 'user-1' });
    } else pets.saveUser({ id: 'user-2' });
    pending.resolve(vo('a', '2026-10-01', '新名字'));
    assert.strictEqual((await completion).ok, false, change + ' invalidates old action response');
    assert.strictEqual(pets.getPet(), null, 'old action cannot restore current pet after ' + change);
    assert.strictEqual(pets.getPetById('a'), null, 'old action cannot resurrect pet after ' + change);
  }
  {
    const { store: pets, api: server } = setup();
    server.listPets = async () => [vo('a', '2026-10-01')];
    await pets.refreshPets();
    const oldCopy = pets.getPet();
    const pending = deferred();
    server.listPets = () => pending.promise;
    const refresh = pets.refreshPets();
    server.updateNickname = async () => vo('a', '2026-10-01', '新名字');
    await pets.updateNickname('新名字');
    pending.resolve([vo('a', '2026-10-01', '旧名字')]);
    await refresh;
    assert.strictEqual(pets.getPet().name, '新名字', 'refresh started before rename cannot overwrite its result');
    server.listPets = async () => [];
    await pets.refreshPets();
    assert.strictEqual(pets.savePet(oldCopy), null, 'ordinary cache write cannot resurrect an absent pet');
    assert.strictEqual(pets.getPet(), null);
    pets.savePetFromVO(vo('adopted', '2026-10-02'));
    assert.strictEqual(pets.getPet().id, 'adopted', 'explicit adoption can add pet after an authoritative empty list');
  }
  {
    const { store: pets, api: server } = setup();
    server.listPets = async () => [vo('a', '2026-10-01')];
    await pets.refreshPets();
    const pending = deferred();
    server.listPets = () => pending.promise;
    const refresh = pets.refreshPets();
    pets.savePetFromVO(vo('adopted', '2026-10-02'));
    pending.resolve([vo('a', '2026-10-01')]);
    assert.strictEqual(await refresh, null, 'pre-adoption list cannot delete the new pet');
    assert.strictEqual(pets.getPet().id, 'adopted');
    assert.strictEqual(pets.getPetById('adopted').id, 'adopted');
  }
  {
    const { store: pets, api: server } = setup();
    const old = deferred();
    server.listPets = () => old.promise;
    const first = pets.refreshPets();
    server.listPets = async () => [];
    await pets.refreshPets();
    old.reject(new Error('old offline response'));
    assert.strictEqual(await first, null, 'obsolete failures do not replace a newer success with an error');
    assert.deepStrictEqual(pets.getPetSnapshot(), { pets: [], pet: null, currentPetIndex: 0 });
  }
  for (const entry of ['nfc', 'selection']) {
    const { store: pets, api: server } = setup();
    server.listPets = async () => [vo('a', '2026-10-01'), vo('b', '2026-10-02')];
    if (entry === 'nfc') pets.savePetFromVO(vo('b', '2026-10-02'));
    else {
      await pets.refreshPets();
      pets.selectPet('b');
    }
    assert.strictEqual((await pets.refreshPets({ resetSelection: true })).pet.id, 'b',
      entry + ' explicit intent before first home refresh must win over default oldest');
  }
  console.log('pet-roster.test.js: ALL PASS');
})().catch((error) => { console.error(error); process.exitCode = 1; });
