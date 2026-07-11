# 蛋宝宝孵化修炼前端接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把蛋宝宝小程序的 5 个修炼动作、破壳仪式、stage 派生接入已 landed 的后端接口，非演示场景下后端为唯一事实源，保留展会演示模式。

**Architecture:** 新增 `utils/pet-api.js` 封装后端 HTTP 调用；`utils/pet-store.js` 作为门面按 `pet.demoMode` 分流——demo 走现有本地 mock 逻辑，非 demo 调 `pet-api` 并用返回的 PetVO 更新本地缓存。页面层不感知分流，但因后端调用是异步的，相关页面处理函数需改为 `async`。

**Tech Stack:** 微信原生小程序（JS/WXML/WXSS/JSON，无构建工具），后端 `manager-api`（Spring Boot，`/xiaozhi` 上下文）。测试用 plain Node + `assert`，mock `global.wx` 与 `Module._load`。

## Global Constraints

- 纯前端接入，后端不动
- 后端契约（已 landed，见 `main/egg-miniprogram/docs/egg-pet-identity-and-hatch-api.md` §10）：
  - `POST /pet/adopt { inviteCode }` → `PetVO`
  - `POST /pet/{id}/hatch-action { type, payload }` → `HatchActionResultVO { addedMinutes, alreadyDone, readyToHatch, pet }`
  - `GET /pet/{id}/hatch-actions` → `HatchActionVO[]`
  - `POST /pet/{id}/hatch` → `PetVO`
  - `GET /pet/{id}` → `PetVO`
  - `GET /pet/list` → `PetVO[]`
  - `PUT /pet/update { id, nickname }` → `PetVO`
- 5 动作 type：`NICKNAME`(720分/一次性,payload `{nickname}`) / `CUDDLE`(60分/每日,payload `{}`) / `WISH`(60分/每日,payload `{value}`) / `LESSON`(60分/每日,payload `{value}`) / `DOODLE`(720分/一次性,payload `{color,colorName,pattern}`)
- 7 天 = 10080 分钟；进度条 = `acceleratedMinutes / 10080`
- stage 单轨 5 态：`waiting / hatching / soon / ready / hatched`（删 `prepared`，进度满即时间到）
- demo 双轨：`pet.demoMode === true` 走 mock，否则走后端
- 收藏卡：身份字段取后端 PetVO，装饰字段（serial/hatchQuality/style）前端生成
- token/openid/wx.login code 严禁落日志、严禁入库
- 后端 schema 变更走 Liquibase（本次不涉及后端）
- 文件组织：四件套 `.js/.json/.wxml/.wxss`；`navigationStyle: custom`
- 运行测试：`node main/egg-miniprogram/miniprogram/utils/<name>.test.js`
- 语法校验：`node --check main/egg-miniprogram/miniprogram/utils/<name>.js`
- 工程校验：`node main/egg-miniprogram/scripts/verify-project.js`

## 统一返回契约（所有修炼动作函数）

非 demo 与 demo 两条路径都返回同一形状，页面层据此渲染：

```js
{ ok: boolean, alreadyDone: boolean, pet: Pet, message?: string }
```

- `ok === false` → 页面 toast `result.message`
- `alreadyDone === true` → 页面 toast "今天已经做过了"（页面层不再用 `added`/`+X%`，因 Model X 单位是分钟不是百分比）
- `ok && !alreadyDone` → 页面 toast 成功文案（无单位后缀）

`createCollectionCard` 契约不变：`{ ok, created, card, pet, message }`。

---

### Task 1: 新增 `utils/pet-api.js` 后端调用封装

**Files:**
- Create: `main/egg-miniprogram/miniprogram/utils/pet-api.js`
- Create: `main/egg-miniprogram/miniprogram/utils/pet-api.test.js`

**Interfaces:**
- Consumes: `utils/request.js` 的 `{ get, post, put }`（返回 `envelope.data`，失败 reject `{type, code, message, userMessage}`）
- Produces: `petApi.adoptPet(inviteCode)` / `petApi.submitHatchAction(petId, type, payload)` / `petApi.listHatchActions(petId)` / `petApi.hatchPet(petId)` / `petApi.getPet(petId)` / `petApi.listPets()` / `petApi.updateNickname(petId, nickname)`，均返回 `Promise<VO>`

- [ ] **Step 1: Write the failing test**

Create `main/egg-miniprogram/miniprogram/utils/pet-api.test.js`:

```js
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

(async () => {
  enqueue({ id: 'pet-1', hatchStatus: 'EGG' });
  const adopted = await petApi.adoptPet('CODE-1');
  assert.strictEqual(adopted.id, 'pet-1');
  assert.strictEqual(calls.at(-1).url, '/pet/adopt');
  assert.strictEqual(calls.at(-1).method, 'POST');
  assert.deepStrictEqual(calls.at(-1).data, { inviteCode: 'CODE-1' });

  enqueue({ addedMinutes: 60, alreadyDone: false, readyToHatch: false, pet: { id: 'pet-1' } });
  const action = await petApi.submitHatchAction('pet-1', 'LESSON', { value: '学会勇敢' });
  assert.strictEqual(calls.at(-1).url, '/pet/pet-1/hatch-action');
  assert.strictEqual(calls.at(-1).method, 'POST');
  assert.deepStrictEqual(calls.at(-1).data, { type: 'LESSON', payload: { value: '学会勇敢' } });
  assert.strictEqual(action.addedMinutes, 60);
  assert.strictEqual(action.alreadyDone, false);

  enqueue([{ actionType: 'LESSON', payload: '{}' }]);
  const actions = await petApi.listHatchActions('pet-1');
  assert.strictEqual(calls.at(-1).url, '/pet/pet-1/hatch-actions');
  assert.strictEqual(calls.at(-1).method, 'GET');
  assert.strictEqual(Array.isArray(actions), true);

  enqueue({ id: 'pet-1', hatchStatus: 'HATCHED' });
  const hatched = await petApi.hatchPet('pet-1');
  assert.strictEqual(calls.at(-1).url, '/pet/pet-1/hatch');
  assert.strictEqual(calls.at(-1).method, 'POST');
  assert.strictEqual(hatched.hatchStatus, 'HATCHED');

  enqueue({ id: 'pet-1' });
  await petApi.getPet('pet-1');
  assert.strictEqual(calls.at(-1).url, '/pet/pet-1');
  assert.strictEqual(calls.at(-1).method, 'GET');

  enqueue([{ id: 'pet-1' }, { id: 'pet-2' }]);
  const list = await petApi.listPets();
  assert.strictEqual(calls.at(-1).url, '/pet/list');
  assert.strictEqual(list.length, 2);

  enqueue({ id: 'pet-1', nickname: '小金' });
  await petApi.updateNickname('pet-1', '小金');
  assert.strictEqual(calls.at(-1).url, '/pet/update');
  assert.strictEqual(calls.at(-1).method, 'PUT');
  assert.deepStrictEqual(calls.at(-1).data, { id: 'pet-1', nickname: '小金' });

  console.log('pet-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-api.test.js`
