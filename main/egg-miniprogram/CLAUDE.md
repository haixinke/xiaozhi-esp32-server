# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

`egg-miniprogram` 是"蛋宝宝"微信小程序：孵化型 AI 宠物小程序，核心流程为 **领取蛋 → 孵化修炼 → 破壳 → 语音对话**。属于 [xiaozhi-esp32-server](../..) 生态，与后端 `manager-api`（Java，端口 8002）和聊天服务 `xiaozhi-server`（Python，WS 端口 8000）协同。

- **技术栈**：微信原生小程序（JS / WXML / WXSS / JSON），无构建工具、无包管理器
- **工程根**：`project.config.json` 中 `miniprogramRoot: "miniprogram/"`，开发者工具导入 `main/egg-miniprogram/`
- **AppID**：`wx661d20e88437af73`
- **设计规范**：[DESIGN.md](./DESIGN.md)；产品需求：`docs/蛋宝宝小程序PRD.md`、`docs/蛋宝宝小程序MVP_PRD.md`

### 与其它子项目的关系

| 交互对象 | 子项目 | 用途 |
|---|---|---|
| 后端管理服务 | `main/manager-api/`（Java Spring Boot，端口 8002，上下文 `/xiaozhi`） | 微信注册登录、宠物创建/孵化/破壳、设备绑定 |
| 聊天服务 | `main/xiaozhi-server/`（Python，WebSocket 端口 8000） | 破壳后语音对话（ASR → LLM → TTS） |
| 姊妹小程序 | `main/miniprogram/`（"笨笨女友"） | 同生态聊天小程序，架构可参考 |

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
    ├── config/                  # api.js（BASE_URL）、wish-questions.js
    ├── libs/opus/               # Opus 解码库
    ├── pages/                   # 20 个页面（见 app.json）
    └── utils/
        ├── request.js           # HTTP 封装（Bearer token，401 静默重登）
        ├── auth.js              # 登录态管理
        ├── pet-api.js           # 宠物 API 封装
        ├── pet-store.js         # 宠物状态缓存层（PetVO ↔ 本地 pet 映射）
        ├── wechat-api.js        # 微信 API（bindPhone）
        ├── invite-api.js        # 邀请码 API
        ├── ota.js               # OTA 设备检查（返回 WS 凭证）
        ├── websocket.js         # WebSocket 管理器（心跳/重连/分发）
        ├── audio.js             # 音频管理器（Opus 解码播放）
        └── *.test.js            # 单元测试
```

页面清单见 `app.json`（20 个页面，tabBar 为 `home` + `my`）。自定义组件：`nav-bar`、`egg-avatar`、`pet-avatar`、`button`、`card`、`list-row`、`collapse-item`、`mood-badge`、`signal-bars`。

## 当前实现状态

项目**已接入后端服务**，核心链路均走真实 API：

- **微信登录**：`app.js` → `wx.login` → `POST /wechat/login`；登录态由 `auth.js` 管理（提前 5 分钟续期，401 静默重登）
- **手机号绑定**：`home.js` 的“添加蛋宝宝”操作 → `POST /wechat/bindPhone`（领取蛋宝宝前的强制门槛；绑定成功后进入邀请码/激活码页面）
- **领养**：`add-device.js` → `POST /pet/adopt`（激活码即邀请码）
- **孵化修炼**：`pet-store.js` → `POST /pet/{id}/hatch-action`
- **破壳**：`pet-store.createCollectionCard()` → `POST /pet/{id}/hatch` → 跳转收藏卡页
- **每日心情**：后端 `GET /pet/list` 懒生成 `todayMood`，前端优先使用，无值时本地 fallback
- **语音对话**：`chat.js` → `ota.js` 获取 WS 凭证 → `websocket.js` 连接 → `audio.js` 播放 Opus
- **邀请码**：`invite-api.js` → `GET /invite/mine`

`pet-store.js` 为本地缓存层：`savePetFromVO(vo)` 将后端 `PetVO` 映射为本地 pet 并缓存，支持离线回显。**后端为唯一事实源**。**尚未实现**：分享图小程序码、数据统计。

## 与后端服务 `manager-api` 的交互

`manager-api` 运行在 `http://<host>:8002/xiaozhi`。HTTP 封装在 `utils/request.js`（自动附加 Bearer token，401 静默重登）。`BASE_URL` 在 `config/api.js` 配置，本机联调用 `/mini-ip` skill 切换。

### 微信登录

