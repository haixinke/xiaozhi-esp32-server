# Code Review: 小程序语音通话功能 (f-mini-voice)

**Reviewed**: 2026-06-21  
**Branch**: f-mini-voice (ahead of origin/f-mini-voice)  
**Scope**: 未提交更改已处理；本报告覆盖语音通话功能在分支上的完整实现

## Summary

功能实现完整，覆盖了设计文档中的主要需求。单元测试全部通过，无安全漏洞。存在若干代码质量和健壮性问题，建议修复后再合并。

## Findings

### CRITICAL

**None**

### HIGH

#### 1. `onVoiceCallTap` 等待连接时无错误传播保护
- **File**: `main/miniprogram/pages/index/index.js`
- **Location**: `onVoiceCallTap()`
- **Issue**: 当 `connectionState !== 'connected'` 时调用 `this.onSummon()`，然后用 `while` 循环轮询最多 10 秒。如果 `onSummon()` 内部失败（例如无网络、设备未绑定），循环会空转 10 秒才提示用户。
- **Suggested fix**: 在 `onSummon()` 中增加失败回调，或在轮询中检查 `bindFailed` / 连接错误状态，提前退出。

#### 2. `_waitForHelloAndStart` 使用轮询而非事件驱动
- **File**: `main/miniprogram/pages/voice-call/voice-call.js`
- **Location**: `_waitForHelloAndStart()`
- **Issue**: 每 100ms 检查一次 `wsManager.isConnected()`，虽然加了 10 秒超时，但仍是不必要的轮询。WebSocketManager 已经通过 `onStateChange` 暴露状态变化。
- **Suggested fix**: 将等待逻辑改为监听 `WebSocketManager.onStateChange`，在 `connected` 时启动录音，减少 CPU/定时器开销。

#### 3. 录音重启期间可能出现状态不同步
- **File**: `main/miniprogram/pages/voice-call/voice-call.js`
- **Location**: `_restartRecord()`
- **Issue**: 10 分钟录音重启时，`stopRecord` → `sendListenStop` → 100ms 后 `startRecord` → `sendListenStart`。如果在这 100ms 内收到服务端 `audio` 帧，会调用已停止的 AudioManager 的 `appendOpusFrame`，虽不会崩溃，但音频可能丢失。
- **Suggested fix**: 在 `_restartRecord` 期间设置一个标志位，暂停将音频帧写入 AudioManager，直到录音重新启动。

### MEDIUM

#### 4. 生产代码中保留 `console.warn` / `console.error`
- **File**: `main/miniprogram/pages/voice-call/voice-call.js`, `main/miniprogram/pages/index/index.js`
- **Issue**: 多处使用 `console.warn` / `console.error` 输出错误信息，虽然项目其他地方也有使用，但不符合"No console.log in production code"的最佳实践。
- **Suggested fix**: 封装一个小的日志工具，在开发环境下输出，生产环境静默。

#### 5. `_ensureRecordPermission` 嵌套较深
- **File**: `main/miniprogram/pages/index/index.js`
- **Location**: `_ensureRecordPermission()`
- **Issue**: 多层回调嵌套（`wx.getSetting` → `wx.showModal` → `wx.openSetting`），深度超过 4 层。
- **Suggested fix**: 将每个权限状态的处理拆分为独立的小函数，或使用 async/await 重构。

#### 6. `VoiceCallManager` 单例未重置媒体引用
- **File**: `main/miniprogram/utils/voice-call-manager.js`
- **Location**: `hangup()` / `startCall()`
- **Issue**: `hangup()` 会调用 `_stopMedia()`，但不会调用 `clearMedia()`。下一次 `startCall()` 后如果 `_startMedia()` 未被调用（例如异常），旧的 `this._audioManager` / `this._wsManager` 引用可能指向已销毁的对象。
- **Suggested fix**: 在 `hangup()` 中调用 `this.clearMedia()`，确保状态干净。

### LOW

#### 7. `voice-call.js` 文件接近 300 行
- **File**: `main/miniprogram/pages/voice-call/voice-call.js`
- **Issue**: 文件包含 UI、WebSocket、AudioManager、生命周期等多种职责，略长。
- **Suggested fix**: 后续迭代时可考虑将媒体控制逻辑抽取到 `utils/voice-call-media.js`。

#### 8. 图标资源缺少尺寸校验
- **File**: `main/miniprogram/images/*.png`
- **Issue**: 部分从 Icons8 下载的图标尺寸不一致（虽然显示为 100x100 PNG），建议在项目文档中记录图标规范。
- **Suggested fix**: 非阻塞，可后续统一规范。

## Validation Results

| Check | Result |
|---|---|
| Unit tests (`voice-call-manager.test.js`) | Pass |
| Unit tests (`voice-catalog.test.js`) | Pass |
| Unit tests (`logic.test.js`) | Pass |
| Lint | Skipped (项目无统一 lint 配置) |
| Build | Skipped (微信小程序无本地 build 命令) |

## Decision

**REQUEST CHANGES**

建议修复 HIGH 和 MEDIUM 问题后再合并。特别是：
1. `onVoiceCallTap` 等待连接时的错误处理
2. `_waitForHelloAndStart` 轮询改为事件驱动
3. `hangup()` 中清理媒体引用
