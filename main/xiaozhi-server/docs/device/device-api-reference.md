# 设备端 API 快速参考

> xiaozhi-esp32-server 设备接口速查表

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
      "ip": "0.0.0.0",
      "port": 8000
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

**示例**:
```
ws://192.168.1.100:8000/xiaozhi/v1/?device-id=AA:BB:CC:DD:EE:FF&client-id=esp32_001
```

### 连接参数 (Query String)

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| device-id | string | ✅ | 设备 ID (MAC 地址) |
| client-id | string | ❌ | 客户端标识 |
| authorization | string | ❌ | 认证 Token |

### 示例

```
ws://192.168.1.100:8000/xiaozhi/v1/?device-id=AA:BB:CC:DD:EE:FF&client-id=esp32_001
```

---

## REST API (认证、设备注册与配置)

### 基础 URL

```
http://HOST:8002/xiaozhi
```

### 认证流程

#### 1. 获取公共配置 (含 SM2 公钥)

```http
GET /user/pub-config

响应:
{
  "code": 0,
  "data": {
    "sm2PublicKey": "YOUR_SM2_PUBLIC_KEY",
    "enableMobileRegister": true,
    "allowUserRegister": true
  }
}
```

#### 2. 获取验证码

```http
GET /user/captcha?uuid=UUID

响应: 图片 (PNG 格式)

参数:
- uuid: 客户端生成的唯一标识符
```

#### 3. 用户登录

```http
POST /user/login
Content-Type: application/json

{
  "username": "admin",
  "password": "SM2加密后的密码",
  "captchaId": "UUID",
  "captcha": "验证码内容"
}

响应:
{
  "code": 0,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expire": 7200,
    "clientHash": "abc123"
  }
}
```

**注意**: `data.token` 即为后续接口需要的 `access_token`，有效期 2 小时。

### 设备注册与管理

#### 4. 设备注册

```http
POST /device/register
Content-Type: application/json

{
  "macAddress": "AA:BB:CC:DD:EE:FF"
}

响应:
{
  "code": 0,
  "data": "123456"  // 6位验证码
}
```

#### 5. 绑定设备

```http
POST /device/bind/{agentId}/{deviceCode}
Authorization: Bearer {access_token}

路径参数:
- agentId: 智能体 ID
- deviceCode: 6 位验证码

响应:
{
  "code": 0,
  "msg": "success"
}
```

#### 6. 获取设备配置 (含 WebSocket 地址)

```http
POST /config/agent-models
Content-Type: application/json

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
    "server": {
      "websocket": "ws://192.168.1.100:8000/xiaozhi/v1/",
      "ip": "0.0.0.0",
      "port": 8000,
      "http_port": 8003,
      "vision_explain": "http://192.168.1.100:8003/mcp/vision/explain"
    },
    "ASR": {...},
    "LLM": {...},
    "TTS": {...}
  }
}
```

**注意**:
- `server.websocket`: WebSocket 连接地址，**必须从此配置获取**
- `server.port`: WebSocket 端口
- `server.http_port`: HTTP 服务端口（用于 OTA 和视觉分析）

---

## WebSocket 消息协议