Expected: FAIL with `Cannot find module './pet-api'`

- [ ] **Step 3: Write minimal implementation**

Create `main/egg-miniprogram/miniprogram/utils/pet-api.js`:

```js
const { get, post, put } = require('./request');

// 后端 PetVO 字段：id, userId, deviceId, nickname, birthDate, bazi, wuxing, zodiac,
// mbti, personality, personalityBrief, todayMood, todayMoodDate, todayMoodSentence,
// hatchStatus(EGG/HATCHED), hatchStartTime, expectedHatchTime, hatchedAt, acceleratedMinutes,
// avatarUrl, prototype, gender, bloodType, createDate
function adoptPet(inviteCode) {
  return post('/pet/adopt', { inviteCode });
}

// HatchActionResultVO: { addedMinutes, alreadyDone, readyToHatch, pet }
function submitHatchAction(petId, type, payload) {
  return post(`/pet/${petId}/hatch-action`, { type, payload });
}

// HatchActionVO[]: [{ id, actionType, payload, actionDate, acceleratedMinutes, createDate }]
function listHatchActions(petId) {
  return get(`/pet/${petId}/hatch-actions`);
}

function hatchPet(petId) {
  return post(`/pet/${petId}/hatch`);
}

function getPet(petId) {
  return get(`/pet/${petId}`);
}

function listPets() {
  return get('/pet/list');
}

function updateNickname(petId, nickname) {
  return put('/pet/update', { id: petId, nickname });
}

module.exports = {
  adoptPet,
  submitHatchAction,
  listHatchActions,
  hatchPet,
  getPet,
  listPets,
  updateNickname
};
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-api.test.js`
Expected: `pet-api.test.js: ALL PASS`

- [ ] **Step 5: Syntax check**

Run: `node --check main/egg-miniprogram/miniprogram/utils/pet-api.js`
Expected: no output (success)

- [ ] **Step 6: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/pet-api.js main/egg-miniprogram/miniprogram/utils/pet-api.test.js
git commit -m "feat(egg): add pet-api.js backend hatch/cultivation call wrappers"
```

---

### Task 2: `pet-store.js` savePetFromVO 扩展 + buildCollectionCard + getStage 5 态

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.js` (`savePetFromVO` ~L93-129, `getStage` ~L267-275, `STAGE_PRESENTATION` ~L277-284, 新增 `buildCollectionCard`)
- Test: `main/egg-miniprogram/miniprogram/utils/pet-store.test.js`

**Interfaces:**
- Consumes: Task 1 的 `petApi`（此任务仅扩展映射与纯函数，不调 petApi）
- Produces: `savePetFromVO` 返回的 pet 含 `hatchStartTime / expectedHatchTime / hatchedAt / bazi / wuxing / zodiac / mbti / personality / personalityBrief / gender / bloodType / avatarUrl / todayMoodDate`；`getStage` 不再有 `prepared` 分支；`buildCollectionCard(vo)` 接 PetVO 返回收藏卡对象（供 Task 4 用）

- [ ] **Step 1: Write the failing test**

Append to `main/egg-miniprogram/miniprogram/utils/pet-store.test.js` (before the final `console.log` line `pet-store.test.js: ALL PASS`):

```js
// --- savePetFromVO: 破壳后身份字段映射 ---
const hatchedIdentityVO = {
  ...eggVO,
  hatchStatus: 'HATCHED',
  expectedHatchTime: new Date(now - 8 * 24 * 60 * 60 * 1000).toISOString(),
  hatchedAt: new Date(now - 8 * 24 * 60 * 60 * 1000).toISOString(),
  bazi: '庚子', wuxing: '金水', zodiac: '水瓶座',
  mbti: 'ENFP', personality: '热烈又好奇', personalityBrief: '好运小福星',
  gender: 'FEMALE', bloodType: 'A', avatarUrl: 'https://img/koi.png',
  todayMoodDate: petStore.todayKey()
};
const hatchedIdentity = petStore.savePetFromVO(hatchedIdentityVO);
assert.strictEqual(hatchedIdentity.bazi, '庚子', 'maps bazi');
assert.strictEqual(hatchedIdentity.mbti, 'ENFP', 'maps mbti');
assert.strictEqual(hatchedIdentity.personality, '热烈又好奇', 'maps personality');
assert.strictEqual(hatchedIdentity.gender, 'FEMALE', 'maps gender');
assert.strictEqual(hatchedIdentity.bloodType, 'A', 'maps bloodType');
assert.strictEqual(hatchedIdentity.avatarUrl, 'https://img/koi.png', 'maps avatarUrl');
assert.strictEqual(hatchedIdentity.zodiac, '水瓶座', 'maps zodiac');

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

// --- buildCollectionCard: 后端身份 + 前端装饰 ---
petStore.saveUser({ id: 42, nickname: '蛋友' });
const card = petStore.buildCollectionCard(hatchedIdentityVO);
assert.strictEqual(card.prototype, '锦鲤', 'card prototype from vo');
assert.strictEqual(card.mbti, 'ENFP', 'card mbti from vo');
assert.strictEqual(card.gender, 'FEMALE', 'card gender from vo');
assert.strictEqual(card.bloodType, 'A', 'card bloodType from vo');
assert.ok(card.serial.startsWith('EGG-KOI-'), 'card serial prefix');
assert.ok(card.hatchQuality === '完整孵化' || card.hatchQuality === '轻量孵化', 'card hatchQuality');
```

Also add near the top of the file (after `const sevenDays = ...` line):

```js
const HATCH_TOTAL_MINUTES = 7 * 24 * 60;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-store.test.js`
Expected: FAIL — `petStore.buildCollectionCard is not a function` (and identity field asserts fail)

- [ ] **Step 3: Extend `savePetFromVO` to map identity fields**

In `main/egg-miniprogram/miniprogram/utils/pet-store.js`, replace the `pet` object literal in `savePetFromVO` (lines ~103-125) to add identity fields. Replace:

```js
  const pet = {
    id: vo.id,
    ownerId: (user && user.id) || null,
    prototype: vo.prototype || '玉兔',
    name: vo.nickname || '',
    createdAt,
    hatchAt,
    progress,
    stage: 'waiting',
    lastInteractionAt: createdAt,
    tasks: { nicknameDone: false, cuddleDate: '', wishDate: '', lessonDate: '', doodleDone: false },
    preferences: { wishes: [], lessons: [] },
    shell: { color: '#EDE78E', colorName: '奶油白', pattern: '星星' },
    dailyStatus: null,
    collectionCard: isHatched ? { placeholder: true, prototype: vo.prototype } : null,
    inviteCodes: [],
    messages: [],
    todayMood: vo.todayMood || '',
    todayMoodSentence: vo.todayMoodSentence || '',
    hatchStatus: vo.hatchStatus || 'EGG',
    acceleratedMinutes: accelerated,
    deviceId: vo.deviceId || null
  };
```

