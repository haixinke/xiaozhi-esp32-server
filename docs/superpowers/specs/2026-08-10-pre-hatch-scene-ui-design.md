# 蛋宝宝小程序破壳前场景 UI 改造设计

日期：2026-08-10
分支：f-mini-before
状态：已通过分段评审

## 1. 背景与目标

egg-miniprogram（现网蛋宝宝小程序）破壳前 home 页是纯 CSS 绘制的蛋形，参考 eggbabe-miniprogram（静态 UI 项目）改造为 OSS 场景图叠加的「孵蛋房」：整幅房间背景 + 窝垫 + 蛋图 + 窗口特效 + 天气粒子 + 窗外景色组件。

业务逻辑全部保留：许愿池、早教班页面内容与逻辑不变，仅 UI 入口改为场景内图标；涂鸦从选色表单改为画笔交互；破壳流程（视频遮罩 → 收藏卡页）保留现状。

## 2. 素材与 OSS 路径

场景共 **36 个**（春 9 / 夏 9 / 秋 6 / 冬 12，静态项目背景目录中的 `spring_clear_night_v2`、`spring_clear_night_moonlight` 为备选调试图，不进清单）。

场景 key = `{季节}_{天气}_{时段}`，派生三类图，已全量上传 OSS（110 张 md5 与静态项目本地一致）：

| 素材 | OSS 前缀 | 文件名规则 |
|---|---|---|
| 整幅房间背景 | `https://oss.eggbabe.com/scenes/pre-hatch/incubation-room/season-weather-full-scenes/` | `{key}.webp` |
| 窝垫 | `https://oss.eggbabe.com/scenes/pre-hatch/nest/season-weather/` | `{key}_nest_pad.webp` |
| 蛋 | `https://oss.eggbabe.com/scenes/pre-hatch/egg/season-weather/` | `{key}_egg_right45.webp` |
| 窗景（23 张） | `https://oss.eggbabe.com/scenes/pre-hatch/window/window-weather/` | 共享 7 张 `w_01`~`w_07` + 精确 16 张 `window_{sceneKey}_v01.webp` |

窗景两级查找（移植静态项目最新逻辑 `windowAssetPath(sceneKey, weather, period)`，不含旧两参数兼容分支）：

1. `bySceneKey[sceneKey]` 精确命中 → 用精确图（16 张，覆盖春多云日落/春雨 3/夏多云日落/夏雷雨 3/秋雨 3/冬多云日落/冬降雪日落/冬雪后 3）
2. 否则按 weather × period 走共享图：晴朗 3 张（日间/日落/夜晚）、多云 2 张（日间/夜晚）、降雪 2 张（日间/夜晚）
3. 仍未命中 → 返回空串。**当前 36 场景全覆盖无空串；保留空串分支作为未来新增场景未配图的防御**

**禁止跨天气、跨季节或用整幅房间图回退窗景**（静态项目已移除旧的日落回退逻辑，本次不同步该旧逻辑）。

本地保留素材（不进 OSS）：蛋壳 depth/specular overlay（2 张，全场景共用）、3D 互动入口图标（许愿池/绘本/调色盘等）、涂鸦编辑器 UI 素材。

## 3. 环境规则（照搬静态项目）

不读定位、不调天气 API，纯本地可复现计算：

- **季节**：领养日起每天一季，春夏秋冬循环。`season = SEASONS[(陪伴日 - 1) % 4]`，陪伴日 = 本地日期 - `hatchStartTime` 本地日期 + 1
- **时段**：本机时间 → `day`(6:00-17:00) / `sunset`(17:00-19:00) / `night`(其余)；lightPhase = midday/sunset/night
- **天气**：按蛋 id + 日期哈希从季节天气池稳定抽取（同一只蛋同一天任何时候结果相同）：春 [晴朗/多云/雨]、夏 [晴朗/多云/雷雨]、秋 [晴朗/雨]、冬 [晴朗/多云/降雪/雪后]

## 4. 架构与模块划分

