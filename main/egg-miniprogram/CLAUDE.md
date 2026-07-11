# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

`egg-miniprogram` 是"蛋宝宝"微信小程序：一款孵化型 AI 宠物小程序，核心故事线为 **领取蛋 → 孵化修炼 → 破壳 → 见面语音对话**。它是 [xiaozhi-esp32-server](../..) 生态的一个子项目，与后端管理服务 `manager-api` 和聊天服务 `xiaozhi-server` 协同工作。

- **技术栈**：微信原生小程序（JavaScript / WXML / WXSS / JSON），无构建工具、无包管理器
- **工程根**：`project.config.json` 中 `miniprogramRoot: "miniprogram/"`，开发者工具导入本目录（`main/egg-miniprogram/`），而非单独导入 `miniprogram/`
- **AppID**：`wx661d20e88437af73`（未配置正式 AppID 时可用 `touristappid` 做界面预览）
- **设计规范**：见 [DESIGN.md](./DESIGN.md)；产品需求见 `docs/蛋宝宝小程序MVP_PRD.md`、`docs/蛋宝宝小程序PRD.md`

### 与其它子项目的关系

| 交互对象 | 子项目 | 用途 |
|---|---|---|
| 后端管理服务 | `main/manager-api/`（Java Spring Boot，端口 8002，上下文 `/xiaozhi`） | 微信注册登录、蛋宝宝宠物创建/孵化/破壳事件、设备绑定 |
| 聊天服务 | `main/xiaozhi-server/`（Python，WebSocket 端口 8000） | 破壳后与宠物语音对话（ASR → LLM → TTS） |
| 姊妹小程序 | `main/miniprogram/`（"笨笨女友"） | 同生态的聊天小程序，HTTP 封装 / WebSocket / OTA 设备流程可作实现参照 |

## 目录结构

```
main/egg-miniprogram/
├── project.config.json          # 微信开发者工具项目配置（miniprogramRoot=miniprogram/）
├── project.private.config.json  # 本机私有配置（不入库）
├── AGENTS.md                    # Codex 协作指引
├── DESIGN.md                    # 设计系统（改样式必读）
├── README.md                    # MVP 说明与演示激活码
├── docs/                        # PRD 与附件
├── scripts/verify-project.js    # 工程完整性校验（四件套/JSON/组件引用/禁止页面）
└── miniprogram/                 # 小程序源码根
    ├── app.js                   # 入口（onLaunch 调 wx.login 取 code）
    ├── app.json                 # 23 个页面 + tabBar（home/my）+ custom 导航
    ├── app.wxss                 # 全局样式与 token
    ├── sitemap.json
    ├── assets/                  # tab 图标、场景图（assets/scenes/*_with_egg.jpg）
    ├── components/              # 自定义组件（见下表）
    ├── pages/                   # 23 个页面（见下表）
    └── utils/
        ├── pet-store.js         # 当前 MVP 的本地存储 mock（孵化状态机、任务、收藏卡）
        └── exhibition-scenes.js # 展会六场景数据
```

### 页面（`miniprogram/pages/`，路径前缀 `pages/xxx/xxx`）

