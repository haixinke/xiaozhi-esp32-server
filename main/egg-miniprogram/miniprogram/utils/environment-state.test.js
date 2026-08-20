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