with:

```js
  const pet = {
    id: vo.id,
    ownerId: (user && user.id) || null,
    prototype: vo.prototype || '玉兔',
    name: vo.nickname || '',
    createdAt,
    hatchAt,
    progress,
    stage: 'waiting',
    lastInteractionAt: createdAt,
    tasks: { nicknameDone: false, cuddleDate: '', wishDate: '', lessonDate: '', doodleDone: false },
    preferences: { wishes: [], lessons: [] },
    shell: { color: '#EDE78E', colorName: '奶油白', pattern: '星星' },
    dailyStatus: null,
    collectionCard: isHatched ? { placeholder: true, prototype: vo.prototype } : null,
    inviteCodes: [],
    messages: [],
    todayMood: vo.todayMood || '',
    todayMoodSentence: vo.todayMoodSentence || '',
    todayMoodDate: vo.todayMoodDate || '',
    hatchStatus: vo.hatchStatus || 'EGG',
    acceleratedMinutes: accelerated,
    hatchStartTime: toTimestamp(vo.hatchStartTime),
    expectedHatchTime: toTimestamp(vo.expectedHatchTime),
    hatchedAt: toTimestamp(vo.hatchedAt),
    deviceId: vo.deviceId || null,
    bazi: vo.bazi || '',
    wuxing: vo.wuxing || '',
    zodiac: vo.zodiac || '',
    mbti: vo.mbti || '',
    personality: vo.personality || '',
    personalityBrief: vo.personalityBrief || '',
    gender: vo.gender || '',
    bloodType: vo.bloodType || '',
    avatarUrl: vo.avatarUrl || ''
  };
```

- [ ] **Step 4: Remove `prepared` from getStage + STAGE_PRESENTATION**

In `pet-store.js`, replace the `getStage` function (lines ~267-275):

```js
function getStage(pet, now) {
  if (!pet) return 'empty';
  if (pet.collectionCard) return 'hatched';
  const current = now || Date.now();
  if (current >= pet.hatchAt) return 'ready';
  if (pet.hatchAt - current <= DAY) return 'soon';
  if (pet.progress >= 100) return 'prepared';
  return pet.progress > 0 ? 'hatching' : 'waiting';
}
```

with:

```js
function getStage(pet, now) {
  if (!pet) return 'empty';
  if (pet.collectionCard) return 'hatched';
  const current = now || Date.now();
  if (current >= pet.hatchAt) return 'ready';
  if (pet.hatchAt - current <= DAY) return 'soon';
  // 单轨 Model X：进度满即时间到（落到 ready），不保留 prepared 中间态
  return pet.acceleratedMinutes > 0 || pet.progress > 0 ? 'hatching' : 'waiting';
}
```

Replace the `STAGE_PRESENTATION` object (lines ~277-284), removing the `prepared` line:

```js
const STAGE_PRESENTATION = {
  waiting: { homeText: '它还在睡觉，试着叫醒它吧', actionLabel: '孵化修炼手册', myStage: '待激活' },
  hatching: { homeText: '它正在慢慢长大', actionLabel: '孵化修炼手册', myStage: '孵化中' },
  soon: { homeText: '蛋壳里传来了动静', actionLabel: '孵化修炼手册', myStage: '即将破壳' },
  ready: { homeText: '它准备好见你了', actionLabel: '查看破壳结果', myStage: '待破壳' },
  hatched: { homeText: '它终于来到你身边了', actionLabel: '和它说说话', myStage: '已破壳' }
};
```

- [ ] **Step 5: Add `buildCollectionCard(vo)` helper**

In `pet-store.js`, add a new function (insert immediately before the existing `createCollectionCard` function, ~L376):

```js
// 从后端 PetVO（破壳后含身份字段）拼装收藏卡：身份字段取自 vo，装饰字段前端生成。
function buildCollectionCard(vo) {
  if (!vo || !vo.id) return null;
  const hatchTs = toTimestamp(vo.hatchedAt) || toTimestamp(vo.expectedHatchTime) || Date.now();
  const accelerated = vo.acceleratedMinutes || 0;
  const ratio = accelerated / HATCH_TOTAL_MINUTES;
  const prefix = vo.prototype === '锦鲤' ? 'KOI' : 'RABBIT';
  const date = new Date(hatchTs);
  const compact = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')}`;
  const serial = `EGG-${prefix}-${compact}-${String(simpleHash(vo.id) % 999999).padStart(6, '0')}`;
  const user = getUser();
  return {
    id: `card-${vo.id}`,
    serial,
    prototype: vo.prototype || '玉兔',
    style: vo.prototype === '锦鲤' ? '好运红白款' : '月白桂花款',
    name: vo.nickname || vo.prototype || '玉兔',
    birthday: todayKey(hatchTs),
    zodiac: vo.zodiac || getZodiac(hatchTs),
    gender: vo.gender || (simpleHash(vo.id) % 2 ? '♀' : '♂'),
    mbti: vo.mbti || '',
    bloodType: vo.bloodType || ['A', 'B', 'O', 'AB'][simpleHash(vo.id) % 4],
    personality: vo.personality || '',
    personalityBrief: vo.personalityBrief || '',
    avatarUrl: vo.avatarUrl || '',
    collectible: '普通',
    hatchQuality: ratio >= 0.8 ? '完整孵化' : '轻量孵化',
    originalOwner: (user && user.nickname) || '蛋友3024'
  };
}
```

Add `buildCollectionCard` to the `module.exports` object (in the block near `createCollectionCard`):

```js
  buildCollectionCard,
  createCollectionCard,
```

- [ ] **Step 6: Run test to verify it passes**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-store.test.js`
Expected: `pet-store.test.js: ALL PASS`

- [ ] **Step 7: Syntax check**

Run: `node --check main/egg-miniprogram/miniprogram/utils/pet-store.js`
Expected: no output (success)

- [ ] **Step 8: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/pet-store.js main/egg-miniprogram/miniprogram/utils/pet-store.test.js
git commit -m "feat(egg): map identity fields, drop prepared stage, add buildCollectionCard"
```

---

### Task 3: `pet-store.js` 5 个修炼动作函数双轨 + getHatchActionState

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.js` (`updateNickname` L212-226, `completeDailyTask` L228-241, `completeCuddle/Wish/Lesson` L243-253, `saveDoodle` L255-265, 顶部 require, module.exports)
- Create: `main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`（async 双轨测试，参照 `request.test.js` 的 async IIFE + `Module._load` 拦截结构）

**Interfaces:**
- Consumes: Task 1 `petApi.submitHatchAction` / `petApi.listHatchActions`，Task 2 `savePetFromVO`
- Produces: 5 个修炼动作函数变为 `async`，返回统一契约 `{ ok, alreadyDone, pet, message }`；新增 `getHatchActionState(pet)` 返回 `{ nicknameDone, cuddleDone, wishDone, lessonDone, doodleDone }`

