// 破壳前环境解析：场景 key -> OSS 图片 URL 与样式类名。
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
