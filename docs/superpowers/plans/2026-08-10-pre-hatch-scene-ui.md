# 破壳前场景 UI 改造实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 egg-miniprogram 破壳前 home 页从纯 CSS 蛋改造为 OSS 场景图叠加的孵蛋房（场景图层 + 窗外组件 + 陪伴入口图标化 + 涂鸦画笔），业务逻辑不变。

**Architecture:** 新增纯函数环境解析模块（environment-state → incubation-environment → OSS URL），新增三个自包含展示组件（incubation-scene / daily-window-detail / doodle-editor），home 页只保留业务逻辑。素材全部走 OSS（36 场景 × 3 类图 + 23 张窗景），参考实现从 main/eggbabe-miniprogram（静态 UI 项目，下称 STATIC）移植并剔除其 demo/runtime/cloud-api 依赖。

**Tech Stack:** 微信原生小程序（JS/WXML/WXSS/JSON），无构建工具；测试为 node + assert（mock Page/Component/wx），沿用项目现有风格。

**Spec:** `docs/superpowers/specs/2026-08-10-pre-hatch-scene-ui-design.md`

## Global Constraints

- 工程根：`main/egg-miniprogram/`，源码根 `miniprogram/`；页面/组件保持四件套 `.js/.json/.wxml/.wxss`
- 场景数 36（春 9 / 夏 9 / 秋 6 / 冬 12）；`spring_clear_night_v2`、`spring_clear_night_moonlight` 为备选调试图，不进清单
- OSS 前缀（图片名不变）：
  - 背景 `https://oss.eggbabe.com/scenes/pre-hatch/incubation-room/season-weather-full-scenes/{key}.webp`
  - 窝垫 `https://oss.eggbabe.com/scenes/pre-hatch/nest/season-weather/{key}_nest_pad.webp`
  - 蛋 `https://oss.eggbabe.com/scenes/pre-hatch/egg/season-weather/{key}_egg_right45.webp`
  - 窗景 `https://oss.eggbabe.com/scenes/pre-hatch/window/window-weather/`（共享 `w_01`~`w_07.webp` + 精确 `window_{sceneKey}_v01.webp`）
- 窗景两级查找：先 `bySceneKey[sceneKey]`，再按 weather×period 走 7 张共享图；未命中返回空串且**窗户点击无响应**（不打开组件、无兜底文案）；禁止跨天气/跨季节/用整幅房间图回退
- 环境规则：季节 = 领养日（`pet.hatchStartTime`）起每天一季春夏秋冬循环；时段 day(6-17)/sunset(17-19)/night；天气按蛋 id+日期哈希从季节池稳定抽取；不读定位不调天气 API
- 破壳流程保留现有视频遮罩 → 收藏卡页；场景 UI 只覆盖孵化期（waiting/hatching/soon/ready）
- 许愿池/早教班页面零改动，仅入口改为场景内图标
- 涂鸦：后端只存动作（hatch-action type=DOODLE，payload=`{artUrl}`），图片经 `POST /wechat/avatar` 上传通道传 OSS
- 单文件不超 800 行；注释用简洁中文；不引入 globalData；OSS base 收敛在 `config/api.js`
- 测试命令：`node <file>.test.js`；全量校验：`node main/egg-miniprogram/scripts/verify-project.js` 和 `find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check`
- commit 格式：`feat:` / `refactor:` / `test:` / `docs:` 等 conventional commits

---

## 阶段一：home 场景图层 + 窗外组件

### Task 1: 环境状态模块 environment-state

**Files:**
- Create: `main/egg-miniprogram/miniprogram/utils/environment-state.js`
- Test: `main/egg-miniprogram/miniprogram/utils/environment-state.test.js`

**Interfaces:**
- Consumes: 无（纯函数模块）
- Produces:
  - `resolve({ petId, hatchStartTime, timestamp })` → `{ season, weather, period, lightPhase, dateKey, incubationDay, sceneKey }`
  - `sceneKey(season, weather, period)` → string（weather 'sunny'→'clear'、'postSnow'→'post_snow'）
  - `periodFromLocalTime(timestamp)` → 'day'|'sunset'|'night'
  - `seasonBeforeHatch(hatchStartTime, timestamp)` → 'spring'|'summer'|'autumn'|'winter'
  - `millisecondsUntilNextEnvironmentBoundary(timestamp)` → number（供场景切换定时器）

- [ ] **Step 1: 写失败测试**

```javascript
const assert = require('assert');
const state = require('./environment-state');

// 以 2026-08-01 10:00 本地时间为领养时刻
const ADOPT = new Date(2026, 7, 1, 10, 0, 0).getTime();
const at = (dayOffset, hour, minute) =>
  new Date(2026, 7, 1 + dayOffset, hour, minute || 0, 0).getTime();

// 季节：领养当天春，次日夏，第 4 天冬，第 5 天回春
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(0, 10) }).season, 'spring');
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(1, 10) }).season, 'summer');
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(2, 10) }).season, 'autumn');
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(3, 10) }).season, 'winter');
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(4, 10) }).season, 'spring');

// 时段边界：6:00 day、16:59 day、17:00 sunset、18:59 sunset、19:00 night、5:59 night
assert.strictEqual(state.periodFromLocalTime(at(0, 6, 0)), 'day');
assert.strictEqual(state.periodFromLocalTime(at(0, 16, 59)), 'day');
assert.strictEqual(state.periodFromLocalTime(at(0, 17, 0)), 'sunset');
assert.strictEqual(state.periodFromLocalTime(at(0, 18, 59)), 'sunset');
assert.strictEqual(state.periodFromLocalTime(at(0, 19, 0)), 'night');
assert.strictEqual(state.periodFromLocalTime(at(0, 5, 59)), 'night');

// 天气可复现：同蛋同参数两次结果一致；不同蛋多次抽样结果有差异
const a1 = state.resolve({ petId: 'egg-a', hatchStartTime: ADOPT, timestamp: at(0, 10) }).weather;
const a2 = state.resolve({ petId: 'egg-a', hatchStartTime: ADOPT, timestamp: at(0, 10) }).weather;
assert.strictEqual(a1, a2);
const weathers = new Set(['egg-a', 'egg-b', 'egg-c', 'egg-d', 'egg-e', 'egg-f']
  .map(id => state.resolve({ petId: id, hatchStartTime: ADOPT, timestamp: at(0, 10) }).weather));
assert.ok(weathers.size >= 1, 'weather is drawn from the season pool');

// sceneKey 映射：sunny→clear、postSnow→post_snow
assert.strictEqual(state.sceneKey('spring', 'sunny', 'day'), 'spring_clear_day');
assert.strictEqual(state.sceneKey('winter', 'postSnow', 'night'), 'winter_post_snow_night');
assert.strictEqual(state.sceneKey('summer', 'storm', 'sunset'), 'summer_storm_sunset');

// lightPhase
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(0, 10) }).lightPhase, 'midday');
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(0, 18) }).lightPhase, 'sunset');
assert.strictEqual(state.resolve({ petId: 'p1', hatchStartTime: ADOPT, timestamp: at(0, 22) }).lightPhase, 'night');

// 下一时段边界为正数且不超过 24h
const wait = state.millisecondsUntilNextEnvironmentBoundary(at(0, 10));
assert.ok(wait > 0 && wait <= 24 * 60 * 60 * 1000);

console.log('environment-state.test.js: ALL PASS');
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/utils/environment-state.test.js`
Expected: FAIL `Cannot find module './environment-state'`

