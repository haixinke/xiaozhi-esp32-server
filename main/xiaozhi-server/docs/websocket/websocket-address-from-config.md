# 重要更正：WebSocket 地址获取方式

## 🔧 问题说明

在之前的文档中，我们建议设备直接连接到硬编码的 WebSocket 地址：

```
ws://HOST:8000/xiaozhi/v1/?device-id=DEVICE_ID
```

**这种方式存在问题**：
- ❌ 不支持 Docker/公网部署
- ❌ 无法使用域名
- ❌ 配置变更需要重新烧录固件

## ✅ 正确做法：从配置接口获取

### 架构设计

系统设计中，`server.websocket` 配置项专门用于**向设备下发 WebSocket 连接地址**：

**配置文件** (`config.yaml`):
```yaml
server:
  ip: 0.0.0.0
  port: 8000
  # 向设备下发的 WebSocket 地址
  # Docker/公网部署时，应配置为实际访问地址
  websocket: ws://your-ip-or-domain:port/xiaozhi/v1/
```

### 完整流程

```
1️⃣ 设备注册
   POST /device/register
   └─ 获得 6 位验证码

2️⃣ 用户登录并绑定设备
   POST /user/login
   POST /device/bind/{agentId}/{deviceCode}

3️⃣ 获取配置（包含 WebSocket 地址）
   POST /config/agent-models
   {
     "macAddress": "AA:BB:CC:DD:EE:FF",
     "selectedModule": {...}
   }
   └─ 响应包含 server.websocket

4️⃣ 使用获取到的地址建立 WebSocket 连接
   ws://从配置获取的地址?device-id=XXX
```

### 配置接口响应示例

```json
{
  "code": 0,
  "data": {
    "server": {
      "websocket": "ws://192.168.1.100:8000/xiaozhi/v1/",
      "port": 8000,
      "http_port": 8003,
      "vision_explain": "http://192.168.1.100:8003/mcp/vision/explain"
    },
    "selected_module": {
      "ASR": {...},
      "LLM": {...},
      "TTS": {...}
    }
  }
}
```

## 🎯 优势

### 1. 支持多种部署场景

**局域网部署**:
```yaml
websocket: ws://192.168.1.100:8000/xiaozhi/v1/
```

**Docker 部署**:
```yaml
websocket: ws://localhost:8000/xiaozhi/v1/
```

**公网部署（域名）**:
```yaml
websocket: wss://xiaozhi.example.com/xiaozhi/v1/
```

**公网部署（IP+SSL）**:
```yaml
websocket: wss://123.45.67.89:8000/xiaozhi/v1/
```

### 2. 配置集中管理

- ✅ 修改服务器配置后，设备下次启动自动使用新地址
- ✅ 无需重新烧录固件
- ✅ 支持灰度发布和蓝绿部署

### 3. 多环境支持

```
开发环境: ws://dev.internal:8000/xiaozhi/v1/
测试环境: ws://test.internal:8000/xiaozhi/v1/
生产环境: wss://xiaozhi.production.com/xiaozhi/v1/
```

## 📝 设备端实现建议

### C 语言伪代码

```c
// 1. 获取配置
char* ws_url = get_websocket_url_from_config();

// 2. 构建完整连接地址
char full_url[256];
snprintf(full_url, sizeof(full_url),
         "%s?device-id=%s&client-id=esp32_001",
         ws_url, device_id);

// 3. 建立 WebSocket 连接
ws_connect(full_url);
```

### Python 伪代码

```python
# 1. 获取配置
config = requests.post(
    "http://server:8002/xiaozhi/config/agent-models",
    json={
        "macAddress": device_mac,
        "selectedModule": {...}
    }
).json()

# 2. 提取 WebSocket 地址
ws_url = config["data"]["server"]["websocket"]

# 3. 构建完整连接地址
full_url = f"{ws_url}?device-id={device_mac}"

# 4. 建立 WebSocket 连接
async with websockets.connect(full_url) as ws:
    ...
```

## ⚠️ 注意事项

### 1. 配置接口调用时机

- ✅ 设备启动时调用
- ✅ 定期刷新（如每小时）
- ✅ 网络切换时重新获取

### 2. 错误处理

- ✅ 如果配置接口失败，使用缓存的地址
- ✅ 如果完全没有缓存，提示用户检查网络
- ✅ 记录日志便于排查问题

### 3. 地址格式验证

从配置接口获取的地址应该：
- ✅ 包含协议头 (`ws://` 或 `wss://`)
- ✅ 包含完整路径 (`/xiaozhi/v1/`)
- ✅ 不包含查询参数（设备自己添加）

## 🔍 配置来源

### 开发环境

配置来自本地 `config.yaml`:
```yaml
server:
  websocket: ws://localhost:8000/xiaozhi/v1/
```

### 生产环境（使用智控台）

配置来自数据库，通过智控台管理界面配置：
- 登录智控台
- 系统设置 → 服务器配置
- 修改 WebSocket 地址

## 📚 更新的文档

- ✅ `docs/device-api-reference.md`
- ✅ `main/xiaozhi-server/docs/device-api-reference.md`

---

**更新日期**: 2026-04-24
**版本**: v1.1
**重要性**: 🔴 关键更新，所有设备端开发人员请注意
