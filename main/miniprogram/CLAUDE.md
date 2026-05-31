# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指引。

## 项目概述

这是 "xiaozhi-esp32-server" 的微信小程序 —— 一个 AI 驱动的语音助手设备。该小程序提供聊天界面，通过 WebSocket 连接到 Python 后端，与 ESP32 硬件设备进行交互。

**品牌标识**："完美女友" (Perfect Girlfriend) - 一个旨在提供温暖、情感连接和亲密互动的 AI 伴侣。

**设计系统**：[Ethereal Companion](./DESIGN.md) - 以樱花粉 (#864e5a) 和瓷白 (#fbf9f8) 为主题的治愈系亲密风格，采用玻璃态效果和极致圆润的设计。

## 架构

### 应用生命周期

```
App Launch → Silent Login → Agent Creation → Device Binding → Chat Interface
```

1. **静默登录** (`app.js`)：微信 `wx.login()` → 后端 `/wechat/login` → token + openid
2. **虚拟 MAC**：从 openid 生成，用于标识此小程序实例
3. **Agent 创建**：如果不存在则通过 `/agent` 端点自动创建 AI agent
4. **设备绑定**：检查 OTA 端点，如果存在激活码则自动绑定设备
5. **聊天就绪**：WebSocket URL + token 存储在 `globalData` 中供聊天页面使用

### 全局数据结构

```javascript
globalData: {
  token: null,           // 认证 token
  openid: null,          // 微信 openid
  virtualMAC: null,      // 设备标识符（从 openid 生成）
  wsUrl: null,           // WebSocket 服务器 URL
  wsToken: null,         // WebSocket 认证 token
  agentId: null,         // AI agent ID
  agentName: null,       // AI agent 名称（默认："翠花"）
  isDeviceBound: undefined // 设备绑定状态
}
```

### 页面架构

**两个主要页面**：
- `pages/index/` - 与 AI 伴侣的聊天界面
- `pages/settings/` - 设置页面（主题切换、关于、版本）

**核心组件**：
- `components/chat-bubble/` - 玻璃态风格的聊天消息气泡
- `components/voice-button/` - 语音输入按钮（当前未使用，纯文本模式）

**核心工具**：
- `utils/websocket.js` - WebSocket 管理器，支持自动重连、30s 心跳、状态机
- `utils/audio.js` - Opus 音频解码器/播放器，用于 TTS 播放
- `utils/request.js` - HTTP 请求封装，自动注入 token
- `utils/auth.js` - Token 管理和存储
- `utils/device.js` - 虚拟 MAC 生成和设备绑定（OTA 流程）

### 聊天流程架构

```
用户输入文本 → WebSocketManager.sendText()
     ↓
后端处理 (ASR → LLM → TTS)
     ↓
WebSocket 消息（流式）：
  - 'stt' 消息 → 用户语音识别结果，显示在聊天中
  - 'llm' 消息 → 流式 AI 响应，累积到 currentReply
  - 'tts' 消息 (state='start') → AI 开始说话
  - 'tts' 消息 (text='...') → 追加到 currentReply
  - 'tts' 消息 (state='stop') → 将 currentReply 移到 messages[]，清空缓冲区
  - 'audio' 消息 → 二进制 Opus 帧 → AudioManager.decode & play
```

**聊天状态机**：`idle` → `thinking` → `speaking` → `idle`

### WebSocket 消息类型

- `hello` - 连接建立，包含 sessionId
- `audio` - 用于 TTS 播放的二进制 Opus 音频帧
- `stt` - 语音转文本结果（用户的消息）
- `llm` - 流式 LLM 文本响应
- `tts` - TTS 状态变化和文本内容
- `goodbye` - 会话结束
- `iot` - IoT 设备命令

## 开发工作流

### 构建小程序

无需构建步骤 - 微信开发者工具会处理编译。

**项目配置** (`project.config.json`)：
- 启用 ES6 模块
- WXSS 使用 PostCSS
- 生产环境启用代码压缩
- 上传 source maps 用于调试

### 在微信开发者工具中打开

```bash
# 在微信开发者工具中打开项目目录
# 文件 → 打开 → 选择 /Users/minwang/codes/github/xiaozhi-esp32-server/main/miniprogram
```

**重要提示**：确保 `project.private.config.json` 中配置了你的 appid。

### 测试聊天界面

1. **前置条件**：后端服务器必须运行（`xiaozhi-server` 在 8000 端口）
2. **设备绑定**：需要一个真实的 ESP32 设备或使用 demo-web 模拟器
3. **连接流程**：点击"召唤"按钮建立 WebSocket 连接
4. **文本输入**：在药丸形输入框中输入并点击"发送"

### 常见开发任务

**添加新的聊天消息类型**：
1. 在 `pages/index/index.js` → `_handleWSMessage()` 中添加消息处理器
2. 处理消息类型并更新 `messages` 数组
3. 使用 `setData()` 触发 UI 更新并滚动到底部

**修改 UI 样式**：参考 [DESIGN.md](./DESIGN.md) 获取完整的设计系统规范。

**调试 WebSocket 问题**：
- 检查控制台中的 WebSocketManager 日志
- 验证 `globalData.wsUrl` 和 `globalData.wsToken` 已设置
- 连接状态转换：`disconnected` → `connecting` → `connected`
- 自动重连尝试：最多 5 次，指数退避

## UI 设计

**Ethereal Companion 设计系统**：完整的设计规范（颜色、排版、组件模式、玻璃态样式等）请参考 [DESIGN.md](./DESIGN.md)。

## 重要架构说明

### WebSocketManager 设计

- **状态隔离**：每个管理器实例使用自己的 SocketTask（无全局回调污染）
- **认证**：Token 通过 URL 查询传递（小程序无法设置 WebSocket 头）
- **心跳**：30s PING 间隔保持连接活跃
- **自动重连**：指数退避（1s → 2s → 4s → 8s → 15s，最多 5 次尝试）
- **优雅关闭**：页面卸载前使用 `disconnect()` 防止内存泄漏

### 聊天状态管理

- **booting 标志**：控制是否显示加载 spinner（当前为 `false` 以实现即时访问）
- **connectionState**：`disconnected` | `connecting` | `connected`
- **chatState**：`idle` | `thinking` | `speaking`
- **currentReply**：流式 LLM 响应的缓冲区，在提交到 messages 数组之前

### 音频播放

- **Opus 解码**：由 `AudioManager` 工具处理
- **帧累积**：接收 `audio` 消息 → `appendOpusFrame()` → 准备好时播放
- **播放队列**：支持连续 TTS 流式传输

### 设备绑定流程

1. **虚拟 MAC**：首次登录时从 openid 生成（持久化在存储中）
2. **OTA 检查**：`checkOrRegisterDevice(mac)` → 返回激活码 OR websocket 信息
3. **绑定**：如果激活码存在，`completeDeviceBinding(mac, agentId, code)` → wsUrl + wsToken
4. **自动绑定**：如果设备未绑定，应用启动时自动进行设备绑定

## 常见问题与解决方案

**"启动失败"**：
- 检查后端服务器是否运行
- 验证网络连接
- 检查控制台中 app.js 初始化流程的具体错误

**"召唤"按钮无响应**：
- 验证 app.js 中 `globalData.wsUrl` 已设置
- 检查设备绑定是否成功完成
- 在控制台中查找 WebSocketManager 错误

**聊天消息不显示**：
- 验证 WebSocket 连接状态（应为 "connected"）
- 检查控制台是否有消息解析错误
- 确保 `_handleWSMessage()` 有该消息类型的 case 处理器

**玻璃态不可见**：
- 确保设置了 `backdrop-filter: blur()`（需要 iOS 9+ / Android 9+）
- 检查背景具有透明度（< 1.0 不透明度）
- 验证边框颜色不太暗（应为半透明白色）

**小程序加载失败**：
- 检查 `project.private.config.json` 有正确的 appid
- 验证所有引用的图片存在（图标、头像、tabbar）
- 检查控制台是否有 WXML/WXSS/JS 语法错误

## 文件组织

```
miniprogram/
├── app.js                  # 应用生命周期和初始化
├── app.json                # 全局配置（navigationBar、tabBar）
├── app.wxss                 # 全局样式
├── DESIGN.md               # 设计系统规范
├── UI_REDESIGN_SUMMARY.md  # 最近 UI 重设计总结
├── project.config.json      # 微信项目配置
├── project.private.config.json # 私有配置（appid 等）
├── components/              # 可复用组件
│   ├── chat-bubble/        # 聊天消息气泡
│   └── voice-button/       # 语音输入按钮（未使用）
├── pages/                   # 页面模块
│   ├── index/              # 聊天页面（主界面）
│   └── settings/           # 设置页面
├── utils/                   # 工具和管理器
│   ├── audio.js            # Opus 音频播放
│   ├── auth.js             # Token 管理
│   ├── device.js           # 设备绑定和 OTA
│   ├── request.js          # HTTP 请求
│   └── websocket.js        # WebSocket 管理器
└── images/                 # 静态资源
    ├── avatar-default.png  # 默认 AI 头像
    ├── beijing.png         # 背景图片
    ├── icons/              # UI 图标（已弃用，大多未使用）
    └── tabbar/             # 底部导航图标
```

## 品牌语调

**"完美女友"** 定位为：
- **温暖亲密**：不仅是工具，更是伴侣
- **情感智能**：记住上下文，用同理心回应
- **始终在场**：随时可用（无冷启动屏幕）
- **温柔体贴**：每次交互都感觉精心呵护

编写 UI 文本或错误消息时：
- ❌ "系统错误"
- ✅ "翠花遇到了一些问题，请稍后再试"

- ❌ "连接中..."
- ✅ "正在建立连接..."

- ❌ "发送失败"
- ✅ "消息发送失败，请重试"