> 注：双轨动作函数是 async，不能 append 到同步的 `pet-store.test.js`（会与末尾同步 `console.log` 产生时序问题）。新建独立 async 测试文件，结构同 `request.test.js`。

- [ ] **Step 1: Write the failing test**

Create `main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`:

```js
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
      hatchPet: async () => { hatchCalls += 1; return hatchResponse; }
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
  const realPet = { ...demoPet, id: 'real-1', demoMode: false };
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

  console.log('pet-store-actions.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`
Expected: FAIL — `petStore.completeLesson` returns non-Promise (sync) or `getHatchActionState is not a function`

- [ ] **Step 3: Add pet-api require at top of pet-store.js**

In `main/egg-miniprogram/miniprogram/utils/pet-store.js`, after line 1 (the `PET_KEY` const) — actually add the require at the very top, before the first const. Insert as the new first line:

```js
const petApi = require('./pet-api');
```

- [ ] **Step 4: Rewrite `updateNickname` for dual-track**

In `pet-store.js`, replace the `updateNickname` function (L212-226):

```js
function updateNickname(name) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const value = String(name || '').trim();
  if (!value) return { ok: false, message: '昵称不能为空' };
  if (Array.from(value).length > 10) return { ok: false, message: '昵称最多 10 个字符' };
  if (['违法', '诈骗', '赌博'].some(word => value.includes(word))) return { ok: false, message: '昵称含有不适合的内容，请换一个' };
  const first = !pet.tasks.nicknameDone;
  pet.name = value;
  if (first) addProgress(pet, 20);
  pet.tasks.nicknameDone = true;
  pet.lastInteractionAt = Date.now();
  savePet(pet);
  return { ok: true, added: first ? 20 : 0, pet };
}
```

with:

```js
async function updateNickname(name) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const value = String(name || '').trim();
  if (!value) return { ok: false, message: '昵称不能为空' };
  if (Array.from(value).length > 10) return { ok: false, message: '昵称最多 10 个字符' };
  if (['违法', '诈骗', '赌博'].some(word => value.includes(word))) return { ok: false, message: '昵称含有不适合的内容，请换一个' };
  if (pet.demoMode) {
    const first = !pet.tasks.nicknameDone;
    pet.name = value;
    if (first) addProgress(pet, 20);
    pet.tasks.nicknameDone = true;
    pet.lastInteractionAt = Date.now();
    savePet(pet);
    return { ok: true, alreadyDone: !first, pet };
  }
  try {
    const result = await petApi.submitHatchAction(pet.id, 'NICKNAME', { nickname: value });
    const updated = savePetFromVO(result.pet);
    updated.name = value;
    savePet(updated);
    return { ok: true, alreadyDone: !!result.alreadyDone, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '提交失败，请稍后重试' };
  }
}
```

- [ ] **Step 5: Rewrite `completeDailyTask` + `completeCuddle/Wish/Lesson` for dual-track**

In `pet-store.js`, replace `completeDailyTask`, `completeCuddle`, `completeWish`, `completeLesson` (L228-253):

```js
function completeDailyTask(task, value) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const date = todayKey();
  const field = `${task}Date`;
  if (pet.tasks[field] === date) return { ok: true, added: 0, alreadyDone: true, pet };
  pet.tasks[field] = date;
  if (task === 'wish') pet.preferences.wishes.push({ date, value });
  if (task === 'lesson') pet.preferences.lessons.push({ date, value });
  addProgress(pet, 5);
  pet.lastInteractionAt = Date.now();
  savePet(pet);
  return { ok: true, added: 5, alreadyDone: false, pet };
}

function completeCuddle() {
  return completeDailyTask('cuddle', '贴贴');
}

function completeWish(value) {
  return completeDailyTask('wish', value);
}

function completeLesson(value) {
  return completeDailyTask('lesson', value);
}
```

with:

```js
const ACTION_TYPE = { cuddle: 'CUDDLE', wish: 'WISH', lesson: 'LESSON' };

async function completeDailyTask(task, value) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (pet.demoMode) {
    const date = todayKey();
    const field = `${task}Date`;
    if (pet.tasks[field] === date) return { ok: true, alreadyDone: true, pet };
    pet.tasks[field] = date;
    if (task === 'wish') pet.preferences.wishes.push({ date, value });
    if (task === 'lesson') pet.preferences.lessons.push({ date, value });
    addProgress(pet, 5);
    pet.lastInteractionAt = Date.now();
    savePet(pet);
    return { ok: true, alreadyDone: false, pet };
  }
  try {
    const payload = task === 'cuddle' ? {} : { value };
    const result = await petApi.submitHatchAction(pet.id, ACTION_TYPE[task], payload);
    const updated = savePetFromVO(result.pet);
    if (task === 'wish') updated.preferences.wishes.push({ date: todayKey(), value });
    if (task === 'lesson') updated.preferences.lessons.push({ date: todayKey(), value });
    savePet(updated);
    return { ok: true, alreadyDone: !!result.alreadyDone, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '提交失败，请稍后重试' };
  }
}

function completeCuddle() {
  return completeDailyTask('cuddle', '贴贴');
}

function completeWish(value) {
  return completeDailyTask('wish', value);
}

function completeLesson(value) {
  return completeDailyTask('lesson', value);
}
```

- [ ] **Step 6: Rewrite `saveDoodle` for dual-track**

In `pet-store.js`, replace `saveDoodle` (L255-265):

```js
function saveDoodle(color, colorName, pattern) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  const first = !pet.tasks.doodleDone;
  pet.shell = { color, colorName, pattern };
  pet.tasks.doodleDone = true;
  if (first) addProgress(pet, 20);
  pet.lastInteractionAt = Date.now();
  savePet(pet);
  return { ok: true, added: first ? 20 : 0, pet };
}
```

with:

```js
async function saveDoodle(color, colorName, pattern) {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (pet.demoMode) {
    const first = !pet.tasks.doodleDone;
    pet.shell = { color, colorName, pattern };
    pet.tasks.doodleDone = true;
    if (first) addProgress(pet, 20);
    pet.lastInteractionAt = Date.now();
    savePet(pet);
    return { ok: true, alreadyDone: !first, pet };
  }
  try {
    const result = await petApi.submitHatchAction(pet.id, 'DOODLE', { color, colorName, pattern });
    const updated = savePetFromVO(result.pet);
    updated.shell = { color, colorName, pattern };
    savePet(updated);
    return { ok: true, alreadyDone: !!result.alreadyDone, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '提交失败，请稍后重试' };
  }
}
```

- [ ] **Step 7: Add `getHatchActionState(pet)`**

In `pet-store.js`, add this function (insert immediately before `getStage`, ~L267):

