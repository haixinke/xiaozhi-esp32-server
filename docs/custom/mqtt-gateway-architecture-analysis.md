# MQTT 网关架构与网络切换行为分析

## 1. 概述

`xiaozhi-esp32-server` 支持通过 [xiaozhi-mqtt-gateway](https://github.com/xinnan-tech/xiaozhi-mqtt-gateway) 为 ESP32 设备提供 MQTT + UDP 的接入方式。本报告基于以下源码和文档整理：

- `main/xiaozhi-server/core/connection.py`
- `main/xiaozhi-server/core/handle/sendAudioHandle.py`
- `main/xiaozhi-server/core/api/ota_handler.py`
- `main/xiaozhi-server/core/providers/memory/base.py`
- `main/manager-api/src/main/java/xiaozhi/modules/device/service/impl/DeviceServiceImpl.java`
- `docs/mqtt-gateway-integration.md`
- `xiaozhi-mqtt-gateway/app.js`
- `xiaozhi-mqtt-gateway/mqtt-protocol.js`
- `xiaozhi-mqtt-gateway/utils/call-manager.js`

## 2. 整体架构

```text
┌─────────────┐      MQTT(1883)      ┌─────────────────────┐      WebSocket       ┌──────────────────┐
│  ESP32 设备  │ ◄──────────────────► │  xiaozhi-mqtt-gateway │ ◄──────────────────► │  xiaozhi-server  │
│             │      UDP(8884)       │   (Node.js 网关)      │  ws://...:8000/    │  (Python AI)     │
└─────────────┘                      │                     │   ?from=mqtt_gateway │                  │
                                     └─────────────────────┘                      └──────────────────┘
```

- **MQTT 端口 1883**：设备与网关之间的控制信令。
- **UDP 端口 8884**：设备与网关之间的音频数据传输，低延迟。
- **网关管理 API 端口 8007**：智控台 / manager-api 向网关查询设备状态、下发指令。
- **WebSocket 端口 8000**：网关作为客户端，向后端 `xiaozhi-server` 建立 WebSocket 连接，把设备的音频 / 信令转发给 Python 服务。

后端通过 `request_path.endswith("?from=mqtt_gateway")` 识别该连接来自 MQTT 网关，并对音频包做 16 字节头部解析和乱序重排。

## 3. 配置方式

### 3.1 MQTT 网关侧

编辑 `config/mqtt.json`：

```json
{
  "production": {
    "chat_servers": [
      "ws://xiaozhi-server-1:8000/xiaozhi/v1/?from=mqtt_gateway",
      "ws://xiaozhi-server-2:8000/xiaozhi/v1/?from=mqtt_gateway",
      "ws://xiaozhi-server-3:8000/xiaozhi/v1/?from=mqtt_gateway"
    ]
  },
  "debug": false,
  "max_mqtt_payload_size": 8192
}
```

在项目根目录创建 `.env`：

```bash
PUBLIC_IP=你的公网IP或域名
MQTT_PORT=1883
UDP_PORT=8884
API_PORT=8007
MQTT_SIGNATURE_KEY=复杂密钥（8位以上，含大小写）
SERVER_SECRET=与智控台或 xiaozhi-server 的 auth_key 保持一致
```

### 3.2 xiaozhi-server 侧

单模块部署时，修改 `data/.config.yaml`：

```yaml
server:
  mqtt_gateway: "192.168.0.7:1883"
  mqtt_signature_key: "你的MQTT签名密钥"
  udp_gateway: "192.168.0.7:8884"
```

全模块部署时，在智控台「参数管理」中配置：

- `server.mqtt_gateway`
- `server.mqtt_signature_key`
- `server.udp_gateway`
- `server.mqtt_manager_api`

### 3.3 设备侧

设备通过 OTA 接口自动获取 MQTT 配置。OTA 返回示例：

```json
{
  "websocket": { "url": "ws://..." },
  "mqtt": {
    "endpoint": "192.168.0.7:1883",
    "client_id": "GID_default@@@11_22_33_44_55_66@@@11_22_33_44_55_66",
    "username": "...",
    "password": "...",
    "publish_topic": "device-server",
    "subscribe_topic": "devices/p2p/11_22_33_44_55_66"
  }
}
```

## 4. 网络切换行为分析

### 4.1 切换 Wi-Fi 时 MQTT 连接会断吗？

**会断。** MQTT 底层是 TCP。设备从 Wi-Fi-A 切换到 Wi-Fi-B 时：

- IP 地址变化；
- 旧的 TCP socket 对端不可达；
- MQTT 客户端会触发 reconnect，重新连接 MQTT broker（即网关的 1883 端口）。

### 4.2 重连后还是原来的 xiaozhi-server 实例吗？

**不一定。** 这取决于 MQTT 网关内部的路由策略。

在 `xiaozhi-mqtt-gateway/app.js` 的 `WebSocketBridge.initializeChatServer()` 中：

```javascript
initializeChatServer() {
    const devMacAddresss = configManager.get('development')?.mac_addresss || [];
    let chatServers;
    if (devMacAddresss.includes(this.macAddress)) {
        chatServers = configManager.get('development')?.chat_servers;
    } else {
        chatServers = configManager.get('production')?.chat_servers;
    }
    if (!chatServers) {
        throw new Error(`未找到 ${this.macAddress} 的聊天服务器`);
    }
    this.chatServer = chatServers[Math.floor(Math.random() * chatServers.length)];
}
```

关键结论：

- **没有一致性哈希**；
- **没有按 device_id / MAC 做粘性路由**；
- **每次新建 `WebSocketBridge` 时都是纯随机选择后端**。

因此，设备切换 Wi-Fi 后重连，很可能被分配到三台 xiaozhi-server 中的任意一台，无法保证还是原来的实例。

### 4.3 上下文会丢失吗？

要分两层看：

| 类型 | 是否丢失 | 说明 |
|------|---------|------|
| 内存中的当前对话 `dialogue` | 会丢失 | `ConnectionHandler` 是连接级内存对象，断连即释放 |
| 长期记忆 | 不会丢失 | 记忆模块按 `device_id` 作为 `role_id` 持久化存储 |

后端在连接关闭时保存记忆：

```python
await self.memory.save_memory(self.dialogue.dialogue, self.session_id)
```

记忆初始化时使用设备 MAC 作为身份标识：

```python
self.memory.init_memory(
    role_id=self.device_id,
    llm=self.llm,
    ...
)
```

所以切换网络后，虽然当前对话历史会中断，但历史记忆可以通过 `device_id` 从记忆模块恢复。

## 5. 为什么还需要 MQTT 网关？

虽然 MQTT 网关无法保证后端实例粘性，但它在以下场景有明显优势：

| 维度 | 设备直连 WebSocket | 设备 → MQTT 网关 → WebSocket |
|------|-------------------|---------------------------|
| 网络链路 | 短，一跳 | 长，两跳 |
| 音频传输 | TCP，可能有队头阻塞 | UDP，低延迟 |
| 移动网络适应性 | 一般，需自行处理重连 | 好，MQTT 原生支持重连 / 心跳 / 遗嘱 |
| NAT / 防火墙穿透 | 依赖 8000 / 443 | 1883 / UDP 8884 可能更开放 |
| 后端实例粘性 | 无 | 无 |
| 上下文恢复 | 都靠 `device_id` + Memory Provider | 都靠 `device_id` + Memory Provider |
| 设备端复杂度 | 需自行处理心跳、重连、二进制帧 | 复用成熟 MQTT 客户端 |
| 设备间通话 | 需要额外信令通道 | MQTT 主题天然支持 |
| 部署复杂度 | 低 | 高（多一个网关服务） |

核心结论：

> MQTT 网关不是为了"把设备粘到同一台后端"而存在的。它的价值在于：用 MQTT 做可靠信令、用 UDP 做低延迟音频、用成熟 IoT 协议应对复杂网络环境。

## 6. 如果要实现"切换网络后连到同一台后端"

当前架构无法直接满足。需要额外改造：

### 方案一：MQTT 网关层做一致性哈希

修改 `WebSocketBridge.initializeChatServer()`，按 `macAddress` 哈希选择后端：

```javascript
const hash = this.macAddress.split('').reduce((h, c) => h * 31 + c.charCodeAt(0), 0);
this.chatServer = chatServers[Math.abs(hash) % chatServers.length];
```

限制：只能在单网关实例内生效。如果 MQTT 网关本身是多实例部署，还需要在网关前再做一层一致性哈希负载均衡。

### 方案二：分布式对话状态共享

把 `ConnectionHandler` 中的 `dialogue` 当前对话历史也持久化到 Redis / 数据库，任何 xiaozhi-server 实例都能恢复完整上下文。

### 方案三：单实例部署

只部署一台 xiaozhi-server，自然不存在实例切换问题，但会失去高可用能力。

## 7. 总结

1. MQTT 网关通过 MQTT + UDP 桥接设备与 xiaozhi-server，音频走 UDP 以降低延迟。
2. 设备切换 Wi-Fi 后，MQTT TCP 连接会断开并重连。
3. MQTT 网关**没有实现一致性哈希**，后端 xiaozhi-server 实例是**随机分配**的。
4. 切换网络后无法保证还是原来的 xiaozhi-server 实例，内存中的当前对话会丢失。
5. 长期上下文通过 `device_id` 绑定的 Memory Provider 恢复，不依赖同一后端进程。
6. MQTT 网关的价值在于网络可达性、移动性、低延迟音频和设备管理，而不是后端实例粘性。
7. 如果需要切换网络后保持同一后端或无缝续聊同一轮对话，需要额外实现一致性哈希或分布式会话共享。
