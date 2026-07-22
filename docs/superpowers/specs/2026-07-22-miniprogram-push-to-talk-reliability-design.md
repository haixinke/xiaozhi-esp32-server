# 女友小程序按住说话可靠性改造设计

## 1. 目标

修复聊天页“按住说话、松手发送、上滑取消”链路中的异步竞态，使一次语音轮次在录音器、WebSocket、服务端音频队列和 ASR 之间具有明确且可验证的开始、结束和取消边界。

完成后必须满足：

- 快速松手早于原生 `RecorderManager.onStart` 时，不会遗留录音或创建空服务端轮次。
- 正常松手时，最后一帧音频一定先于本轮结束标记进入服务端处理队列。
- 上滑取消、录音错误、页面隐藏和连接断开均只终止当前轮次一次，不会污染下一轮。
- Opus 编码器未就绪或回退到 stub 时，不允许创建语音轮次。
- 流式和非流式 ASR 都在消费完本轮全部音频后才最终化。
- 无语音、ASR 失败或服务端无响应时，客户端能够回到 `idle`，不会永久停在 `thinking`。

本次仅改造聊天页的按住说话链路；不改变语音通话产品行为，不调整聊天 UI，也不引入新的第三方依赖。

## 2. 方案比较

### 方案 A：只修小程序状态机

在客户端增加 `starting/recording/stopping`，并把 `listen.stop` 延迟到原生 `onStop`。

- 优点：改动最小。
- 缺点：服务端音频帧和 `listen.stop` 仍走不同的处理路径，无法消除后台消费队列竞态。

不采用。它只能降低复现率，不能证明尾帧先于 ASR 最终化。

### 方案 B：客户端状态机 + 服务端 FIFO 轮次标记

客户端严格禁止轮次重叠；服务端把手动语音的 Start、Audio、End、Abort 作为带 `turnId` 的事件放入同一个 FIFO 队列，由单一消费者按顺序处理。

- 优点：直接修复根因；保留原始 Opus 二进制协议；兼容未携带 `turn_id` 的既有客户端。
- 缺点：需要同时修改小程序、连接路由、listen/abort 处理和 ASR 最终化。

采用此方案。

### 方案 C：每个二进制音频帧携带 wire-level `turnId`

为 Opus 帧增加自定义二进制头，服务端解析后再交给 ASR。

- 优点：跨网络层隔离最强。
- 缺点：改变设备协议，影响 ESP32、网页客户端和其他接入方；本项目只要禁止客户端轮次重叠并在服务端接收时绑定轮次即可达到目标。

暂不采用。若未来允许同一连接并发多路音频，再升级协议。

## 3. 总体架构

### 3.1 客户端职责

新增一个仅服务聊天页的 `VoiceInputController`，集中维护语音手势和协议轮次。页面只负责把触摸事件、连接状态和生命周期事件交给控制器，并根据控制器回调更新 UI。

控制器维护非响应式状态：

```text
idle -> starting -> recording -> stopping -> waiting -> idle
                 \-> cancelling ---------> idle
```

- `starting`：手指已按下，正在等待原生录音成功；尚未发送 `listen.start`。
- `recording`：原生 `onStart` 已到达、编码器可用、`listen.start` 已成功排入发送队列。
- `stopping`：手指正常松开，等待原生 `onStop` 刷出尾帧。
- `waiting`：尾帧和 `listen.stop` 已按顺序发送，等待服务端确认或聊天响应；页面既有 `chatState` 仍负责 thinking/speaking。
- `cancelling`：只是在幂等取消动作执行期间使用的瞬时状态；尾部 PCM 必须丢弃，动作完成后回到 idle。

每次按下分配单调递增的本地 `turnId`。所有异步回调先比较 `turnId`，陈旧回调不得修改当前轮次。

### 3.2 AudioManager 职责

`AudioManager` 保持录音和 Opus 编码职责，但补充显式的原生状态：

```text
idle | starting | recording | retrying | stopping | destroyed
```

它必须：

- 暴露 `isReady()` 和 `isUsable()`；只有 WASM 编码器就绪时才允许开始录音。
- 记录 `stopRequested`，解决 `onStart` 前松手：迟到的 `onStart` 立即触发原生 stop。
- 让正常停止、取消、内部 file-error 重试和最终错误使用不同 reason。
- 正常停止在 `onStop` 中补齐尾帧，然后才通知控制器停止完成。
- 取消时清空 `_pcmBacklog`，禁止尾帧通过 `onAudioFrame` 发送。
- 内部重试产生的 `onStop` 不得向页面发布用户轮次终态。
- `destroy()` 与尚未完成的 `ready()` 幂等，不能在异步初始化完成后访问已销毁的 encoder。

保留现有 `startRecord()` / `stopRecord()` 调用方式，避免破坏语音通话页面；新增选项和状态查询保持向后兼容，旧调用方可以继续忽略 `stopRecord()` 的 Promise 返回值。

