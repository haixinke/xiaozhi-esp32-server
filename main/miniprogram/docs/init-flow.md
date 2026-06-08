# 小程序初始化流程

本文档详细描述"完美女友"微信小程序从启动到聊天就绪的完整流程，涵盖登录、OTA、伴侣设置聚合接口、设备绑定、伴侣查询等环节。

---

## 两条路径

| 路径 | 触发条件 | 流程 |
|------|---------|------|
| 老用户 | Storage 中有 token + agentId | 恢复登录态 → OTA 设备检查 → 查询伴侣 → 聊天就绪 |
| 新用户 | 首次使用（无 token 或无 agentId） | 静默登录 → 命运初见向导(3页) → 调用 `/companion/setup` 聚合接口 → 聊天就绪 |

---

## 整体时序图

```mermaid
sequenceDiagram
    participant User as 用户
    participant Mini as 小程序
    participant API as manager-api
    participant WX as 微信服务器
    participant Redis as Redis
    participant DB as MySQL

    Note over Mini,DB: ========== 老用户路径 ==========

    User->>Mini: 打开小程序
    Mini->>Mini: 从 Storage 恢复 token/openid/agentId
    Mini->>Mini: needsDestiny = false

    par 并行执行
        Mini->>API: POST /ota/ (Device-ID: openid)
        API->>DB: 查询 device 表
        alt 设备已绑定
            API-->>Mini: {websocket: {url, token}}
            Mini->>Mini: globalData.wsUrl/wsToken
        else 设备未绑定
            API->>Redis: 写入激活码缓存
            API-->>Mini: {activation: {code}}
            Mini->>API: POST /device/bind/{agentId}/{code}
            API->>DB: 创建 DeviceEntity
            API-->>Mini: 绑定成功
            Mini->>API: POST /ota/ (再次获取 wsInfo)
            API-->>Mini: {websocket: {url, token}}
        end
    and
        Mini->>API: GET /companion/detail/{openid}
        API-->>Mini: {avatar, defaultImage, ...}
    end

    Mini->>Mini: _waitForAppReady() 轮询就绪
    User->>Mini: 点击"召唤"
    Mini->>API: WebSocket connect
    API-->>Mini: hello {session_id}
    Note over Mini: 聊天就绪

    Note over Mini,DB: ========== 新用户路径 ==========

    User->>Mini: 打开小程序
    Mini->>WX: wx.login()
    WX-->>Mini: code
    Mini->>API: POST /wechat/login {code}
    API-->>Mini: {token, openid, agentId: null}
    Mini->>Mini: needsDestiny = true

    Mini->>Mini: index.js 检测 needsDestiny
    Mini->>Mini: wx.redirectTo(/pages/destiny/destiny)

    User->>Mini: 选择角色/职业/音色/职业病
    Mini->>Mini: 保存到 globalData.destinyFlow
    Mini->>Mini: wx.navigateTo(/pages/soul-resonance/soul-resonance)

    User->>Mini: 选择灵魂特质(最多2条)/小任性
    Mini->>Mini: 更新 globalData.destinyFlow
    Mini->>Mini: wx.navigateTo(/pages/memory-anchor/memory-anchor)

    User->>Mini: 选择关系/宠物 → onComplete()
    Mini->>API: POST /companion/setup {...全部参数}
    Note over API: 聚合接口：创建伴侣 + 创建Agent + 同步提示词 + 设备绑定
    API->>DB: [事务] 创建 CompanionEntity + Agent + 同步 systemPrompt
    API->>DB: [非事务] 设备绑定 → 获取 wsInfo
    API-->>Mini: {agentId, companion, deviceBound, wsUrl, wsToken}

    Mini->>Mini: 6.5s 后 wx.reLaunch(/pages/index/index)
    Note over Mini: 等待用户点击"召唤"连接
```

---

## 详细步骤

### 1. 微信登录

**认证要求**: 无需 Token

| 项目 | 内容 |
|------|------|
| 前端函数 | `app.js:80` `silentLogin()` |
| API 端点 | `POST /xiaozhi/wechat/login` |
| 后端处理 | `WechatServiceImpl.java` |
| Shiro 配置 | `anon`（公开） |

**请求参数**:

```json
{
  "code": "<wx.login() 返回的临时凭证>"
}
```

**后端逻辑**:

1. 用 `appid` + `secret` + `code` 调微信 `jscode2session` 接口获取 `openid`
2. 查询 `ai_wechat_user` 表：
   - 新用户：自动创建 `sys_user`（username=openid）+ `ai_wechat_user` 关联记录
   - 老用户：直接读取
3. 查询该用户最新的 Agent（`getLatestUserAgent()`）— 可能为 null
4. 生成 Bearer Token

**响应数据**:

```json
{
  "code": 0,
  "data": {
    "token": "Bearer Token 字符串",
    "expire": 86400,
    "openid": "微信 openid",
    "agentId": "agent的ID，新用户为null"
  }
}
```

**globalData 存储**:

```javascript
globalData.token = token;
globalData.openid = openid;
globalData.virtualMAC = openid;  // openid 作为虚拟设备标识
globalData.agentId = agentId;    // 可能为 null
```

**登录态持久化**: `setToken(token, openid)` 写入 Storage，下次启动直接恢复，无需再次登录。

---

### 2. 后台初始化 (`initInBackground`)

`app.js:32` `initInBackground()` 在 `onLaunch` 时调用，不阻塞启动。

```mermaid
flowchart TD
    A[onLaunch → initInBackground] --> B{Storage 有 token?}
    B -->|是| C[恢复 globalData]
    C --> D{有 agentId?}
    D -->|否| E["needsDestiny = true<br>companionDataLoaded = true"]
    D -->|是| F["checkDeviceStatus() (后台)<br>fetchCompanionData() (后台)"]
    B -->|否| G["silentLogin()"]
    G --> H{有 agentId?}
    H -->|否| I["needsDestiny = true<br>companionDataLoaded = true"]
    H -->|是| J["fetchCompanionData()<br>checkDeviceStatus()"]
```

**关键点**: 老用户有 agentId 时，`checkDeviceStatus()` 和 `fetchCompanionData()` 并行执行，互不依赖。

---

### 3. OTA 设备状态检查

**认证要求**: 无需 Bearer Token，但需 `Device-ID` Header

| 项目 | 内容 |
|------|------|
| 前端函数 | `device.js:15` `checkOrRegisterDevice(deviceId)` |
| API 端点 | `POST /xiaozhi/ota/` |
| Shiro 配置 | `anon`（公开） |

**请求参数**:

```
Headers:
  Device-ID: <openid>
  Client-ID: wechat-miniprogram

Body:
{
  "application": { "name": "xiaozhi-miniprogram", "version": "1.0.0" },
  "board": { "type": "wechat-miniprogram", "mac": "<openid>" },
  "chip_model_name": "wechat-miniprogram"
}
```

**后端逻辑**:

1. `clientId = "wechat-miniprogram"` 时 **跳过 MAC 格式校验**，直接用 openid 作为合法设备标识
2. 通过 `macAddress`（openid）查询 `device` 表：

```mermaid
flowchart TD
    A[POST /ota/] --> B{clientId == wechat-miniprogram?}
    B -->|是| C[跳过 MAC 格式校验]
    B -->|否| D[校验 MAC 地址格式]
    C --> E{device 表中存在?}
    D --> E
    E -->|已存在| F[返回 websocket 信息]
    E -->|不存在| G[生成6位激活码写入 Redis]
    G --> H[返回 activation 激活码]
    F --> I["globalData.wsUrl / wsToken"]
    H --> J[进入自动绑定流程]
```

**响应数据（设备已绑定时）**:

```json
{
  "websocket": {
    "url": "ws://host:8000/xiaozhi/v1/",
    "token": "Base64签名.时间戳"
  }
}
```

**响应数据（设备未绑定时）**:

```json
{
  "activation": {
    "code": "123456",
    "message": "http://前端地址\n123456",
    "challenge": "<openid>"
  }
}
```

---

### 4. 设备注册绑定

**认证要求**: Bearer Token（绑定接口），无需 Token（OTA 接口）

绑定流程由 `app.js:196` `checkDeviceStatus()` 驱动：

```mermaid
flowchart TD
    A[checkDeviceStatus] --> B{有 agentId?}
    B -->|否| C[跳过，等待向导完成]
    B -->|是| D[checkOrRegisterDevice]
    D --> E{OTA 响应含 activation?}
    E -->|否| F[设备已绑定，直接获取 wsInfo]
    E -->|是| G[completeDeviceBinding]
    G --> H["POST /device/bind/{agentId}/{code}"]
    H --> I[再次 checkOrRegisterDevice]
    I --> J[获取 wsUrl + wsToken]
    J --> K["globalData.isDeviceBound = true"]
    F --> K
```

**错误处理**: 绑定失败时 `globalData.isDeviceBound = false`，index 页面显示重试 UI。

---

### 5. 伴侣设置聚合接口 (`/companion/setup`)

**认证要求**: Bearer Token

| 项目 | 内容 |
|------|------|
| 前端函数 | `memory-anchor.js:102` `onComplete()` |
| API 端点 | `POST /xiaozhi/companion/setup` |
| 后端处理 | `CompanionServiceImpl.java` `setup()` |
| Shiro 配置 | `oauth2` |

这是新用户向导完成后调用的 **聚合接口**，一次请求完成：创建伴侣 + 创建/校验 Agent + 同步提示词 + 设备绑定。

