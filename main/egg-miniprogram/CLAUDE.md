# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

`egg-miniprogram` 是"蛋宝宝"微信小程序：孵化型 AI 宠物小程序，核心流程为 **领取蛋 → 孵化修炼 → 破壳 → 语音对话**。属于 [xiaozhi-esp32-server](../..) 生态，与后端 `manager-api`（Java，端口 8002）和聊天服务 `xiaozhi-server`（Python，WS 端口 8000）协同。

- **技术栈**：微信原生小程序（JS / WXML / WXSS / JSON），无构建工具、无包管理器
- **工程根**：`project.config.json` 中 `miniprogramRoot: "miniprogram/"`，开发者工具导入 `main/egg-miniprogram/`
- **AppID**：`wx661d20e88437af73`
- **设计规范**：[DESIGN.md](./DESIGN.md)

### 与其它子项目的关系

| 交互对象 | 子项目 | 用途 |
|---|---|---|
| 后端管理服务 | `main/manager-api/`（Java Spring Boot，端口 8002，上下文 `/xiaozhi`） | 微信注册登录、宠物创建/孵化/破壳、设备绑定 |
| 聊天服务 | `main/xiaozhi-server/`（Python，WebSocket 端口 8000） | 破壳后语音对话（ASR → LLM → TTS） |

## 目录结构

```
main/egg-miniprogram/
├── project.config.json / project.private.config.json
├── AGENTS.md / DESIGN.md / README.md
├── docs/                        # PRD 与附件
├── scripts/verify-project.js    # 工程完整性校验
└── miniprogram/                 # 小程序源码根
    ├── app.js / app.json / app.wxss
    ├── assets/  components/  sitemap.json
    ├── config/                  # api.js（BASE_URL）、wish-questions.js、age-ranges.js 等
    ├── libs/opus/               # Opus 解码库
    ├── pages/                   # 25 个页面（见 app.json）
    └── utils/                   # 27 个模块，按职责命名（*-api.js 为后端封装）
        ├── request.js           # HTTP 封装（Bearer token，401 静默重登）
        ├── auth.js              # 登录态管理
        ├── pet-store.js         # 宠物状态缓存层（PetVO ↔ 本地 pet 映射）
        ├── ota.js / websocket.js / audio.js   # 语音链路：WS 凭证 → 连接管理 → Opus 播放
        └── *.test.js            # 单元测试（node 直跑）
```

页面清单见 `app.json`（tabBar 为 `home` + `my`）。自定义组件 14 个，见 `components/`（`nav-bar`、`egg-avatar`、`pet-avatar`、`doodle-editor`、`incubation-scene`、`mood-badge` 等）。

## 核心链路路由速查

| 链路 | 入口页面 → 后端/API |
|---|---|
| 微信登录 | `app.js` → `wx.login` → `POST /wechat/login` |
| 手机号绑定 | `pages/home`「添加蛋宝宝」→ `POST /wechat/bindPhone`（领养前置门槛） |
| 领养 | `pages/add-device` → `POST /pet/adopt`（激活码即邀请码） |
| 孵化修炼 | `pages/home` 等 → `POST /pet/{id}/hatch-action` |
| 破壳 | `pet-store.createCollectionCard()` → `POST /pet/{id}/hatch` → 收藏卡页 |
| 每日心情 | `GET /pet/list` 懒生成 `todayMood`，无值时前端本地 fallback |
| 语音对话 | `pages/chat` → `ota.js` 取 WS 凭证 → `websocket.js` → `audio.js` 播放 |
| 邀请码 | `pages/invite-codes` → `GET /invite/mine` |

登录态由 `utils/auth.js` 管理（提前 5 分钟续期，401 静默重登）。

`pet-store.js` 为本地缓存层：`savePetFromVO(vo)` 将后端 `PetVO` 映射为本地 pet 并缓存，支持离线回显。**后端为唯一事实源**。

## 与后端服务 `manager-api` 的交互

`manager-api` 运行在 `http://<host>:8002/xiaozhi`。HTTP 封装在 `utils/request.js`（自动附加 Bearer token，401 静默重登）。`BASE_URL` 在 `config/api.js` 配置，本机联调用 `/mini-ip` skill 切换。

### 微信登录

- `POST /wechat/login`（匿名）：`wx.login` 的 `code` 换取 `token` + `openid` + `userId` + `hasPhone`
- `POST /wechat/bindPhone`（需认证）：`button open-type="getPhoneNumber"` 回调 `code` 绑定手机号
- `GET/PUT /wechat/profile`：查询/更新用户资料；`POST /wechat/avatar`：上传头像到 OSS