| 路径 | 名称 | 阶段 |
|---|---|---|
| `pages/welcome/welcome` | 启动欢迎/授权 | 入口 |
| `pages/home/home` | 首页（蛋宝宝 tab） | 状态机主舞台 |
| `pages/add-device/add-device` | 输入激活码/领蛋 | 入口 |
| `pages/hatch-guide/hatch-guide` | 孵化修炼手册（5 任务） | 孵化 |
| `pages/nickname/nickname` | 起昵称（+20%） | 孵化任务 |
| `pages/wish/wish` | 许愿池（每日 +5%） | 孵化任务 |
| `pages/lesson/lesson` | 蛋蛋早教班（每日 +5%） | 孵化任务 |
| `pages/doodle/doodle` | 彩蛋涂鸦（+20%） | 孵化任务 |
| `pages/hatch/hatch` | 破壳仪式 | 破壳 |
| `pages/collection-card/collection-card` | 破壳收藏卡（档案） | 破壳后 |
| `pages/album/album` | 卡册 | 破壳后 |
| `pages/pet-detail/pet-detail` | 宠物详情 | 破壳后 |
| `pages/chat/chat` | 语音对话 | 破壳后 |
| `pages/my/my` | 我的（tab） | 账号中心 |
| `pages/profile/profile` | 个人资料 | 账号 |
| `pages/settings/settings` | 通知设置 | 账号 |
| `pages/account/account` | 账号/登出 | 账号 |
| `pages/deregister/deregister` | 注销 | 账号 |
| `pages/help/help` | 帮助 | 账号 |
| `pages/invite-codes/invite-codes` | 邀请码（裂变） | 增长 |
| `pages/exhibition-scenes/exhibition-scenes` | 展会场景列表 | 展会模式 |
| `pages/exhibition-scene/exhibition-scene` | 单个展会场景 | 展会模式 |
| `pages/privacy/privacy` | 隐私政策 | 合规 |

页面流转详见 `README.md` 与首页 `home.js` 的 stage 分发。tabBar 仅 `home`（蛋宝宝）、`my`（我的）。

### 自定义组件（`miniprogram/components/`）

`nav-bar`（custom 导航栏）、`egg-avatar`（蛋形头像）、`pet-avatar`（破壳后宠物头像）、`button`、`card`、`list-row`、`switch-row`、`collapse-item`、`mood-badge`（心情标签）、`signal-bars`（信号强度）。每组件均为四件套 `.js/.json/.wxml/.wxss`，通过页面 `.json` 的 `usingComponents` 引入。

## 当前实现状态（重要）

MVP 阶段**未接后端**，所有数据由 `miniprogram/utils/pet-store.js` 用 `wx.*StorageSync` 本地模拟，目的是无后端预览完整交互闭环。**不要把 mock 行为描述为线上生产行为**。需要接服务端的清单（来自 `README.md` 边界）：

- 微信登录态、激活码校验、唯一编号、每日状态、对话、分享图小程序码、数据统计
- 登录、激活码校验、唯一编号、每日状态、对话、分享图小程序码和数据统计需接服务端

接入后端时，**后端为唯一事实源**：设备归属、宠物归属、孵化状态、事件、性格、邀请码、账号状态。客户端 ID 与 query 参数仅为提示，不得用于鉴权。

## 与后端服务 `manager-api` 的交互

`manager-api` 运行在 `http://<host>:8002/xiaozhi`。HTTP 请求封装参照姊妹小程序 `main/miniprogram/utils/request.js`：自动附加 `Authorization: Bearer <token>`，401 自动静默重登重试。`BASE_URL` 指向本机时可用 `/mini-ip` skill 切换到本机 IP。

### 1. 微信注册 / 登录

```
wx.login() → code
  └─POST /xiaozhi/wechat/login { code }              # 无需认证
      → { token, expire, openid, userId, isNewUser, hasPhone, agentId }
```

- `POST /wechat/login`：用 `wx.login` 的 `code` 换取 Bearer `token` + `openid` + `userId`；`isNewUser=true` 表示后端自动建了用户；`agentId` 为关联智能体。响应字段见 `WechatLoginRespDTO`。
- `POST /wechat/bindPhone`（需认证）：欢迎页用 `button open-type="getPhoneNumber"` 回调 `e.detail.code` 绑定手机号，响应 `WechatBindPhoneRespDTO`。手机号是进入首页的强制门槛；拒绝授权或绑定失败时必须留在欢迎页。
- `POST /wechat/bindAccount`（需认证）：将当前微信账号绑定到已有账号（用户名+密码）。
- `GET /wechat/profile`：查询当前用户资料（昵称/头像/性别/生日/城市/MBTI/星座/脱敏手机号）
- `PUT /wechat/profile`：更新当前用户资料（字段全可选，部分更新）
- `POST /wechat/avatar`：上传头像到阿里云 OSS，返回公开 URL