**请求参数**:

```json
{
  "deviceId": "<openid>",
  "type": "gf",
  "avatar": "https://...角色头像URL",
  "defaultImage": "https://...角色背景图URL",
  "character": "baiyueguang",
  "occupation": "design",
  "voice": "TTS_HSDSTTS_V2_0001",
  "soulTraits": "clingy,flirty",
  "soulQuirk": "grumpyMorning",
  "relationType": "childhood",
  "petType": "cat",
  "petName": "咪咪",
  "quirksText": "用户自定义输入的职业病描述",
  "agentId": ""
}
```

**后端逻辑（两阶段）**:

```mermaid
flowchart TD
    A[POST /companion/setup] --> B["阶段1: 数据库事务"]
    B --> C[创建 CompanionEntity]
    C --> D{agentId 已提供?}
    D -->|否| E[创建新 Agent]
    D -->|是| F[校验 Agent 所有权]
    E --> G[同步 systemPrompt + ttsVoiceId]
    F --> G
    G --> H["阶段2: 非事务"]
    H --> I[检查/绑定设备]
    I --> J{设备绑定成功?}
    J -->|是| K["返回 wsUrl + wsToken"]
    J -->|否| L["deviceBound = false（可重试）"]
```

**响应数据**:

```json
{
  "code": 0,
  "data": {
    "agentId": "新创建或已有的agentId",
    "companion": {
      "avatar": "https://...",
      "defaultImage": "https://...",
      "character": "baiyueguang"
    },
    "deviceBound": true,
    "wsUrl": "ws://host:8000/xiaozhi/v1/",
    "wsToken": "签名token"
  }
}
```

**前端处理**（`memory-anchor.js:onComplete()`）:

1. 调用 `/companion/setup`
2. 从响应中获取 `agentId`、`companion`、`wsUrl`、`wsToken`
3. 更新 `globalData`，设置 `needsDestiny = false`
4. 如果 `deviceBound` 为 false，额外调用 `app.checkDeviceStatus()`
5. 6.5 秒后跳转首页 → `wx.reLaunch('/pages/index/index')`

---

### 6. 命运初见向导

新用户进入 3 页向导流程，通过 `globalData.destinyFlow` 传递中间数据：

```mermaid
flowchart LR
    A["/pages/destiny/destiny<br>选择角色/职业/音色/职业病"] -->|wx.navigateTo| B["/pages/soul-resonance/soul-resonance<br>选择灵魂特质(≤2条)/小任性"]
    B -->|wx.navigateTo| C["/pages/memory-anchor/memory-anchor<br>选择关系/宠物 → onComplete()"]
    C -->|"/companion/setup"| D["wx.reLaunch → /pages/index/index"]
```

**`destinyFlow` 数据结构**:

```javascript
globalData.destinyFlow = {
  charId: 'baiyueguang',      // 角色
  occId: 'design',            // 职业
  voiceId: 'TTS_HSDSTTS_V2_0001', // 音色
  quirksText: '职业病描述',    // 自定义文本
  traits: ['clingy', 'flirty'], // 灵魂特质
  quirk: 'grumpyMorning',     // 小任性
};
```

---

### 7. 查询伴侣

**认证要求**: Bearer Token

| 项目 | 内容 |
|------|------|
| 前端函数 | `app.js:172` `fetchCompanionData()` |
| API 端点 | `GET /xiaozhi/companion/detail/{openid}` |
| Shiro 配置 | `oauth2` |

**调用时机**:
- 老用户：`initInBackground()` 恢复 storage 后调用（与 `checkDeviceStatus` 并行）
- 新用户：`needsDestiny = true` 时跳过，向导完成时由 `/companion/setup` 响应直接提供

**响应数据**:

```json
{
  "code": 0,
  "data": {
    "avatar": "https://...头像URL",
    "defaultImage": "https://...背景图URL",
    "character": "baiyueguang",
    "mood": "CALM"
  }
}
```

**globalData 存储**:

```javascript
globalData.companionAvatar = res.data.avatar;
globalData.companionBgImage = res.data.defaultImage;
globalData.companionDataLoaded = true;  // 无论成功失败都标记
```

---

### 8. 首页启动序列

| 项目 | 内容 |
|------|------|
| 前端文件 | `pages/index/index.js` |

index 页面通过 `_waitForAppReady()` 轮询 `globalData`，确认所有前置条件就绪：

```mermaid
flowchart TD
    A["onLoad()"] --> B["_waitForAppReady() 轮询"]
    B --> C{needsDestiny?}
    C -->|是| D["wx.redirectTo(/pages/destiny/destiny)"]
    C -->|否| E{"token && virtualMAC<br>&& isDeviceBound<br>&& wsUrl<br>&& companionDataLoaded?"}
    E -->|是| F["_bootstrap()"]
    E -->|否| G{"isDeviceBound === false?"}
    G -->|是| H["显示绑定重试 UI<br>bindFailed = true"]
    G -->|否| I["继续轮询 (250ms间隔，30s超时)"]
    I --> E
    F --> J["初始化 AudioManager + WebSocketManager"]
    J --> K["等待用户点击'召唤'"]
```

