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
