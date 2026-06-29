# 小程序 WebSocket 连接生命周期管理

> 本文档完整描述小程序客户端与 xiaozhi-server 服务端之间的 WebSocket 连接机制，涵盖连接建立、心跳保活、自动重连、超时断开及断连恢复的完整设计。

---

## 目录

1. [整体架构概览](#1-整体架构概览)
2. [连接建立机制](#2-连接建立机制)
3. [心跳保活机制](#3-心跳保活机制)
4. [自动重连机制](#4-自动重连机制)
5. [超时断开机制](#5-超时断开机制)
6. [断开场景与处理](#6-断开场景与处理)
7. [各层超时参数总览](#7-各层超时参数总览)
8. [关键配置项](#8-关键配置项)
9. [时序图](#9-时序图)
10. [已修复的问题记录](#10-已修复的问题记录)

---

## 1. 整体架构概览

### 1.1 组件角色

| 组件 | 文件 | 职责 |
|------|------|------|
| WebSocketManager | `miniprogram/utils/websocket.js` | 客户端连接管理、心跳、重连状态机 |
| 首页控制器 | `miniprogram/pages/index/index.js` | 页面生命周期、空闲超时、前后台切换 |
| 应用入口 | `miniprogram/app.js` | 启动登录、token 刷新 |
| WebSocketServer | `core/websocket_server.py` | 服务端连接准入、连接数限制 |
| ConnectionHandler | `core/connection.py` | 服务端单连接处理、超时检查、资源清理 |
| PingMessageHandler | `core/handle/textHandler/pingMessageHandler.py` | 服务端 ping 消息处理 |
| ReceiveAudioHandle | `core/handle/receiveAudioHandle.py` | 无语音活动超时检测 |
| ListenMessageHandler | `core/handle/textHandler/listenMessageHandler.py` | 文字消息处理，更新活动时间 |

### 1.2 设计目标

```
┌──────────────┐                          ┌──────────────────┐
│   小程序端     │                          │    服务端          │
│              │     WebSocket             │                  │
│  WebSocket   │◄──────────────────────────│  WebSocketServer │
│  Manager     │     双向通信               │                  │
│              │                          │  ConnectionHandler│
│  ┌────────┐  │                          │  ├ 超时检查任务    │
│  │心跳30s │  │──── ping ──────────────►  │  ├ 无语音超时      │
│  │pong 60s│  │◄──── pong ─────────────   │  ├ 绝对超时        │
│  └────────┘  │                          │  └ 连接数限制      │
│              │                          │                  │
│  ┌────────┐  │                          │                  │
│  │空闲5min│  │                          │                  │
│  │自动断开 │  │                          │                  │
│  └────────┘  │                          │                  │
│              │                          │                  │
│  ┌────────┐  │                          │                  │
│  │指数退避│  │                          │                  │
│  │自动重连│  │                          │                  │
│  └────────┘  │                          │                  │
└──────────────┘                          └──────────────────┘
```

### 1.3 客户端状态机

```
    ┌─────────────┐
    │ disconnected │◄────────────────────────────────┐
    └──────┬──────┘                                   │
           │ connect()                                │
           ▼                                          │
    ┌─────────────┐  超时/断开                        │
    │ connecting  │──────────────────────────────────►│
    └──────┬──────┘                                   │
           │ 收到服务端 hello 响应                     │
           ▼                                          │
    ┌─────────────┐  超时/断开                        │
    │  connected  │──────────────────────────────────►│
    └─────────────┘                                   │
                                                      │
    disconnect() / destroy() ───── 不触发重连 ────────┘
    （_manualClose = true）
```

状态说明：

| 状态 | 含义 | 触发条件 |
|------|------|---------|
| `disconnected` | 未连接/已断开 | 初始状态、连接断开后 |
| `connecting` | 连接中 | 调用 `connect()` 后，收到 hello 前 |
| `connected` | 已连接并完成握手 | 收到服务端 hello 响应 |

---

## 2. 连接建立机制

### 2.1 客户端连接流程

**触发时机：** 用户打开小程序主页 → `_bootstrap()` → `_connectToChat()`

```
app.js onLaunch
  └─► initInBackground()
        └─► silentLogin() → 获取 token + openid
        └─► checkDeviceStatus() → 获取 wsUrl + wsToken
        └─► fetchCompanionData() → 加载伴侣信息

index.js onLoad
  └─► _waitForAppReady() → 等待登录和设备绑定完成
  └─► _bootstrap()
        └─► _initAudio() → 初始化音频引擎
        └─► _initWebSocketManager() → 创建 WebSocketManager 实例
        └─► _connectToChat() → 建立连接
```

### 2.2 WebSocket 握手流程

```
客户端                                      服务端
  │                                           │
  ├── wx.connectSocket(url) ────────────────►│ _handle_connection()
  │   URL 参数:                               │   解析 headers
  │   ?device-id={openid}                     │   连接数检查 (max_connections)
  │   &client-id=wechat-miniprogram           │   认证检查
  │   &authorization=Bearer {token}           │
  │                                           │
  │◄── onOpen ────────────────────────────────│ 创建 ConnectionHandler
  │                                           │ 初始化 last_activity_time
  │                                           │ 启动 _check_timeout 任务
  │                                           │
  ├── 发送 hello 消息 ──────────────────────►│ 处理 hello
  │   { type: "hello",                        │   解析音频参数
  │     version: 1,                           │   返回 welcome_msg
  │     transport: "websocket",               │
  │     audio_params: {...} }                 │
  │                                           │
  │◄── hello 响应 ───────────────────────────│ { type: "hello",
  │   { type: "hello",                        │   session_id: "...",
  │     session_id: "xxx",                    │   audio_params: {...} }
  │     ... }                                 │
  │                                           │
  │ 状态升级: connecting → connected          │
  │ 启动心跳定时器                             │
  │ 启动空闲计时器                             │
```

### 2.3 连接 URL 构建

```javascript
// websocket.js _buildUrl()
const url = wsUrl + '?'
  + 'device-id=' + encodeURIComponent(openid)
  + '&client-id=wechat-miniprogram'
  + '&authorization=' + encodeURIComponent('Bearer ' + token);
```

> 小程序无法自定义 WebSocket Header，因此 token 通过 URL query 传递。

### 2.4 握手超时保护

客户端在 `onOpen` 后启动 10 秒握手超时计时器：

```javascript
// websocket.js L113-L120
this._handshakeTimer = setTimeout(() => {
  if (this.state !== 'connected') {
    this._emitError(new Error('handshake timeout'), 'connect');
    this._teardownSocket(false);
    this._scheduleReconnect();
  }
}, HANDSHAKE_TIMEOUT_MS); // 10秒
```

收到服务端 hello 响应后清除该计时器。如果 10 秒内未收到 hello，视为握手失败，断开并触发自动重连。

---

## 3. 心跳保活机制

### 3.1 设计目的

- 防止中间网络设备（NAT、负载均衡）因长时间无数据传输而断开 TCP 连接
- 让服务端更新 `last_activity_time`，防止 180 秒绝对超时关闭连接
- 让客户端检测服务端是否存活（pong 响应）

### 3.2 心跳参数

| 参数 | 值 | 定义位置 | 说明 |
|------|-----|---------|------|
| `PING_INTERVAL_MS` | 30 秒 | `websocket.js` L22 | 客户端发送 ping 的间隔 |
| `PONG_TIMEOUT_MS` | 60 秒 | `websocket.js` L23 | 等待 pong 响应的超时时间 |
| `enable_websocket_ping` | `true` | `config.yaml` L81 | 服务端是否处理 ping 并回 pong |

### 3.3 心跳工作流程

```
客户端                                      服务端
  │                                           │
  │  每 30 秒:                                │
  │  ┌───────────────────────────┐            │
  │  │ 1. 发送 ping              │            │
  │  │ 2. 若无活跃 pong 超时计时器│            │
  │  │    则启动 60s pong 超时   │            │
  │  └───────────────────────────┘            │
  │                                           │
  ├── { type: "ping" } ─────────────────────►│ PingMessageHandler
  │                                           │   1. 更新 last_activity_time
  │                                           │   2. 返回 pong
  │                                           │
  │◄── { type: "pong", timestamp: "..." } ───┤
  │                                           │
  │  收到 pong:                               │
  │  ┌───────────────────────────┐            │
  │  │ 清除 pong 超时计时器      │            │
  │  └───────────────────────────┘            │
```

### 3.4 pong 超时检测逻辑（已修复）

**核心规则：** 仅在没有活跃的 pong 超时计时器时才启动新的。

```javascript
// websocket.js _startPing() — 修复后
_startPing() {
  this._stopPing();
  this._pingTimer = setInterval(() => {
    if (this.state === 'connected') {
      this.sendPing();
      // 仅在没有活跃计时器时才启动，避免每次 ping 重置导致超时永远不触发
      if (!this._pongTimeoutTimer) {
        this._startPongTimeout();
      }
    }
  }, PING_INTERVAL_MS);
}
```

**正确行为：**

| 场景 | 时间线 | 结果 |
|------|--------|------|
| 服务端正常 | t=30 ping → t=30.1 pong → 清除计时器 → t=60 ping → 启动新计时器 → ... | 连接保持 ✓ |
| 服务端无响应 | t=30 ping → 启动 60s 计时器 → t=60 ping(不重置) → t=90 计时器到期 → 断开重连 | 90 秒检测到故障 ✓ |

> 详见 [已修复的问题记录](#10-已修复的问题记录) 中的 P1 修复。

### 3.5 服务端 ping 处理

```python
# pingMessageHandler.py
async def handle(self, conn, msg_json):
    enable_websocket_ping = conn.config.get("enable_websocket_ping", False)
    if not enable_websocket_ping:
        conn.logger.debug("WebSocket心跳功能未启用，忽略PING消息")
        return

    # 更新活动时间戳（保活关键）
    conn.last_activity_time = time.time() * 1000

    # 返回 pong
    pong_message = {
        "type": "pong",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
    }
    await conn.websocket.send(json.dumps(pong_message))
```

`last_activity_time` 的更新会同时影响服务端两层超时检查：
- 120 秒无语音活动超时（`receiveAudioHandle.py`）
- 180 秒绝对超时（`connection.py _check_timeout`）

---

## 4. 自动重连机制

### 4.1 触发条件

自动重连在以下**被动断开**场景中触发（`_manualClose = false`）：

| 场景 | 触发位置 |
|------|---------|
| WebSocket onClose 回调 | `websocket.js` L265-L272 |
| pong 超时 | `websocket.js` L384-L388 |
| 握手超时 | `websocket.js` L114-L120 |
| 连接失败（connectSocket fail） | `websocket.js` L99-L102 |
| 小程序切回前台（连接已断开） | `index.js` L1037-L1043 |

**不触发重连的场景**（`_manualClose = true`）：
- 空闲超时主动断开（`wsManager.disconnect()`）
- 页面卸载（`wsManager.destroy()`）
- 用户手动断开

### 4.2 指数退避策略

```javascript
// websocket.js L24
const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 15000];

_scheduleReconnect() {
  if (this._destroyed || this._manualClose) return;
  if (this._reconnectTimer) return;
  if (!this._url || !this._deviceId) return;

  const idx = Math.min(this._reconnectAttempts, RECONNECT_DELAYS.length - 1);
  const delay = RECONNECT_DELAYS[idx];
  this._reconnectAttempts += 1;

  this._reconnectTimer = setTimeout(() => {
    this._reconnectTimer = null;
    if (this._destroyed || this._manualClose) return;
    this.connect(this._url, this._deviceId, this._token);
  }, delay);
}
```

| 重连次数 | 等待时间 | 说明 |
|---------|---------|------|
| 第 1 次 | 1 秒 | 快速重试 |
| 第 2 次 | 2 秒 | |
| 第 3 次 | 4 秒 | |
| 第 4 次 | 8 秒 | |
| 第 5+ 次 | 15 秒 | 上限，持续重试 |

重连成功后（`onOpen` → 收到 hello），`_reconnectAttempts` 重置为 0。

### 4.3 切前台自动重连

```javascript
// index.js _handleAppShow()
_handleAppShow() {
  // 从后台恢复：若连接已断开则自动重连
  if (this.wsManager && this.data.connectionState === 'disconnected') {
    this._connectToChat();
    this._resetIdleTimer();
  }
}
```

小程序从后台切回前台时，微信可能已冻结进程导致连接中断。此处检测到断开状态后主动重连，恢复"在线"状态。

---

## 5. 超时断开机制

### 5.1 超时层级总览

```
┌─────────────────────────────────────────────────────────┐
│                    超时断开层级                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  客户端层（小程序）                                      │
│  ├─ 第0层: 空闲超时 5 分钟无交互 → 主动断开              │
│  │         （_manualClose=true，不触发重连）              │
│  └─ 第0.5层: pong 超时 60-90 秒无响应 → 断开+自动重连    │
│                                                         │
│  服务端层（xiaozhi-server）                              │
│  ├─ 第1层: 无语音超时 120 秒 → 发告别语 → 关闭连接       │
│  ├─ 第2层: 绝对超时 180 秒（120+60）→ 强制关闭连接       │
│  └─ 第3层: 连接数上限 max_connections → 拒绝新连接(1013) │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 5.2 第 0 层：客户端空闲超时（5 分钟）

**位置：** `index.js` L28, L1011-L1031

```javascript
const IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 5分钟

_resetIdleTimer() {
  if (this._idleTimer) {
    clearTimeout(this._idleTimer);
    this._idleTimer = null;
  }
  this._idleTimer = setTimeout(() => {
    if (this.data.connectionState !== 'connected') return;
    if (this.data.chatState !== STATE_IDLE) return; // 正在对话中不断开
    // 语音通话激活时跳过
    try {
      const VoiceCallManager = require('../../utils/voice-call-manager');
      const callState = VoiceCallManager().getState().state;
      if (callState === 'connected' || callState === 'calling') return;
    } catch (_) {}
    // 5 分钟无交互 → 主动断开
    this.wsManager.disconnect(); // _manualClose = true，不触发重连
  }, IDLE_TIMEOUT_MS);
}
```

**重置条件（任一触发即重置 5 分钟计时器）：**

| 触发事件 | 代码位置 |
|---------|---------|
| 连接建立（connected） | `index.js` L375-L378 |
| 收到 WebSocket 消息 | `index.js` `_handleWSMessage` 内调用 |
| 用户发消息 | `index.js` `_sendMessage` 内调用 |
| 切回前台重连 | `index.js` L1041 |

**设计意图：** 控制后端会话成本。5 分钟无交互的用户大概率已离开，主动断开释放服务端资源。下次发消息时秒级重连，对用户无感知。

### 5.3 第 0.5 层：客户端 pong 超时（60-90 秒）

详见 [心跳保活机制 - pong 超时检测逻辑](#34-pong-超时检测逻辑已修复)。

| 检测时间 | 说明 |
|---------|------|
| 最快 60 秒 | ping 后立即进入 60s 倒计时，期间无 pong |
| 最慢 90 秒 | 刚发出 ping 后收到 pong 清除计时器，需等下一次 ping(30s) + 60s 超时 |

**触发后动作：** 断开连接 → 指数退避自动重连（`_manualClose = false`）

### 5.4 第 1 层：服务端无语音活动超时（120 秒）

**位置：** `receiveAudioHandle.py` L99-L123

```python
async def no_voice_close_connect(conn, have_voice):
    if have_voice:
        conn.last_activity_time = time.time() * 1000
        return
    if conn.last_activity_time > 0.0:
        no_voice_time = time.time() * 1000 - conn.last_activity_time
        close_connection_no_voice_time = int(
            conn.config.get("close_connection_no_voice_time", 120)
        )
        if not conn.close_after_chat and no_voice_time > 1000 * close_connection_no_voice_time:
            conn.close_after_chat = True
            conn.client_abort = False
            # 发送结束提示语
            prompt = end_prompt.get("prompt", "请你以时间过得真快...")
            await startToChat(conn, prompt)  # LLM 生成告别语 → TTS 播报 → 关闭
```

**触发条件：** `last_activity_time` 超过 120 秒未更新（仅检查语音活动路径）。

**处理流程：**
1. 设置 `close_after_chat = True`（防止重复触发）
2. LLM 生成告别语（如"时间过得真快，我们下次再聊吧~"）
3. TTS 播报告别语
4. 播报完成后关闭连接

> **注意：** 此超时主要在语音对话场景触发。文字聊天场景中，心跳 ping 和文字消息都会更新 `last_activity_time`，因此此层通常不触发。心跳 ping 更新 `last_activity_time` 是关键——这正是 `enable_websocket_ping: true` 必须启用的原因。

### 5.5 第 2 层：服务端绝对超时（180 秒）

**位置：** `connection.py` L229-L232, L1567-L1596

```python
# 超时秒数 = 无语音超时(120s) + 60s = 180s
self.timeout_seconds = int(
    self.config.get("close_connection_no_voice_time", 120)
) + 60

async def _check_timeout(self):
    while not self.stop_event.is_set():
        last_activity_time = self.last_activity_time
        if self.need_bind:
            last_activity_time = self.first_activity_time

        if last_activity_time > 0.0:
            current_time = time.time() * 1000
            if current_time - last_activity_time > self.timeout_seconds * 1000:
                self.logger.info("连接超时，准备关闭")
                self.stop_event.set()
                await self.close(self.websocket)
                break

        await asyncio.sleep(10)  # 每 10 秒检查一次
```

**触发条件：** 180 秒内 `last_activity_time` 未被任何操作更新。

**与其他超时的关系：**

| 心跳状态 | 第 1 层(120s) | 第 2 层(180s) | 实际关闭时间 |
|---------|--------------|--------------|-------------|
| ping 启用 | 心跳更新时间戳，不触发 | 心跳更新时间戳，不触发 | 客户端 5min 空闲断开 |
| ping 禁用 | 不触发（无语音路径） | **180s 触发** | 180s 强制关闭 |
| ping 启用 + 网络中断 | 不触发 | 不触发 | 客户端 60-90s pong 超时检测 |

### 5.6 第 3 层：连接数上限保护

**位置：** `websocket_server.py` L73, L117-L125

```python
self._max_connections = max(1, int(server_config.get("max_connections", 50)))

# 新连接检查
if self._active_connections >= self._max_connections:
    await websocket.close(1013, "服务器连接数已满，请稍后重试")
    return
```

**触发条件：** 当前活跃连接数达到 `max_connections` 上限（默认 80）。

**Close Code 1013 (Try Again Later)：** WebSocket 标准协议码，告知客户端"服务器暂时无法处理，请稍后重试"。

**与 SAE 弹性伸缩的配合：**

```
max_connections × warning_ratio = SAE 弹性目标值

示例: 80 × 0.8 = 64
当单实例 TCP 活跃连接数 ≥ 64 → SAE 触发扩容
当单实例 TCP 活跃连接数 ≥ 80 → 拒绝新连接（保护实例不 OOM）
```

---

## 6. 断开场景与处理

### 6.1 场景总览

| # | 场景 | 触发方 | 动作 | 重连 |
|---|------|--------|------|------|
| 1 | 用户退出小程序 | 客户端 | `onUnload` → `_teardown()` → `destroy()` | 不重连 |
| 2 | 空闲 5 分钟 | 客户端 | `disconnect()` → `_manualClose=true` | 不重连 |
| 3 | 用户手动断开 | 客户端 | `disconnect()` → `_manualClose=true` | 不重连 |
| 4 | 网络中断 | 客户端 | pong 超时检测 → 断开 | 自动重连 |
| 5 | 服务端无语音 120s | 服务端 | 发告别语 → 关闭 | 自动重连 |
| 6 | 服务端绝对超时 180s | 服务端 | 强制关闭 | 自动重连 |
| 7 | 切后台 → 切前台 | 客户端 | 检测断开 → 重连 | 自动重连 |
| 8 | 服务端连接数满 | 服务端 | close(1013) | 客户端自动重连 |

### 6.2 切后台/切前台处理

```
┌─────────────────────────────────────────────────┐
│  小程序切后台 (onHide / wx.onAppHide)            │
│                                                 │
│  1. 代码层面：不主动断开 WebSocket               │
│  2. 仅挂断正在进行的语音通话                     │
│  3. 心跳定时器继续运行（但可能被微信冻结）        │
│                                                 │
│  微信系统行为：                                   │
│  - 短暂切后台：进程保持，连接维持                │
│  - 长时间后台：进程冻结，JS 定时器停止            │
│    → 心跳中断 → 服务端 180s 超时关闭连接         │
│    → 客户端 pong 超时（如果定时器还在跑）         │
└─────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│  小程序切回前台 (onShow / wx.onAppShow)          │
│                                                 │
│  1. 检查 token 是否过期 → 静默刷新              │
│  2. 检查连接状态：                                │
│     - connected → 无需操作                       │
│     - disconnected → _connectToChat() 自动重连   │
│  3. 重置空闲计时器                               │
└─────────────────────────────────────────────────┘
```

### 6.3 服务端连接关闭流程

当连接断开时（无论客户端还是服务端触发），服务端执行以下清理：

```
连接断开
  │
  ▼
_handle_connection finally 块
  │
  ├─► _active_connections -= 1  （递减连接计数）
  │
  ├─► _save_and_close(ws)
  │     ├─► 守护线程1: 生成会话标题 (generate_and_save_chat_title)
  │     ├─► 守护线程2: 保存记忆 (memory.save_memory)
  │     └─► close(ws)
  │           ├─► 清理 VAD 连接资源
  │           ├─► 清理音频缓冲区
  │           ├─► 取消超时检查任务 (timeout_task.cancel)
  │           ├─► 清理工具处理器 (func_handler.cleanup)
  │           ├─► 设置停止事件 (stop_event.set)
  │           ├─► 清空任务队列
  │           ├─► 关闭 WebSocket 连接
  │           ├─► 关闭 TTS 连接
  │           ├─► 关闭 ASR 连接
  │           └─► 日志: "连接资源已释放"
  │
  └─► 强制关闭 WebSocket（兜底）
```

> **全局线程池不随连接关闭而销毁**，仅在服务器退出时通过 `shutdown_global_executor()` 统一清理。

---

## 7. 各层超时参数总览

### 7.1 参数对照表

| 层级 | 参数 | 默认值 | 配置位置 | 说明 |
|------|------|--------|---------|------|
| 客户端 - 心跳间隔 | `PING_INTERVAL_MS` | 30s | `websocket.js` L22 | 发送 ping 的间隔 |
| 客户端 - pong 超时 | `PONG_TIMEOUT_MS` | 60s | `websocket.js` L23 | 等待 pong 响应的超时 |
| 客户端 - 握手超时 | `HANDSHAKE_TIMEOUT_MS` | 10s | `websocket.js` L25 | 等待服务端 hello 的超时 |
| 客户端 - 空闲超时 | `IDLE_TIMEOUT_MS` | 5min | `index.js` L28 | 无交互自动断开 |
| 客户端 - 重连退避 | `RECONNECT_DELAYS` | [1,2,4,8,15]s | `websocket.js` L24 | 指数退避重连延迟 |
| 服务端 - 无语音超时 | `close_connection_no_voice_time` | 120s | `config.yaml` L67 | 无语音活动后关闭 |
| 服务端 - 绝对超时 | 上述值 + 60s | 180s | `connection.py` L229-231 | 兜底强制关闭 |
| 服务端 - 超时检查间隔 | 硬编码 | 10s | `connection.py` L1592 | `_check_timeout` 轮询间隔 |
| 服务端 - 心跳开关 | `enable_websocket_ping` | true | `config.yaml` L81 | 是否处理 ping 并回 pong |
| 服务端 - 最大连接数 | `max_connections` | 80 | `config.yaml` L47 | 单实例连接上限 |
| 服务端 - 告警阈值 | `max_connections_warning_ratio` | 0.8 | `config.yaml` L49 | 告警/SAE 扩容触发比例 |

### 7.2 超时触发优先级

在 `enable_websocket_ping: true` 的正常配置下，各超时的实际触发顺序：

```
时间轴 ──────────────────────────────────────────────────────►

  0s          30s         60s        90s        120s       180s     300s
  │            │           │          │           │          │        │
  │  ping      │  ping     │  ping    │  ping     │  ping    │        │
  │  ↓         │  ↓        │  ↓       │  ↓        │  ↓       │        │
  │  pong ← 更新 last_activity_time ──────────────────────────►        │
  │            │           │          │           │          │        │
  │            │           │          │           │     180s 绝对超时     │
  │            │           │          │     120s 无语音超时   (不触发*） │
  │            │           │          │           │          │   300s 客户端
  │            │           │          │           │          │   空闲断开
  │            │           │          │           │          │   （实际触发）
  │            │           │          │           │          │        │
  │            │           │          │           │          │        ▼
  │            │           │          │           │          │   主动断开
  │            │           │          │           │          │  (不重连)
  └────────────┴───────────┴──────────┴───────────┴──────────┴────────┘
  
  * 心跳更新 last_activity_time，120s/180s 超时不触发
  ** 客户端 5min 空闲超时是实际关闭连接的主路径
```

### 7.3 `last_activity_time` 更新点

| 更新来源 | 代码位置 | 说明 |
|---------|---------|------|
| 连接建立 | `connection.py` L279 | 初始化时间戳 |
| 收到语音帧 | `receiveAudioHandle.py` L101 | 检测到人声时更新 |
| 收到 ping | `pingMessageHandler.py` L34 | 心跳保活关键 |
| 收到文字消息 | `listenMessageHandler.py` L55 | `listen` + `detect` 模式 |
| 发送音频帧 | `sendAudioHandle.py` L217, L241 | TTS 回传时更新 |

---

## 8. 关键配置项

### 8.1 服务端配置（config.yaml）

```yaml
# 是否启用WebSocket心跳保活机制
# 必须设为 true，否则服务端不处理 ping，导致:
# 1. 不回 pong → 客户端 pong 超时检测无法工作
# 2. 不更新 last_activity_time → 180s 后连接被强制关闭
enable_websocket_ping: true

# 没有语音输入多久后断开连接(秒)，默认120秒
# 服务端在检测到无语音活动超过该时间后，发送告别语并关闭连接
close_connection_no_voice_time: 120

# WebSocket最大连接数
max_connections: 80

# 连接数告警阈值比例
max_connections_warning_ratio: 0.8
```

### 8.2 客户端常量（websocket.js）

```javascript
const PING_INTERVAL_MS = 30 * 1000;     // 心跳间隔 30 秒
const PONG_TIMEOUT_MS = 60 * 1000;      // pong 超时 60 秒
const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 15000]; // 重连退避
const HANDSHAKE_TIMEOUT_MS = 10 * 1000; // 握手超时 10 秒
```

### 8.3 客户端常量（index.js）

```javascript
const IDLE_TIMEOUT_MS = 5 * 60 * 1000;  // 空闲超时 5 分钟
```

### 8.4 配置关系图

```
enable_websocket_ping: true
        │
        ├──► 服务端处理 ping → 回 pong
        │    ├──► 客户端收到 pong → 清除超时 → 连接保持 ✓
        │    └──► 服务端更新 last_activity_time
        │         ├──► 120s 无语音超时不触发（心跳保活）
        │         └──► 180s 绝对超时不触发（心跳保活）
        │
        └──► 实际断开由客户端 5min 空闲超时控制
             └──► 用户下次发消息时秒级重连
```

---

## 9. 时序图

### 9.1 正常连接生命周期

```
 用户打开小程序                          服务端
     │                                    │
     ├─ onLaunch ─► 静默登录 ─► 获取token │
     ├─ onLoad ─► 等待app ready            │
     │     └─► _bootstrap()               │
     │           ├─ 初始化音频引擎         │
     │           └─ _connectToChat()       │
     │                ├─ wx.connectSocket │
     │                │   url?device-id=xx ├─► _handle_connection()
     │                │                    │    连接数检查 ✓
     │                │                    │    认证 ✓
     │                │                    │    创建 ConnectionHandler
     │                │                    │    初始化 last_activity_time
     │                │                    │    启动 _check_timeout 任务
     │                │                    │
     │                ├─ 发送 hello ──────►│    处理 hello
     │                │◄── hello 响应 ────┤    返回 session_id
     │                │                    │
     │   connected ✓  │                    │
     │   启动心跳 30s  │                    │
     │   启动空闲 5min │                    │
     │                                    │
     │◄──── 每 30s: ping ─────────────────►│    更新 last_activity_time
     │───── pong ──────────────────────►──┤    返回 pong
     │                                    │
     │   ...用户正常对话...                │
     │                                    │
     │   5 分钟无交互                      │
     │   ├─ disconnect()                  │
     │   │  _manualClose=true             │
     │   ├─ WebSocket close ─────────────►│    ConnectionClosed 异常
     │   │                                │    _save_and_close()
     │   │                                │    ├ 保存记忆
     │   │                                │    ├ 生成标题
     │   │                                │    └ close() 清理资源
     │   │                                │    _active_connections -= 1
     │   ▼                                │
     │  女友显示离线                       │
     │                                    │
     │   用户再次发消息                     │
     │   ├─ _isReadyForAction()           │
     │   │  检测 disconnected             │
     │   │  → _connectToChat() 秒级重连   │
     │   └─► 连接恢复，女友重新在线 ✓       │
```

### 9.2 网络中断恢复

```
 t=0    连接正常，心跳中
 t=30   发 ping, 启动 60s pong 超时
 t=60   发 ping (pong 超时计时器仍在，不重置)
 t=90   pong 超时触发!
        ├─ teardownSocket(false)
        ├─ state → disconnected
        └─ scheduleReconnect() → 1s 后重连

 t=91   connect() → connecting
 t=91.5 onOpen → 发 hello
 t=92   收到 hello → connected ✓
        重连成功，_reconnectAttempts 重置为 0
```

### 9.3 切后台/切前台

```
 t=0    用户使用中，连接正常
 t=T    用户按 Home 键切后台
        ├─ _handleAppHide(): 挂断语音通话
        ├─ WebSocket 连接保持（不主动断开）
        └─ 心跳定时器继续（但可能被微信冻结）

 [微信冻结进程: JS 定时器停止]
        ├─ 心跳中断
        └─ 服务端 180s 后 _check_timeout 触发关闭
           ├─ 连接断开
           └─ _active_connections -= 1

 t=T+X  用户切回前台
        ├─ _handleAppShow()
        │   检测 connectionState === 'disconnected'
        │   → _connectToChat() 自动重连
        ├─ _resetIdleTimer()
        └─ 连接恢复，女友重新在线 ✓
```

---

## 10. 已修复的问题记录

### P0: `enable_websocket_ping` 默认 false 与小程序设计冲突

**问题：** `config.yaml` 中 `enable_websocket_ping: false`，导致服务端不处理 ping 消息，不回 pong，不更新 `last_activity_time`。

**影响：**
- 客户端每 30s 发 ping 但服务端忽略
- 服务端 180s 后绝对超时 → 强制关闭连接
- 客户端检测到断开 → 自动重连
- 每 3 分钟连接断一次再重连，女友在线状态闪烁

**修复：** 将 `enable_websocket_ping` 改为 `true`。

**文件：** `config.yaml` L81

---

### P1: 客户端 pong 超时检测永远不触发

**问题：** `_startPing()` 中每次发送 ping 后无条件调用 `_startPongTimeout()`，而 `_startPongTimeout()` 内部先 `clearTimeout` 旧的再设新的。由于心跳间隔（30s）小于 pong 超时（60s），每次 ping 都重置了 60s 计时器，导致超时永远不触发。

**影响：** 即使服务端完全不响应 pong，客户端也无法通过心跳检测到连接已死。

**修复：** 只在没有活跃的 pong 超时计时器时才启动新的：

```javascript
// 修改前
this.sendPing();
this._startPongTimeout();  // 每次都重置

// 修改后
this.sendPing();
if (!this._pongTimeoutTimer) {  // 仅在没有活跃计时器时启动
  this._startPongTimeout();
}
```

**文件：** `miniprogram/utils/websocket.js` L361-L373

---

### 修复后的行为验证

| 场景 | 修复前 | 修复后 |
|------|--------|--------|
| 服务端正常响应 | ping/pong 正常 | ping/pong 正常 ✓ |
| 服务端无响应 | **无法检测**，等待 180s 服务端关闭 | 60-90s 内检测到 → 自动重连 ✓ |
| `enable_websocket_ping: false` | 服务端不回 pong → 客户端无法检测 | 客户端 60-90s 检测到 → 重连（但服务端 180s 也会关闭，竞态） |
| `enable_websocket_ping: true` | 客户端无法检测 pong 超时 | 完整心跳闭环正常工作 ✓ |