登录态管理参照 `main/miniprogram/utils/auth.js`：`token`、`openid`、签发时间、有效期存 Storage；提前 5 分钟视为过期触发静默刷新；`onShow` 检查并续期。**token / openid / wx.login code 严禁落日志、严禁入库**。

启动时允许静默注册或恢复账号，但欢迎页只在登录响应 `hasPhone=true` 时进入首页。未绑定手机号的用户必须先同意隐私政策并主动授权手机号；首次流程不获取微信昵称和头像，默认使用“蛋友”和默认头像，用户后续可在“我的 → 个人信息”自行修改。

### 2. 蛋宝宝宠物创建 / 孵化 / 破壳事件

后端宠物模型为 `ai_pet` 表（实体 `PetEntity`，见 `manager-api` 的 `modules/pet`）。**目标端点**（将 `birth()` 拆为领养 + 破壳两段，见下方"设备/宠物身份模型"与"接口草案"）：

| 方法 | 路径 | 鉴权 | 入参 / 出参 |
|---|---|---|---|
| POST | `/pet/adopt` | normal | `{ inviteCode }`（必填，核销裂变邀请码；prototype 后端随机锦鲤/玉兔）→ `PetVO`（`hatchStatus=EGG`，`deviceId=null`，无破壳档案） |
| POST | `/pet/{id}/hatch-action` | normal | `{ type, payload }` → `HatchActionResultVO`（减时 + 幂等：`addedMinutes`/`alreadyDone`/`readyToHatch`/`pet`） |
| GET | `/pet/{id}/hatch-actions` | normal | → `HatchActionVO[]`（该蛋修炼动作明细列表） |
| POST | `/pet/{id}/hatch` | normal | → `PetVO`（破壳：建 device+agent、回填档案、`hatchStatus=HATCHED`）✅ 已实现 |
| GET | `/pet/{id}` | normal | → `PetVO`（按 petId 查 + 归属校验）✅ 已实现 |
| GET | `/pet/list` | normal | → `PetVO[]`（当前用户所有蛋） |
| PUT | `/pet/update` | normal | `{ id, nickname }` → 更新昵称 |

> 现状：`POST /pet/adopt`、`POST /pet/{id}/hatch-action`、`GET /pet/{id}/hatch-actions`、破壳 `POST /pet/{id}/hatch`、单宠查询 `GET /pet/{id}` 均已落地。每日心情 todayMood 亦已落地（不新增端点，懒生成于 `GET /pet/{id}`/`GET /pet/list`：`today_mood_date != 今日(Asia/Shanghai)` 时按 `MoodDecider` 取 5 类心情 → LLM 生成 ≤20字文案、失败兜底 `MoodLinePool` 静态池 → 幂等写回 `ai_pet`，随 `PetVO` 返回；详见 `manager-api/docs/egg-pet-hatch-backend.md` §5.8）。schema 由 `202607101500` 放宽 `device_id` 为可空；hatch-action 表 changeset `202607101600`（5 动作减时模型，详见 `docs/egg-pet-identity-and-hatch-api.md` 第 10 节）。破壳实现：`PetBirthCalculator` 算 bazi/wuxing/zodiac → LLM 推 MBTI → LLM 生成 personality（作 agent 系统提示词，`createAgent` 后 `update system_prompt`）；`personalityBrief` 内置卡片语随机；`gender/bloodType` 随机；`avatarUrl` 按 prototype（锦鲤/玉兔）从配置池随机（非 AI 生图）。建虚拟 `ai_device`（`id=ASSIGN_UUID`、`macAddress=ai_device.id`、`board=wechat-egg-miniprogram`、`autoUpdate=0`、`alias=nickname`、`agentId`），回填 `ai_pet`，单事务。错误码 `PET_ALREADY_HATCHED=10209`、`PET_HATCH_TIME_NOT_REACHED=10214`。旧 `POST /pet/birth {deviceId}` 的"创建即出生"演示逻辑、旧 `GET /pet/detail/{deviceId}` 保留待迁移。`PetVO` 字段已就绪。

