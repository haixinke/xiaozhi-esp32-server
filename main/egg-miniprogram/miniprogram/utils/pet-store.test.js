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
  'eggbaby_active_pet_v1',
  'eggbaby_profile_v1',
  'eggbaby_deregister_request_v1'
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
const HATCH_TOTAL_MINUTES = 7 * 24 * 60;
const pad2 = (n) => String(n).padStart(2, '0');
const dateKey = (ms) => {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
};
const todayStr = dateKey(now);
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
  todayMoodSentence: '蛋壳里传来轻轻的回应。',
  todayMoodDate: todayStr
};
const egg = petStore.savePetFromVO(eggVO);
assert.strictEqual(egg.id, 'pet-1', 'savePetFromVO maps id');
assert.strictEqual(egg.prototype, '锦鲤', 'savePetFromVO maps prototype');
assert.strictEqual(egg.name, '', 'savePetFromVO maps empty nickname');
assert.strictEqual(egg.progress, 0, 'no accelerated minutes -> 0% progress');
assert.strictEqual(egg.hatchStatus, 'EGG', 'savePetFromVO maps hatchStatus');
assert.strictEqual(egg.hatchAt, now + sevenDays, 'hatchAt derived from expectedHatchTime');
assert.strictEqual(egg.collectionCards.length, 0, 'EGG has empty collectionCards');
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

// getDailyStatus 优先用后端 mood(todayMoodDate 为今天)
petStore.savePetFromVO(eggVO);
const daily = petStore.getDailyStatus();
assert.strictEqual(daily.mood, '开心', 'getDailyStatus prefers backend todayMood');
assert.strictEqual(daily.line, '蛋壳里传来轻轻的回应。', 'getDailyStatus uses backend sentence');
assert.strictEqual(daily.source, 'backend', 'getDailyStatus marked backend source');

// 跨天旧缓存：todayMoodDate 是昨天 → 不能再用后端旧心情，防止“今日状态三天不变”
const staleMoodVO = { ...eggVO, todayMood: '低落', todayMoodSentence: '它今天有一点点没精神。', todayMoodDate: dateKey(now - 24 * 60 * 60 * 1000) };
petStore.savePetFromVO(staleMoodVO);
const staleDaily = petStore.getDailyStatus();
assert.notStrictEqual(staleDaily.source, 'backend', 'stale todayMoodDate must not be served as backend mood');

// getCountdown 用 expectedHatchTime
assert.ok(petStore.getCountdown(egg).includes('还剩'), 'getCountdown returns remaining text');

// 无后端心情时走本地 fallback
const noMoodVO = { ...eggVO, todayMood: '', todayMoodSentence: '' };
petStore.savePetFromVO(noMoodVO);
const fallbackDaily = petStore.getDailyStatus();
assert.ok(fallbackDaily, 'getDailyStatus local fallback returns a status');
assert.notStrictEqual(fallbackDaily.source, 'backend', 'fallback not marked backend');

// savePetFromVO 不得把 recordTouch 写入的互动时间重置回 createdAt，
// 否则老宠物本地 fallback 心情 inactiveDays 恒大 → 永远"低落"
const oldPetVO = {
  ...eggVO,
  createDate: new Date(now - 10 * 24 * 60 * 60 * 1000).toISOString(),
  hatchStartTime: new Date(now - 10 * 24 * 60 * 60 * 1000).toISOString()
};
petStore.savePetFromVO(oldPetVO);
petStore.recordTouch();
const refreshed = petStore.savePetFromVO(oldPetVO);
assert.ok(refreshed.lastInteractionAt >= now, 'savePetFromVO preserves recordTouch lastInteractionAt');

petStore.clearAccountData();
assert.strictEqual(petStore.getActivePetId(), null, 'activePetId cleared on account clear');

