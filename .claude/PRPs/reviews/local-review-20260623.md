# Local Code Review

**Reviewed**: 2026-06-23
**Decision**: BLOCK

## Summary

本次未提交变更是围绕小程序语音通话页去掉免提/听筒切换、保留静音按钮、默认免提播放。功能实现基本正确，测试也已更新并通过。

但 `main/miniprogram/utils/request.js` 中已有的 `console.log` / `console.error` 会输出 token 内容（即使做了截断），存在敏感信息泄露风险；同时 `BASE_URL` 指向局域网 IP，属于本地开发配置，不适合提交到共享分支。

## Findings

### CRITICAL

**None**

### HIGH

#### HIGH-1: 日志输出中泄露 token 片段
- **文件**: `main/miniprogram/utils/request.js:24`
- **问题**: `console.log('[request] URL:', ..., 'Token value:', token ? token.substring(0, 30) + '...' : 'EMPTY or NULL')` 会在运行时打印 token 前 30 个字符。即使截断，也足以被日志收集系统捕获，存在凭证泄露风险。
- **建议**: 删除 `Token value` 字段，仅保留 `Token exists: !!token` 布尔标识即可。

### MEDIUM

#### MEDIUM-1: 本地开发 IP 硬编码在提交代码中
- **文件**: `main/miniprogram/utils/request.js:7`
- **问题**: `BASE_URL = 'http://192.168.4.12:8002/xiaozhi'` 是局域网地址。虽然注释说明「可按需修改」，但作为未提交变更提交流入共享分支容易污染他人环境。
- **建议**: 若必须提交，应改为环境变量或 `project.private.config.json` 等本地覆盖方式；否则不要将此文件纳入本次提交。

#### MEDIUM-2: 默认免提设置缺少失败回调
- **文件**: `main/miniprogram/pages/voice-call/voice-call.js:136-139`
- **问题**: `wx.setInnerAudioOption({ speakerOn: true })` 未处理 `fail` 情况，若微信 API 调用失败没有任何日志或降级提示。
- **建议**: 添加 `fail` 回调并记录日志，例如：
  ```js
  wx.setInnerAudioOption({
    speakerOn: true,
    fail: (err) => logger.warn('[VoiceCall] setInnerAudioOption failed:', err),
  });
  ```

### LOW

#### LOW-1: 测试 mock 中 `connectCount` 未使用
- **文件**: `main/miniprogram/pages/voice-call/voice-call.test.js:8`
- **问题**: `connectCount` 变量被声明并在 mock 中递增，但测试断言中未使用，属于轻微冗余。
- **建议**: 要么添加断言，要么移除该变量。

## Validation Results

| Check | Result |
|---|---|
| Type check | Skipped (miniprogram project, no tsc setup) |
| Lint | Skipped (no project lint command configured) |
| Tests | Pass |
| Build | Skipped (wechat dev tools compiles automatically) |

测试命令：
```bash
cd main/miniprogram
node utils/voice-call-manager.test.js
node pages/voice-call/voice-call.test.js
```

结果：全部通过。

## Files Reviewed

| File | Change Type |
|---|---|
| `main/miniprogram/pages/voice-call/voice-call.js` | Modified |
| `main/miniprogram/pages/voice-call/voice-call.test.js` | Modified |
| `main/miniprogram/pages/voice-call/voice-call.wxml` | Modified |
| `main/miniprogram/utils/request.js` | Modified (pre-existing local config + logging issue) |
| `main/miniprogram/utils/voice-call-manager.js` | Modified |
| `main/miniprogram/utils/voice-call-manager.test.js` | Modified |

## Next Steps

1. 修复 HIGH-1：移除 `request.js` 中 token 值的日志输出。
2. 确认 MEDIUM-1：`request.js` 的本地 IP 是否应随本次提交；如不应提交，请将其从暂存区移除。
3. 可选处理 MEDIUM-2 和 LOW-1。

修复后可重新运行测试并再次审查。