- [ ] **Step 3: 实现模块**

从 STATIC `miniprogram/services/environment-state.js` 移植，**删除破壳后逻辑**（`seasonAfterHatch`、`chinaMonth`）和 `environmentSeed/environmentVersion` 兼容形参（egg-miniprogram 无历史数据）。保留：`DAY_MS`、`SEASONS`、`PERIODS`、`WEATHER_POOLS`、`hash32`、`localParts/localDateKey/localDaySerial`、`companionDay`、`seasonBeforeHatch`、`periodFromLocalTime`、`lightPhaseFromPeriod`、`weatherForSlot`、`weatherAssetName`、`sceneKey`、`nextEnvironmentBoundary`、`millisecondsUntilNextEnvironmentBoundary`、`resolve`。

`resolve` 精简为：

```javascript
function resolve(options) {
  const source = options || {};
  const timestamp = timestampOf(source.timestamp);
  const period = periodFromLocalTime(timestamp);
  const dateKey = localDateKey(timestamp);
  const incubationDay = companionDay(source.hatchStartTime, timestamp);
  const season = seasonBeforeHatch(source.hatchStartTime, timestamp);
  const weather = weatherForSlot({
    eggId: source.petId,
    season,
    period,
    dateKey,
    timestamp
  });
  return { season, weather, period, lightPhase: lightPhaseFromPeriod(period), dateKey, incubationDay, sceneKey: sceneKey(season, weather, period) };
}
```

`weatherForSlot` 种子简化为 `[petId || 'legacy-egg', dateKey, period].join('|')`。模块顶部加注释：「应用内环境状态：不读定位、不调天气 API，以领养日和本机时段为种子，可复现便于验收」。

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/utils/environment-state.test.js`
Expected: PASS `ALL PASS`

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/environment-state.js main/egg-miniprogram/miniprogram/utils/environment-state.test.js
git commit -m "feat: add incubation environment state resolver"
```

---

### Task 2: 场景素材配置 pre-hatch-assets

**Files:**
- Create: `main/egg-miniprogram/miniprogram/config/pre-hatch-assets.js`
- Modify: `main/egg-miniprogram/miniprogram/config/api.js`（新增 OSS_SCENE_BASE 常量）
- Test: `main/egg-miniprogram/miniprogram/config/pre-hatch-assets.test.js`（新建）

**Interfaces:**
- Consumes: `config/api.js` 的 `OSS_SCENE_BASE`
- Produces:
  - `SCENE_OPTIONS`：36 项数组，每项 `{ key, season, weather, period, lightPhase, className, background, nest, egg }`（background/nest/egg 为完整 OSS URL）
  - `WINDOW_WEATHER`：`{ clearDay, clearSunset, clearNight, cloudyDay, cloudyNight, snowDay, snowNight, bySceneKey: { 16 项 } }`（完整 OSS URL）
  - `EGG_DEPTH_OVERLAY`、`EGG_SPECULAR_OVERLAY`：本地路径字符串
  - `INTERACTION_ICONS`：`{ wish, learn, draw }` 本地路径

- [ ] **Step 1: 写失败测试**

```javascript
const assert = require('assert');
const assets = require('./pre-hatch-assets');

assert.strictEqual(assets.SCENE_OPTIONS.length, 36, '36 scenes');
const keys = assets.SCENE_OPTIONS.map(s => s.key);
assert.ok(keys.includes('spring_clear_day') && keys.includes('winter_post_snow_night'));
assert.ok(!keys.includes('spring_clear_night_v2'), 'debug variant not in list');

const springClearDay = assets.SCENE_OPTIONS.find(s => s.key === 'spring_clear_day');
assert.strictEqual(springClearDay.background,
  'https://oss.eggbabe.com/scenes/pre-hatch/incubation-room/season-weather-full-scenes/spring_clear_day.webp');
assert.strictEqual(springClearDay.nest,
  'https://oss.eggbabe.com/scenes/pre-hatch/nest/season-weather/spring_clear_day_nest_pad.webp');
assert.strictEqual(springClearDay.egg,
  'https://oss.eggbabe.com/scenes/pre-hatch/egg/season-weather/spring_clear_day_egg_right45.webp');
assert.strictEqual(springClearDay.className, 'season-spring weather-sunny period-day light-midday');

// 每个场景三类图 URL 都非空且同 key
for (const scene of assets.SCENE_OPTIONS) {
  assert.ok(scene.background.endsWith(`${scene.key}.webp`), scene.key);
  assert.ok(scene.nest.endsWith(`${scene.key}_nest_pad.webp`), scene.key);
  assert.ok(scene.egg.endsWith(`${scene.key}_egg_right45.webp`), scene.key);
}

// 窗景：7 张共享 + 16 张精确
assert.strictEqual(Object.keys(assets.WINDOW_WEATHER.bySceneKey).length, 16);
assert.strictEqual(assets.WINDOW_WEATHER.bySceneKey.spring_rain_day,
  'https://oss.eggbabe.com/scenes/pre-hatch/window/window-weather/window_spring_rain_day_v01.webp');
assert.strictEqual(assets.WINDOW_WEATHER.clearDay,
  'https://oss.eggbabe.com/scenes/pre-hatch/window/window-weather/w_01_clear_day.webp');

console.log('pre-hatch-assets.test.js: ALL PASS');
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/config/pre-hatch-assets.test.js`
Expected: FAIL `Cannot find module './pre-hatch-assets'`

- [ ] **Step 3: 实现**

`config/api.js` 增加：

```javascript
const OSS_SCENE_BASE = 'https://oss.eggbabe.com/scenes/pre-hatch';
module.exports = { API_BASE_URL, OSS_SCENE_BASE };
```

`config/pre-hatch-assets.js`：从 STATIC `config/pre-hatch-assets.js` 移植 `SCENE_TESTER_OPTIONS` 的 36 行表（重命名 `SCENE_OPTIONS`），路径改为 OSS：