- `POST /wechat/login`（匿名）：`wx.login` 的 `code` 换取 `token` + `openid` + `userId` + `hasPhone`
- `POST /wechat/bindPhone`（需认证）：`button open-type="getPhoneNumber"` 回调 `code` 绑定手机号
- `GET/PUT /wechat/profile`：查询/更新用户资料；`POST /wechat/avatar`：上传头像到 OSS

**token / openid / wx.login code 严禁落日志、严禁入库**。未绑定手机号可以进入并浏览首页；点击“添加蛋宝宝”后必须完成手机号授权，才能继续进入邀请码/激活码页面领取蛋宝宝。

### 宠物 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/pet/adopt` | `{ inviteCode }` → `PetVO`（`hatchStatus=EGG`，`deviceId=null`） |
| POST | `/pet/{id}/hatch-action` | `{ type, payload }` → `HatchActionResultVO`（减时 + 幂等） |
| GET | `/pet/{id}/hatch-actions` | → `HatchActionVO[]` |
| POST | `/pet/{id}/hatch` | → `PetVO`（破壳：建 device+agent、回填档案） |
| GET | `/pet/{id}` / `/pet/list` | → `PetVO` / `PetVO[]` |
| PUT | `/pet/update` | `{ id, nickname }` |

错误码：`PET_ALREADY_HATCHED=10209`、`PET_HATCH_TIME_NOT_REACHED=10214`。

`PetVO` 关键字段：`id, userId, deviceId, nickname, birthDate` / `bazi, wuxing, zodiac, mbti, personality, personalityBrief` / `todayMood, todayMoodDate, todayMoodSentence` / `hatchStatus(EGG/HATCHED), hatchStartTime, expectedHatchTime, hatchedAt, acceleratedMinutes` / `avatarUrl, sceneUrl, prototype, gender, bloodType`。

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
| 摸一摸 | `CUDDLE` | 1h/日 | 每日 | `pages/home` 长按 |
| 许愿池 | `WISH` | 1h/日 | 每日 | `pages/wish` |
| 蛋蛋早教班 | `LESSON` | 1h/日 | 每日 | `pages/lesson` |
| 彩蛋涂鸦 | `DOODLE` | 12h | 一次性 | `pages/doodle` |

"每日一次"由唯一索引 `uk_pet_action_date` 保证。doodle 不做 AI 生图。详见 `docs/egg-pet-identity-and-hatch-api.md` 第 10 节。

### 设备 / 宠物身份模型

蛋宝宝是"一人多宠"，`openid` 不能当 device id。聊天身份下沉到宠物级：

```
微信用户(openid) ──1:N── 蛋宝宝(ai_pet) ──1:1── 虚拟设备(ai_device) ──1:1── agent(ai_agent)
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

本机联调：`/start-api` 启动后端、`/start-ai` 启动聊天服务、`/mini-ip` 切换 BASE_URL 到本机 IP。

## 约定与注意事项

- 页面/组件保持四件套 `.js/.json/.wxml/.wxss`；新增页面在 `app.json` 注册；`navigationStyle: custom`，所有页面需自带 `nav-bar`。
- **样式修改必读 [DESIGN.md](./DESIGN.md)**。孵化期只展示蛋形，不展示背景场景（PRD 红线，`verify-project.js` 会校验）。
- 一个账号只能绑定 1 只蛋宝宝（后端 `uk_ai_pet_device_id` 唯一索引保证）。
- 昵称限制：最多 10 个字符（5 汉字），含敏感词拦截。
- **不入库 / 不落日志**：AppSecret、token、openid、unionid、`wx.login` code 等。
- 后端 schema 变更走 Liquibase：**新增 changeset + SQL 文件，不编辑已有 changeset**。
- **WXML 数据绑定禁止使用 `prototype` 作为字段名**：`prototype` 是 JS 原型链保留属性，WXML 引擎会沿原型链命中 `Object.prototype` 而非自身属性，导致渲染空白。改用 `petType` 等别名。

## 相关文档

- [DESIGN.md](./DESIGN.md) — 设计系统（改样式必读）
- [AGENTS.md](./AGENTS.md) — Codex 协作指引
- [README.md](./README.md) — 说明文档
- `docs/蛋宝宝小程序PRD.md` — 完整产品需求
- `docs/egg-pet-identity-and-hatch-api.md` — 设备/宠物身份模型与接口契约
- `../manager-api/CLAUDE.md` — 后端服务架构
- `../miniprogram/CLAUDE.md` — 姊妹聊天小程序（架构参考）
