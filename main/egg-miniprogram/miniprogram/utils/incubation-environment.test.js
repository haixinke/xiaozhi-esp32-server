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

// 未知场景 key -> valid=false 且图片字段为空串
const invalid = env.resolveForPet(pet, new Date(2026, 7, 1, 12, 0, 0).getTime());
assert.strictEqual(invalid.valid, true); // 正常时间必然命中 36 场景之一
// 直接构造不在清单的 sceneKey 验证兜底分支
const missing = env.resolveScene({ season: 'spring', weather: 'sunny', period: 'day', lightPhase: 'midday', dateKey: '2026-08-01', incubationDay: 1, sceneKey: 'spring_clear_dawn' });
assert.strictEqual(missing.valid, false);
assert.strictEqual(missing.fullSceneImage, '');

console.log('incubation-environment.test.js: ALL PASS');