// --- savePetFromVO: 破壳后身份字段映射 ---
const hatchedIdentityVO = {
  ...eggVO,
  hatchStatus: 'HATCHED',
  expectedHatchTime: new Date(now - 8 * 24 * 60 * 60 * 1000).toISOString(),
  hatchedAt: new Date(now - 8 * 24 * 60 * 60 * 1000).toISOString(),
  bazi: '庚子', wuxing: '金水', zodiac: 'aquarius',
  mbti: 'ENFP', personality: '热烈又好奇', personalityBrief: '好运小福星',
  gender: 'FEMALE', bloodType: 'A', avatarUrl: 'https://img/koi.png',
  sceneUrl: 'https://oss.eggbabe.com/default-scenes/fish/scenes-fish-3.jpg',
  todayMoodDate: petStore.todayKey()
};
const hatchedIdentity = petStore.savePetFromVO(hatchedIdentityVO);
assert.strictEqual(hatchedIdentity.bazi, '庚子', 'maps bazi');
assert.strictEqual(hatchedIdentity.mbti, 'ENFP', 'maps mbti');
assert.strictEqual(hatchedIdentity.personality, '热烈又好奇', 'maps personality');
assert.strictEqual(hatchedIdentity.gender, 'FEMALE', 'maps gender');
assert.strictEqual(hatchedIdentity.bloodType, 'A', 'maps bloodType');
assert.strictEqual(hatchedIdentity.avatarUrl, 'https://img/koi.png', 'maps avatarUrl');
assert.strictEqual(hatchedIdentity.sceneUrl, 'https://oss.eggbabe.com/default-scenes/fish/scenes-fish-3.jpg', 'maps sceneUrl');
assert.strictEqual(hatchedIdentity.zodiac, '水瓶座', 'maps zodiac');

// collectionCards 映射
const cardsVO = {
  ...hatchedIdentityVO,
  collectionCards: [{ id: 'card-1', imageUrl: 'https://img/card.png', brief: 'test', source: 'HATCH', sortOrder: 0 }]
};
const cardsPet = petStore.savePetFromVO(cardsVO);
assert.strictEqual(cardsPet.collectionCards.length, 1, 'maps collectionCards array');
assert.strictEqual(cardsPet.collectionCards[0].imageUrl, 'https://img/card.png', 'collectionCards[0].imageUrl');

// --- getStage 不再有 prepared 分支 ---
// 构造一个 progress=100 但未到破壳时间的 pet（单轨下应落到 ready 或 soon 而非 prepared）
const fullProgressVO = {
  ...eggVO,
  acceleratedMinutes: HATCH_TOTAL_MINUTES,
  expectedHatchTime: new Date(now + sevenDays).toISOString()
};
const fullProgressPet = petStore.savePetFromVO(fullProgressVO);
assert.strictEqual(fullProgressPet.progress, 100, '100% progress');
// acceleratedMinutes=10080 -> expectedHatchTime 被压到 hatchStartTime(=now)，now >= expected -> ready
// 但 savePetFromVO 用 vo.expectedHatchTime 原值(now+7d)，故这里仍 soon/hatching；
// 真实场景由后端重算 expectedHatchTime 返回。此处只校验无 prepared 返回。
assert.notStrictEqual(petStore.getStage(fullProgressPet), 'prepared', 'no prepared stage');

// --- savePetFromVO: 冷启动破壳宠物生成完整收藏卡(非 placeholder) ---
petStore.clearAccountData();
petStore.saveUser({ id: 42, nickname: '蛋友' });
const coldHatchedVO = {
  ...eggVO,
  hatchStatus: 'HATCHED',
  expectedHatchTime: new Date(now - 8 * 24 * 60 * 60 * 1000).toISOString(),
  hatchedAt: new Date(now - 8 * 24 * 60 * 60 * 1000).toISOString(),
  bazi: '庚子', wuxing: '金水', zodiac: 'aquarius',
  mbti: 'ENFP', personality: '热烈又好奇', personalityBrief: '好运小福星',
  gender: 'FEMALE', bloodType: 'A', avatarUrl: 'https://img/koi.png'
};
const coldHatched = petStore.savePetFromVO(coldHatchedVO);
assert.ok(coldHatched.collectionCards, 'cold-start hatched pet has collectionCards array');
assert.strictEqual(coldHatched.collectionCards.length, 0, 'cold-start without backend cards has empty array');