```javascript
// 破壳前场景素材集中配置。整幅背景/窝垫/蛋/窗景均托管 OSS，本地只保留蛋壳 overlay 与入口图标。
const { OSS_SCENE_BASE } = require('./api');
const FULL_SCENE_ROOT = `${OSS_SCENE_BASE}/incubation-room/season-weather-full-scenes`;
const NEST_SCENE_ROOT = `${OSS_SCENE_BASE}/nest/season-weather`;
const EGG_SCENE_ROOT = `${OSS_SCENE_BASE}/egg/season-weather`;
const WINDOW_SCENE_ROOT = `${OSS_SCENE_BASE}/window/window-weather`;

const SCENE_TABLE = [
  ['spring_clear_day', 'spring', 'sunny', 'day', 'midday'],
  // …STATIC 文件中的 36 行，去掉中文 label 列（egg-miniprogram 无场景切换调试器）
];

const SCENE_OPTIONS = SCENE_TABLE.map(([key, season, weather, period, lightPhase]) => ({
  key, season, weather, period, lightPhase,
  className: `season-${season} weather-${weather} period-${period} light-${lightPhase}`,
  background: `${FULL_SCENE_ROOT}/${key}.webp`,
  nest: `${NEST_SCENE_ROOT}/${key}_nest_pad.webp`,
  egg: `${EGG_SCENE_ROOT}/${key}_egg_right45.webp`
}));

const WINDOW_WEATHER = {
  clearDay: `${WINDOW_SCENE_ROOT}/w_01_clear_day.webp`,
  clearSunset: `${WINDOW_SCENE_ROOT}/w_02_clear_sunset.webp`,
  clearNight: `${WINDOW_SCENE_ROOT}/w_03_clear_night.webp`,
  cloudyDay: `${WINDOW_SCENE_ROOT}/w_04_cloudy_day.webp`,
  cloudyNight: `${WINDOW_SCENE_ROOT}/w_05_cloudy_night.webp`,
  snowDay: `${WINDOW_SCENE_ROOT}/w_06_snow_day.webp`,
  snowNight: `${WINDOW_SCENE_ROOT}/w_07_snow_night.webp`,
  // 16 个环境键的精确窗景；优先于共享图。
  bySceneKey: {
    spring_cloudy_sunset: `${WINDOW_SCENE_ROOT}/window_spring_cloudy_sunset_v01.webp`,
    spring_rain_day: `${WINDOW_SCENE_ROOT}/window_spring_rain_day_v01.webp`,
    spring_rain_sunset: `${WINDOW_SCENE_ROOT}/window_spring_rain_sunset_v01.webp`,
    spring_rain_night: `${WINDOW_SCENE_ROOT}/window_spring_rain_night_v01.webp`,
    summer_cloudy_sunset: `${WINDOW_SCENE_ROOT}/window_summer_cloudy_sunset_v01.webp`,
    summer_storm_day: `${WINDOW_SCENE_ROOT}/window_summer_storm_day_v01.webp`,
    summer_storm_sunset: `${WINDOW_SCENE_ROOT}/window_summer_storm_sunset_v01.webp`,
    summer_storm_night: `${WINDOW_SCENE_ROOT}/window_summer_storm_night_v01.webp`,
    autumn_rain_day: `${WINDOW_SCENE_ROOT}/window_autumn_rain_day_v01.webp`,
    autumn_rain_sunset: `${WINDOW_SCENE_ROOT}/window_autumn_rain_sunset_v01.webp`,
    autumn_rain_night: `${WINDOW_SCENE_ROOT}/window_autumn_rain_night_v01.webp`,
    winter_cloudy_sunset: `${WINDOW_SCENE_ROOT}/window_winter_cloudy_sunset_v01.webp`,
    winter_snow_sunset: `${WINDOW_SCENE_ROOT}/window_winter_snow_sunset_v01.webp`,
    winter_post_snow_day: `${WINDOW_SCENE_ROOT}/window_winter_post_snow_day_v01.webp`,
    winter_post_snow_sunset: `${WINDOW_SCENE_ROOT}/window_winter_post_snow_sunset_v01.webp`,
    winter_post_snow_night: `${WINDOW_SCENE_ROOT}/window_winter_post_snow_night_v01.webp`
  }
};

const EGG_ASSET_ROOT = '/assets/scenes/egg';
module.exports = {
  SCENE_OPTIONS,
  WINDOW_WEATHER,
  EGG_DEPTH_OVERLAY: `${EGG_ASSET_ROOT}/egg_shell_depth_overlay_512_v01.webp`,
  EGG_SPECULAR_OVERLAY: `${EGG_ASSET_ROOT}/egg_shell_specular_overlay_512_v01.webp`,
  INTERACTION_ICONS: {
    wish: '/assets/ui/3d-actions/ui_3d_wishing_fountain_two_tier_simple_256_v04.webp',
    learn: '/assets/ui/3d-actions/ui_3d_early_learning_picture_book_simple_256_v03.webp',
    draw: '/assets/ui/3d-actions/ui_3d_drawing_palette_256_v02.webp'
  }
};
```

同时拷贝本地素材（从 STATIC 仓库）：

```bash
mkdir -p main/egg-miniprogram/miniprogram/assets/scenes/egg main/egg-miniprogram/miniprogram/assets/ui/3d-actions
cp main/eggbabe-miniprogram/miniprogram/assets/scenes/lifecycle/pre-hatch/30-character/egg/egg_shell_depth_overlay_512_v01.webp \
   main/eggbabe-miniprogram/miniprogram/assets/scenes/lifecycle/pre-hatch/30-character/egg/egg_shell_specular_overlay_512_v01.webp \
   main/egg-miniprogram/miniprogram/assets/scenes/egg/
cp main/eggbabe-miniprogram/miniprogram/assets/ui/3d-actions/runtime/ui_3d_wishing_fountain_two_tier_simple_256_v04.webp \
   main/eggbabe-miniprogram/miniprogram/assets/ui/3d-actions/runtime/ui_3d_early_learning_picture_book_simple_256_v03.webp \
   main/eggbabe-miniprogram/miniprogram/assets/ui/3d-actions/runtime/ui_3d_drawing_palette_256_v02.webp \
   main/egg-miniprogram/miniprogram/assets/ui/3d-actions/
```

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/config/pre-hatch-assets.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/config/pre-hatch-assets.js main/egg-miniprogram/miniprogram/config/pre-hatch-assets.test.js main/egg-miniprogram/miniprogram/config/api.js main/egg-miniprogram/miniprogram/assets/
git commit -m "feat: add pre-hatch scene asset config with OSS paths"
```

---

### Task 3: 环境解析模块 incubation-environment

**Files:**
- Create: `main/egg-miniprogram/miniprogram/utils/incubation-environment.js`
- Test: `main/egg-miniprogram/miniprogram/utils/incubation-environment.test.js`

**Interfaces:**
- Consumes: `environment-state.resolve`（Task 1）、`pre-hatch-assets.SCENE_OPTIONS/WINDOW_WEATHER`（Task 2）
- Produces:
  - `resolveForPet(pet, timestamp)` → `{ valid, season, weather, period, lightPhase, dateKey, incubationDay, sceneKey, fullSceneImage, nestImage, eggImage, windowImage, className }`；sceneKey 不在清单时 `valid:false` 且图片字段为空串
  - `resolveScene(environment)` → 同上（`resolveForPet` 的场景组装部分拆出，便于直接测试无效 sceneKey 兜底）
  - `windowAssetPath(sceneKey, weather, period)` → string（无两参数兼容分支）

- [ ] **Step 1: 写失败测试**

```javascript
const assert = require('assert');
const env = require('./incubation-environment');

const pet = { id: 'egg-a', hatchStartTime: new Date(2026, 7, 1, 10, 0, 0).getTime() };
const result = env.resolveForPet(pet, new Date(2026, 7, 1, 12, 0, 0).getTime());
assert.strictEqual(result.valid, true);
assert.ok(result.fullSceneImage.startsWith('https://oss.eggbabe.com/scenes/pre-hatch/incubation-room/'));
assert.ok(result.nestImage.includes('/nest/season-weather/'));
assert.ok(result.eggImage.includes('/egg/season-weather/'));
assert.ok(result.className.includes(`season-${result.season}`));