`PetVO` 关键字段（与前端 `pet-store.js` 的 mock 字段对应）：

- 基础：`id, userId, deviceId, nickname, birthDate`
- 命理：`bazi, wuxing, zodiac, mbti, personality, personalityBrief`
- 每日状态：`todayMood, todayMoodDate, todayMoodSentence`
- **孵化生命周期**：`hatchStatus`（`EGG`/`HATCHED`）、`hatchStartTime`（7 天倒计时起点，Model X 下由 adopt 设为 `now`）、`expectedHatchTime`（预计破壳时间，`=hatchStartTime+7d-acceleratedMinutes`）、`hatchedAt`（实际破壳时间=生日）、`acceleratedMinutes`（累计已加速分钟）
- **破壳档案**：`avatarUrl`、`prototype`（锦鲤/玉兔）、`gender`、`bloodType`

#### 前后端孵化状态机映射（注意粒度差异）

前端 `pet-store.getStage()` 有 5 态：`waiting / hatching / soon / ready / hatched`；后端 `hatchStatus` 仅 2 态 `EGG / HATCHED`。前端多态由 `hatchStartTime`、`expectedHatchTime`、`acceleratedMinutes`、`hatchedAt` 在客户端派生（单轨 Model X，不保留 `prepared`——进度满即时间到，详见 `docs/蛋宝宝小程序MVP_PRD.md` §5.3）。接入时对齐：

| 前端 stage | 触发条件 | 后端判定 |
|---|---|---|
| `empty` | 无 pet | `PetVO` 为空 |
| `waiting` | 无加速动作，距 `expectedHatchTime` ≥ 24h | `hatchStatus=EGG` 且 `acceleratedMinutes=0` |
| `hatching` | 有加速动作，距 `expectedHatchTime` ≥ 24h | `hatchStatus=EGG` 且 `acceleratedMinutes>0` |
| `soon` | 距 `expectedHatchTime` < 24h | `hatchStatus=EGG` |
| `ready` | 已到 `expectedHatchTime` 未破壳 | `hatchStatus=EGG` 且 `now ≥ expectedHatchTime` |
| `hatched` | 已破壳 | `hatchStatus=HATCHED`（`hatchedAt` 已写） |

#### 孵化修炼手册（5 个加速动作）

PRD 5.2：每个动作减少孵化时长。孵化修炼任务已落地：`ai_pet_hatch_action` 表（changeset `202607101600`）+ `POST /pet/{id}/hatch-action`、`GET /pet/{id}/hatch-actions` 端点（鉴权 `sys:role:normal`）。5 动作减时表与类型、payload、前端页面：

| 动作 | `type` | 减少时长 | 幂等 | payload | 前端页面 |
|---|---|---|---|---|---|
| 起昵称 | `NICKNAME` | 12h（720 分） | 一次性 | `{ nickname }` | `pages/nickname` |
| 摸一摸 | `CUDDLE` | 1h/日（60 分） | 每日 | `{ }` | `pages/home` 长按蛋壳 |
| 许愿池 | `WISH` | 1h/日（60 分） | 每日 | `{ value }` | `pages/wish` |
| 蛋蛋早教班 | `LESSON` | 1h/日（60 分） | 每日 | `{ value }` | `pages/lesson` |
| 彩蛋涂鸦 | `DOODLE` | 12h（720 分） | 一次性 | `{ color, colorName, pattern }` | `pages/doodle` |

减时模型（Model X）：**adopt 即设基线** `hatchStartTime=now`、`expectedHatchTime=now+7d`；hatch-action 不写起点，只累加 `acceleratedMinutes` 并重算 `expectedHatchTime=hatchStartTime+7d-acceleratedMinutes`（clamp ≥ `hatchStartTime`）。无动作蛋到 `adopt+7d` 即可破壳；有动作蛋更早破壳，动作永远让破壳更早、不推迟。日界按 `Asia/Shanghai`。"每日一次"由唯一索引 `uk_pet_action_date(pet_id, action_type, action_date)` 保证；"一次性"由业务层校验。`POST /pet/{id}/hatch-action` 响应 `HatchActionResultVO{addedMinutes, alreadyDone, readyToHatch, pet}`：当日已完成则 `alreadyDone=true, addedMinutes=0`，不重复减时。doodle 仅记录颜色/图样 payload，**不做 AI 生图**。错误码 `PET_ALREADY_HATCHED=10209`（蛋已破壳不能再提交任务）。完整契约与 payload 示例见 `docs/egg-pet-identity-and-hatch-api.md` 第 10 节。