**token / openid / wx.login code 严禁落日志、严禁入库**。未绑定手机号可以进入并浏览首页；点击“添加蛋宝宝”后必须完成手机号授权，才能继续进入邀请码/激活码页面领取蛋宝宝。


### 孵化状态机映射

前端 5 态由后端 2 态（`EGG`/`HATCHED`）+ 时间字段派生：

| 前端 stage | 条件 | 后端判定 |
|---|---|---|
| `empty` | 无 pet | `PetVO` 为空 |
| `waiting` | 距破壳 ≥ 24h，无加速 | `EGG` 且 `acceleratedMinutes=0` |
| `hatching` | 距破壳 ≥ 24h，有加速 | `EGG` 且 `acceleratedMinutes>0` |
| `soon` | 距破壳 < 24h | `EGG` |
| `ready` | 已到破壳时间 | `EGG` 且 `now ≥ expectedHatchTime` |
| `hatched` | 已破壳 | `HATCHED` |

### 孵化修炼（5 个加速动作）

减时模型（Model X）：adopt 设基线 `expectedHatchTime=now+7d`，动作累加 `acceleratedMinutes` 下推 `expectedHatchTime`（clamp ≥ 起点），进度满即时间到。前端进度条 = `acceleratedMinutes / 10080`。

| 动作 | type | 减时 | 幂等 | 前端页面 |
|---|---|---|---|---|
| 起昵称 | `NICKNAME` | 12h | 一次性 | `pages/nickname` |
| 摸一摸 | `CUDDLE` | 2h/日 | 每日 | `pages/home` 长按 |
| 许愿池 | `WISH` | 2h/日 | 每日 | `pages/wish` |
| 蛋蛋早教班 | `LESSON` | 2h/日 | 每日 | `pages/lesson` |
| 彩蛋涂鸦 | `DOODLE` | 12h | 一次性 | `pages/home`（doodle-editor 组件） |

"每日一次"由唯一索引 `uk_pet_action_date` 保证。减时分钟数以 manager-api `HatchActionType` 枚举现行值为准（每日动作现为 120 分/2h）。doodle 不做 AI 生图。详见 `docs/egg-pet-identity-and-hatch-api.md` 第 10 节。

### 设备 / 宠物身份模型

当前产品规则：**NFC 渠道可领养多只，同一原型（锦鲤/玉兔）全局限一只；邀请码渠道仍限一只**（邀请码领养是产品前期验证阶段的中间产物，保留兼容、不再作为主流程）。原型占用全局判定、与渠道无关；重复领养（同原型 / 邀请码渠道超限额）由后端预检查拦截，两条领养入口（邀请码、NFC 触碰）共用错误码 `PET_ALREADY_EXISTS`(10206)。`openid` 不能当 device id。聊天身份下沉到宠物级：

```
微信用户(openid) ──1:N（NFC 按原型限一只；邀请码渠道限一只）── 蛋宝宝(ai_pet) ──1:1── 虚拟设备(ai_device) ──1:1── agent(ai_agent)
```

- 领养只建 `ai_pet`（`deviceId=null`）；破壳时才建 `ai_device` + `agent`（懒创建）
- `ai_device.id` = ASSIGN_UUID，`macAddress` = `ai_device.id`，`board` = `"wechat-egg-miniprogram"`
- `pet-store.js` 管理 `activePetId`；`chat.js` 从 `pet.deviceId` 获取 WS 凭证。未破壳 `deviceId=null`，不能进 chat

## 与聊天服务 `xiaozhi-server` 的交互

破壳后 `pages/chat` 通过 **WebSocket 直连 `xiaozhi-server`（端口 8000）**。自有实现：`ota.js`（获取 WS 凭证）→ `websocket.js`（WS 管理器）→ `audio.js`（Opus 解码播放）。

**OTA 流程**：`POST /xiaozhi/ota/`（`board.mac=activeDeviceId`）→ 响应含 `{ websocket: { url, token } }` → `WebSocketManager.connect(wsUrl, deviceId, token)`。

**WS 协议**：token 放 URL query（小程序无法自定义 WS Header）；30s ping / 60s pong 超时；断线指数退避重连（1/2/4/8/15s）。消息 type：`hello`/`stt`/`llm`/`tts`/`audio`(Opus 帧)/`goodbye`/`iot`。聊天状态机：`idle` → `thinking` → `speaking` → `idle`。

## 开发与校验命令