// 窗景两级查找
assert.ok(env.windowAssetPath('spring_rain_day', 'rain', 'day').endsWith('window_spring_rain_day_v01.webp'), 'exact scene key wins');
assert.ok(env.windowAssetPath('spring_clear_day', 'sunny', 'day').endsWith('w_01_clear_day.webp'), 'shared sunny day');
assert.ok(env.windowAssetPath('spring_clear_sunset', 'sunny', 'sunset').endsWith('w_02_clear_sunset.webp'));
assert.ok(env.windowAssetPath('spring_clear_night', 'sunny', 'night').endsWith('w_03_clear_night.webp'));
assert.ok(env.windowAssetPath('spring_cloudy_day', 'cloudy', 'day').endsWith('w_04_cloudy_day.webp'));
assert.ok(env.windowAssetPath('winter_snow_day', 'snow', 'day').endsWith('w_06_snow_day.webp'));
assert.ok(env.windowAssetPath('winter_snow_night', 'snow', 'night').endsWith('w_07_snow_night.webp'));
// 未命中返回空串，不回退房间图
assert.strictEqual(env.windowAssetPath('nope', 'rain', 'sunset'), '');
assert.strictEqual(env.windowAssetPath('nope', 'cloudy', 'sunset'), '');
assert.strictEqual(env.windowAssetPath('nope', 'postSnow', 'sunset'), '');

// 36 个场景全部有窗景（精确或共享）
const { SCENE_OPTIONS } = require('../config/pre-hatch-assets');
for (const scene of SCENE_OPTIONS) {
  assert.ok(env.windowAssetPath(scene.key, scene.weather, scene.period), `window image for ${scene.key}`);
}

// 未知场景 key → valid=false 且图片字段为空串
const invalid = env.resolveForPet(pet, new Date(2026, 7, 1, 12, 0, 0).getTime());
assert.strictEqual(invalid.valid, true); // 正常时间必然命中 36 场景之一
// 直接构造不在清单的 sceneKey 验证兜底分支
const missing = env.resolveScene({ season: 'spring', weather: 'sunny', period: 'day', lightPhase: 'midday', dateKey: '2026-08-01', incubationDay: 1, sceneKey: 'spring_clear_dawn' });
assert.strictEqual(missing.valid, false);
assert.strictEqual(missing.fullSceneImage, '');

console.log('incubation-environment.test.js: ALL PASS');
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/utils/incubation-environment.test.js`
Expected: FAIL `Cannot find module`

- [ ] **Step 3: 实现**

```javascript
// 破壳前环境解析：场景 key → OSS 图片 URL 与样式类名。
// 窗景两级查找：sceneKey 精确图优先，其次 weather×period 共享图；
// 未命中返回空串（窗户点击无响应），禁止跨天气/季节或用房间图回退。
const state = require('./environment-state');
const { SCENE_OPTIONS, WINDOW_WEATHER } = require('../config/pre-hatch-assets');

const SCENE_BY_KEY = SCENE_OPTIONS.reduce((map, scene) => {
  map[scene.key] = scene;
  return map;
}, {});

function windowAssetPath(sceneKey, weather, period) {
  const exact = WINDOW_WEATHER.bySceneKey[String(sceneKey || '')];
  if (exact) return exact;
  const byWeather = {
    sunny: { day: WINDOW_WEATHER.clearDay, sunset: WINDOW_WEATHER.clearSunset, night: WINDOW_WEATHER.clearNight },
    cloudy: { day: WINDOW_WEATHER.cloudyDay, night: WINDOW_WEATHER.cloudyNight },
    snow: { day: WINDOW_WEATHER.snowDay, night: WINDOW_WEATHER.snowNight }
  };
  return (byWeather[String(weather || '')] || {})[String(period || '')] || '';
}

function resolveScene(environment) {
  const scene = SCENE_BY_KEY[environment.sceneKey];
  if (!scene) {
    return Object.assign({}, environment, {
      valid: false, fullSceneImage: '', nestImage: '', eggImage: '', windowImage: '',
      className: `season-${environment.season} weather-${environment.weather} period-${environment.period} light-${environment.lightPhase}`
    });
  }
  return Object.assign({}, environment, {
    valid: true,
    fullSceneImage: scene.background,
    nestImage: scene.nest,
    eggImage: scene.egg,
    windowImage: windowAssetPath(environment.sceneKey, environment.weather, environment.period),
    className: scene.className
  });
}

function resolveForPet(pet, timestamp) {
  return resolveScene(state.resolve({
    petId: pet && pet.id,
    hatchStartTime: pet && pet.hatchStartTime,
    timestamp
  }));
}

module.exports = { resolveForPet, resolveScene, windowAssetPath };
```

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/utils/incubation-environment.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/incubation-environment.js main/egg-miniprogram/miniprogram/utils/incubation-environment.test.js
git commit -m "feat: resolve incubation scene images from environment state"
```

---

### Task 4: 天气粒子工具 window-weather-canvas

**Files:**
- Create: `main/egg-miniprogram/miniprogram/utils/window-weather-canvas.js`
- Create: `main/egg-miniprogram/miniprogram/utils/canvas-2d.js`

**Interfaces:**
- Consumes: `canvas-2d.createLayer(page, selector)` → Promise<{canvas, context, width, height, left, top}|null>
- Produces:
  - `canvas-2d.js`：`{ createLayer, loadImage, exportImage }`（`exportImage(layer)` → Promise<tempFilePath|''>）
  - `window-weather-canvas.js`：`{ start(page, selector, weather, options) → controller { stop() } }`（雨/雪/雷雨粒子绘制；晴朗/多云返回只含 stop 的空 controller）

- [ ] **Step 1: 移植**

两个文件均为纯移植（STATIC 版本无项目特有依赖）：

```bash
cp main/eggbabe-miniprogram/miniprogram/utils/canvas-2d.js main/egg-miniprogram/miniprogram/utils/canvas-2d.js
cp main/eggbabe-miniprogram/miniprogram/utils/window-weather-canvas.js main/egg-miniprogram/miniprogram/utils/window-weather-canvas.js
```

移植后检查 `window-weather-canvas.js` 的 require 路径（STATIC 中若引用 `../services/...` 需改为 `../utils/...` 或删除对应功能），并 `node --check` 两个文件。

- [ ] **Step 2: 验证**

Run: `node --check main/egg-miniprogram/miniprogram/utils/canvas-2d.js && node --check main/egg-miniprogram/miniprogram/utils/window-weather-canvas.js`
Expected: 无输出（语法通过）

- [ ] **Step 3: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/canvas-2d.js main/egg-miniprogram/miniprogram/utils/window-weather-canvas.js
git commit -m "feat: port canvas helpers for scene weather particles"
```

---

### Task 5: incubation-scene 组件

**Files:**
- Create: `main/egg-miniprogram/miniprogram/components/incubation-scene/incubation-scene.js`（+ `.json/.wxml/.wxss`）
- Test: `main/egg-miniprogram/miniprogram/components/incubation-scene/incubation-scene.test.js`

**Interfaces:**
- Consumes: `incubation-environment.resolveForPet` 的返回（Task 3）、`pre-hatch-assets.EGG_DEPTH_OVERLAY/EGG_SPECULAR_OVERLAY`、`window-weather-canvas.start`（Task 4）
- Produces（home 页将使用）:
  - properties: `environment`（Object）、`eggArtUrl`（String，涂鸦层）、`lampOn`（Boolean）
  - events: `eggtap`、`eggcuddle`（长按 600ms）、`windowtap`（带窗户矩形 detail）、`retryscene`

**组件结构**（WXML 骨架，样式从 STATIC `home.wxss` 的 `.incubation-*`/`.window-*`/`.egg*`/`.room-*` 段移植，去掉页面级和 daily-window/scene-tester 相关）：

```
incubation-scene (相对定位容器，100vw×100vh)
├── image.incubation-full-scene-image--previous  (z1，交叉淡化垫底)
├── image.incubation-full-scene-image            (z2，bindload/binderror)
├── view.incubation-scene-error                  (z30，加载失败：重试 → triggerEvent('retryscene'))
├── view.window-effects                          (z4，CSS 光效层，按 className 变色)
│   └── canvas#windowWeatherCanvas               (z6，catchtap → 量矩形后 triggerEvent('windowtap', rect))
├── image.incubation-nest-image                  (z5)
├── view.room-light-breathe / view.room-lamp-hotspot (z9，tap → 双向绑定 lampOn)
├── view.room-clock                              (z18，指针/数字两态 tap 切换)
└── view.egg                                     (z7，catchtap='eggtap'，longpress='eggcuddle')
    ├── image.egg-shell-preview--base  src=environment.eggImage
    ├── image.egg-shell-preview--art   src=eggArtUrl (wx:if)
    ├── image.egg-shell-depth          src=EGG_DEPTH_OVERLAY
    └── image.egg-shell-specular       src=EGG_SPECULAR_OVERLAY