> 前端进度条由 `acceleratedMinutes / 10080`（7 天 = 10080 分）派生，后端不返 `progress%`。

> 注：PRD §5.3 已修订为单轨任务减时模型（2026-07-11），与本实现一致——任务累加 `acceleratedMinutes` 下推 `expectedHatchTime`，进度满即时间到，不保留"进度满但时间未到"的 `prepared` 中间态。详见 `docs/egg-pet-identity-and-hatch-api.md` 第 10 节。

#### 破壳事件处理

`pages/hatch` 触发破壳仪式 → 成功后 `redirectTo /pages/collection-card?new=1`。后端对应 `POST /pet/{id}/hatch`（✅ 已落地，鉴权 normal，单事务）：前置 `hatchStatus==EGG`（否则 `PET_ALREADY_HATCHED`=10209）且 `now>=expectedHatchTime`（否则 `PET_HATCH_TIME_NOT_REACHED`=10214）；`PetBirthCalculator.calculate(hatchedAt)` 算 bazi/wuxing/zodiac → LLM 推 MBTI → LLM 生成 personality（作 agent 系统提示词，`createAgent` 拿默认模板后 `update system_prompt=personality`）；`personalityBrief` 内置卡片语随机；`gender/bloodType` 随机；`avatarUrl` 按 prototype（锦鲤/玉兔）从配置池随机（非 AI 生图）。建虚拟 `ai_device`（`id=ASSIGN_UUID`、`macAddress=ai_device.id`、`board="wechat-egg-miniprogram"`、`autoUpdate=0`、`alias=nickname`、`agentId`），回填 `ai_pet`（`deviceId`/`hatchStatus=HATCHED`/`hatchedAt`/`birthDate`/`bazi`/`wuxing`/`zodiac`/`mbti`/`personality`/`personalityBrief`/`gender`/`bloodType`/`avatarUrl`）。旧 `PetServiceImpl.birth()` 的"创建即出生"演示逻辑（`birthDate=now()`，立即生成 MBTI/性格）保留待迁移，前端改走 `POST /pet/{id}/hatch`。**OTA 对该虚拟设备的 WS 解析（manager-api 返回 websocket+token、xiaozhi-server 侧）尚未真机联调验证**。

### 3. 设备 / 宠物身份模型（多宠关键设计）

笨笨女友是"一人一伴侣"，用 `openid` 当 device id 成立。**蛋宝宝是"一人多宠"，`openid` 不能当 device id** —— 否则所有蛋共用一条 `ai_device`、一个 `agentId`，性格/音色无法区分，且 `ai_pet.device_id` 唯一索引会阻止第二只蛋。xiaozhi 的模型里 **`ai_device` 是聊天通道单位、`agentId` 1:1 挂在 device 上**，所以"聊天身份"必须下沉到宠物级：

```
微信用户(openid) ──1:N── 蛋宝宝(ai_pet) ──1:1── 虚拟设备(ai_device) ──1:1── agent(ai_agent)
```