```
miniprogram/
├── config/
│   └── pre-hatch-assets.js        # 新增：36 场景清单 + OSS 路径前缀 + 本地图标路径
├── utils/
│   ├── environment-state.js       # 新增：纯函数，{petId, hatchStartTime, timestamp} → {season, weather, period, lightPhase, sceneKey, dateKey}
│   ├── incubation-environment.js  # 新增：environment → {fullSceneImage, nestImage, eggImage, windowImage, className}
│   └── canvas-2d.js               # 新增：移植静态项目的 canvas 工具（场景蛋层/涂鸦共用）
├── components/
│   ├── incubation-scene/          # 新增：场景渲染组件（四件套）
│   ├── daily-window-detail/       # 新增：窗外全屏组件（四件套）
│   └── doodle-editor/             # 新增：涂鸦画笔组件（第 3 步）
└── pages/home/                    # 改造：孵化期渲染场景组件，页内只留业务逻辑
```

职责边界：

- `environment-state.js`：纯函数，timestamp 依赖注入，单测不需 mock 时钟库
- `incubation-environment.js`：路径组装，输入 environment + OSS base
- `<incubation-scene>`：纯展示。properties：`environment`、`eggArtUrl`、`stage`；events：`eggtap`、`eggcuddle`、`lamptap`、`clocktap`、`windowtap`。不碰业务 API
- `<daily-window-detail>`：纯展示。properties：`visible`、`image`、`weather`、`season`、`period`、`lightPhase`、`originStyle`；events：`close`、`retry`
- `pages/home`：pet 状态机、hatch-action 调用、破壳视频、陪伴入口导航
- OSS base 收敛为 `config/api.js` 旁新增 `OSS_SCENE_BASE` 常量，不引入 globalData

### 场景组件图层（z-index 由底到顶）

旧背景(1) → 整幅背景(2) → 窗口 CSS 光效(4) → 窝垫(5) → 天气 canvas(6) → 蛋(7：base→art→depth overlay→specular overlay) → 台灯热区(9) → 时钟(18)

- 场景切换：新图 load 完成后 z2 淡入，旧图 z1 垫底，0.6s 交叉淡化；加载失败保持旧场景
- 台灯：纯前端开关，暖色光晕层
- 时钟：指针/数字两态点击切换，1s  tick
- 天气 canvas：按 weather 绘制雨/雪粒子，页面 hide 时停止
- 环境刷新：home onShow 重算 + 每分钟定时器（season/weather 一天内不变，仅 period 跨时段切换）

### 窗外组件

点击窗户热区（天气 canvas 的 catchtap）→ 量出 `.window-effects` 矩形 → 写 `--daily-window-origin-*` CSS 变量 → `<daily-window-detail>` 从窗口矩形 320ms 淡入全屏 → 返回淡出卸载。组件内叠时段色调层、CSS 光效/飞鸟、全屏天气 canvas。

## 5. 三步交付

### 第 1 步：home 场景图层 + 窗外组件

- 新增 `config/pre-hatch-assets.js`、`utils/environment-state.js`、`utils/incubation-environment.js`、`utils/canvas-2d.js`
- 新增 `<incubation-scene>`、`<daily-window-detail>` 组件
- home 页：孵化期（waiting/hatching/soon/ready）渲染场景组件；hatched 保留现有分支；摸蛋/长按 cuddle 事件照旧调 CUDDLE 动作；ready 保留破壳按钮 + 视频遮罩
- 移除旧 CSS 蛋样式（egg-pattern/egg-shine 等孵化期分支样式）

### 第 2 步：陪伴入口改版

- home 底部 action 区替换为 companion 图标条：许愿池/早教班/画画，图标从静态项目拷贝到本地 `assets/ui/3d-actions/`
- 许愿池/早教班 → `wx.navigateTo` 现有 wish/lesson 页（页面零改动），带场景过渡动效（scene effect class + 300ms 延迟跳转）
- 解锁判定保留 egg-miniprogram 现有逻辑（wishUnlocked/learnUnlocked），不搬静态项目判定