```js
// 修炼任务完成态：demo 从 pet.tasks 读；非 demo 从 pet._hatchActions（hatch-guide onShow 拉取缓存）派生当日完成。
function getHatchActionState(pet) {
  if (!pet) return { nicknameDone: false, cuddleDone: false, wishDone: false, lessonDone: false, doodleDone: false };
  if (pet.demoMode || !Array.isArray(pet._hatchActions) || pet._hatchActions.length === 0) {
    const today = todayKey();
    return {
      nicknameDone: !!(pet.tasks && pet.tasks.nicknameDone),
      cuddleDone: !!(pet.tasks && pet.tasks.cuddleDate === today),
      wishDone: !!(pet.tasks && pet.tasks.wishDate === today),
      lessonDone: !!(pet.tasks && pet.tasks.lessonDate === today),
      doodleDone: !!(pet.tasks && pet.tasks.doodleDone)
    };
  }
  const today = todayKey();
  const done = {};
  pet._hatchActions.forEach((a) => {
    if (a.actionDate === today || a.actionType === 'NICKNAME' || a.actionType === 'DOODLE') {
      done[a.actionType] = true;
    }
  });
  return {
    nicknameDone: !!done.NICKNAME,
    cuddleDone: !!done.CUDDLE,
    wishDone: !!done.WISH,
    lessonDone: !!done.LESSON,
    doodleDone: !!done.DOODLE
  };
}
```

Add `getHatchActionState` to `module.exports`:

```js
  getHatchActionState,
  getStage,
```

- [ ] **Step 8: Run test to verify it passes**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`
Expected: `pet-store-actions.test.js: ALL PASS`

- [ ] **Step 9: Syntax check**

Run: `node --check main/egg-miniprogram/miniprogram/utils/pet-store.js`
Expected: no output (success)

- [ ] **Step 10: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/pet-store.js main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js
git commit -m "feat(egg): dual-track cultivation actions via pet-api + getHatchActionState"
```

---

### Task 4: `pet-store.js` `createCollectionCard` 双轨（真实破壳）

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.js` (`createCollectionCard` L376-402, module.exports)
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`（在 Task 3 创建的 async IIFE 内、`console.log('ALL PASS')` 之前追加破壳断言）

**Interfaces:**
- Consumes: Task 1 `petApi.hatchPet`，Task 2 `buildCollectionCard` / `savePetFromVO`；Task 3 测试文件已有的 `hatchCalls` / `hatchResponse` 桩
- Produces: `async createCollectionCard()` 返回 `{ ok, created, card, pet, message }`；非 demo 调 `POST /pet/{id}/hatch`，用返回 PetVO 经 `buildCollectionCard` 拼装收藏卡

- [ ] **Step 1: Write the failing test**

In `main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`, inside the existing async IIFE (created in Task 3), insert these assertions **before** the final `console.log('pet-store-actions.test.js: ALL PASS');` line:

```js
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
    zodiac: '水瓶座', avatarUrl: 'https://img/koi.png'
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`
Expected: FAIL — `createCollectionCard` returns sync `{ok:true,...}` without calling hatchPet, so `hatchCalls` assertion fails (0 !== 1)

- [ ] **Step 3: Rewrite `createCollectionCard` for dual-track**

In `pet-store.js`, replace the `createCollectionCard` function (L376-402):

```js
function createCollectionCard() {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (Date.now() < pet.hatchAt) return { ok: false, message: '还没到预设破壳时间' };
  if (pet.collectionCard) return { ok: true, created: false, card: pet.collectionCard, pet };
  const isKoi = pet.prototype === '锦鲤';
  const personality = derivePersonality(pet);
  pet.collectionCard = {
    id: `card-${pet.id}`,
    serial: cardSerial(pet),
    prototype: pet.prototype,
    style: isKoi ? '好运红白款' : '月白桂花款',
    name: pet.name || pet.prototype,
    birthday: todayKey(pet.hatchAt),
    zodiac: getZodiac(pet.hatchAt),
    gender: simpleHash(pet.id) % 2 ? '♀' : '♂',
    mbti: personality.mbti,
    bloodType: ['A', 'B', 'O', 'AB'][simpleHash(pet.id) % 4],
    personality: personality.text,
    collectible: '普通',
    hatchQuality: pet.progress >= 80 ? '完整孵化' : '轻量孵化',
    originalOwner: (getUser() && getUser().nickname) || '蛋友3024'
  };
  pet.stage = 'hatched';
  savePet(pet);
  return { ok: true, created: true, card: pet.collectionCard, pet };
}
```

with:

```js
async function createCollectionCard() {
  const pet = getPet();
  if (!pet) return { ok: false, message: '还没有蛋宝宝' };
  if (Date.now() < pet.hatchAt) return { ok: false, message: '还没到破壳时间' };
  if (pet.collectionCard) return { ok: true, created: false, card: pet.collectionCard, pet };
  if (pet.demoMode) {
    const isKoi = pet.prototype === '锦鲤';
    const personality = derivePersonality(pet);
    pet.collectionCard = {
      id: `card-${pet.id}`,
      serial: cardSerial(pet),
      prototype: pet.prototype,
      style: isKoi ? '好运红白款' : '月白桂花款',
      name: pet.name || pet.prototype,
      birthday: todayKey(pet.hatchAt),
      zodiac: getZodiac(pet.hatchAt),
      gender: simpleHash(pet.id) % 2 ? '♀' : '♂',
      mbti: personality.mbti,
      bloodType: ['A', 'B', 'O', 'AB'][simpleHash(pet.id) % 4],
      personality: personality.text,
      collectible: '普通',
      hatchQuality: pet.progress >= 80 ? '完整孵化' : '轻量孵化',
      originalOwner: (getUser() && getUser().nickname) || '蛋友3024'
    };
    pet.stage = 'hatched';
    savePet(pet);
    return { ok: true, created: true, card: pet.collectionCard, pet };
  }
  try {
    const vo = await petApi.hatchPet(pet.id);
    const card = buildCollectionCard(vo);
    const updated = savePetFromVO(vo);
    updated.collectionCard = card;
    updated.stage = 'hatched';
    savePet(updated);
    return { ok: true, created: true, card, pet: updated };
  } catch (error) {
    return { ok: false, message: (error && error.userMessage) || '破壳失败，请稍后重试' };
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js`
Expected: `pet-store-actions.test.js: ALL PASS`（含 Task 3 的动作双轨断言 + 本任务的破壳断言）

- [ ] **Step 5: Syntax check**

Run: `node --check main/egg-miniprogram/miniprogram/utils/pet-store.js`
Expected: no output (success)

- [ ] **Step 6: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/pet-store.js main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js
git commit -m "feat(egg): dual-track createCollectionCard via POST /pet/{id}/hatch"
```

---

### Task 5: 接入 4 个修炼动作页（nickname/wish/lesson/doodle）为 async

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/lesson/lesson.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/wish/wish.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/nickname/nickname.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/doodle/doodle.js`

