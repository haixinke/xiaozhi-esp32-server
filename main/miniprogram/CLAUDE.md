# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the WeChat Mini Program for "xiaozhi-esp32-server" - an AI-powered voice assistant device. The mini program provides a chat interface for interacting with the ESP32 hardware device via WebSocket connection to the Python backend.

**Brand Identity**: "完美女友" (Perfect Girlfriend) - An AI companion designed to provide warmth, emotional connection, and intimate interaction.

**Design System**: [Ethereal Companion](./DESIGN.md) - A healing intimacy theme with cherry blossom pink (#864e5a) and porcelain white (#fbf9f8) colors, glassmorphism effects, and extreme roundedness.

## Architecture

### Application Lifecycle

```
App Launch → Silent Login → Agent Creation → Device Binding → Chat Interface
```

1. **Silent Login** (`app.js`): WeChat `wx.login()` → backend `/wechat/login` → token + openid
2. **Virtual MAC**: Generated from openid to identify this mini program instance
3. **Agent Creation**: Auto-create AI agent if none exists via `/agent` endpoint
4. **Device Binding**: Check OTA endpoint, auto-bind device if activation code exists
5. **Chat Ready**: WebSocket URL + token stored in `globalData` for chat page to use

### Global Data Structure

```javascript
globalData: {
  token: null,           // Authentication token
  openid: null,          // WeChat openid
  virtualMAC: null,      // Device identifier (generated from openid)
  wsUrl: null,           // WebSocket server URL
  wsToken: null,         // WebSocket authentication token
  agentId: null,         // AI agent ID
  agentName: null,       // AI agent name (default: "翠花")
  isDeviceBound: undefined // Device binding status
}
```

### Page Architecture

**Two main pages**:
- `pages/index/` - Chat interface with AI companion
- `pages/settings/` - Settings page (theme toggle, about, version)

**Key components**:
- `components/chat-bubble/` - Chat message bubble with glassmorphism style
- `components/voice-button/` - Voice input button (currently unused, text-only mode)

**Core utilities**:
- `utils/websocket.js` - WebSocket manager with auto-reconnect, 30s heartbeat, state machine
- `utils/audio.js` - Opus audio decoder/player for TTS playback
- `utils/request.js` - HTTP request wrapper with token injection
- `utils/auth.js` - Token management and storage
- `utils/device.js` - Virtual MAC generation and device binding (OTA flow)

### Chat Flow Architecture

```
User Input Text → WebSocketManager.sendText()
     ↓
Backend Processing (ASR → LLM → TTS)
     ↓
WebSocket Messages (streaming):
  - 'stt' message → User speech recognized, show in chat
  - 'llm' message → Streaming AI response, accumulate in currentReply
  - 'tts' message (state='start') → AI starts speaking
  - 'tts' message (text='...') → Append to currentReply
  - 'tts' message (state='stop') → Move currentReply to messages[], clear buffer
  - 'audio' message → Binary Opus frame → AudioManager.decode & play
```

**Chat State Machine**: `idle` → `thinking` → `speaking` → `idle`

### WebSocket Message Types

- `hello` - Connection established, includes sessionId
- `audio` - Binary Opus audio frame for TTS playback
- `stt` - Speech-to-text result (user's message)
- `llm` - Streaming LLM text response
- `tts` - TTS state changes with text content
- `goodbye` - Session ended
- `iot` - IoT device commands

## Development Workflow

### Building the Mini Program

No build step required - WeChat Developer Tools handles compilation.

**Project configuration** (`project.config.json`):
- ES6 modules enabled
- PostCSS for WXSS
- Minification enabled for production
- Source maps uploaded for debugging

### Opening in WeChat Developer Tools

```bash
# Open project directory in WeChat Developer Tools
# File → Open → Select /Users/minwang/codes/github/xiaozhi-esp32-server/main/miniprogram
```

**Important**: Ensure `project.private.config.json` has your appid configuration.

### Testing Chat Interface

1. **Prerequisites**: Backend server must be running (`xiaozhi-server` on port 8000)
2. **Device binding**: Either have a real ESP32 device or use the demo-web simulator
3. **Connection flow**: Click "召唤" (Summon) button to establish WebSocket connection
4. **Text input**: Type in the pill-shaped input field and click "发送" (Send)

### Common Development Tasks

**Add a new chat message type**:
1. Add message handler in `pages/index/index.js` → `_handleWSMessage()`
2. Process the message type and update `messages` array
3. Use `setData()` to trigger UI update and scroll to bottom

**Modify glassmorphism style**:
- Update `components/chat-bubble/chat-bubble.wxss` for chat bubbles
- Update `pages/index/index.wxss` for page-level glassmorphism
- Key pattern: `background: rgba(255, 255, 255, 0.75)` + `backdrop-filter: blur(40rpx)`

**Update theme colors**:
- Reference `DESIGN.md` for color tokens
- Primary: `#864e5a` (Cherry Blossom Pink)
- Surface: `#fbf9f8` (Porcelain White)
- Surface Container Low: `#f6f3f2` (slightly darker for contrast)

**Debug WebSocket issues**:
- Check console for WebSocketManager logs
- Verify `globalData.wsUrl` and `globalData.wsToken` are set
- Connection state transitions: `disconnected` → `connecting` → `connected`
- Auto-reconnect attempts: up to 5 times with exponential backoff

## Design System: Ethereal Companion

### Core Principles

- **Healing Intimacy**: Warm, emotional, safe sanctuary feeling
- **Minimalism + Glassmorphism**: Airy, translucent, layered elements
- **Cloud-like Softness**: No sharp edges, aggressive transitions, or dark contrasts
- **Negative Space**: Generous spacing to let UI "breathe"

### Color Palette

```css
/* Primary Colors */
--primary: #864e5a;              /* Cherry Blossom Pink */
--primary-container: #ffb7c5;    /* Light Pink */
--on-primary-container: #7b4551; /* Dark Pink for text */

/* Surface Colors */
--surface: #fbf9f8;              /* Porcelain White - main background */
--surface-container-low: #f6f3f2; /* Slightly darker for layering */
--surface-container: #f0eded;    /* Card backgrounds */

/* Text Colors */
--on-surface: #1b1c1c;          /* Charcoal Grey - primary text */
--on-surface-variant: #514345;   /* Medium Grey - secondary text */
--outline: #837375;              /* Border color */
```

### Glassmorphism Pattern

```css
/* Standard Glass Card */
.glass-card {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border: 1rpx solid rgba(255, 255, 255, 0.8);
  border-radius: 32rpx;
  box-shadow:
    0 8rpx 32rpx rgba(134, 78, 90, 0.08),
    0 0 20rpx rgba(255, 183, 193, 0.12);
}
```

### Typography Scale (WeChat Mini Program rpx units)

- **Headline**: 56rpx (28px), weight 700
- **Body**: 32rpx (16px), weight 400-500, line-height 1.6
- **Label**: 28rpx (14px), weight 500, letter-spacing 0.5rpx
- **Caption**: 24rpx (12px), weight 400

### Shape System

- **Pill buttons**: `border-radius: 100rpx`
- **Cards/Containers**: `border-radius: 32rpx`
- **Small elements**: `border-radius: 16rpx`
- **No sharp edges**: Avoid 90° angles

### Component Patterns

**Chat Bubbles**:
- AI messages: Glassmorphic white
- User messages: Soft pink tint (`rgba(255, 183, 193, 0.8)`)
- Minimum 32rpx border-radius
- One straight corner (user: bottom-right, AI: bottom-left)

**Input Fields**:
- Pill-shaped (`border-radius: 100rpx`)
- Glassmorphic background
- Cherry Blossom Pink glow on focus
- `box-shadow: 0 0 20rpx rgba(255, 183, 193, 0.4)`

**Buttons**:
- Primary: Solid Cherry Blossom Pink with glow
- Secondary: Ghost style with pink border
- All buttons: Pill-shaped

## Important Architecture Notes

### WebSocketManager Design

- **State isolation**: Each manager instance uses its own SocketTask (no global callback pollution)
- **Authentication**: Token passed via URL query (miniprogram cannot set WebSocket headers)
- **Heartbeat**: 30s PING interval to keep connection alive
- **Auto-reconnect**: Exponential backoff (1s → 2s → 4s → 8s → 15s, max 5 attempts)
- **Graceful shutdown**: Use `disconnect()` before page unload to prevent memory leaks

### Chat State Management

- **booting flag**: Controls whether to show loading spinner (currently `false` for instant access)
- **connectionState**: `disconnected` | `connecting` | `connected`
- **chatState**: `idle` | `thinking` | `speaking`
- **currentReply**: Buffer for streaming LLM response before committing to messages array

### Audio Playback

- **Opus decoding**: Handled by `AudioManager` utility
- **Frame accumulation**: `audio` messages received → `appendOpusFrame()` → play when ready
- **Playback queue**: Supports continuous TTS streaming

### Device Binding Flow

1. **Virtual MAC**: Generated from openid on first login (persists in storage)
2. **OTA Check**: `checkOrRegisterDevice(mac)` → returns activation code OR websocket info
3. **Binding**: If activation code exists, `completeDeviceBinding(mac, agentId, code)` → wsUrl + wsToken
4. **Auto-bind**: Device binding happens automatically on app launch if device is unbound

## Common Issues & Solutions

**"启动失败" (Startup failed)**:
- Check backend server is running
- Verify network connectivity
- Check console for specific error in app.js init flow

**"召唤" button does nothing**:
- Verify `globalData.wsUrl` is set in app.js
- Check if device binding completed successfully
- Look for WebSocketManager errors in console

**Chat messages not appearing**:
- Verify WebSocket connection state (should be "connected")
- Check console for message parsing errors
- Ensure `_handleWSMessage()` has case handler for the message type

**Glassmorphism not visible**:
- Ensure `backdrop-filter: blur()` is set (requires iOS 9+ / Android 9+)
- Check that background has transparency (< 1.0 opacity)
- Verify border color isn't too dark (should be semi-transparent white)

**Mini program fails to load**:
- Check `project.private.config.json` has correct appid
- Verify all referenced images exist (icons, avatar, tabbar)
- Check console for syntax errors in WXML/WXSS/JS

## File Organization

```
miniprogram/
├── app.js                  # Application lifecycle & initialization
├── app.json                # Global config (navigationBar, tabBar)
├── app.wxss                 # Global styles
├── DESIGN.md               # Design system specification
├── UI_REDESIGN_SUMMARY.md  # Recent UI redesign summary
├── project.config.json      # WeChat project configuration
├── project.private.config.json # Private config (appid, etc.)
├── components/              # Reusable components
│   ├── chat-bubble/        # Chat message bubble
│   └── voice-button/       # Voice input button (unused)
├── pages/                   # Page modules
│   ├── index/              # Chat page (main interface)
│   └── settings/           # Settings page
├── utils/                   # Utilities & managers
│   ├── audio.js            # Opus audio playback
│   ├── auth.js             # Token management
│   ├── device.js           # Device binding & OTA
│   ├── request.js          # HTTP requests
│   └── websocket.js        # WebSocket manager
└── images/                 # Static assets
    ├── avatar-default.png  # Default AI avatar
    ├── beijing.png         # Background image
    ├── icons/              # UI icons (deprecated, mostly unused)
    └── tabbar/             # Bottom navigation icons
```

## Brand Voice

**"完美女友" (Perfect Girlfriend)** is positioned as:
- **Warm and intimate**: Not just a tool, but a companion
- **Emotionally intelligent**: Remembers context, responds with empathy
- **Always present**: Available whenever needed (no cold startup screens)
- **Soft and gentle**: Every interaction feels deliberate and caring

When writing UI text or error messages:
- ❌ "系统错误" (System Error)
- ✅ "翠花遇到了一些问题，请稍后再试" (Cuihua encountered some issues, please try again later)

- ❌ "连接中..." (Connecting...)
- ✅ "正在建立连接..." (Establishing connection...)

- ❌ "发送失败" (Send failed)
- ✅ "消息发送失败，请重试" (Message failed to send, please retry)