- `openid` 仅换 `token/userId`（用户身份），**不再进 device**。
- 每只蛋破壳时后端建一条 `ai_device`：`macAddress = ai_device.id`（device 自身 id）、`userId = 微信userId`、`board = "wechat-egg-miniprogram"`、`autoUpdate = 0`、`alias = 昵称`；`ai_pet.deviceId` 指向它。
- `agentId`（性格/音色/系统提示词）由 `AgentService.createAgent(AgentCreateDTO)` 创建并绑到该 device，承载该蛋的 MBTI/性格。
- **懒创建**：领养只建 `ai_pet`（`deviceId=null`，`hatchStatus=EGG`）；device+agent **延迟到破壳**才建。`ai_pet.device_id` 原为 NOT NULL，已由 changeset `202607101500` 放宽为可空（MySQL 唯一索引允许多 NULL，`uk_ai_pet_device_id` 保留）。理由：PRD 里 MBTI/性格/造型本就破壳生成，agent 只在破壳后聊天才用得上。
- **device id 用 `ai_device.id`（ASSIGN_UUID）**，`macAddress` 存 `ai_device.id` 本身（破壳实现）。不要用 `openid` 或 `{openid}:{seq}`。

#### 小程序侧身份切换

globalData 从"用户级 device"改为"当前活跃宠物级 device"：

```js
globalData: {
  token, userId, openid,          // 用户身份（不变）
  activePetId: null,             // 当前选中的蛋
  activeDeviceId: null,          // 该蛋的 device id（破壳后才有，OTA/WS 用）
  wsUrl: null, wsToken: null,     // 当前蛋的 WS 通道
}
```

流程：`/wechat/login` → `/pet/list` 选 `activePetId` 取其 `deviceId` → 进 `chat` 前用 `activeDeviceId` 走 OTA → `WebSocketManager.connect(wsUrl, activeDeviceId, token)`。**切换活跃宠物需主动断开 WS 重连到新 device 的 channel**（同一时刻只连一个 channel，参照姊妹 `websocket.js` 的 `connect()` 先 teardown 再重连）。未破壳的蛋 `deviceId=null`，不能进 `chat`。

> 多宠 UI 演进：`pages/home` 现按"单只蛋"渲染，多宠后需改"蛋列表 + 当前蛋"；`my.js` 的"我的蛋"入口变列表。可分阶段：MVP 先单宠（`activePetId` 固定为第一只），后续放开多宠。

## 与聊天服务 `xiaozhi-server` 的交互（语音对话）

破壳后 `pages/chat` 与宠物语音对话。语音通道是 **WebSocket 直连 `xiaozhi-server`（端口 8000）**，不经过 `manager-api`。完整实现参照姊妹小程序 `main/miniprogram/utils/websocket.js`、`utils/audio.js`、`utils/device.js`。

### 连接获取流程（OTA 设备绑定）

蛋宝宝复用 xiaozhi 设备模型，但 **device id 是当前活跃宠物的 `ai_device.id`，不是 `openid`**（见上方"设备/宠物身份模型"）：

```
1. POST /xiaozhi/ota/  { board.mac = activeDeviceId, ... }  Header: Device-Id=activeDeviceId, Client-ID=...
      → 已绑定（破壳时已建 device 并绑 agent）：响应含 { websocket: { url, token } }
      → 未绑定（蛋未破壳，deviceId=null）：前端禁止进 chat
2. WebSocketManager.connect(wsUrl, deviceId=activeDeviceId, token)
```

> 破壳时后端已建好 `ai_device`（`userId` 已写、`agentId` 已绑），故 OTA 直接返回 `websocket` 信息，无需再走 `/device/bind/{agentId}/{deviceCode}` 验证码绑定流程（那是姊妹小程序首次绑设备的路径）。若 OTA 对"未绑 agent"有特殊分支，需确认虚拟设备破壳时已绑 agent。`wsUrl` 形如 `ws://host:8000/xiaozhi/v1/`。小程序无需直接感知 `agentId`——它绑在 device 上，OTA 按 `activeDeviceId` 即可取到该蛋的专属通道。

### WebSocket 消息协议

- **鉴权**：小程序无法自定义 WS Header，`token` 放进 URL query
- **心跳**：30s ping / 60s pong 超时；断线指数退避重连（最多 5 次：1/2/4/8/15s）
- **文本消息按 type 分发**，二进制消息直通音频回调：