```bash
# 工程完整性校验
node main/egg-miniprogram/scripts/verify-project.js

# 语法检查
find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
find main/egg-miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
```

## 小程序自动化测试

排查或验证**页面级交互问题**（WXML 事件绑定、手势、页面跳转、组件联动）时，不要只靠读代码推测——用下面两个官方工具搭可重复运行的反馈回路。选择标准：纯 JS 逻辑用单测；组件渲染/事件用 simulate；整页交互、只有真引擎才暴露的问题用 automator。

### miniprogram-automator（整页 E2E，驱动真实开发者工具）

适用：页面跳转、WXML 事件绑定、手势交互、原生组件（swiper/image/scroll-view）行为、`wx.*` API 真实行为。凡是「逻辑看着对但真机/模拟器就是不生效」的 bug（如 `catchtouchstart` 吞掉 `bindtap`），只有它能复现。

前置条件：微信开发者工具 → 设置 → 安全设置 → 开启「服务端口」。依赖装在临时目录即可（本项目无 npm 基建，不入库）：

```bash
mkdir -p /tmp/egg-e2e && cd /tmp/egg-e2e && npm init -y && npm install miniprogram-automator
```

核心用法：

```js
const automator = require('miniprogram-automator');
const miniProgram = await automator.launch({
  cliPath: '/Applications/wechatwebdevtools.app/Contents/MacOS/cli',
  projectPath: '<repo>/main/egg-miniprogram'
});
const page = await miniProgram.reLaunch('/pages/photo-gallery/photo-gallery');
await page.waitFor(3000);                        // 页面 API 无加载完成回调，用固定等待
const el = await page.$('.deck-card');           // CSS 选择器；$$ 取全部
await el.tap();                                  // 还有 touchstart/touchmove/touchend/longpress
const data = await page.data();                  // 直接读页面 data 断言状态
await page.callMethod('onCardTap', fakeEvent);   // 绕过事件系统直调页面方法（区分「事件没触发」与「逻辑错了」）
await miniProgram.evaluate(() => {               // 在小程序上下文执行任意 JS，
  const p = getCurrentPages().pop();             // 可 monkeypatch 页面方法记录调用
});
await miniProgram.close();
```

调试范式：先 `$()` + `tap()` 复现症状，再 `callMethod()` 直调处理函数——若直调正常而 tap 无效，问题在 WXML 事件绑定层而非 JS 逻辑。

### miniprogram-simulate（组件单测，纯 JS，无需开发者工具）

适用：自定义组件（`components/` 下 nav-bar、egg-avatar 等）的渲染与事件单测，可进 CI。配合 jest 使用：

```js
const simulate = require('miniprogram-simulate');
const id = simulate.load('/components/nav-bar/nav-bar');   // 组件路径
const comp = simulate.render(id, { title: 'AI写真' });     // 传 properties
comp.attach(document.createElement('parent-wrapper'));     // 挂载触发生命周期
expect(comp.querySelector('.title').dom.textContent).toBe('AI写真');
comp.querySelector('.back').dispatchEvent('touchstart');   // 触发事件
await simulate.sleep(0);
```

### 选型速查

| 问题层级 | 工具 |
|---|---|
| utils/ 纯函数 | 现有 `*.test.js`（node 直跑） |
| 自定义组件渲染/事件 | miniprogram-simulate |
| 整页交互、WXML 绑定、手势、原生组件 | miniprogram-automator |


## 约定与注意事项

- 页面/组件保持四件套 `.js/.json/.wxml/.wxss`；新增页面在 `app.json` 注册；`navigationStyle: custom`，所有页面需自带 `nav-bar`。
- **样式修改必读 [DESIGN.md](./DESIGN.md)**。孵化期只展示蛋形，不展示背景场景（PRD 红线，`verify-project.js` 会校验）。
- 昵称限制：最多 10 个字符（5 汉字），含敏感词拦截。
- **不入库 / 不落日志**：AppSecret、token、openid、unionid、`wx.login` code 等。
- **WXML 数据绑定禁止使用 `prototype` 作为字段名**：`prototype` 是 JS 原型链保留属性，WXML 引擎会沿原型链命中 `Object.prototype` 而非自身属性，导致渲染空白。改用 `petType` 等别名。

## 相关文档

- [DESIGN.md](./DESIGN.md) — 设计系统（改样式必读）
- [AGENTS.md](./AGENTS.md) — Codex 协作指引
- [README.md](./README.md) — 说明文档
- `../manager-api/CLAUDE.md` — 后端服务架构
