# 破壳前场景素材对应关系

> egg-miniprogram 破壳前场景素材托管于阿里云 OSS，本地仅保留蛋壳 overlay 与互动入口图标。
> 配置见 `miniprogram/config/pre-hatch-assets.js`，运行时解析见 `miniprogram/utils/incubation-environment.js`。

## 命名与路径规则

场景 key = `{季节}_{天气}_{时段}`，共 36 个场景（春 9 / 夏 9 / 秋 6 / 冬 12）。每个 key 派生三张场景内素材（整幅背景 / 窝垫 / 蛋）；窗景按两级查找取 23 张之一。

| 素材 | OSS 前缀 | 文件名规则 |
|---|---|---|
| 整幅房间背景 | `https://oss.eggbabe.com/scenes/pre-hatch/incubation-room/season-weather-full-scenes/` | `{key}.webp` |
| 窝垫 | `https://oss.eggbabe.com/scenes/pre-hatch/nest/season-weather/` | `{key}_nest_pad.webp` |
| 蛋 | `https://oss.eggbabe.com/scenes/pre-hatch/egg/season-weather/` | `{key}_egg_right45.webp` |
| 窗景 | `https://oss.eggbabe.com/scenes/pre-hatch/window/window-weather/` | 共享 `w_01`~`w_07.webp` + 精确 `window_{sceneKey}_v01.webp` |

蛋壳立体感/高光 overlay（`egg_shell_depth_overlay_512_v01.webp`、`egg_shell_specular_overlay_512_v01.webp`）所有场景共用，本地 `assets/scenes/egg/`。

## 窗景两级查找（`utils/incubation-environment.js` 的 `windowAssetPath`）

1. **精确匹配**：`WINDOW_WEATHER.bySceneKey[sceneKey]` 命中 → 用精确图（16 张，覆盖春多云日落/春雨 3、夏多云日落/夏雷雨 3、秋雨 3、冬多云日落/冬降雪日落/冬雪后 3）
2. **共享兜底**：按 weather × period 取 7 张共享图之一
   - 晴朗：日间 `w_01_clear_day` / 日落 `w_02_clear_sunset` / 夜晚 `w_03_clear_night`
   - 多云：日间 `w_04_cloudy_day` / 夜晚 `w_05_cloudy_night`
   - 降雪：日间 `w_06_snow_day` / 夜晚 `w_07_snow_night`
3. **未命中**：返回空串 → **窗户点击无响应**（不打开窗外组件、不显示兜底文案）；禁止跨天气、跨季节或用整幅房间图回退

当前 36 场景全部命中（16 精确 + 20 共享），无空串；保留空串分支作为未来新增场景未配图的防御。

## 场景对照表（36 个）

路径缩写：`BG`=背景前缀、`NEST`=窝垫前缀、`EGG`=蛋前缀、`WIN`=窗景前缀（均省略 OSS 根）。

### 春季（spring）

| 场景 key | 中文 | 窗景查找结果 |
|---|---|---|
| `spring_clear_day` | 春季 · 晴朗·日间 | `w_01_clear_day`（共享） |
| `spring_clear_sunset` | 春季 · 晴朗·日落 | `w_02_clear_sunset`（共享） |
| `spring_clear_night` | 春季 · 晴朗·夜晚 | `w_03_clear_night`（共享） |
| `spring_cloudy_day` | 春季 · 多云·日间 | `w_04_cloudy_day`（共享） |
| `spring_cloudy_sunset` | 春季 · 多云·日落 | `window_spring_cloudy_sunset_v01`（精确） |
| `spring_cloudy_night` | 春季 · 多云·夜晚 | `w_05_cloudy_night`（共享） |
| `spring_rain_day` | 春季 · 下雨·日间 | `window_spring_rain_day_v01`（精确） |
| `spring_rain_sunset` | 春季 · 下雨·日落 | `window_spring_rain_sunset_v01`（精确） |
| `spring_rain_night` | 春季 · 下雨·夜晚 | `window_spring_rain_night_v01`（精确） |

### 夏季（summer）

