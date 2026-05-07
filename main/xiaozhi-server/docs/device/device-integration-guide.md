# 小智 ESP32 设备联调指南

> 本文档面向设备端开发人员,提供从零开始完成设备与平台联调的完整步骤说明。

## 目录

- [前置准备](#前置准备)
- [服务端架构概述](#服务端架构概述)
- [设备注册与绑定](#设备注册与绑定)
- [WebSocket 连接](#websocket-连接)
- [消息协议](#消息协议)
- [完整联调流程](#完整联调流程)
- [常见问题](#常见问题)
- [附录:接口文档](#附录接口文档)

---

## 前置准备

### 1. 环境检查

在开始联调前,请确保以下服务已正常运行:

```bash
# 检查后端服务 (端口 8002)
curl http://localhost:8002/xiaozhi/doc.html

# 检查聊天服务 (端口 8000)
telnet localhost 8000

# 检查 Web 管理控制台 (端口 8001)
curl http://localhost:8001
```

### 2. 获取必要信息

准备以下信息:
- **服务器 IP 地址**: 例如 `192.168.1.100`
- **设备 MAC 地址**: 唯一标识设备,例如 `AA:BB:CC:DD:EE:FF`
- **智能体 ID**: 在管理后台创建智能体后获得,例如 `agent_123456`

### 3. 工具准备

- **WebSocket 测试工具**: 浏览器或 Postman
- **网络抓包工具**: Wireshark (可选,用于调试)
- **日志查看**: 需要查看服务器日志权限

---

## 服务端架构概述

### 服务组件

```
┌─────────────────┐
│   ESP32 设备     │
└────────┬────────┘
         │ WebSocket (8000)
         ▼
┌─────────────────────────┐
│  xiaozhi-server         │
│  (Python AI 核心)       │
│  - ASR (语音转文字)     │
│  - LLM (大模型对话)     │
│  - TTS (文字转语音)     │
└────────┬────────────────┘
         │ HTTP (8002)
         ▼
┌─────────────────────────┐
│  manager-api            │
│  (Java Spring Boot)     │
│  - 设备注册/绑定        │
│  - 配置管理             │
└─────────────────────────┘
```

### 关键端口

| 服务 | 端口 | 协议 | 用途 |
|------|------|------|------|
| xiaozhi-server | 8000 | WebSocket | 实时语音对话 |
| manager-api | 8002 | HTTP | REST API |
| manager-web | 8001 | HTTP | Web 管理控制台 |

---

## 设备注册与绑定

### 步骤 1: 设备注册 (获取验证码)

设备首次联网时,需要向管理后台注册,获取 6 位数字验证码。

**接口**: `POST /xiaozhi/device/register`

**请求体**:
```json
{
  "macAddress": "AA:BB:CC:DD:EE:FF"
}
```

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": "123456"
}
```

**cURL 示例**:
```bash
curl -X POST http://192.168.1.100:8002/xiaozhi/device/register \
  -H "Content-Type: application/json" \
  -d '{"macAddress": "AA:BB:CC:DD:EE:FF"}'
```

### 步骤 2: 用户登录获取 Token

设备绑定需要用户权限，因此需要先登录获取访问令牌（access_token）。

#### 方式 1: 使用 Web 管理后台登录（推荐）

这是最简单的方式，适合小白用户：

1. 浏览器访问管理控制台: `http://192.168.1.100:8001`
2. 在登录页面输入用户名和密码
3. 完成图形验证码验证
4. 登录成功后，浏览器会自动保存 Token
5. 后续操作由 Web 界面自动处理

#### 方式 2: 使用 API 登录

如果需要通过 API 绑定设备，需要按照以下步骤获取 Token：

**2.1 获取验证码图片**

```bash
# 生成一个随机 UUID
UUID="12345678-1234-1234-1234-123456789abc"

# 获取验证码图片
curl "http://192.168.1.100:8002/xiaozhi/user/captcha?uuid=$UUID" \
  --output captcha.png

# 查看验证码图片，手动识别验证码内容
open captcha.png  # macOS
# 或 xdg-open captcha.png  # Linux
```

**2.2 获取 SM2 公钥（用于密码加密）**

```bash
curl http://192.168.1.100:8002/xiaozhi/user/pub-config
```

响应示例:
```json
{
  "code": 0,
  "data": {
    "sm2PublicKey": "YOUR_SM2_PUBLIC_KEY_HERE",
    "enableMobileRegister": true
  }
}
```

**2.3 使用 SM2 公钥加密密码**

系统使用 SM2 国密算法对密码进行加密。你需要使用 SM2 加密工具对原始密码进行加密。

> **注意**: SM2 加密需要在客户端完成，服务器只接收加密后的密码。具体的加密实现请参考前端代码或使用第三方 SM2 加密库。

**2.4 提交登录请求**

```bash
curl -X POST http://192.168.1.100:8002/xiaozhi/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "ENCRYPTED_PASSWORD_HERE",
    "captchaId": "12345678-1234-1234-1234-123456789abc",
    "captcha": "abc123"
  }'
```

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MTQwNDQ4MDAsInVzZXJJZCI6MSwidXNlcm5hbWUiOiJhZG1pbiJ9...",
    "expire": 7200,
    "clientHash": "abc123def456"
  }
}
```

**Token 说明**:
- `token`: 访问令牌（即 access_token），有效期 2 小时（7200 秒）
- 后续所有需要权限的接口都需要在请求头中携带此 Token
- 格式: `Authorization: Bearer {token}`

### 步骤 3: 绑定设备

#### 方式 1: 使用 Web 管理后台（推荐）

登录后，在 Web 界面中完成绑定：

1. 进入"设备管理"页面
2. 点击"绑定设备"按钮
3. 输入验证码 `123456`
4. 选择要绑定的智能体
5. 确认绑定

#### 方式 2: 使用 API 绑定

**接口**: `POST /xiaozhi/device/bind/{agentId}/{deviceCode}`

**请求头**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**路径参数**:
- `agentId`: 智能体 ID
- `deviceCode`: 6 位验证码

**示例**: 绑定验证码 `123456` 到智能体 `agent_123456`

```bash
curl -X POST http://192.168.1.100:8002/xiaozhi/device/bind/agent_123456/123456 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**响应**:
```json
{
  "code": 0,
  "msg": "success"
}
```

### 步骤 4: 验证绑定成功

**接口**: `GET /xiaozhi/device/bind/{agentId}`

**响应**:
```json
{
  "code": 0,
  "data": [
    {
      "id": "device_789",
      "macAddress": "AA:BB:CC:DD:EE:FF",
      "deviceName": "我的小智",
      "status": "online"
    }
  ]
}
```

---

## WebSocket 连接

### ⚠️ 重要提示

**WebSocket 连接地址应该从配置接口获取，而不是硬编码！**

设备应该先调用 `/config/agent-models` 接口获取服务器配置，其中包含 `server.websocket` 字段，返回实际的 WebSocket 连接地址。

这样设计的优势：
- ✅ 支持 Docker/公网部署（域名或动态 IP）
- ✅ 配置集中管理，无需固件升级
- ✅ 支持多环境部署（开发/测试/生产）

### 获取 WebSocket 地址

**接口**: `POST /config/agent-models`

**请求**:
```json
{
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "selectedModule": {
    "ASR": "default",
    "LLM": "openai",
    "TTS": "azure"
  }
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "server": {
      "websocket": "ws://192.168.1.100:8000/xiaozhi/v1/",
      "port": 8000,
      "http_port": 8003
    },
    "ASR": {...},
    "LLM": {...},
    "TTS": {...}
  }
}
```

### 连接地址格式

**从配置接口获取**:
```
{server.websocket}?device-id=DEVICE_ID
```

**完整示例**:
```
ws://192.168.1.100:8000/xiaozhi/v1/?device-id=AA:BB:CC:DD:EE:FF&client-id=esp32_001
```

### 连接参数

| 参数 | 必填 | 说明 | 示例 |
|------|------|------|------|
| device-id | ✅ | 设备 ID (MAC 地址) | `AA:BB:CC:DD:EE:FF` |
| client-id | ❌ | 客户端标识 (便于日志追踪) | `esp32_001` |
| authorization | ❌ | 认证 Token (如启用认证) | `Bearer xxx` |

### 连接流程

1. **建立 TCP 连接**
2. **WebSocket 握手**
3. **发送 Hello 消息**
4. **等待服务器响应**
5. **开始音频交互**

---

## 消息协议

### 1. Hello 握手消息

设备连接成功后,必须首先发送 Hello 消息进行握手。

**客户端 → 服务器**:
```json
{
  "type": "hello",
  "audio_params": {
    "format": "opus",
    "sample_rate": 24000,
    "channels": 1
  },
  "features": {
    "mcp": true,
    "camera": true
  }
}
```

**参数说明**:
- `format`: 音频编码格式,当前支持 `opus`
- `sample_rate`: 采样率,推荐 `24000`
- `channels`: 声道数,`1` 为单声道
- `features.mcp`: 是否支持 MCP (模型上下文协议)
- `features.camera`: 是否支持摄像头

**服务器 → 客户端** (Hello 响应):
```json
{
  "type": "hello",
  "session_id": "abc123def456",
  "server_time": 1713964800000,
  "audio_params": {
    "format": "opus",
    "sample_rate": 24000
  },
  "welcome_message": "您好,我是小智助手"
}
```

### 2. 音频消息

音频数据采用**二进制格式**直接发送,无需 JSON 包装。

**格式**:
```
[Opus 数据包 1][Opus 数据包 2][Opus 数据包 3]...
```

**编码参数**:
- 编码: Opus
- 采样率: 24000 Hz
- 比特率: 24000 bps (单声道)
- 帧大小: 20ms (960 samples)

**发送频率**: 建议每 20-40ms 发送一次音频包

### 3. 文本控制消息

#### Listen 消息 (切换监听模式)

**客户端 → 服务器**:
```json
{
  "type": "listen",
  "mode": "manual"
}
```

**模式说明**:
- `auto`: 自动监听 (默认,检测到声音自动开始)
- `manual`: 手动监听 (需要手动触发)

#### Abort 消息 (中断对话)

**客户端 → 服务器**:
```json
{
  "type": "abort"
}
```

**用途**: 停止当前正在播放的 TTS 音频

#### Ping 消息 (心跳保活)

**客户端 → 服务器**:
```json
{
  "type": "ping"
}
```

**服务器 → 客户端**:
```json
{
  "type": "pong",
  "timestamp": 1713964800000
}
```

**建议**: 每 30 秒发送一次 Ping 消息

### 4. 服务器响应消息

#### TTS 音频数据

**格式**: 二进制 Opus 数据包

**文本元数据** (可选,在音频前发送):
```json
{
  "type": "text",
  "content": "今天天气很好"
}
```

**音频数据**:
- `FIRST` 标记: 表示句子开始
- `MIDDLE` 标记: 表示句子中间
- `LAST` 标记: 表示句子结束

#### 状态消息

**错误提示**:
```json
{
  "type": "error",
  "code": 1001,
  "message": "认证失败"
}
```

---

## 完整联调流程

### 阶段 1: 准备工作

```bash
# 1. 确认服务器运行正常
ping 192.168.1.100

# 2. 注册设备,获取验证码
curl -X POST http://192.168.1.100:8002/xiaozhi/device/register \
  -H "Content-Type: application/json" \
  -d '{"macAddress": "AA:BB:CC:DD:EE:FF"}'

# 假设返回验证码: 123456
```

### 阶段 2: 绑定设备

1. 打开浏览器访问 `http://192.168.1.100:8001`
2. 登录管理后台
3. 进入"设备管理" → "绑定设备"
4. 输入验证码 `123456`
5. 选择智能体
6. 确认绑定

### 阶段 3: 建立 WebSocket 连接

**使用浏览器测试** (推荐初次使用):

1. 启动 Python HTTP 服务器:
```bash
cd main/xiaozhi-server/test
python -m http.server 8006
```

2. 浏览器访问: `http://192.168.1.100:8006/test_page.html`

3. 在测试页面中:
   - 输入 WebSocket 地址: `ws://192.168.1.100:8000/xiaozhi/v1/`
   - 输入设备 ID: `AA:BB:CC:DD:EE:FF`
   - 点击"连接"按钮
   - 观察日志输出

**使用 Postman 测试**:

1. 创建 WebSocket Request
2. URL: `ws://192.168.1.100:8000/xiaozhi/v1/?device-id=AA:BB:CC:DD:EE:FF`
3. 发送 Hello 消息

### 阶段 4: 发送音频测试

**准备测试音频**:

1. 使用手机录音工具录制一段语音 ("你好小智")
2. 转换为 Opus 编码 (采样率 24000 Hz)
3. 使用工具分帧 (每帧 20ms)

**发送音频**:

在 WebSocket 连接建立后,直接发送二进制音频数据。

### 阶段 5: 验证响应

检查服务器返回:
1. **TTS 音频**: 应该收到小智的回复音频
2. **文本消息**: 应该收到小智回复的文本 (如果启用)
3. **日志输出**: 查看服务器日志,确认处理流程

---

## 常见问题

### Q1: WebSocket 连接失败

**现象**: 连接立即断开,收到 "认证失败" 消息

**排查**:
1. 检查 device-id 是否正确
2. 确认设备已完成绑定
3. 检查服务器是否启用了认证 (`config.yaml` 中 `server.auth.enabled`)

### Q2: 收不到 TTS 音频

**现象**: 发送音频后,服务器没有返回音频数据

**排查**:
1. 检查服务器日志,确认 ASR 是否正常识别
2. 检查 LLM 是否正常响应
3. 检查 TTS 模块是否初始化成功

### Q3: 音频质量差

**现象**: 语音识别准确率低

**优化建议**:
1. 确保音频采样率为 24000 Hz
2. 使用 Opus 编码,比特率设置为 24000 bps
3. 减少环境噪音
4. 调整 VAD 参数 (`config.yaml` 中 `VAD.threshold`)

### Q4: 连接频繁断开

**现象**: WebSocket 连接建立后不久自动断开

**排查**:
1. 检查心跳机制,确保每 30 秒发送一次 Ping
2. 检查网络稳定性
3. 检查服务器日志,查看断开原因

### Q5: 如何查看详细日志?

**服务器日志位置**:
```bash
# 开发环境
tail -f main/xiaozhi-server/logs/app.log

# Docker 环境
docker logs -f xiaozhi-server
```

**关键字搜索**:
```bash
# 搜索特定设备的日志
grep "AA:BB:CC:DD:EE:FF" logs/app.log

# 搜索错误日志
grep "ERROR" logs/app.log
```

---

## 附录:接口文档

### REST API 接口

#### 1. 设备注册

```
POST /xiaozhi/device/register
Content-Type: application/json

请求体:
{
  "macAddress": "AA:BB:CC:DD:EE:FF"
}

响应:
{
  "code": 0,
  "msg": "success",
  "data": "123456"  // 6位验证码
}
```

#### 2. 设备绑定

```
POST /xiaozhi/device/bind/{agentId}/{deviceCode}
Authorization: Bearer {access_token}

路径参数:
- agentId: 智能体 ID
- deviceCode: 6位验证码

响应:
{
  "code": 0,
  "msg": "success"
}
```

#### 3. 获取已绑定设备列表

```
GET /xiaozhi/device/bind/{agentId}
Authorization: Bearer {access_token}

响应:
{
  "code": 0,
  "data": [
    {
      "id": "device_id",
      "macAddress": "AA:BB:CC:DD:EE:FF",
      "deviceName": "我的小智",
      "status": "online",  // online | offline
      "lastOnlineTime": 1713964800000
    }
  ]
}
```

#### 4. 获取设备配置

```
POST /xiaozhi/config/agent-models
Content-Type: application/json

请求体:
{
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "selectedModule": {
    "ASR": "default",
    "LLM": "openai",
    "TTS": "azure"
  }
}

响应:
{
  "code": 0,
  "data": {
    "ASR": {
      "provider": "funasr",
      "params": {...}
    },
    "LLM": {
      "provider": "openai",
      "model": "gpt-4",
      "params": {...}
    },
    "TTS": {
      "provider": "azure",
      "voice": "xiaoxiao",
      "params": {...}
    }
  }
}
```

### WebSocket 消息协议

#### 消息类型汇总

| 类型 | 方向 | 说明 |
|------|------|------|
| hello | 双向 | 握手消息 |
| audio | C→S | 音频数据 (二进制) |
| text | S→C | 文本消息 |
| listen | C→S | 切换监听模式 |
| abort | C→S | 中断当前对话 |
| ping/pong | 双向 | 心跳保活 |
| error | S→C | 错误提示 |

#### 错误码定义

| 错误码 | 说明 |
|--------|------|
| 1001 | 认证失败 |
| 1002 | 设备未绑定 |
| 1003 | 配置加载失败 |
| 1004 | 音频格式错误 |
| 1005 | 服务器内部错误 |

---

## 技术支持

如遇到问题,请通过以下方式获取帮助:

1. **查看日志**: 服务器日志通常包含详细错误信息
2. **接口文档**: 访问 `http://服务器IP:8002/xiaozhi/doc.html` 查看完整 API 文档
3. **GitHub Issues**: 在 [xiaozhi-esp32-server](https://github.com/xinnan-tech/xiaozhi-esp32-server) 提交问题

---

**文档版本**: v1.0
**最后更新**: 2026-04-24
**维护者**: xiaozhi-esp32-server 团队