// --- savePetFromVO: 合并前端独占字段(shell/preferences)，不被后端 VO 清空 ---
petStore.clearAccountData();
petStore.saveUser({ id: 42, nickname: '蛋友' });
const tk = petStore.todayKey();
petStore.savePet({
  id: 'pet-1', ownerId: 42, prototype: '锦鲤', name: '小金',
  createdAt: now, hatchAt: now + sevenDays, progress: 7, stage: 'hatching',
  lastInteractionAt: now,
  tasks: { nicknameDone: true, cuddleDate: tk, wishDate: '', lessonDate: '', doodleDone: false },
  preferences: { wishes: [{ date: tk, value: '安静陪伴你' }], lessons: [{ date: tk, value: '学会勇敢' }] },
  shell: { color: '#FF0000', colorName: '正红', pattern: '波点' },
  dailyStatus: { date: tk, mood: '开心', line: 'x', source: 'local-fallback' },
  collectionCards: [], inviteCodes: ['EGG-1'], messages: [{ text: 'hi' }],
  hatchStatus: 'EGG', acceleratedMinutes: 720
});
const mergeVO = {
  id: 'pet-1', userId: 42, hatchStatus: 'EGG', prototype: '锦鲤',
  acceleratedMinutes: 780,
  expectedHatchTime: new Date(now + sevenDays).toISOString(),
  hatchStartTime: new Date(now).toISOString(),
  createDate: new Date(now).toISOString(),
  nickname: '小金'
};
const merged = petStore.savePetFromVO(mergeVO);
assert.strictEqual(merged.shell.color, '#FF0000', 'merge preserves existing shell.color');
assert.strictEqual(merged.shell.colorName, '正红', 'merge preserves existing shell.colorName');
assert.strictEqual(merged.shell.pattern, '波点', 'merge preserves existing shell.pattern');
assert.strictEqual(merged.preferences.lessons.length, 1, 'merge preserves existing preferences.lessons');
assert.strictEqual(merged.preferences.lessons[0].value, '学会勇敢', 'merge preserves lesson value');
assert.strictEqual(merged.tasks.nicknameDone, true, 'merge preserves existing tasks');
assert.strictEqual(merged.inviteCodes[0], 'EGG-1', 'merge preserves existing inviteCodes');
assert.strictEqual(merged.messages[0].text, 'hi', 'merge preserves existing messages');
assert.strictEqual(merged.dailyStatus.mood, '开心', 'merge preserves existing dailyStatus');
assert.strictEqual(merged.acceleratedMinutes, 780, 'merge takes acceleratedMinutes from vo');
assert.strictEqual(merged.name, '小金', 'merge keeps name when backend nickname present');

// --- changeScene: 调用后端更换场景并更新本地缓存 ---
const petApi = require('./pet-api');
const originalChangeScene = petApi.changeScene;

petStore.clearAccountData();
petStore.saveUser({ id: 42, nickname: '蛋友' });
const changeSceneVO = {
  ...hatchedIdentityVO,
  sceneUrl: 'https://oss.eggbabe.com/default-scenes/fish/scenes-fish-5.jpg'
};
petStore.savePetFromVO(hatchedIdentityVO);

// mock: 后端返回新场景 URL
petApi.changeScene = async (petId) => {
  assert.strictEqual(petId, 'pet-1', 'changeScene passes correct petId');
  return changeSceneVO;
};

(async () => {
  const result = await petStore.changeScene();
  assert.strictEqual(result.ok, true, 'changeScene returns ok');
  assert.strictEqual(result.sceneUrl, 'https://oss.eggbabe.com/default-scenes/fish/scenes-fish-5.jpg', 'changeScene returns new sceneUrl');
  const cached = petStore.getPet();
  assert.strictEqual(cached.sceneUrl, 'https://oss.eggbabe.com/default-scenes/fish/scenes-fish-5.jpg', 'changeScene updates local cache');

  // mock: 后端报错
  petApi.changeScene = async () => { throw { userMessage: '服务异常' }; };
  const failResult = await petStore.changeScene();
  assert.strictEqual(failResult.ok, false, 'changeScene returns not ok on error');
  assert.strictEqual(failResult.message, '服务异常', 'changeScene propagates error message');

  // 无宠物
  petStore.clearAccountData();
  petStore.saveUser({ id: 42, nickname: '蛋友' });
  const noPetResult = await petStore.changeScene();
  assert.strictEqual(noPetResult.ok, false, 'changeScene fails without pet');

  // 恢复
  petApi.changeScene = originalChangeScene;

  console.log('pet-store.test.js: ALL PASS');
})();
