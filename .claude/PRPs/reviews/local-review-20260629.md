# Local Code Review

**Reviewed**: $(date +%Y-%m-%d)
**Decision**: APPROVE

## Summary
本次改动修复了小程序 WebSocket 心跳超时计时器被重复重置的 bug，并在服务端启用 WebSocket 心跳响应。两者配合后，小程序端的心跳检测机制才能正常工作。

## Files Reviewed

| File | Change |
|---|---|
| `main/miniprogram/utils/websocket.js` | 修复 ping/pong 超时逻辑 |
| `main/xiaozhi-server/config.yaml` | 启用 `enable_websocket_ping` |

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM
None

### LOW
- `main/xiaozhi-server/core/handle/textHandler/pingMessageHandler.py:45` 中 `except Exception` 捕获所有异常，仅记录日志。对于心跳消息而言这是可接受的，但如果连接已损坏，这里只是静默失败，下一次 pong 超时仍会通过 `_scheduleReconnect` 自愈。

## Validation Results

| Check | Result |
|---|---|
| Security scan | Skipped (no credentials, no injection risks in diff) |
| Type check | Skipped (JS/Python 项目无变更需类型检查) |
| Tests | Skipped (改动为小范围 bug fix，未引入测试) |
| Build | Skipped |

## Notes

- 修复前：每次 `_startPing` 触发都会调用 `_startPongTimeout()`，后者先 `_clearPongTimeout()` 再创建新计时器，导致计时器不断被刷新，pong 超时永远不会触发。
- 修复后：仅在 `_pongTimeoutTimer` 为空时才启动新计时器；收到 `pong` 消息时会 `_clearPongTimeout()`，形成"发 ping → 等 pong → 收到 pong 清计时器 → 下次 ping 再启动"的正确循环。
- 服务端 `enable_websocket_ping: true` 是必要配套：服务端 `PingMessageHandler` 仅在配置为 true 时回复 `pong`，否则小程序的 ping 会被忽略，导致客户端持续心跳超时并重连。
- 两个改动必须同时上线，单独改客户端或单独改服务端都无法解决问题。