聊天页使用的新停止合约为 `stopRecord({ flush, reason }) -> Promise<StopResult>`。Promise 只允许在对应原生 `onStop` 已到达、PCM backlog 已按 `flush` 策略处理完后 resolve。内部 file-error 重试必须先等待本 attempt 的 `onStop`；该次停止不 flush、不发布用户轮次终态，确认停止后才允许同一 turn 重试一次。若原生 `onStop` 在 2 秒内未到达，则本轮直接 error，禁止再启动新 attempt。

### 3.3 WebSocketManager 职责

WebSocketManager 为每个语音轮次提供绑定到确切 `SocketTask + connectionGeneration` 的串行 Promise 发送链：

- `sendAudioFrame(frame)` 与 `sendListenStart/Stop(turnId)` 使用 `SocketTask.send` 的 `success/fail` 回调并返回 Promise。
- 同一轮次的调用顺序必须是 `listen.start -> audio... -> listen.stop`。
- 任一异步 `fail` 都使该轮发送链进入粘性失败状态；后续 Audio/End 不再发送，只允许 best-effort Abort 和本地收口。
- 旧轮发送队列不能跨 generation 向重连后的新 Socket 补发，音频帧禁止自动重试。
- 收到服务端 `listen` 反馈消息时，解析为 `{type:'listen', state, turnId, reason}`。
- Socket 断开时增加连接 generation；控制器绑定的旧 generation 立即失效。

发送 success 不是服务端确认。正常结束必须先等待 `stopRecord({flush:true})`，再等待截至尾帧的发送 Promise 链成功，最后发送 End；服务端消费到 End 后回传 `stopped` ack。

### 3.4 服务端职责

手动拾音模式使用不可变的统一语音输入事件：

```text
VoiceInputEvent(kind='start'|'audio'|'end'|'abort', turn_id, payload)
```

新增单连接 `VoiceTurnCoordinator`，由它独占 `TurnContext`：

```text
TurnContext(turn_id, state, frames, cancelled, frame_count, connection_generation)
```

`TurnContext` 的集合字段只通过 Coordinator 创建新快照更新。`conn.asr_audio`、`client_voice_stop` 和 provider 的 `text/is_processing` 不再作为轮次身份来源；旧 provider 的 finally 只能清理自己持有的匹配 turn，不能无条件重置新 turn。

兼容规则：

- 新小程序在 `listen.start/stop` 中发送 `turn_id`。
- 旧客户端没有 `turn_id` 时，ConnectionHandler 为每次 manual start 生成内部单调轮次号。
- 自动拾音模式继续使用现有音频处理，不改变 ESP32 自动 VAD 行为。
- manual 模式下，WebSocket 路由按收到消息的顺序将 Start、Audio、End 放入现有 ASR 输入 FIFO。Audio 在入队时绑定当前接收轮次。
- stop handler 不再直接检查 `asr_audio`，也不使用 `queue.empty()` 或 pending 布尔值。
- 控制 sentinel 永不丢弃。队列为控制事件预留容量；音频入队溢出时将整轮标记为 error，拒绝后续 Audio，并确保 Abort/Error 控制事件仍能入队。禁止“丢最旧帧后继续识别”。

消费者规则：

1. Start：激活轮次并重置该轮 VAD/ASR 缓冲。
2. Audio：仅当 turnId 仍是活动轮次时交给现有 `handleAudioMessage`。
3. End：因为它与音频共用 FIFO，到达时可证明之前的帧已处理完成；此时才设置 `client_voice_stop`。
4. Abort：使轮次立即失效；后续同 turnId 的帧和 ASR 结果全部丢弃，并清理流式 provider。

非流式 ASR 在 End 时从 TurnContext 取得完整不可变快照并创建独立最终化任务，不阻塞 WebSocket 接收循环。流式 ASR 统一实现 `end_turn(turn) -> VoiceTurnOutcome`：End 消费者 await 到 provider 停止请求已写入云端连接后才发送 stopped ack；provider 后续以 recognized/no_speech/error outcome 恰好完成一次。1013、task-finished 无文本、空最终结果、超时和异常都必须收敛为明确 outcome。

在 `enqueue_asr_report`、STT 下发、`startToChat`/LLM 启动前都必须再次验证 turn 仍有效。清理函数也必须携带 turnId，只能清理匹配轮次。

## 4. 协议

客户端控制消息扩展为：

```json
{"type":"listen","mode":"manual","state":"start","turn_id":"m-17"}
{"type":"listen","mode":"manual","state":"stop","turn_id":"m-17"}
{"type":"abort","turn_id":"m-17"}
```

服务端反馈消息：

```json
{"type":"listen","state":"stopped","turn_id":"m-17"}
{"type":"listen","state":"no_speech","turn_id":"m-17"}
{"type":"listen","state":"error","turn_id":"m-17","reason":"asr_failed"}
{"type":"listen","state":"cancelled","turn_id":"m-17"}
```

`stopped` 不是终态，只确认 End 已越过服务端 FIFO 屏障，客户端继续等待 STT/TTS。真正终态是 recognized（由既有 STT/TTS 表示）、`no_speech`、`error` 或 `cancelled`。未知客户端会忽略这些新增消息，因此协议向后兼容。