### 1. Hello 握手 (客户端 → 服务器)

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
    "camera": false
  }
}
```

### 2. Hello 响应 (服务器 → 客户端)

```json
{
  "type": "hello",
  "session_id": "abc123",
  "server_time": 1713964800000,
  "welcome_message": "您好,我是小智助手"
}
```

### 3. 切换监听模式 (客户端 → 服务器)

```json
{
  "type": "listen",
  "mode": "auto"  // "auto" | "manual"
}
```

### 4. 中断对话 (客户端 → 服务器)

```json
{
  "type": "abort"
}
```

### 5. 心跳保活

**Ping** (客户端 → 服务器):
```json
{
  "type": "ping"
}
```

**Pong** (服务器 → 客户端):
```json
{
  "type": "pong",
  "timestamp": 1713964800000
}
```

### 6. 文本消息 (服务器 → 客户端)

```json
{
  "type": "text",
  "content": "今天天气很好"
}
```

### 7. 错误消息 (服务器 → 客户端)

```json
{
  "type": "error",
  "code": 1001,
  "message": "认证失败"
}
```

**错误码**:
- `1001`: 认证失败
- `1002`: 设备未绑定
- `1003`: 配置加载失败
- `1004`: 音频格式错误
- `1005`: 服务器内部错误

---

## 音频数据格式

### 编码参数

- **编码**: Opus
- **采样率**: 24000 Hz
- **比特率**: 24000 bps
- **声道**: 单声道 (1)
- **帧大小**: 20ms (960 samples)

### 发送格式

**客户端 → 服务器** (语音):
```
[Opus Packet 1][Opus Packet 2][Opus Packet 3]...
```

**服务器 → 客户端** (TTS):
```
[FIRST Marker][Opus Packet 1][Opus Packet 2]...[LAST Marker]
```

---

## 典型交互流程

```
客户端                              服务器
  │                                   │
  │───── WebSocket 连接 ──────────────→│
  │                                   │
  │───── Hello 消息 ──────────────────→│
  │                                   │
  │←──── Hello 响应 + session_id ──────│
  │                                   │
  │───── Opus 音频数据 ───────────────→│
  │      (持续发送)                    │
  │                                   │
  │←──── Text 消息 "今天天气很好" ──────│
  │←──── Opus TTS 音频 ─────────────────│
  │                                   │
  │───── Ping (每30秒) ───────────────→│
  │←──── Pong ─────────────────────────│
  │                                   │
  │───── Abort (可选) ─────────────────→│
  │                                   │
  │───── Close ────────────────────────→│
```

---

## 配置参数说明

### selected_module 对象

| 字段 | 可选值 | 说明 |
|------|--------|------|
| ASR | `default`, `funasr` | 语音识别引擎 |
| LLM | `openai`, `azure`, `claude` | 大模型提供商 |
| TTS | `azure`, `edge`, `pyttsx3` | 语音合成引擎 |
| VAD | `silero`, `webrtc` | 语音活动检测 |
| Intent | `nointent`, `openai` | 意图识别 |
| Memory | `powermem`, `default` | 记忆存储 |

---

## 调试建议

### 1. 使用浏览器测试

启动测试服务器:
```bash
cd main/xiaozhi-server/test
python -m http.server 8006
```

浏览器访问: `http://HOST:8006/test_page.html`

### 2. 查看服务器日志

```bash
# 开发环境
tail -f main/xiaozhi-server/logs/app.log

# 过滤特定设备
grep "DEVICE_ID" logs/app.log

# 查看错误
grep "ERROR" logs/app.log
```

### 3. 使用 Wireshark 抓包

过滤器:
```
tcp.port == 8000 && websocket
```

---

## 完整示例 (C 语言伪代码)

```c
#include <websockets.h>

void on_receive(char* data, int len) {
    // 处理服务器响应
    if (is_json(data)) {
        json_msg = parse_json(data);
        if (json_msg.type == "hello") {
            printf("Connected: %s\n", json_msg.session_id);
        }
    } else {
        // Opus 音频数据,解码播放
        opus_decode(data, len);
    }
}

void main() {
    // 1. 建立 WebSocket 连接
    ws = ws_connect(
        "ws://192.168.1.100:8000/xiaozhi/v1/?device-id=AA:BB:CC:DD:EE:FF"
    );

    // 2. 发送 Hello 消息
    hello_msg = {
        "type": "hello",
        "audio_params": {
            "format": "opus",
            "sample_rate": 24000,
            "channels": 1
        },
        "features": {
            "mcp": false
        }
    };
    ws_send(ws, json_encode(hello_msg));

    // 3. 开始录音并发送
    while (recording) {
        audio = record_audio(20);  // 录制 20ms
        opus_data = opus_encode(audio, 24000);
        ws_send_binary(ws, opus_data);
    }

    // 4. 接收响应
    ws_on_message(ws, on_receive);

    // 5. 心跳保活
    while (connected) {
        sleep(30);
        ws_send(ws, "{\"type\":\"ping\"}");
    }

    ws_close(ws);
}
```

---

**版本**: v1.0
**更新**: 2026-04-24
