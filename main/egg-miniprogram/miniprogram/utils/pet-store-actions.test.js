const assert = require('assert');
const Module = require('module');

// ---- 测试桩：global.wx 用 Map 存储，pet-api 用 Module._load 注入 mock ----
const storage = new Map();
global.wx = {
  getStorageSync(key) { return storage.has(key) ? storage.get(key) : ''; },
  setStorageSync(key, value) { storage.set(key, value); },
  removeStorageSync(key) { storage.delete(key); }
};
global.getApp = () => ({ silentLogin: async () => {} });

let actionCalls = [];
let actionResponses = [];
let hatchCalls = 0;
let hatchResponse = null;
let updateNicknameCalls = [];
let updateNicknameResponse = null;
const originalLoad = Module._load;
Module._load = function (req) {
  if (req === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
  if (req === './auth') return { getSession: () => ({ token: 't' }), clearSession: () => {} };
  if (req === './pet-api') {
    return {
      submitHatchAction: async (petId, type, payload) => {
        actionCalls.push({ petId, type, payload });
        return actionResponses.shift();
      },
      listHatchActions: async () => [],
      hatchPet: async () => { hatchCalls += 1; return hatchResponse; },
      updateNickname: async (petId, nickname) => {
        updateNicknameCalls.push({ petId, nickname });
        return updateNicknameResponse;
      }
    };
  }
  return originalLoad.apply(this, arguments);
};

const petStore = require('./pet-store');

const DAY = 24 * 60 * 60 * 1000;
function todayKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

(async () => {
  petStore.saveUser({ id: 42, nickname: '蛋友' });

  // === demo 模式：走本地 mock，不调后端 ===
  const demoPet = {
    id: 'demo-1', ownerId: 42, prototype: '玉兔', name: '', createdAt: Date.now(),
    hatchAt: Date.now() + 7 * DAY, progress: 0, stage: 'waiting',
    lastInteractionAt: Date.now(),
    tasks: { nicknameDone: false, cuddleDate: '', wishDate: '', lessonDate: '', doodleDone: false },
    preferences: { wishes: [], lessons: [] },
    shell: { color: '#EDE78E', colorName: '奶油白', pattern: '星星' },
    dailyStatus: null, collectionCard: null, inviteCodes: [], messages: [],
    hatchStatus: 'EGG', acceleratedMinutes: 0, demoMode: true
  };
  petStore.savePet(demoPet);
  actionCalls = [];
  const demoResult = await petStore.completeLesson('学会勇敢');
  assert.strictEqual(demoResult.ok, true, 'demo lesson ok');
  assert.strictEqual(demoResult.alreadyDone, false, 'demo first lesson not alreadyDone');
  assert.strictEqual(actionCalls.length, 0, 'demo path does not call pet-api');

  // getHatchActionState：demo 从 pet.tasks 读（storage 同引用，completeLesson 已写回 lessonDate）
  const demoState = petStore.getHatchActionState(petStore.getPet());
  assert.strictEqual(demoState.lessonDone, true, 'demo lessonDone from tasks');

  // === 非 demo 模式：走后端 ===
  actionCalls = [];
  const realPet = {
    ...demoPet,
    id: 'real-1', demoMode: false,
    tasks: { nicknameDone: false, cuddleDate: '', wishDate: '', lessonDate: '', doodleDone: false },
    preferences: { wishes: [], lessons: [] }
  };
  petStore.savePet(realPet);
  actionResponses = [{
    addedMinutes: 60, alreadyDone: false, readyToHatch: false,
    pet: { id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 60, prototype: '玉兔',
           expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString() }
  }];
  const realResult = await petStore.completeLesson('学会勇敢');
  assert.strictEqual(realResult.ok, true, 'real lesson ok');
  assert.strictEqual(realResult.alreadyDone, false, 'real first lesson not alreadyDone');
  assert.strictEqual(actionCalls.length, 1, 'real path calls pet-api once');
  assert.strictEqual(actionCalls[0].type, 'LESSON');
  assert.deepStrictEqual(actionCalls[0].payload, { value: '学会勇敢' });

  // 非 demo 幂等：后端返回 alreadyDone=true
  actionResponses = [{
    addedMinutes: 0, alreadyDone: true, readyToHatch: false,
    pet: { id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 60, prototype: '玉兔',
           expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString() }
  }];
  const dup = await petStore.completeLesson('学会勇敢');
  assert.strictEqual(dup.ok, true, 'real dup ok');
  assert.strictEqual(dup.alreadyDone, true, 'real duplicate alreadyDone');
  assert.strictEqual(petStore.getPet().preferences.lessons.length, 1, 'alreadyDone retry does not double-push preferences');

  // === NICKNAME 非 demo ===
  actionResponses = [{
    addedMinutes: 720, alreadyDone: false, readyToHatch: false,
    pet: { id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 720, prototype: '玉兔',
           expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString() }
  }];
  const nickResult = await petStore.updateNickname('小金');
  assert.strictEqual(nickResult.ok, true, 'nickname ok');
  assert.strictEqual(nickResult.alreadyDone, false, 'first nickname not alreadyDone');
  assert.strictEqual(actionCalls.at(-1).type, 'NICKNAME');
  assert.deepStrictEqual(actionCalls.at(-1).payload, { nickname: '小金' });
  assert.strictEqual(petStore.getPet().name, '小金', 'nickname cached on pet');

  // === NICKNAME 非 demo 二次编辑：后端 alreadyDone=true 时走 PUT /pet/update 兜底 ===
  updateNicknameCalls = [];
  actionCalls = [];
  actionResponses = [{
    addedMinutes: 0, alreadyDone: true, readyToHatch: false,
    pet: { id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 720, prototype: '玉兔',
           expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString() }
  }];
  updateNicknameResponse = {
    id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 720, prototype: '玉兔',
    nickname: '小金2', expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString()
  };
  const reeditResult = await petStore.updateNickname('小金2');
  assert.strictEqual(reeditResult.ok, true, 're-edit nickname ok');
  assert.strictEqual(reeditResult.alreadyDone, true, 're-edit alreadyDone true');
  assert.strictEqual(updateNicknameCalls.length, 1, 're-edit calls PUT /pet/update');
  assert.strictEqual(updateNicknameCalls[0].petId, 'real-1', 'PUT called with petId');
  assert.strictEqual(updateNicknameCalls[0].nickname, '小金2', 'PUT called with new nickname');
  assert.strictEqual(reeditResult.pet.name, '小金2', 're-edit pet.name is new value');
  assert.strictEqual(petStore.getPet().name, '小金2', 're-edit nickname cached on pet');

  // === DOODLE 非 demo ===
  actionResponses = [{
    addedMinutes: 720, alreadyDone: false, readyToHatch: false,
    pet: { id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 1440, prototype: '玉兔',
           expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString() }
  }];
  const doodleResult = await petStore.saveDoodle('#FFD700', '金色', '波点');
  assert.strictEqual(doodleResult.ok, true, 'doodle ok');
  assert.strictEqual(actionCalls.at(-1).type, 'DOODLE');
  assert.deepStrictEqual(actionCalls.at(-1).payload, { color: '#FFD700', colorName: '金色', pattern: '波点' });
  assert.strictEqual(petStore.getPet().shell.colorName, '金色', 'shell cached on pet');

  // === 错误路径：后端 reject（business error）===
  const Module2 = require('module');
  // 临时换 pet-api mock 抛 business error
  const originalLoad2 = Module2._load;
  Module2._load = function (req) {
    if (req === '../config/api') return { API_BASE_URL: 'https://api.example/xiaozhi' };
    if (req === './auth') return { getSession: () => ({ token: 't' }), clearSession: () => {} };
    if (req === './pet-api') return {
      submitHatchAction: async () => { throw { type: 'business', code: 10209, message: '已破壳', userMessage: '已破壳' }; },
      listHatchActions: async () => [], hatchPet: async () => ({})
    };
    return originalLoad2.apply(this, arguments);
  };
  delete require.cache[require.resolve('./pet-store')];
  const petStoreErr = require('./pet-store');
  petStoreErr.saveUser({ id: 42, nickname: '蛋友' });
  petStoreErr.savePet({ ...realPet, id: 'real-1' });
  const errResult = await petStoreErr.completeLesson('学会勇敢');
  assert.strictEqual(errResult.ok, false, 'error path ok=false');
  assert.strictEqual(errResult.message, '已破壳', 'error path surfaces userMessage');
  Module2._load = originalLoad2;

  // === CUDDLE 非 demo：空 payload {}，type=CUDDLE ===
  actionCalls = [];
  actionResponses = [{
    addedMinutes: 60, alreadyDone: false, readyToHatch: false,
    pet: { id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 1500, prototype: '玉兔',
           expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString() }
  }];
  const cuddleResult = await petStore.completeCuddle();
  assert.strictEqual(cuddleResult.ok, true, 'cuddle ok');
  assert.strictEqual(actionCalls.length, 1, 'cuddle calls pet-api once');
  assert.strictEqual(actionCalls[0].type, 'CUDDLE', 'cuddle type CUDDLE');
  assert.deepStrictEqual(actionCalls[0].payload, {}, 'cuddle payload empty object');

  // === WISH 非 demo：{value} payload，type=WISH ===
  actionCalls = [];
  actionResponses = [{
    addedMinutes: 60, alreadyDone: false, readyToHatch: false,
    pet: { id: 'real-1', hatchStatus: 'EGG', acceleratedMinutes: 1560, prototype: '玉兔',
           expectedHatchTime: new Date(Date.now() + 7 * DAY).toISOString() }
  }];
  const wishResult = await petStore.completeWish('安静陪伴你');
  assert.strictEqual(wishResult.ok, true, 'wish ok');
  assert.strictEqual(actionCalls.length, 1, 'wish calls pet-api once');
  assert.strictEqual(actionCalls[0].type, 'WISH', 'wish type WISH');
  assert.deepStrictEqual(actionCalls[0].payload, { value: '安静陪伴你' }, 'wish payload {value}');

  // === getHatchActionState 非 demo：one-time vs daily 派生 ===
  const today = todayKey();
  const yesterday = '2020-01-01';
  const statePet = {
    id: 'real-1', ownerId: 42, demoMode: false,
    _hatchActions: [
      { actionType: 'NICKNAME', actionDate: yesterday },
      { actionType: 'DOODLE', actionDate: yesterday },
      { actionType: 'CUDDLE', actionDate: today },
      { actionType: 'LESSON', actionDate: yesterday },
      { actionType: 'WISH', actionDate: today }
    ]
  };
  const hatchState = petStore.getHatchActionState(statePet);
  assert.strictEqual(hatchState.nicknameDone, true, 'nickname one-time ignores date');
  assert.strictEqual(hatchState.doodleDone, true, 'doodle one-time ignores date');
  assert.strictEqual(hatchState.cuddleDone, true, 'cuddle done today');
  assert.strictEqual(hatchState.lessonDone, false, 'lesson not done today');
  assert.strictEqual(hatchState.wishDone, true, 'wish done today');

  // === createCollectionCard 双轨：非 demo 走 POST /pet/{id}/hatch ===
  petStore.saveUser({ id: 42, nickname: '蛋友' });
  const readyPet = {
    id: 'real-2', ownerId: 42, prototype: '锦鲤', name: '小金', createdAt: Date.now(),
    hatchAt: Date.now() - 1000, progress: 30, stage: 'ready',
    lastInteractionAt: Date.now(),
    tasks: { nicknameDone: true, cuddleDate: '', wishDate: '', lessonDate: '', doodleDone: false },
    preferences: { wishes: [], lessons: [] },
    shell: { color: '#EDE78E', colorName: '奶油白', pattern: '星星' },
    dailyStatus: null, collectionCard: null, inviteCodes: [], messages: [],
    hatchStatus: 'EGG', acceleratedMinutes: 3000, demoMode: false,
    expectedHatchTime: Date.now() - 1000, hatchStartTime: Date.now() - 7 * DAY
  };
  petStore.savePet(readyPet);
  hatchCalls = 0;
  hatchResponse = {
    id: 'real-2', hatchStatus: 'HATCHED', prototype: '锦鲤', nickname: '小金',
    acceleratedMinutes: 3000, hatchedAt: new Date(Date.now()).toISOString(),
    mbti: 'ENFP', personality: '热烈又好奇', gender: 'FEMALE', bloodType: 'A',
    zodiac: 'aquarius', avatarUrl: 'https://img/koi.png'
  };
  const hatchResult = await petStore.createCollectionCard();
  assert.strictEqual(hatchResult.ok, true, 'hatch ok');
  assert.strictEqual(hatchResult.created, true, 'hatch created');
  assert.strictEqual(hatchCalls, 1, 'called hatchPet once');
  assert.strictEqual(hatchResult.card.prototype, '锦鲤', 'card prototype from vo');
  assert.strictEqual(hatchResult.card.mbti, 'ENFP', 'card mbti from vo');
  assert.strictEqual(hatchResult.card.gender, 'FEMALE', 'card gender from vo');
  assert.ok(hatchResult.card.serial.startsWith('EGG-KOI-'), 'card serial from hatchedAt');
  assert.strictEqual(hatchResult.card.hatchQuality, '轻量孵化', '3000/10080 ≈ 30% < 80% -> 轻量孵化');
  const storedHatched = petStore.getPet();
  assert.strictEqual(storedHatched.collectionCard && storedHatched.collectionCard.serial, hatchResult.card.serial, 'card cached on pet');
  assert.strictEqual(storedHatched.hatchStatus, 'HATCHED', 'pet hatchStatus updated');

  console.log('pet-store-actions.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