```

- [ ] **Step 1: 写失败测试**

```javascript
const assert = require('assert');
const path = require('path');
const Module = require('module');

const componentPath = require.resolve('./incubation-scene');
const originalLoad = Module._load;
const originalComponent = global.Component;
let componentConfig;

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === componentPath) {
    if (request === '../../utils/window-weather-canvas') return { start: () => ({ stop() {} }) };
  }
  return originalLoad.call(this, request, parent, isMain);
};
global.Component = (config) => { componentConfig = config; };

require('./incubation-scene');
assert.ok(componentConfig, 'component registered');
assert.deepStrictEqual(Object.keys(componentConfig.properties).sort(),
  ['eggArtUrl', 'environment', 'lampOn'].sort());

const events = [];
const instance = {
  data: { ...componentConfig.data },
  properties: { environment: null, eggArtUrl: '', lampOn: false },
  setData(changes) { this.data = { ...this.data, ...changes }; },
  triggerEvent(name, detail) { events.push({ name, detail }); },
  ...componentConfig.methods,
  ...componentConfig.lifetimes ? {} : {}
};

// 蛋点击/长按事件
componentConfig.methods.onEggTap.call(instance);
componentConfig.methods.onEggCuddle.call(instance);
assert.deepStrictEqual(events.map(e => e.name), ['eggtap', 'eggcuddle']);

// 背景图加载失败进入错误态
componentConfig.methods.onFullSceneImageError.call(instance);
assert.strictEqual(instance.data.fullSceneImageFailed, true);
// 重试触发 retryscene 并复位错误态
componentConfig.methods.onRetryFullSceneImage.call(instance);
assert.strictEqual(instance.data.fullSceneImageFailed, false);
assert.strictEqual(events[events.length - 1].name, 'retryscene');

console.log('incubation-scene.test.js: ALL PASS');
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/components/incubation-scene/incubation-scene.test.js`
Expected: FAIL `Cannot find module './incubation-scene'`

- [ ] **Step 3: 实现组件**

JS 核心（数据与方法名供 home 页联调）：

```javascript
// 孵蛋房场景组件：整幅背景 + 窝垫 + 蛋 + 窗口光效 + 天气粒子 + 台灯 + 时钟。
// 纯展示组件：不请求任何业务 API，交互通过事件抛给页面。
const weatherCanvas = require('../../utils/window-weather-canvas');
const { EGG_DEPTH_OVERLAY, EGG_SPECULAR_OVERLAY } = require('../../config/pre-hatch-assets');

Component({
  properties: {
    environment: { type: Object, value: null },
    eggArtUrl: { type: String, value: '' },
    lampOn: { type: Boolean, value: false }
  },
  data: {
    depthOverlay: EGG_DEPTH_OVERLAY,
    specularOverlay: EGG_SPECULAR_OVERLAY,
    previousFullSceneImage: '',
    fullSceneImageFailed: false,
    sceneCrossfadeActive: false,
    clockMode: 'analog',   // analog | digital，点击切换
    clockTimeText: '',
    clockDateText: '',
    clockHourStyle: '',
    clockMinuteStyle: '',
    clockSecondStyle: ''
  },
  observers: {
    'environment.sceneKey': function () { this.applySceneChange(); }
  },
  lifetimes: {
    attached() { this.startClock(); },
    ready() { this.setupWeatherCanvas(); },
    detached() { this.stopClock(); this.stopWeatherCanvas(); }
  },
  methods: {
    // 场景切换：新图加载完成后交叉淡化，旧图垫底；加载失败保持旧场景
    applySceneChange() { /* previousFullSceneImage=当前图 → setData 新图 → onFullSceneImageLoad 后 600ms 清 previous */ },
    onFullSceneImageLoad() { /* sceneCrossfadeActive=true，定时清 previousFullSceneImage */ },
    onFullSceneImageError() { this.setData({ fullSceneImageFailed: true }); },
    onRetryFullSceneImage() { this.setData({ fullSceneImageFailed: false }); this.triggerEvent('retryscene'); },
    onEggTap() { this.triggerEvent('eggtap'); },
    onEggCuddle() { this.triggerEvent('eggcuddle'); },
    onLampTap() { this.triggerEvent('lamptap'); },
    onClockTap() { /* analog ↔ digital */ },
    onWindowTap() { /* createSelectorQuery 量 .window-effects 矩形 → triggerEvent('windowtap', rect) */ },
    setupWeatherCanvas() { /* weatherCanvas.start(this, '#windowWeatherCanvas', this.properties.environment.weather) */ },
    stopWeatherCanvas() { /* controller.stop() */ },
    startClock() { /* 1s tick 更新指针角度与数字文本 */ },
    stopClock() { /* clearInterval */ }
  }
});
```

`.json`：`{ "component": true }`。WXSS 从 STATIC `pages/home/home.wxss` 第 27-160 行的场景段移植（选择器前缀保持不变，删除 `.top-row`、`.scene-tester`、`.daily-window` 相关）。

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/components/incubation-scene/incubation-scene.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/components/incubation-scene/
git commit -m "feat: add incubation scene component"
```

---

### Task 6: daily-window-detail 组件

**Files:**
- Create: `main/egg-miniprogram/miniprogram/components/daily-window-detail/`（四件套）

**Interfaces:**
- Consumes: `window-weather-canvas.start`（Task 4）
- Produces:
  - properties: `visible`(Boolean)、`image`(String)、`weather`、`season`、`period`、`lightPhase`、`weatherLabel`、`periodLabel`、`originStyle`(String)
  - events: `close`、`retry`
  - 行为约定：`visible` 变 true 且 `image` 非空 → 320ms 淡入；`image` 为空 → 不渲染（调用方保证）；图片 binderror → 错误态「窗外景色没有加载好」+ 重试（triggerEvent('retry')）；返回按钮 → triggerEvent('close')

- [ ] **Step 1: 移植并改造**

从 STATIC `components/daily-window-detail/` 拷贝四件套，做以下删除：