| type | 含义 |
|---|---|
| `hello` | 连接建立 / 握手 |
| `stt` | 语音识别结果（用户消息上屏） |
| `llm` | AI 流式文本响应 |
| `tts` | TTS 状态与文本 |
| `audio` | Opus 音频帧（播放） |
| `goodbye` | 会话结束 |
| `iot` | IoT 设备命令 |

### 聊天状态机

`idle` → `thinking`（收到 stt/llm）→ `speaking`（收到 tts/audio，播放 Opus）→ `idle`

后端流水线：用户输入 → ASR → 意图识别 → LLM → TTS → 音频帧回推。

## 开发与校验命令

```bash
# 工程完整性校验（四件套、JSON、组件引用、禁止页面是否仍被编译）
node main/egg-miniprogram/scripts/verify-project.js

# 语法检查
find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
find main/egg-miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
```

页面变更在微信开发者工具中验证；涉及相机/扫码/订阅消息/客服/登录/设备绑定/音视频的行为，用真机验证。

### 本机联调

- 后端：`/start-api` 启动 `manager-api`（端口 8002）
- 聊天：`/start-ai` 启动 `xiaozhi-server`（端口 8000）
- 小程序 `BASE_URL` 指本机时用 `/mini-ip` 切到本机 IP

## 约定与注意事项

- 页面/组件保持四件套 `.js/.json/.wxml/.wxss`；新增页面在 `app.json` 的 `pages` 注册；新增组件通过页面 `.json` 的 `usingComponents` 引入；复用 `app.wxss` 的 token 与布局。
- `navigationStyle: custom`，所有页面需自带 `nav-bar` 组件承担导航栏。
- **样式修改必读 [DESIGN.md](./DESIGN.md)**（AGENTS.md 已强约束）。孵化期只展示蛋形，不展示背景场景，也不提供场景选择入口（PRD 红线，`verify-project.js` 会校验禁止的孵化场景页面未被编译）。
- 一个账号当前版本只能绑定 1 只蛋宝宝（`pet-store.bindPet` 的 `BOUND` 校验）；接入后端后由 `ai_pet` 的 `uk_ai_pet_device_id` 唯一索引保证一设备一宠物。
- 昵称限制：最多 10 个字符（5 汉字），含敏感词拦截；后端 `PetUpdateDTO` 校验在后端侧补齐。
- **不入库 / 不落日志**：AppSecret、token、openid、unionid、`wx.login` code、客服 ID、模板 ID、私有 API URL、真实用户数据。`project.private.config.json`、`.DS_Store`、生成产物保持本地。
- 孵化时长规则产品方向已确认（单轨 Model X）：**adopt 时设基线**（`hatchStartTime=now`、`expectedHatchTime=now+7d`），修炼动作只累加 `acceleratedMinutes` 下推 `expectedHatchTime`（`=hatchStartTime+7d-acceleratedMinutes`，clamp ≥ 起点），无动作蛋到 `adopt+7d` 即可破壳。进度满（`acceleratedMinutes≥10080`）即 `expectedHatchTime=hatchStartTime` 即可破壳，不保留 `prepared` 中间态。PRD §5.3 已于 2026-07-11 修订为单轨模型，与本实现一致。详见"孵化修炼手册"小节与 `docs/egg-pet-identity-and-hatch-api.md` 第 10 节。
- 后端 schema 变更走 Liquibase：**新增 changeset + SQL 文件，不编辑已有 changeset**（见 `manager-api/CLAUDE.md`）。

## 相关文档

- [DESIGN.md](./DESIGN.md) — 设计系统（改样式必读）
- [AGENTS.md](./AGENTS.md) — Codex 协作指引（含产品/状态/API 规则）
- [README.md](./README.md) — MVP 说明与演示激活码
- `docs/蛋宝宝小程序PRD.md` — 完整产品需求
- `docs/egg-pet-identity-and-hatch-api.md` — 设备/宠物身份模型与 adopt/hatch 接口草案（多宠设计）
- `../manager-api/CLAUDE.md` — 后端服务架构与 pet/wechat 模块
- `../miniprogram/CLAUDE.md` — 姊妹聊天小程序（HTTP/WS/OTA 实现参照）