**Interfaces:**
- Consumes: Task 3 的 `async petStore.completeLesson/completeWish/updateNickname/saveDoodle`，返回 `{ ok, alreadyDone, pet, message }`
- Produces: 4 页 `onSubmit` 改 `async`，按统一契约渲染 toast；不再引用 `result.added`/`+X%`

> 页面层无单元测试框架（项目仅对 utils 跑 node 测试，页面靠微信开发者工具真机验证，见 `egg-miniprogram/CLAUDE.md`）。本任务用 `node --check` 语法校验 + `verify-project.js` 工程校验作为最低保证，真机验证在最后统一做。

- [ ] **Step 1: Rewrite `lesson.js` onSubmit**

Replace the entire `main/egg-miniprogram/miniprogram/pages/lesson/lesson.js`:

```js
const petStore = require('../../utils/pet-store');
Page({
  data: { selected: '', options: [{ icon: '♡', value: '学会撒娇' }, { icon: '✦', value: '学会勇敢' }, { icon: '☺', value: '学会讲冷笑话' }] },
  onSelect(e) { this.setData({ selected: e.currentTarget.dataset.value }); },
  async onSubmit() {
    if (!this.data.selected) return wx.showToast({ title: '先选一堂课吧', icon: 'none' });
    const result = await petStore.completeLesson(this.data.selected);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '今天已经上过课啦' : '它认真听完了', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
```

- [ ] **Step 2: Rewrite `wish.js` onSubmit**

Replace the entire `main/egg-miniprogram/miniprogram/pages/wish/wish.js`:

```js
const petStore = require('../../utils/pet-store');
Page({
  data: { selected: '', options: ['安静陪伴你', '活泼逗你开心', '聪明帮你出主意'] },
  onSelect(e) { this.setData({ selected: e.currentTarget.dataset.value }); },
  async onSubmit() {
    if (!this.data.selected) return wx.showToast({ title: '先选一个愿望吧', icon: 'none' });
    const result = await petStore.completeWish(this.data.selected);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '今天已经许过愿啦' : '它记住了', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
```

- [ ] **Step 3: Rewrite `nickname.js` onSave**

Replace the entire `main/egg-miniprogram/miniprogram/pages/nickname/nickname.js`:

```js
const petStore = require('../../utils/pet-store');

Page({
  data: { name: '', count: 0, error: '' },
  onLoad() {
    const pet = petStore.getPet();
    const name = pet ? pet.name : '';
    this.setData({ name, count: Array.from(name).length });
  },
  onInput(e) {
    const name = e.detail.value;
    this.setData({ name, count: Array.from(name).length, error: '' });
  },
  async onSave() {
    const result = await petStore.updateNickname(this.data.name);
    if (!result.ok) return this.setData({ error: result.message });
    wx.showToast({ title: result.alreadyDone ? '昵称已更新' : '它记住了自己的名字', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
```

- [ ] **Step 4: Rewrite `doodle.js` onSave**

Replace the entire `main/egg-miniprogram/miniprogram/pages/doodle/doodle.js`:

```js
const petStore = require('../../utils/pet-store');
Page({
  data: {
    selectedColor: '#EDE78E', selectedColorName: '奶油白', selectedPattern: '星星',
    colors: [{ name: '奶油白', value: '#EDE78E' }, { name: '薄荷绿', value: '#BFD9C1' }, { name: '樱桃粉', value: '#F4B9AE' }, { name: '月亮蓝', value: '#B6CDE8' }],
    patterns: ['星星', '波点', '云朵', '裂纹', '爱心']
  },
  onLoad() {
    const pet = petStore.getPet();
    if (pet && pet.shell) this.setData({ selectedColor: pet.shell.color, selectedColorName: pet.shell.colorName, selectedPattern: pet.shell.pattern });
  },
  onColor(e) { this.setData({ selectedColor: e.currentTarget.dataset.value, selectedColorName: e.currentTarget.dataset.name }); },
  onPattern(e) { this.setData({ selectedPattern: e.currentTarget.dataset.value }); },
  async onSave() {
    const result = await petStore.saveDoodle(this.data.selectedColor, this.data.selectedColorName, this.data.selectedPattern);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '蛋壳外观已更新' : '蛋壳变漂亮了', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
```

- [ ] **Step 5: Syntax check all four pages**

Run:
```bash
node --check main/egg-miniprogram/miniprogram/pages/lesson/lesson.js && \
node --check main/egg-miniprogram/miniprogram/pages/wish/wish.js && \
node --check main/egg-miniprogram/miniprogram/pages/nickname/nickname.js && \
node --check main/egg-miniprogram/miniprogram/pages/doodle/doodle.js
```
Expected: no output (success for all four)

- [ ] **Step 6: Engineering check**

Run: `node main/egg-miniprogram/scripts/verify-project.js`
Expected: pass (no forbidden pages, four-file sets intact)

- [ ] **Step 7: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/lesson/lesson.js main/egg-miniprogram/miniprogram/pages/wish/wish.js main/egg-miniprogram/miniprogram/pages/nickname/nickname.js main/egg-miniprogram/miniprogram/pages/doodle/doodle.js
git commit -m "feat(egg): wire cultivation action pages to async pet-store dual-track"
```

---

### Task 6: 接入 home 页（cuddle async + 删 prepared 分支）

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.js` (`onTouchStart` 长按回调 L114-135, `onPrimaryAction` L143-154)

**Interfaces:**
- Consumes: Task 3 的 `async petStore.completeCuddle`，返回 `{ ok, alreadyDone, pet, message }`；Task 2 的 5 态 `getStage`
- Produces: home 长按贴贴走 async + `alreadyDone` 文案；`onPrimaryAction` 删除 `prepared` 分支

- [ ] **Step 1: Rewrite the long-press callback in home.js**

In `main/egg-miniprogram/miniprogram/pages/home/home.js`, replace the `onTouchStart` long-press timer body (L114-135). Replace:

```js
  onTouchStart() {
    if (!this.data.pet || this.data.stage === 'hatched') return;
    this.completedLongPress = false;
    const started = Date.now();
    this.setData({ eggMotion: 'egg--warming', cuddleProgress: 1 });
    this.cuddleTicker = setInterval(() => {
      const progress = Math.min(99, Math.round((Date.now() - started) / 30));
      this.setData({ cuddleProgress: progress });
    }, 90);
    this.cuddleTimer = setTimeout(() => {
      clearInterval(this.cuddleTicker);
      const result = petStore.completeCuddle();
      this.completedLongPress = true;
      this.setData({ cuddleProgress: 100, eggMotion: 'egg--warm' });
      this.showFeedback(result.added ? '它暖起来了 · 孵化进度 +5%' : '它又往你这边靠了靠');
      if (wx.vibrateShort) wx.vibrateShort({ type: 'medium' });
      setTimeout(() => {
        this.setData({ cuddleProgress: 0, eggMotion: '' });
        this.onShow();
      }, 900);
    }, 3000);
  },
```

with:

```js
  onTouchStart() {
    if (!this.data.pet || this.data.stage === 'hatched') return;
    this.completedLongPress = false;
    const started = Date.now();
    this.setData({ eggMotion: 'egg--warming', cuddleProgress: 1 });
    this.cuddleTicker = setInterval(() => {
      const progress = Math.min(99, Math.round((Date.now() - started) / 30));
      this.setData({ cuddleProgress: progress });
    }, 90);
    this.cuddleTimer = setTimeout(() => {
      clearInterval(this.cuddleTicker);
      this.completedLongPress = true;
      this.setData({ cuddleProgress: 100, eggMotion: 'egg--warm' });
      if (wx.vibrateShort) wx.vibrateShort({ type: 'medium' });
      (async () => {
        const result = await petStore.completeCuddle();
        this.showFeedback(result.alreadyDone ? '它又往你这边靠了靠' : '它暖起来了');
        setTimeout(() => {
          this.setData({ cuddleProgress: 0, eggMotion: '' });
          this.onShow();
        }, 900);
      })();
    }, 3000);
  },
```

- [ ] **Step 2: Remove the `prepared` branch from `onPrimaryAction`**

In `home.js`, replace `onPrimaryAction` (L143-154):

```js
  onPrimaryAction() {
    const stage = this.data.stage;
    if (stage === 'ready') {
      wx.navigateTo({ url: '/pages/hatch/hatch' });
    } else if (stage === 'hatched') {
      wx.navigateTo({ url: '/pages/chat/chat' });
    } else if (stage === 'prepared') {
      this.showFeedback('它已经准备好了，收藏卡会在破壳日生成');
    } else {
      wx.navigateTo({ url: '/pages/hatch-guide/hatch-guide' });
    }
  },
```

with:

```js
  onPrimaryAction() {
    const stage = this.data.stage;
    if (stage === 'ready') {
      wx.navigateTo({ url: '/pages/hatch/hatch' });
    } else if (stage === 'hatched') {
      wx.navigateTo({ url: '/pages/chat/chat' });
    } else {
      wx.navigateTo({ url: '/pages/hatch-guide/hatch-guide' });
    }
  },
```

- [ ] **Step 3: Syntax check**

Run: `node --check main/egg-miniprogram/miniprogram/pages/home/home.js`
Expected: no output (success)

- [ ] **Step 4: Engineering check**

Run: `node main/egg-miniprogram/scripts/verify-project.js`
Expected: pass

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/home/home.js
git commit -m "feat(egg): home cuddle async + remove dead prepared primary-action branch"
```

---

### Task 7: 接入 hatch-guide 页（onShow 拉 hatch-actions 取当日完成态）

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/hatch-guide/hatch-guide.js`

**Interfaces:**
- Consumes: Task 1 `petApi.listHatchActions`（经 pet-store 间接调用不暴露，直接 require 也可），Task 3 `petStore.getHatchActionState`。本任务直接 `require('../../utils/pet-api')` 取 `listHatchActions`，非 demo 时缓存到 `pet._hatchActions` 再 `getHatchActionState`
- Produces: hatch-guide `onShow` 非 demo 异步拉取当日完成态；任务 done 改读 `getHatchActionState`

- [ ] **Step 1: Rewrite hatch-guide.js**

Replace the entire `main/egg-miniprogram/miniprogram/pages/hatch-guide/hatch-guide.js`:

```js
const petStore = require('../../utils/pet-store');
const petApi = require('../../utils/pet-api');

Page({
  data: { pet: null, tasks: [] },

  async onShow() {
    const pet = petStore.getPet();
    if (!pet) {
      wx.switchTab({ url: '/pages/home/home' });
      return;
    }
    // 非 demo：从后端拉修炼动作列表，缓存到 pet._hatchActions 供 getHatchActionState 派生
    if (!pet.demoMode && pet.hatchStatus !== 'HATCHED') {
      try {
        const actions = await petApi.listHatchActions(pet.id);
        pet._hatchActions = Array.isArray(actions) ? actions : [];
        petStore.savePet(pet);
      } catch (error) {
        // 拉取失败则沿用旧缓存或 tasks 默认值，不阻塞渲染
      }
    }
    const state = petStore.getHatchActionState(pet);
    this.setData({
      pet,
      tasks: [
        { key: 'nickname', title: '给蛋宝宝起昵称', desc: '让它知道自己是谁', reward: '提前 12 小时', done: state.nicknameDone, route: '/pages/nickname/nickname' },
        { key: 'cuddle', title: '贴贴蛋宝宝', desc: '回首页长按蛋壳 3 秒', reward: '提前 1 小时 / 日', done: state.cuddleDone, route: 'home' },
        { key: 'wish', title: '今日许愿', desc: '告诉它你期待怎样的陪伴', reward: '提前 1 小时 / 日', done: state.wishDone, route: '/pages/wish/wish' },
        { key: 'lesson', title: '蛋前教育', desc: '今天想教它一件什么事', reward: '提前 1 小时 / 日', done: state.lessonDone, route: '/pages/lesson/lesson' },
        { key: 'doodle', title: '彩蛋涂鸦', desc: '为蛋壳选颜色和花纹', reward: '提前 12 小时', done: state.doodleDone, route: '/pages/doodle/doodle' }
      ]
    });
  },

  onTask(e) {
    const route = e.currentTarget.dataset.route;
    if (route === 'home') {
      wx.switchTab({ url: '/pages/home/home' });
      setTimeout(() => wx.showToast({ title: '长按蛋壳 3 秒完成贴贴', icon: 'none' }), 300);
      return;
    }
    wx.navigateTo({ url: route });
  }
});
```

> 注：`reward` 文案从原 `+20% / +5%/日` 改为 Model X 的 `提前 12 小时 / 提前 1 小时·日`，与单轨减时模型一致（720 分=12h，60 分=1h）。

- [ ] **Step 2: Syntax check**

Run: `node --check main/egg-miniprogram/miniprogram/pages/hatch-guide/hatch-guide.js`
Expected: no output (success)

- [ ] **Step 3: Engineering check**

Run: `node main/egg-miniprogram/scripts/verify-project.js`
Expected: pass

- [ ] **Step 4: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/hatch-guide/hatch-guide.js
git commit -m "feat(egg): hatch-guide pulls hatch-actions for daily task done-state"
```

---

### Task 8: 接入 hatch 页 onReveal 为 async

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/hatch/hatch.js`

**Interfaces:**
- Consumes: Task 4 的 `async petStore.createCollectionCard`，返回 `{ ok, created, card, pet, message }`
- Produces: hatch `onReveal` 改 `async`，await 破壳后跳收藏卡页

- [ ] **Step 1: Rewrite hatch.js onReveal**

In `main/egg-miniprogram/miniprogram/pages/hatch/hatch.js`, replace `onReveal` (L31-42):