收到匹配 turnId 的 STT 或 TTS start 也视为语音输入轮次成功完成：控制器清理 ack/响应计时器并回到自身 idle；页面原有 `chatState` 继续负责 thinking/speaking，不与语音输入状态混用。

## 5. 关键时序

### 5.1 正常发送

```text
touchstart
  -> AudioManager.startRecord
  -> native onStart
  -> send listen.start(turnId)
  -> send audio(turnId)...
touchend
  -> native stop
  -> native onStop
  -> flush final audio
  -> send listen.stop(turnId)
  -> server FIFO reaches End(turnId)
  -> server sends stopped ack
  -> ASR/STT/LLM/TTS
```

### 5.2 `onStart` 前松手

控制器将轮次标为 stopping，但不发送任何 listen 消息。若 `onStart` 迟到，AudioManager 立即 stop；`onStop` 完成后直接回到 idle。

### 5.3 取消、隐藏和断线

- 若服务端轮次尚未 start：本地停止并丢弃音频，不发送协议消息。
- 若服务端轮次已 start：丢弃 backlog，发送 `abort` 并使 turnId 失效。
- WebSocket generation 改变后，不允许旧录音回调向新 Socket 发送帧。
- `onHide`、`onAppHide`、`onUnload` 共用同一个幂等 cancel 方法。

### 5.4 file error 重试

首次 file error 仅在手指仍按住、连接 generation 未变、轮次未取消时重试一次。内部 stop 不发布轮次完成事件。重试窗口内松手会取消定时器并完成本地轮次；重试后的迟到 onStart 立即停止。

## 6. 超时和用户反馈

- Opus 未就绪：不开始录音，提示“语音引擎准备中，请稍后重试”。
- Opus stub/初始化失败：禁用本次语音输入，提示“语音引擎加载失败，请重启微信后重试”。
- 原生停止后没有成功发送任何音频帧：发送 abort（如果 start 已发送），提示“没有听清，请重试”。
- End 发送后 5 秒未收到 `stopped` ack：取消本轮，恢复 idle，提示连接异常。
- `stopped` 后 30 秒未收到 STT、`no_speech`、`error` 或 TTS：恢复 idle，并提示“暂时没有听清，请重试”。
- 任何异步 send fail 或连接断开：立即停止并取消本轮，不进入或退出 thinking。

超时只是防御措施；FIFO End 屏障和状态机仍是根因修复。

## 7. 安全与隐私

- `turn_id` 只接受字符串或整数，字符串长度不超过 64，非法值由服务端生成内部 ID。
- 日志只记录 turnId、状态和帧数，不记录音频内容、token、URL 鉴权参数或用户识别文本。
- 队列满时不得静默丢弃当前轮次的旧帧后继续识别；应取消该轮并返回 error。
- 每个连接最多一个活动 manual turn，新的 Start 必须先终止旧轮次。
- Start 冲突返回 error；turnId 不匹配的 Stop/Abort 被拒绝并返回 error；manual 模式下 Audio-before-Start 直接丢弃并记录计数，不启动 ASR。

## 8. 测试与验收

### 小程序自动测试

- `touchend` 早于 `onStart`，不得发送 start/stop，迟到 onStart 后原生录音最终停止。
- 正常松手严格得到 start、audio、尾帧、stop 顺序。
- 上滑取消不发送尾帧，且 abort 最多一次。
- file error -> internal onStop -> retry window touchend，不得重启录音。
- 编码器 pending、stub、失败均不得创建服务端轮次。
- 录音中断线、SocketTask async fail、页面 hide 均恢复 idle。
- 陈旧 turn 回调不影响新轮次。
- ack/no_speech/error/响应超时均正确清理计时器和 UI。
- 语音通话现有测试继续通过。

### 服务端自动测试

- 在消费者故意延迟时，End 永远在之前所有 Audio 处理后执行。
- End 入队后到达的同 turn Audio 被拒绝。
- Abort 后的音频和迟到 ASR 结果不能进入 STT/LLM。
- 非流式 ASR 在 End 时获得完整快照。
- 四个流式 provider 只在 End 屏障后收到 stop。
- 旧客户端无 turnId 仍能完成一轮 manual ASR。
- 空音频、空识别和 ASR 异常均发送终态。
- 自动拾音模式行为不变。

### 验收不变量

1. 每个已发送 Start 的 turn 恰好产生一个 End 或 Abort。
2. 服务端不会在 End 之前最终化 ASR。
3. End 之后不会有该 turn 的音频进入 ASR。
4. 任一时刻每个聊天页和服务端连接最多一个活动 manual turn。
5. 任一错误路径最终都回到 idle，不会无限 recording、stopping 或 thinking。
6. 所有新增竞态测试、现有小程序测试和服务端相关测试全部通过。

## 9. 非目标

- 不重写通用 WebSocket 协议。
- 不为每个 Opus 帧增加自定义二进制头。
- 不改变自动 VAD 模式和 ESP32 的音频工作流。
- 不调整语音通话页的持续对话模型；只做兼容性回归验证。
- 不修改当前与本问题无关的 API BASE_URL 和权限改动。