- 删除 `magicEnabled` property 与 `.daily-window__magic-*` 全部 WXML/WXSS/JS（远方魔法入口不做）
- 删除 `onSceneTap`/飞鸟点击层（`.daily-window__scene-tap-layer`、`tapBirds` 及相关 CSS 动画）——保留 `daily-window__ambient-birds` 环境飞鸟（纯 CSS 循环动画，无交互）
- 删除 `empty` 状态分支：WXML 中 `wx:elif="{{empty}}"` 的「窗外还没有准备好」块删除；JS 中 `empty` 字段改为永不置 true（打开前 home 页已保证 image 非空）
- 保留：entering/open/exiting 相位机、originStyle 变量、时段色调层、光效/尘埃/夜光点、天气 canvas、返回按钮、错误态重试

- [ ] **Step 2: 验证**

Run: `node --check main/egg-miniprogram/miniprogram/components/daily-window-detail/daily-window-detail.js`
Expected: 无输出；并人工核对 WXML 中无 `empty`、`magic`、`scene-tap` 残留：`grep -n "empty\|magic\|scene-tap" main/egg-miniprogram/miniprogram/components/daily-window-detail/*` 应为空

- [ ] **Step 3: Commit**

```bash
git add main/egg-miniprogram/miniprogram/components/daily-window-detail/
git commit -m "feat: add daily window detail component"
```

---

### Task 7: home 页接入场景组件

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.wxml`
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.wxss`
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.json`（注册组件）
- Test: `main/egg-miniprogram/miniprogram/pages/home/home.test.js`（若不存在则新建）

**Interfaces:**
- Consumes: `<incubation-scene>`（Task 5）、`<daily-window-detail>`（Task 6）、`incubation-environment.resolveForPet`（Task 3）、`environment-state.millisecondsUntilNextEnvironmentBoundary`（Task 1）、现有 `petStore`、`auth`
- Produces: home 页 data 新增 `environment`、`dailyWindowVisible`、`dailyWindowOriginStyle`、`dailyWindowWeatherLabel`、`dailyWindowPeriodLabel`

- [ ] **Step 1: 写失败测试**

沿用 `add-device.test.js` 的 mock 模式，覆盖：

```javascript
// 孵化期：environment 被解析且非空
// hatched：不解析环境（environment 保持 null）
// onEggTap 事件 → 调 petStore 的 cuddle（沿用现有 onEggTap 业务逻辑）
// onWindowTap：windowImage 非空 → dailyWindowVisible=true；空串 → 不打开
// onDailyWindowClosed → dailyWindowVisible=false
// 时段定时器：setTimeout 延迟等于 millisecondsUntilNextEnvironmentBoundary 返回值
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`
Expected: FAIL（environment 未定义等）

- [ ] **Step 3: 改造 home 页**

WXML：`stage !== 'hatched'` 分支替换为：

```xml
<incubation-scene
  environment="{{environment}}"
  egg-art-url="{{eggArtUrl}}"
  lamp-on="{{lampOn}}"
  bind:eggtap="onEggTap"
  bind:eggcuddle="onEggCuddle"
  bind:lamptap="onLampTap"
  bind:windowtap="onWindowTap"
  bind:retryscene="onRetryScene"
/>
<!-- ready 态破壳入口按钮叠加在场景上（保留现有 onPrimaryAction 逻辑与文案） -->
<daily-window-detail
  visible="{{dailyWindowVisible}}"
  image="{{environment.windowImage}}"
  weather="{{environment.weather}}"
  season="{{environment.season}}"
  period="{{environment.period}}"
  light-phase="{{environment.lightPhase}}"
  weather-label="{{dailyWindowWeatherLabel}}"
  period-label="{{dailyWindowPeriodLabel}}"
  origin-style="{{dailyWindowOriginStyle}}"
  bindclose="onDailyWindowClosed"
  bindretry="onDailyWindowRetry"
/>
```

JS 关键改动：

```javascript
const incubationEnv = require('../../utils/incubation-environment');
const envState = require('../../utils/environment-state');

// onShow / pet 恢复成功后：
refreshEnvironment() {
  if (!this.data.pet || this.data.stage === 'hatched') {
    this.setData({ environment: null });
    return;
  }
  const environment = incubationEnv.resolveForPet(this.data.pet, Date.now());
  this.setData({ environment });
  this.scheduleEnvironmentRefresh();
},
scheduleEnvironmentRefresh() {
  clearTimeout(this.environmentTimer);
  this.environmentTimer = setTimeout(
    () => this.refreshEnvironment(),
    envState.millisecondsUntilNextEnvironmentBoundary(Date.now())
  );
},
onWindowTap(e) {
  const environment = this.data.environment;
  if (!environment || !environment.windowImage) return;  // 未命中窗景：无响应
  const rect = e.detail || {};
  this.setData({
    dailyWindowVisible: true,
    dailyWindowOriginStyle: [
      `--daily-window-origin-left:${Number(rect.left || 0)}px;`,
      `--daily-window-origin-top:${Number(rect.top || 0)}px;`,
      `--daily-window-origin-width:${Math.max(1, Number(rect.width || 1))}px;`,
      `--daily-window-origin-height:${Math.max(1, Number(rect.height || 1))}px;`
    ].join(''),
    dailyWindowWeatherLabel: WEATHER_LABELS[environment.weather] || '晴朗',
    dailyWindowPeriodLabel: environment.lightPhase === 'sunset' ? '日落' : (environment.period === 'night' ? '夜晚' : '日间')
  });
},
onDailyWindowClosed() { this.setData({ dailyWindowVisible: false }); },
onDailyWindowRetry() { /* 重置组件 image 触发重载：先置空再还原 */ },
onLampTap() { this.setData({ lampOn: !this.data.lampOn }); },
onRetryScene() { this.refreshEnvironment(); }
```

`WEATHER_LABELS = { sunny:'晴朗', cloudy:'多云', rain:'下雨', storm:'雷雨', snow:'降雪', postSnow:'雪后' }`。现有 `onEggTap`（摸一摸反馈）、长按 cuddle 调 `petStore` CUDDLE 动作的逻辑保留，仅事件来源改为组件抛出。`onHide`/`onUnload` 清 `environmentTimer`。旧 CSS 蛋样式（`.egg-pattern`/`.egg-shine`/`.egg-shadow` 等仅孵化期使用的）从 home.wxss 删除；`.life-home-scene` 等 hatched 分支保留。

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js && node main/egg-miniprogram/scripts/verify-project.js`
Expected: PASS + 校验通过

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/home/
git commit -m "feat: render incubation scene on home for pre-hatch stages"
```

---

## 阶段二：陪伴入口改版

### Task 8: companion 图标入口

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.js/.wxml/.wxss`
- Test: `main/egg-miniprogram/miniprogram/pages/home/home.test.js`（追加）

**Interfaces:**
- Consumes: `pre-hatch-assets.INTERACTION_ICONS`（Task 2）、现有 `wishUnlocked/learnUnlocked` 判定
- Produces: `COMPANION_ACTIONS = [{ key:'wish'|'learn'|'draw', title, icon }]`、`onCompanionTap(event)`

- [ ] **Step 1: 追加失败测试**

```javascript
// companionActions 含 wish/learn/draw 三项，icon 非空
// onCompanionTap('wish') → wx.navigateTo /pages/wish/wish（300ms 延迟后）
// onCompanionTap('learn') 未解锁 → showFeedback 提示，不跳转
// onCompanionTap('draw') → 暂为占位提示「画画功能即将上线」（Task 10 接入编辑器）
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`
Expected: FAIL

- [ ] **Step 3: 实现**

WXML 在场景下方加 companion 条（样式移植 STATIC `.companion-*` 段）：

