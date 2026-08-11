// 破壳前场景素材集中配置。整幅背景/窝垫/蛋/窗景均托管 OSS，本地只保留蛋壳 overlay 与入口图标。
const { OSS_SCENE_BASE } = require('./api');
const FULL_SCENE_ROOT = `${OSS_SCENE_BASE}/incubation-room/season-weather-full-scenes`;
const NEST_SCENE_ROOT = `${OSS_SCENE_BASE}/nest/season-weather`;
const EGG_SCENE_ROOT = `${OSS_SCENE_BASE}/egg/season-weather`;
const WINDOW_SCENE_ROOT = `${OSS_SCENE_BASE}/window/window-weather`;

const SCENE_TABLE = [
  ['spring_clear_day', 'spring', 'sunny', 'day', 'midday'],
  ['spring_clear_sunset', 'spring', 'sunny', 'sunset', 'sunset'],
  ['spring_clear_night', 'spring', 'sunny', 'night', 'night'],
  ['spring_cloudy_day', 'spring', 'cloudy', 'day', 'midday'],
  ['spring_cloudy_sunset', 'spring', 'cloudy', 'sunset', 'sunset'],
  ['spring_cloudy_night', 'spring', 'cloudy', 'night', 'night'],
  ['spring_rain_day', 'spring', 'rain', 'day', 'midday'],
  ['spring_rain_sunset', 'spring', 'rain', 'sunset', 'sunset'],
  ['spring_rain_night', 'spring', 'rain', 'night', 'night'],
  ['summer_clear_day', 'summer', 'sunny', 'day', 'midday'],
  ['summer_clear_sunset', 'summer', 'sunny', 'sunset', 'sunset'],
  ['summer_clear_night', 'summer', 'sunny', 'night', 'night'],
  ['summer_cloudy_day', 'summer', 'cloudy', 'day', 'midday'],
  ['summer_cloudy_sunset', 'summer', 'cloudy', 'sunset', 'sunset'],
  ['summer_cloudy_night', 'summer', 'cloudy', 'night', 'night'],
  ['summer_storm_day', 'summer', 'storm', 'day', 'midday'],
  ['summer_storm_sunset', 'summer', 'storm', 'sunset', 'sunset'],
  ['summer_storm_night', 'summer', 'storm', 'night', 'night'],
  ['autumn_clear_day', 'autumn', 'sunny', 'day', 'midday'],
  ['autumn_clear_sunset', 'autumn', 'sunny', 'sunset', 'sunset'],
  ['autumn_clear_night', 'autumn', 'sunny', 'night', 'night'],
  ['autumn_rain_day', 'autumn', 'rain', 'day', 'midday'],
  ['autumn_rain_sunset', 'autumn', 'rain', 'sunset', 'sunset'],
  ['autumn_rain_night', 'autumn', 'rain', 'night', 'night'],
  ['winter_clear_day', 'winter', 'sunny', 'day', 'midday'],
  ['winter_clear_sunset', 'winter', 'sunny', 'sunset', 'sunset'],
  ['winter_clear_night', 'winter', 'sunny', 'night', 'night'],
  ['winter_cloudy_day', 'winter', 'cloudy', 'day', 'midday'],
  ['winter_cloudy_sunset', 'winter', 'cloudy', 'sunset', 'sunset'],
  ['winter_cloudy_night', 'winter', 'cloudy', 'night', 'night'],
  ['winter_snow_day', 'winter', 'snow', 'day', 'midday'],
  ['winter_snow_sunset', 'winter', 'snow', 'sunset', 'sunset'],
  ['winter_snow_night', 'winter', 'snow', 'night', 'night'],
  ['winter_post_snow_day', 'winter', 'postSnow', 'day', 'midday'],
  ['winter_post_snow_sunset', 'winter', 'postSnow', 'sunset', 'sunset'],
  ['winter_post_snow_night', 'winter', 'postSnow', 'night', 'night']
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
  // 本地打包资源统一用 PNG：微信上传管线对 .webp 资源可能漏打包导致生产不显示，PNG 稳定
  EGG_DEPTH_OVERLAY: `${EGG_ASSET_ROOT}/egg_shell_depth_overlay_512_v01.png`,
  EGG_SPECULAR_OVERLAY: `${EGG_ASSET_ROOT}/egg_shell_specular_overlay_512_v01.png`,
  INTERACTION_ICONS: {
    wish: '/assets/ui/3d-actions/ui_3d_wishing_fountain_two_tier_simple_256_v04.png',
    learn: '/assets/ui/3d-actions/ui_3d_early_learning_picture_book_simple_256_v03.png',
    draw: '/assets/ui/3d-actions/ui_3d_drawing_palette_256_v02.png'
  }
};