### 第 3 步：涂鸦画笔改造

- 移植静态项目 doodle-definition（783 行）拆分为 `doodle-editor.js`（组件壳）+ `doodle-canvas.js`（画笔引擎），单文件不超 800 行
- `<doodle-editor>` 内嵌 home：画笔/颜色/笔刷/双指缩放
- 保存：canvas 导出 PNG → `wx.uploadFile` 上传 OSS → 拿 artUrl → `pet-store.saveDoodle` 改为调 hatch-action（type=DOODLE，payload=`{artUrl}`）
- 回显：home onShow 拉 `GET /pet/{id}/hatch-actions`，取最新 DOODLE 记录 payload.artUrl 传给场景组件 art 层
- 删除旧 doodle 页四件套 + app.json 路由

## 6. 错误处理

| 场景 | 处理 |
|---|---|
| 整幅背景图加载失败 | 组件内「房间没有加载完整」+ 重新加载 + 返回欢迎页；不用其他场景图顶替 |
| 窝垫/蛋图加载失败 | 静默降级，该层不渲染，背景保留 |
| 窗景图加载失败 | 「窗外景色没有加载好」+ 重新看看 |
| 窗景未命中（空串） | 窗户点击无响应，不打开组件，无兜底文案 |
| 涂鸦导出失败 | toast「画作没有保存好，请再试一次」，画布状态保留 |
| 涂鸦上传失败 | toast 同上；不调 hatch-action（避免减了时长但画丢失） |
| hatch-action 失败 | 沿用 request.js 的 error.userMessage 提示 |
| 时段切换中新图加载中 | 旧图保持，load 完成才交叉淡化 |

## 7. 测试策略

沿用项目现有 node + assert 风格（mock Page/Component/wx）：

- `environment-state.test.js`（新增）：季节日轮换边界（领养当天=春、第 5 天回春）、时段临界点（6:00/17:00/19:00）、同蛋同天天气可复现、不同蛋天气有分布
- `incubation-environment.test.js`（新增）：36 场景 key → 三类 OSS URL 拼装；窗景两级查找（精确/共享兜底/未命中空串）；非晴朗日落不回退房间图
- `incubation-scene` 组件测试（新增）：图层渲染、交叉淡化状态、eggtap/eggcuddle 事件、背景失败错误态
- `home.test.js`（改造）：孵化期渲染场景组件、hatched 走旧分支、摸蛋触发 CUDDLE、ready 显示破壳入口
- `doodle` 测试（改造）：保存顺序=导出→上传→hatch-action；上传失败不调动作；回显取最新 DOODLE artUrl
- 全部通过后 `verify-project.js` + 全量 `node --check` + 真机预览

## 8. 风险与外部依赖

| 项 | 说明 | 状态 |
|---|---|---|
| `HatchActionVO` 返回 payload | 涂鸦回显需要；若未返回则后端加映射 | 待确认 |
| 涂鸦图上传接口 | 优先复用 `/wechat/avatar` OSS 上传通道；不能复用则后端新增 `POST /pet/{id}/doodle-image` | 待确认 |
| 包体 | 场景图全走 OSS；本地增量仅互动图标 + 蛋 overlay，约几百 KB | 可控 |
| 性能 | 整幅背景 300-450KB/张，切换时预加载；canvas 粒子页面 hide 时停止 | 已实现于静态项目，移植 |
| 文档同步 | `docs/pre-hatch-scene-assets.md` 窗景映射规则已过时（7 张 → 23 张两级查找） | 实现时更新 |

## 9. 明确不做

- 破壳流程改造（保留现有视频遮罩 → 收藏卡页）
- 破壳后（hatched）UI 改造
- 许愿池/早教班页面内容与逻辑改动
- 静态项目的 demo 模式、runtime-context、cloud-api、analytics 体系
- 窗外「远方」魔法入口（静态项目 magic-entry）