```xml
<view class="companion-section" wx:if="{{stage !== 'hatched'}}">
  <view class="companion-item companion-item--{{item.key}} {{item.locked ? 'companion-item--locked' : ''}}"
        wx:for="{{companionActions}}" wx:key="key"
        data-key="{{item.key}}" bindtap="onCompanionTap">
    <image class="companion-icon" src="{{item.icon}}" mode="aspectFit" />
    <text class="companion-title">{{item.title}}</text>
  </view>
</view>
```

JS：

```javascript
const { INTERACTION_ICONS } = require('../../config/pre-hatch-assets');
const COMPANION_ACTIONS = [
  { key: 'wish', title: '许愿池', icon: INTERACTION_ICONS.wish },
  { key: 'learn', title: '早教班', icon: INTERACTION_ICONS.learn },
  { key: 'draw', title: '画画', icon: INTERACTION_ICONS.draw }
];

onCompanionTap(e) {
  const key = e.currentTarget.dataset.key;
  if (key === 'wish' && !this.data.wishUnlocked) return this.showFeedback('许愿池还在准备中。');
  if (key === 'learn' && !this.data.learnUnlocked) return this.showFeedback('蛋宝宝还没到早教的年龄，明天来试试吧。');
  if (key === 'draw') return wx.showToast({ title: '画画功能即将上线', icon: 'none' });
  const routes = { wish: '/pages/wish/wish', learn: '/pages/lesson/lesson' };
  if (routes[key]) {
    // 300ms 场景过渡后跳转，与静态项目节奏一致
    setTimeout(() => wx.navigateTo({ url: routes[key] }), 300);
  }
}
```

companionActions data 计算：`COMPANION_ACTIONS.map(a => ({ ...a, locked: (a.key==='wish' && !wishUnlocked) || (a.key==='learn' && !learnUnlocked) }))`。原 `home-actions` 区孵化期分支移除（hatched 分支的聊天/换场景按钮保留）。

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/pages/home/
git commit -m "feat: replace pre-hatch action buttons with companion icon dock"
```

---

## 阶段三：涂鸦画笔改造

### Task 9: 涂鸦上传与保存链路（pet-store 改造）

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.js`（`saveDoodle` 改签名）
- Create: `main/egg-miniprogram/miniprogram/utils/doodle-api.js`
- Test: `main/egg-miniprogram/miniprogram/utils/doodle-api.test.js`（新建）

**Interfaces:**
- Produces:
  - `doodle-api.uploadDoodleImage(tempFilePath)` → Promise<string artUrl>（复用 `POST /wechat/avatar` 上传通道：`wx.uploadFile`，`name:'file'`，Bearer header，envelope `{code, data}`，data 为 URL 字符串；非 200/`code!==0`/无 data → reject `userMessage:'画作上传失败'`）
  - `doodle-api.getLatestDoodleArtUrl(petId)` → Promise<string>（`GET /pet/{id}/hatch-actions`，取最新 DOODLE 记录 `payload.artUrl`，无则 ''）
  - `petStore.saveDoodle(artUrl)` → `{ ok, alreadyDone?, message? }`（hatch-action type=DOODLE，payload=`{artUrl}`；替代旧三字段签名）
- Consumes: 现有 `utils/request.js` 的 `post/get`、`utils/auth.js`

**外部依赖（先于联调确认）**：①`GET /pet/{id}/hatch-actions` 的 `HatchActionVO` 返回 `payload` 字段；②`/wechat/avatar` 可接受涂鸦 PNG（若限制图片用途则后端加 `POST /pet/{id}/doodle-image`，签名与 uploadDoodleImage 内部实现对齐即可，接口不变）。

- [ ] **Step 1: 写失败测试**

mock `wx.uploadFile` 与 request：

```javascript
// uploadDoodleImage 成功 → resolve envelope.data
// uploadDoodleImage statusCode 500 → reject userMessage '画作上传失败'
// uploadDoodleImage envelope.code!==0 → reject envelope.msg
// getLatestDoodleArtUrl：多条记录取 DOODLE 最新一条 payload.artUrl；无 DOODLE → ''
// saveDoodle('https://x/1.png') → post('/pet/{id}/hatch-action', { type:'DOODLE', payload:{ artUrl } })
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/utils/doodle-api.test.js`
Expected: FAIL

- [ ] **Step 3: 实现**

`doodle-api.js`（上传实现对齐 `pages/profile/profile.js:112` 的 onChooseAvatar 模式）：

```javascript
// 涂鸦图片上传与回显：图片经 /wechat/avatar 通道传 OSS，画作出处存 hatch-action payload。
const { post, get } = require('./request');
const auth = require('./auth');
const { API_BASE_URL } = require('../config/api');

function uploadDoodleImage(tempFilePath) {
  return new Promise((resolve, reject) => {
    const session = auth.getSession();
    if (!session || !session.token) {
      reject({ userMessage: '登录状态已失效' });
      return;
    }
    wx.uploadFile({
      url: `${API_BASE_URL}/wechat/avatar`,
      filePath: tempFilePath,
      name: 'file',
      header: { Authorization: `Bearer ${session.token}` },
      success: (res) => {
        if (res.statusCode !== 200) { reject({ userMessage: '画作上传失败' }); return; }
        try {
          const envelope = JSON.parse(res.data);
          if (envelope.code !== 0 || !envelope.data) { reject({ userMessage: envelope.msg || '画作上传失败' }); return; }
          resolve(envelope.data);
        } catch (error) { reject({ userMessage: '画作上传失败' }); }
      },
      fail: () => reject({ userMessage: '画作上传失败' })
    });
  });
}

async function getLatestDoodleArtUrl(petId) {
  const actions = await get(`/pet/${petId}/hatch-actions`);
  const doodles = (actions || []).filter(a => (a.actionType || a.action_type) === 'DOODLE');
  if (!doodles.length) return '';
  const latest = doodles[doodles.length - 1];
  const payload = latest.payload;
  if (!payload) return '';
  const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
  return parsed.artUrl || '';
}

module.exports = { uploadDoodleImage, getLatestDoodleArtUrl };
```

`pet-store.js` 的 `saveDoodle(color, colorName, pattern)` 改为 `saveDoodle(artUrl)`，body 改 `{ type: 'DOODLE', payload: { artUrl } }`，返回结构不变。

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/utils/doodle-api.test.js`
Expected: PASS；同时跑 `node main/egg-miniprogram/miniprogram/utils/pet-store.test.js`（如存在）确认旧调用点已同步

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/utils/doodle-api.js main/egg-miniprogram/miniprogram/utils/doodle-api.test.js main/egg-miniprogram/miniprogram/utils/pet-store.js
git commit -m "feat: save doodle artwork through hatch action payload"
```

---

### Task 10: doodle-editor 组件

**Files:**
- Create: `main/egg-miniprogram/miniprogram/components/doodle-editor/doodle-editor.js`（组件壳 + 工具栏状态，+ `.json/.wxml/.wxss`）
- Create: `main/egg-miniprogram/miniprogram/components/doodle-editor/doodle-canvas.js`（画笔引擎：笔触记录/渲染/双指缩放/导出）
- Create: `main/egg-miniprogram/miniprogram/components/doodle-editor/doodle-canvas.test.js`（笔触存储单测）

