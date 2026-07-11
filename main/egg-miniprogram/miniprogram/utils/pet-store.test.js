const assert = require('assert');

const storage = new Map();
global.wx = {
  getStorageSync(key) { return storage.has(key) ? storage.get(key) : ''; },
  setStorageSync(key, value) { storage.set(key, value); },
  removeStorageSync(key) { storage.delete(key); }
};

const petStore = require('./pet-store');

const accountKeys = [
  'eggbaby_mvp_pet_v1',
  'eggbaby_mvp_user_v1',
  'eggbaby_mvp_identity_v1',
  'eggbaby_exhibition_backup_v1',
  'eggbaby_active_pet_v1'
];

accountKeys.forEach((key) => storage.set(key, { value: key }));
storage.set('eggbaby_theme', 'light');

petStore.clearAccountData();

accountKeys.forEach((key) => assert.strictEqual(storage.has(key), false,
  `account key ${key} should be removed`));
assert.strictEqual(storage.get('eggbaby_theme'), 'light',
  'non-account preference should be preserved');

// --- savePetFromVO: 字段映射与 stage 派生 ---
petStore.saveUser({ id: 42, nickname: '蛋友' });

const now = Date.now();
const sevenDays = 7 * 24 * 60 * 60 * 1000;
const eggVO = {
  id: 'pet-1',
  userId: 42,
  deviceId: null,
  nickname: '',
  hatchStatus: 'EGG',
  hatchStartTime: new Date(now).toISOString(),
  expectedHatchTime: new Date(now + sevenDays).toISOString(),
  acceleratedMinutes: 0,
  prototype: '锦鲤',
  createDate: new Date(now).toISOString(),
  todayMood: '开心',
  todayMoodSentence: '蛋壳里传来轻轻的回应。'
};
const egg = petStore.savePetFromVO(eggVO);
assert.strictEqual(egg.id, 'pet-1', 'savePetFromVO maps id');
assert.strictEqual(egg.prototype, '锦鲤', 'savePetFromVO maps prototype');
assert.strictEqual(egg.name, '', 'savePetFromVO maps empty nickname');
assert.strictEqual(egg.progress, 0, 'no accelerated minutes -> 0% progress');
assert.strictEqual(egg.hatchStatus, 'EGG', 'savePetFromVO maps hatchStatus');
assert.strictEqual(egg.hatchAt, now + sevenDays, 'hatchAt derived from expectedHatchTime');
assert.strictEqual(egg.collectionCard, null, 'EGG has no collectionCard');
assert.strictEqual(egg.todayMood, '开心', 'savePetFromVO maps todayMood');
assert.strictEqual(petStore.getActivePetId(), 'pet-1', 'activePetId set on save');

// stage: EGG + 远未到破壳 -> waiting
assert.strictEqual(petStore.getStage(egg), 'waiting', 'EGG far from hatch -> waiting');
// stage: EGG + 即将破壳(<=1天) -> soon
const soonVO = { ...eggVO, expectedHatchTime: new Date(now + 3 * 60 * 60 * 1000).toISOString() };
const soonPet = petStore.savePetFromVO(soonVO);
assert.strictEqual(petStore.getStage(soonPet), 'soon', 'EGG within 1 day -> soon');
// stage: EGG + 已到破壳时间 -> ready
const readyVO = { ...eggVO, expectedHatchTime: new Date(now - 1000).toISOString() };
const readyPet = petStore.savePetFromVO(readyVO);
assert.strictEqual(petStore.getStage(readyPet), 'ready', 'EGG past expected -> ready');
// stage: 已破壳 -> hatched
const hatchedVO = { ...eggVO, hatchStatus: 'HATCHED', expectedHatchTime: new Date(now - 8 * 24 * 60 * 60 * 1000).toISOString() };
const hatchedPet = petStore.savePetFromVO(hatchedVO);
assert.strictEqual(petStore.getStage(hatchedPet), 'hatched', 'HATCHED -> hatched');

// progress 派生: acceleratedMinutes=720(12h) -> round(720/10080*100)=7
const partialVO = { ...eggVO, acceleratedMinutes: 720 };
const partialPet = petStore.savePetFromVO(partialVO);
assert.strictEqual(partialPet.progress, 7, 'progress derived from acceleratedMinutes');
assert.strictEqual(petStore.getStage(partialPet), 'hatching', 'progress>0 -> hatching');

// getDailyStatus 优先用后端 mood
petStore.savePetFromVO(eggVO);
const daily = petStore.getDailyStatus();
assert.strictEqual(daily.mood, '开心', 'getDailyStatus prefers backend todayMood');
assert.strictEqual(daily.line, '蛋壳里传来轻轻的回应。', 'getDailyStatus uses backend sentence');
assert.strictEqual(daily.source, 'backend', 'getDailyStatus marked backend source');

// getCountdown 用 expectedHatchTime
assert.ok(petStore.getCountdown(egg).includes('还剩'), 'getCountdown returns remaining text');

// 无后端心情时走本地 fallback
const noMoodVO = { ...eggVO, todayMood: '', todayMoodSentence: '' };
petStore.savePetFromVO(noMoodVO);
const fallbackDaily = petStore.getDailyStatus();
assert.ok(fallbackDaily, 'getDailyStatus local fallback returns a status');
assert.notStrictEqual(fallbackDaily.source, 'backend', 'fallback not marked backend');

petStore.clearAccountData();
assert.strictEqual(petStore.getActivePetId(), null, 'activePetId cleared on account clear');

console.log('pet-store.test.js: ALL PASS');