| 场景 key | 中文 | 窗景查找结果 |
|---|---|---|
| `summer_clear_day` | 夏季 · 晴朗·日间 | `w_01_clear_day`（共享） |
| `summer_clear_sunset` | 夏季 · 晴朗·日落 | `w_02_clear_sunset`（共享） |
| `summer_clear_night` | 夏季 · 晴朗·夜晚 | `w_03_clear_night`（共享） |
| `summer_cloudy_day` | 夏季 · 多云·日间 | `w_04_cloudy_day`（共享） |
| `summer_cloudy_sunset` | 夏季 · 多云·日落 | `window_summer_cloudy_sunset_v01`（精确） |
| `summer_cloudy_night` | 夏季 · 多云·夜晚 | `w_05_cloudy_night`（共享） |
| `summer_storm_day` | 夏季 · 雷雨·日间 | `window_summer_storm_day_v01`（精确） |
| `summer_storm_sunset` | 夏季 · 雷雨·日落 | `window_summer_storm_sunset_v01`（精确） |
| `summer_storm_night` | 夏季 · 雷雨·夜晚 | `window_summer_storm_night_v01`（精确） |

### 秋季（autumn）

| 场景 key | 中文 | 窗景查找结果 |
|---|---|---|
| `autumn_clear_day` | 秋季 · 晴朗·日间 | `w_01_clear_day`（共享） |
| `autumn_clear_sunset` | 秋季 · 晴朗·日落 | `w_02_clear_sunset`（共享） |
| `autumn_clear_night` | 秋季 · 晴朗·夜晚 | `w_03_clear_night`（共享） |
| `autumn_rain_day` | 秋季 · 下雨·日间 | `window_autumn_rain_day_v01`（精确） |
| `autumn_rain_sunset` | 秋季 · 下雨·日落 | `window_autumn_rain_sunset_v01`（精确） |
| `autumn_rain_night` | 秋季 · 下雨·夜晚 | `window_autumn_rain_night_v01`（精确） |

### 冬季（winter）

| 场景 key | 中文 | 窗景查找结果 |
|---|---|---|
| `winter_clear_day` | 冬季 · 晴朗·日间 | `w_01_clear_day`（共享） |
| `winter_clear_sunset` | 冬季 · 晴朗·日落 | `w_02_clear_sunset`（共享） |
| `winter_clear_night` | 冬季 · 晴朗·夜晚 | `w_03_clear_night`（共享） |
| `winter_cloudy_day` | 冬季 · 多云·日间 | `w_04_cloudy_day`（共享） |
| `winter_cloudy_sunset` | 冬季 · 多云·日落 | `window_winter_cloudy_sunset_v01`（精确） |
| `winter_cloudy_night` | 冬季 · 多云·夜晚 | `w_05_cloudy_night`（共享） |
| `winter_snow_day` | 冬季 · 降雪·日间 | `w_06_snow_day`（共享） |
| `winter_snow_sunset` | 冬季 · 降雪·日落 | `window_winter_snow_sunset_v01`（精确） |
| `winter_snow_night` | 冬季 · 降雪·夜晚 | `w_07_snow_night`（共享） |
| `winter_post_snow_day` | 冬季 · 雪后·日间 | `window_winter_post_snow_day_v01`（精确） |
| `winter_post_snow_sunset` | 冬季 · 雪后·日落 | `window_winter_post_snow_sunset_v01`（精确） |
| `winter_post_snow_night` | 冬季 · 雪后·夜晚 | `window_winter_post_snow_night_v01`（精确） |

## 备注

- 背景与窝垫/蛋的 OSS URL 由 `pre-hatch-assets.js` 的 `SCENE_OPTIONS` 逐场景拼装；窗景由 `WINDOW_WEATHER`（共享 7 张 + `bySceneKey` 16 张）配出。
- 场景图层叠加顺序（z-index 由底到顶）：旧背景(1) → 整幅背景(2) → 窗口特效(4) → 窝垫(5) → 天气 canvas(6) → 蛋(7) → 台灯(9) → 时钟(18)。