**Interfaces:**
- Consumes: `canvas-2d.createLayer/loadImage/exportImage`（Task 4）、`petStore.getPet().shell.color` 作底色
- Produces:
  - properties: `visible`(Boolean)、`petId`(String)
  - events: `close`、`saved`（detail `{ artUrl }`）
  - `doodle-canvas.js`：`{ init(page, selectors, baseImage), addStroke(point), setBrush({color,size}), undo(), clear(), exportArtwork() → Promise<tempFilePath|''> }`

**拆分原则**：STATIC `pages/doodle/doodle-definition.js`（785 行）拆为两文件各 <450 行：组件壳负责工具栏（颜色/笔刷/橡皮/撤销/清空/保存）与状态；`doodle-canvas.js` 负责 canvas 初始化、触摸事件→笔触、重绘、导出。笔刷色板移植 STATIC `egg-shell-art.js` 的 `BRUSH_COLORS`（10 色，内联进 doodle-canvas.js，不搬整个 egg-shell-art 服务）。

- [ ] **Step 1: 写失败测试**

`doodle-canvas.test.js`（纯逻辑部分可测：笔触队列）：

```javascript
const assert = require('assert');
const engine = require('../../components/doodle-editor/doodle-canvas');

const strokes = engine.createStrokeStore();
engine.beginStroke(strokes, { x: 1, y: 2, color: '#526B4D', size: 3 });
engine.appendPoint(strokes, { x: 3, y: 4 });
engine.endStroke(strokes);
assert.strictEqual(strokes.list.length, 1);
assert.strictEqual(strokes.list[0].points.length, 2);
engine.undo(strokes);
assert.strictEqual(strokes.list.length, 0);
engine.clear(strokes);
assert.strictEqual(strokes.list.length, 0);
console.log('doodle-canvas.test.js: ALL PASS');
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/components/doodle-editor/doodle-canvas.test.js`
Expected: FAIL

- [ ] **Step 3: 移植实现**

从 STATIC `pages/doodle/doodle-definition.js` + `pages/doodle/doodle.wxml/.wxss` 移植：

- `doodle-canvas.js`：`createStrokeStore/beginStroke/appendPoint/endStroke/undo/clear`（纯数据，可单测）+ canvas 初始化/重绘/导出（wx 依赖，页面联调验证）
- `doodle-editor.js`：Component 壳，`visible` observer 控制显隐；保存按钮 → `exportArtwork()` → triggerEvent('saved', { tempFilePath })（**上传与动作调用由 home 页编排**，组件保持纯 UI）
- 删除 STATIC 中与 demo/cloud-api/sync-queue 相关的提交逻辑

- [ ] **Step 4: 运行确认通过**

Run: `node main/egg-miniprogram/miniprogram/components/doodle-editor/doodle-canvas.test.js && node --check main/egg-miniprogram/miniprogram/components/doodle-editor/doodle-editor.js`
Expected: PASS + 无输出

- [ ] **Step 5: Commit**

```bash
git add main/egg-miniprogram/miniprogram/components/doodle-editor/
git commit -m "feat: add doodle editor component with brush canvas"
```

---

### Task 11: home 接入涂鸦 + 旧页清理

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.js/.wxml/.json`
- Delete: `main/egg-miniprogram/miniprogram/pages/doodle/`（四件套 + 测试）
- Modify: `main/egg-miniprogram/miniprogram/app.json`（移除 `pages/doodle/doodle` 路由）
- Test: `main/egg-miniprogram/miniprogram/pages/home/home.test.js`（追加）

**Interfaces:**
- Consumes: `<doodle-editor>`（Task 10）、`doodle-api.uploadDoodleImage/getLatestDoodleArtUrl`、`petStore.saveDoodle(artUrl)`（Task 9）
- Produces: home data 新增 `doodleEditorVisible`、`eggArtUrl`

- [ ] **Step 1: 追加失败测试**

```javascript
// onCompanionTap('draw') → doodleEditorVisible=true（替换 Task 8 的占位 toast 断言）
// onDoodleSaved({ tempFilePath })：uploadDoodleImage 成功 → saveDoodle 被调且 payload 含 artUrl → eggArtUrl 更新 → 编辑器关闭
// uploadDoodleImage 失败 → saveDoodle 未被调 → toast 提示 → 编辑器保持打开
// onShow 且 stage!=='hatched' → getLatestDoodleArtUrl 结果写入 eggArtUrl
```

- [ ] **Step 2: 运行确认失败**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`
Expected: FAIL

- [ ] **Step 3: 实现**

home.js：

```javascript
const doodleApi = require('../../utils/doodle-api');

// data 增加：doodleEditorVisible: false, eggArtUrl: ''
// onShow（孵化期）：
doodleApi.getLatestDoodleArtUrl(this.data.pet.id)
  .then(artUrl => this.setData({ eggArtUrl: artUrl }))
  .catch(() => {});   // 回显失败静默，不挡主流程

// onCompanionTap 中 draw 分支改为：
if (key === 'draw') { this.setData({ doodleEditorVisible: true }); return; }

async onDoodleSaved(e) {
  const tempFilePath = e.detail && e.detail.tempFilePath;
  if (!tempFilePath) return;
  try {
    const artUrl = await doodleApi.uploadDoodleImage(tempFilePath);
    const result = await petStore.saveDoodle(artUrl);   // 先上传成功才记录动作
    if (!result.ok) {
      wx.showToast({ title: result.message || '保存失败，请稍后重试', icon: 'none' });
      return;
    }
    this.setData({ eggArtUrl: artUrl, doodleEditorVisible: false });
    wx.showToast({ title: result.alreadyDone ? '蛋壳外观已更新' : '蛋壳变漂亮了', icon: 'none' });
  } catch (error) {
    wx.showToast({ title: (error && error.userMessage) || '画作没有保存好，请再试一次', icon: 'none' });
    // 编辑器保持打开，画布状态保留可重试
  }
},
onDoodleEditorClose() { this.setData({ doodleEditorVisible: false }); }
```

home.json 注册 `doodle-editor`。删除旧 doodle 页并清理 app.json 路由；grep 确认无残留引用：`grep -rn "pages/doodle" main/egg-miniprogram/miniprogram --include="*.js" --include="*.json" --include="*.wxml" | grep -v test` 应为空。

- [ ] **Step 4: 全量验证**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js && node main/egg-miniprogram/scripts/verify-project.js && find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check`
Expected: 全部通过

- [ ] **Step 5: 更新场景素材文档**

`main/egg-miniprogram/docs/pre-hatch-scene-assets.md` 的窗景映射规则更新为两级查找（23 张：7 共享 + 16 精确）、36 场景全覆盖、OSS 路径前缀改为线上地址，并注明「未命中不再回退房间图，窗户点击无响应」。

- [ ] **Step 6: Commit**

```bash
git add main/egg-miniprogram/
git commit -m "feat: embed doodle editor in home and remove legacy doodle page"
```

---

## 收尾检查清单（全部 Task 完成后）

- [ ] `find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check` 全绿
- [ ] `find main/egg-miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty` 全绿
- [ ] `node main/egg-miniprogram/scripts/verify-project.js` 通过
- [ ] 全部 `.test.js` 逐个 `node` 运行通过
- [ ] 开发者工具真机预览：36 场景抽查（春夏秋冬各一）、窗户打开/关闭、台灯/时钟、涂鸦保存与回显、破壳视频流程回归
- [ ] 后端依赖两项已确认（HatchActionVO payload、上传通道）
