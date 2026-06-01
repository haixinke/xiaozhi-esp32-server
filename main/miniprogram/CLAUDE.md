# CLAUDE.md

## 项目概述

"完美女友"微信小程序 - AI 伴侣聊天界面，通过 WebSocket 连接到 Python 后端。

- **品牌**：温暖、情感连接和亲密互动的 AI 伴侣
- **设计系统**：[Ethereal Companion](./DESIGN.md) - 樱花粉 (#864e5a) + 瓷白 (#fbf9f8)，玻璃态效果
- **后端连接**：WebSocket 连接 `xiaozhi-server` (端口 8000)

## 核心架构

### 应用流程

```
静默登录 → Agent 创建 → 设备绑定 → 聊天就绪
```

### 全局数据 (app.js)

```javascript
globalData: {
  token, openid, virtualMAC,      // 认证与设备标识
  wsUrl, wsToken,                  // WebSocket 连接
  agentId, agentName,      // AI agent 信息
  isDeviceBound                    // 设备绑定状态
}
```

### 目录结构

```
miniprogram/
├── pages/
│   ├── index/         # 聊天页面（主界面）
│   └── settings/      # 设置页面
├── components/
│   ├── chat-bubble/   # 聊天消息气泡
│   └── voice-button/  # 语音输入（未使用）
├── utils/
│   ├── websocket.js   # WebSocket 管理器（自动重连、心跳）
│   ├── audio.js       # Opus 音频播放
│   ├── request.js     # HTTP 请求
│   ├── auth.js        # Token 管理
│   └── device.js      # 设备绑定
└── images/
    ├── avatar-default.png
    ├── beijing.png
    └── tabbar/
```

## 聊天流程

```
用户输入 → WebSocketManager.sendText()
         ↓
后端处理 (ASR → LLM → TTS)
         ↓
WebSocket 消息流：
  - 'stt'   → 用户消息显示
  - 'llm'   → AI 文本响应（流式）
  - 'tts'   → TTS 状态和文本
  - 'audio' → Opus 音频帧播放
```

### WebSocket 消息类型

- `hello` - 连接建立
- `audio` - Opus 音频帧
- `stt` - 语音识别结果
- `llm` - 流式文本响应
- `tts` - TTS 状态和文本
- `goodbye` - 会话结束
- `iot` - IoT 设备命令

### 聊天状态机

`idle` → `thinking` → `speaking` → `idle`

## 核心设计

### WebSocketManager (`utils/websocket.js`)

- 状态隔离：每个实例独立 SocketTask
- 认证：Token 通过 URL 查询传递
- 心跳：30s PING
- 自动重连：指数退避（1s → 2s → 4s → 8s → 15s，最多 5 次）
- 优雅关闭：页面卸载前 `disconnect()`

### 聊天状态管理

- `connectionState`: `disconnected` | `connecting` | `connected`
- `chatState`: `idle` | `thinking` | `speaking`
- `currentReply`: 流式 LLM 响应缓冲区

### 设备绑定

1. 直接使用 openid 作为设备标识
2. `checkOrRegisterDevice(mac)` → 激活码 OR websocket 信息
3. 如有激活码，`completeDeviceBinding()` 完成
4. 应用启动时自动绑定

## 开发指南

### 构建

无需构建步骤 - 微信开发者工具自动编译。

**配置文件**：`project.config.json`、`project.private.config.json` (appid)

### 调试聊天

1. 后端运行：`xiaozhi-server` 端口 8000
2. 设备绑定
3. 点击"召唤"按钮连接
4. 文本输入发送

### 常见任务

**添加消息类型**：
1. `pages/index/index.js` → `_handleWSMessage()` 添加 case
2. 处理并更新 `messages` 数组
3. `setData()` 触发 UI 更新

**修改样式**：参考 [DESIGN.md](./DESIGN.md)

**调试 WebSocket**：

- 检查 WebSocketManager 日志
- 验证 `globalData.wsUrl` 和 `wsToken` 已设置
- 连接状态：`disconnected` → `connecting` → `connected`

## 常见问题

| 问题 | 检查项 |
|------|--------|
| 启动失败 | 后端运行、网络连接、app.js 初始化错误 |
| 召唤无响应 | `globalData.wsUrl`、设备绑定、WebSocketManager 错误 |
| 消息不显示 | 连接状态、消息解析、`_handleWSMessage()` 处理器 |
| 玻璃态不可见 | `backdrop-filter: blur()`、透明度、边框颜色 |
| 加载失败 | appid、图片资源、语法错误 |

## 品牌语调

**完美女友定位**：温暖亲密、情感智能、始终在场、温柔体贴