**就绪条件**: `token` + `virtualMAC` + `isDeviceBound` + `wsUrl` + `companionDataLoaded` 全部为真。

---

### 9. WebSocket 连接

wsUrl/wsToken 从 OTA 响应或 `/companion/setup` 响应获取。

**连接时机**: 用户手动点击"召唤"按钮（不自动连接）

| 项目 | 内容 |
|------|------|
| 前端函数 | `index.js:282` `_connectToChat()` → `wsManager.connect()` |
| WebSocket 管理 | `utils/websocket.js:60` |

**连接 URL 构建**:

```
{wsUrl}?device-id={openid}&client-id=wechat-miniprogram&authorization=Bearer {wsToken}
```

**连接握手流程**:

```mermaid
sequenceDiagram
    participant Mini as 小程序
    participant WS as xiaozhi-server

    Mini->>WS: WebSocket connect (wsUrl + token)
    WS-->>Mini: onOpen (TCP 连接建立)
    Mini->>WS: hello {opus 参数}
    Note over Mini: 10s 握手超时保护
    WS-->>Mini: hello {session_id}
    Note over Mini: 状态升级为 connected
    Note over Mini: 启动 30s 心跳
    Note over Mini: 60s pong 超时检测
    Note over Mini: 聊天就绪
```

**断线处理**: 断开后 **不自动重连**，用户需再次点击"召唤"。

---

## 认证要求汇总

| 端点 | Shiro 过滤器 | 认证方式 |
|------|-------------|---------|
| `POST /wechat/login` | `anon` | 无需认证 |
| `POST /ota/` | `anon` | 无需认证（通过 Device-ID Header 识别设备） |
| `POST /companion/setup` | `oauth2` | Bearer Token |
| `POST /device/bind/{agentId}/{code}` | `oauth2` | Bearer Token |
| `GET /companion/detail/{deviceId}` | `oauth2` | Bearer Token |

---

## globalData 数据流

```mermaid
flowchart LR
    subgraph 登录
        L1["token"]
        L2["openid"]
        L3["virtualMAC = openid"]
        L4["agentId"]
    end

    subgraph OTA/设备
        O1["wsUrl"]
        O2["wsToken"]
        O3["isDeviceBound"]
    end

    subgraph 伴侣
        C1["companionAvatar"]
        C2["companionBgImage"]
        C3["companionDataLoaded"]
    end

    subgraph 状态标记
        S1["needsDestiny"]
        S2["destinyFlow"]
    end
```

---

## 关键文件索引

| 功能 | 前端文件 | 后端文件 |
|------|---------|---------|
| 静默登录 | `app.js:80` | `WechatServiceImpl.java` |
| Token 管理 | `utils/auth.js` | - |
| HTTP 请求（401 自动重试） | `utils/request.js` | - |
| 后台初始化 | `app.js:32` | - |
| OTA / 设备检查 | `utils/device.js:15` | `OTAController.java` |
| 设备绑定 | `utils/device.js:53` | `DeviceServiceImpl.java` |
| 伴侣设置聚合 | `memory-anchor.js:102` | `CompanionServiceImpl.java` |
| 伴侣查询 | `app.js:172` | `CompanionController.java` |
| 命运初见页 | `pages/destiny/destiny.js` | - |
| 灵魂共振页 | `pages/soul-resonance/soul-resonance.js` | - |
| 记忆锚定页 | `pages/memory-anchor/memory-anchor.js` | - |
| WebSocket 连接 | `utils/websocket.js:60` | - |
| 聊天主页面 | `pages/index/index.js` | - |

---

## 错误处理机制

| 场景 | 处理方式 |
|------|---------|
| `wx.login()` 失败 | Promise reject，仅打印日志 |
| 后端接口返回 401 | 自动重新 `silentLogin()` 后重试原请求（仅一次） |
| 设备绑定失败 | index 页显示重试 UI（`bindFailed = true`），用户可点击重试 |
| 伴侣查询失败 | `console.warn`，不阻断流程，`companionDataLoaded` 仍设为 true |
| `/companion/setup` 调用失败 | toast 提示"唤醒失败" |
| WebSocket 握手超时 | 10s 无响应自动断开并尝试重连 |
| WebSocket pong 超时 | 60s 无响应主动断开 |
| WebSocket 断开 | 不自动重连，等待用户点击"召唤" |
| `_waitForAppReady` 超时 | 30s 轮询超时，toast 提示"启动失败" |