```js
  onReveal() {
    this.setData({ phase: 'reveal' });
    setTimeout(() => {
      const result = petStore.createCollectionCard();
      if (!result.ok) {
        this.setData({ phase: 'confirm' });
        wx.showToast({ title: result.message, icon: 'none' });
        return;
      }
      wx.redirectTo({ url: '/pages/collection-card/collection-card?new=1' });
    }, 1450);
  }
```

with:

```js
  onReveal() {
    this.setData({ phase: 'reveal' });
    setTimeout(() => {
      (async () => {
        const result = await petStore.createCollectionCard();
        if (!result.ok) {
          this.setData({ phase: 'confirm' });
          wx.showToast({ title: result.message, icon: 'none' });
          return;
        }
        wx.redirectTo({ url: '/pages/collection-card/collection-card?new=1' });
      })();
    }, 1450);
  }
```

- [ ] **Step 2: Syntax check**

Run: `node --check main/egg-miniprogram/miniprogram/pages/hatch/hatch.js`
Expected: no output (success)

- [ ] **Step 3: Engineering check**

Run: `node main/egg-miniprogram/scripts/verify-project.js`
Expected: pass

- [ ] **Step 4: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/hatch/hatch.js
git commit -m "feat(egg): hatch onReveal awaits async createCollectionCard"
```

---

### Task 9: 全量回归 + 文档收尾

**Files:**
- Verify: all test files, all modified pages
- Read: `main/egg-miniprogram/docs/2026-07-11-egg-hatch-cultivation-frontend-design.md`（设计 spec，已写）

- [ ] **Step 1: Run all unit tests**

Run:
```bash
node main/egg-miniprogram/miniprogram/utils/pet-api.test.js && \
node main/egg-miniprogram/miniprogram/utils/pet-store.test.js && \
node main/egg-miniprogram/miniprogram/utils/pet-store-actions.test.js && \
node main/egg-miniprogram/miniprogram/utils/request.test.js
```
Expected: each prints `ALL PASS`

- [ ] **Step 2: Syntax check all modified JS**

Run:
```bash
find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
```
Expected: no output (all files valid)

- [ ] **Step 3: Engineering check**

Run: `node main/egg-miniprogram/scripts/verify-project.js`
Expected: pass

- [ ] **Step 4: Manual flow verification (微信开发者工具)**

Import `main/egg-miniprogram/` in WeChat DevTools, with `/mini-ip` pointing `BASE_URL` to local `manager-api` (`/start-api`). Verify each flow:

1. 领养：输入有效邀请码 → 首页 stage `waiting`，倒计时 7 天
2. 修炼动作：进 `hatch-guide` → 任务 done 态从 `GET /pet/{id}/hatch-actions` 派生；依次做 `lesson/wish/nickname/doodle` → 每次 toast 成功，进度条/倒计时更新（后端 `acceleratedMinutes` 增加、`expectedHatchTime` 前移）
3. 幂等：同一动作当日再做 → toast "今天已经做过了"，无重复减时
4. 贴贴：首页长按蛋壳 3 秒 → toast "它暖起来了"，后端 cuddle 减 1 小时
5. 破壳：倒计时到 → 首页 `ready` → `查看破壳结果` → 破壳仪式 → `POST /pet/{id}/hatch` → 收藏卡页显示身份字段（mbti/星座/血型/性别/avatar）+ 装饰字段（serial/hatchQuality）
6. 档案：`pet-detail` 显示后端身份字段
7. demo 模式：`首页 → 展会体验` → 进入 demo pet（`demoMode=true`）→ 全流程走本地 mock，无后端调用
8. stage 5 态：确认无 `prepared` 文案出现；进度满即 `ready`

> 如某步在真机失败，记录现象，回到对应 Task 修复后重跑本步。

- [ ] **Step 5: Commit any fixups**

If Step 4 surfaced fixes, commit them:

```bash
git add -A
git commit -m "fix(egg): hatch/cultivation integration fixups from manual verification"
```

- [ ] **Step 6: Final commit (none if nothing to commit)**

If the spec doc edits from the brainstorming phase (PRD §5.3 single-track, CLAUDE.md stage table, API doc notes) are still uncommitted, commit them now:

```bash
git add docs/superpowers/specs/2026-07-11-egg-hatch-cultivation-frontend-design.md \
        docs/superpowers/plans/2026-07-11-egg-hatch-cultivation-frontend.md \
        main/egg-miniprogram/docs/蛋宝宝小程序MVP_PRD.md \
        main/egg-miniprogram/docs/egg-pet-identity-and-hatch-api.md \
        main/egg-miniprogram/CLAUDE.md
git commit -m "docs(egg): single-track hatch model spec + plan + PRD/CLAUDE/API alignment"
```

---

## Self-Review

**1. Spec coverage:**
- pet-store 门面分流 (Approach A) → Task 3/4 双轨实现 ✓
- `utils/pet-api.js` 7 个端点 → Task 1 ✓
- `savePetFromVO` 扩展身份字段 → Task 2 Step 3 ✓
- `getStage` 5 态删 prepared → Task 2 Step 4 ✓
- 5 个修炼动作分流 → Task 3 ✓
- `createCollectionCard` 真实破壳 → Task 4 ✓
- `getHatchActionState` → Task 3 Step 7 ✓
- `buildCollectionCard` 装饰字段 → Task 2 Step 5 ✓
- hatch-guide listHatchActions → Task 7 ✓
- 页面 async 改造 → Task 5/6/8 ✓
- 错误处理（10209/10214/alreadyDone/401）→ request.js 既有 401 重试 + 各函数 try/catch userMessage ✓
- 测试策略（getStage 5 分支/buildCollectionCard/demo 分流/pet-api 端点）→ Task 1-4 测试 ✓
- 文档同步（PRD §5.3/§6.3/§6.7, CLAUDE.md, API doc）→ brainstorming 阶段已改，Task 9 Step 6 提交 ✓

**2. Placeholder scan:** 无 TBD/TODO/「add error handling」；每步含完整代码与命令 ✓

**3. Type/签名一致性:**
- 统一契约 `{ ok, alreadyDone, pet, message }` 在 Task 3/4 定义，Task 5/6/8 页面消费一致 ✓
- `getHatchActionState` 返回 `{ nicknameDone, cuddleDone, wishDone, lessonDone, doodleDone }`，Task 7 消费 `state.nicknameDone` 等一致 ✓
- `buildCollectionCard(vo)` 在 Task 2 定义、Task 4 消费一致 ✓
- `petApi` 方法名在 Task 1 定义、Task 3/4/7 消费一致（`submitHatchAction/listHatchActions/hatchPet`）✓
- action type 常量 `NICKNAME/CUDDLE/WISH/LESSON/DOODLE` 在 Task 3 使用、与契约一致 ✓

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-11-egg-hatch-cultivation-frontend.md`. Two execution options:

1. **Subagent-Driven (recommended)** - 每个 Task 派一个 fresh subagent，Task 间 review，快速迭代
2. **Inline Execution** - 在当前 session 用 executing-plans 批量执行，带 checkpoint review

Which approach?
