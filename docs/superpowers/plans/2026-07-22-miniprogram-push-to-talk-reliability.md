# 女友小程序按住说话可靠性改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让聊天页一次“按住—说话—松手/上滑取消”在录音器、小程序 WebSocket、服务端队列和 ASR 之间形成可证明的单轮次顺序，彻底消除快速松手、尾帧越界、旧回调污染和无终态导致的“无发送/无回复”。

**Architecture:** 小程序新增 `VoiceInputController` 作为唯一手势/轮次状态机，`AudioManager` 提供可等待的原生停止屏障，`WebSocketManager` 为每轮建立绑定具体 SocketTask 与 connection generation 的粘性串行发送链。服务端新增不可变 `VoiceInputEvent`/`TurnContext` 和单连接 `VoiceTurnCoordinator`，把 manual Start、Audio、End、Abort 放入同一 FIFO；非流式和四个流式 ASR provider 都只能在 End 屏障后产生一次 `VoiceTurnOutcome`，且所有下游副作用在执行前验证 turnId。

**Tech Stack:** 微信小程序 CommonJS / RecorderManager / SocketTask / Node.js `assert` 与内置 coverage；Python 3.12 / `asyncio` / `queue.Queue` / `dataclasses` / 标准库 `unittest` 与 `trace`；现有 xiaozhi-server ASR provider 接口。

**Spec:** [`docs/superpowers/specs/2026-07-22-miniprogram-push-to-talk-reliability-design.md`](../specs/2026-07-22-miniprogram-push-to-talk-reliability-design.md)

## Global Constraints

- 本次仅改造聊天页的按住说话链路；语音通话现有 `auto` 模式行为保持不变。
- 不改变 Opus 二进制帧格式，不为音频帧增加 wire-level 自定义头，不引入第三方依赖。
- `AudioManager.stopRecord({ flush, reason })` 的原生 `onStop` 超时固定为 2 秒。
- End 成功发送后 5 秒没有 `stopped` ack 必须失败收口；收到 ack 后 30 秒没有 STT/TTS/终态必须失败收口。
- 每个已发送 Start 的 turn 恰好产生一个 End 或 Abort；服务端不会在 End 前最终化，也不会在 End 后接收同 turn 音频。
- Opus encoder 未 ready、stub 或初始化失败时不得创建服务端轮次。
- 队列溢出必须整轮失败，控制 sentinel 永不丢弃；禁止丢掉旧音频后继续识别。
- 新增日志只记录 turnId、状态、帧数和错误分类，不记录音频内容、token、鉴权 URL 或识别文本。
- 保留当前工作区中 `main/miniprogram/pages/index/index.js`、`index.test.js` 的麦克风权限处理改动，以及 `main/miniprogram/utils/request.js` 的 `BASE_URL` 改动；不得回退、覆盖或误提交它们。
- 每个生产代码任务严格执行 RED（测试按预期失败）→ GREEN（最小实现）→ 回归；新增/重构模块行覆盖率不低于 80%。
- 在当前脏工作区执行时只按任务列出的路径暂存，并在每次 commit 前运行 `git diff --cached --check` 与 `git diff --cached --stat`；包含既有用户改动的 `index.js/index.test.js/request.js` 不做整文件暂存。

---

## File Structure

### 小程序

| 文件 | 动作 | 单一职责 |
|---|---|---|
| `main/miniprogram/utils/audio.js` | 修改 | 原生录音 attempt 状态、ready/usability gate、停止屏障、尾帧 flush/drop、file-error 串行重试 |
| `main/miniprogram/utils/audio.test.js` | 修改 | RecorderManager 快速松手、尾帧、重试、超时和 destroy 竞态测试 |
| `main/miniprogram/utils/websocket.js` | 修改 | connection generation、每轮 Promise 发送链、sticky failure、listen feedback 解析 |
| `main/miniprogram/utils/websocket.test.js` | 新增 | SocketTask 异步回调顺序、失败、断线和陈旧 socket 测试 |
| `main/miniprogram/utils/voice-input-controller.js` | 新增 | 聊天页唯一语音轮次/超时/取消状态机 |
| `main/miniprogram/utils/voice-input-controller.test.js` | 新增 | 手势与跨组件竞态的确定性测试 |
| `main/miniprogram/pages/index/index.js` | 修改 | 将触摸、AudioManager/WebSocketManager 回调和生命周期委托给控制器 |
| `main/miniprogram/pages/index/index.test.js` | 修改 | 页面接线、UI 状态、权限回归和幂等生命周期测试 |

### xiaozhi-server

| 文件 | 动作 | 单一职责 |
|---|---|---|
| `main/xiaozhi-server/core/voice_turn.py` | 新增 | 不可变事件、上下文、outcome、turnId 校验和接收/消费状态机 |
| `main/xiaozhi-server/core/handle/voiceTurnHandle.py` | 新增 | manual FIFO 事件消费、ack/终态发送、非流式/流式最终化编排 |
| `main/xiaozhi-server/core/connection.py` | 修改 | 队列预留容量、manual 二进制帧绑定 turnId、连接 generation 初始化 |
| `main/xiaozhi-server/core/providers/asr/base.py` | 修改 | 队列 union 消费、turn-aware 非流式识别、流式 outcome future 基础合约 |
| `main/xiaozhi-server/core/handle/textHandler/listenMessageHandler.py` | 修改 | manual Start/End 入 FIFO；detect 保持原路径 |
| `main/xiaozhi-server/core/handle/textHandler/abortMessageHandler.py` | 修改 | 带 turnId 的 manual Abort 与既有全局 Abort 分流 |
| `main/xiaozhi-server/core/handle/receiveAudioHandle.py` | 修改 | `startToChat(..., turn_id=None)` 的副作用前有效性检查 |
| `main/xiaozhi-server/core/handle/reportHandle.py` | 修改 | `enqueue_asr_report(..., turn_id=None)` 的上报前有效性检查 |
| `main/xiaozhi-server/core/handle/sendAudioHandle.py` | 修改 | STT 携带 turnId，发送前拒绝陈旧轮次 |
| `main/xiaozhi-server/core/providers/asr/xunfei_stream.py` | 修改 | 将最终结果/空结果/异常收敛到绑定 turn 的 outcome |
| `main/xiaozhi-server/core/providers/asr/aliyun_stream.py` | 修改 | 同上；处理 SentenceEnd/TranscriptionCompleted/失败 |
| `main/xiaozhi-server/core/providers/asr/aliyunbl_stream.py` | 修改 | 同上；处理 result-generated/task-finished/task-failed |
| `main/xiaozhi-server/core/providers/asr/doubao_stream.py` | 修改 | 同上；处理 1013、最终文本、连接关闭和异常 |
| `main/xiaozhi-server/tests/__init__.py` | 新增 | unittest 测试包 |
| `main/xiaozhi-server/tests/test_voice_turn.py` | 新增 | 纯状态机、校验和不可变快照测试 |
| `main/xiaozhi-server/tests/test_manual_voice_pipeline.py` | 新增 | FIFO、队列溢出、handler 和 ack 集成测试 |
| `main/xiaozhi-server/tests/test_asr_voice_turn.py` | 新增 | 非流式/流式 outcome、陈旧结果和副作用门控测试 |

---

### Task 1: AudioManager 原生录音停止屏障

**Files:**
- Modify: `main/miniprogram/utils/audio.js`
- Test: `main/miniprogram/utils/audio.test.js`

**Interfaces:**
- Consumes: WeChat `RecorderManager.onStart/onStop/onError/onFrameRecorded`；现有 `onRecordStart/onRecordStop/onAudioFrame/onError` callbacks。
- Produces: `isReady(): boolean`、`isUsable(): boolean`、`startRecord(): boolean`、`stopRecord({flush?: boolean, reason?: string}): Promise<StopResult>`；`StopResult = {reason, flushedFrames, timedOut}`。

- [ ] **Step 1: 写快速松手、flush/drop 和超时失败测试**

在 `makeRecorderStub()` 保留可控回调并追加异步测试：

```js
async function testRecorderStopBarrier() {
  const frames = [];
  const recorder = makeRecorderStub();
  global.wx.getRecorderManager = () => recorder;
  const mgr = new AudioManager({ onAudioFrame: (frame) => frames.push(frame) });
  await mgr.ready();

  assert.strictEqual(mgr.isReady(), true);
  assert.strictEqual(mgr.isUsable(), true);
  assert.strictEqual(mgr.startRecord(), true);
  const earlyStop = mgr.stopRecord({ flush: false, reason: 'released-before-start' });
  assert.strictEqual(recorder.stopCalls, 0, 'native stop waits for late onStart');
  recorder._onStart();
  assert.strictEqual(recorder.stopCalls, 1, 'late onStart must immediately stop');
  recorder._onStop({});
  assert.deepStrictEqual(await earlyStop, {
    reason: 'released-before-start', flushedFrames: 0, timedOut: false,
  });
  assert.strictEqual(mgr.getRecordState(), 'idle');

  mgr.startRecord();
  recorder._onStart();
  recorder._onFrameRecorded({ frameBuffer: new Int16Array(200).buffer });
  const normalStop = mgr.stopRecord({ flush: true, reason: 'release' });
  recorder._onStop({});
  assert.strictEqual((await normalStop).flushedFrames, 1, 'normal stop flushes one padded tail');
  assert.strictEqual(frames.length, 1);

  mgr.startRecord();
  recorder._onStart();
  recorder._onFrameRecorded({ frameBuffer: new Int16Array(200).buffer });
  const cancelStop = mgr.stopRecord({ flush: false, reason: 'slide-cancel' });
  recorder._onStop({});
  assert.strictEqual((await cancelStop).flushedFrames, 0);
  assert.strictEqual(frames.length, 1, 'cancel must not emit tail audio');
  mgr.destroy();
}
```

再用可注入的 `recordStopTimeoutMs: 10` 创建实例，不触发 `_onStop`，断言 Promise reject、`onError` scope 为 `record`、状态最终为 `idle`；AudioManager 必须在内部给该 Promise 挂 rejection handler，保证仍忽略返回值的 voice-call 旧调用方不会产生 unhandled rejection。创建后立刻 `destroy()`，再 resolve codec ready，断言不访问已清空的 encoder/decoder。首次 file-error 后，在 internal onStop 尚未到达和 300ms retry timer 两个窗口分别调用公共 `stopRecord()`，都必须取消重试且不能再次 native start。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `node main/miniprogram/utils/audio.test.js`

Expected: FAIL，至少出现 `mgr.isReady is not a function` 或快速松手时 `recorder.stopCalls` 仍为 `0`。

- [ ] **Step 3: 实现显式 attempt 状态与停止 Promise**

在 `audio.js` 增加常量和实例字段：

```js
const RECORD_STOP_TIMEOUT_MS = 2000;

this._recordState = 'idle';
this._stopRequested = null;
this._stopDeferred = null;
this._recordStopTimer = null;
this._readySettled = false;
this._readyError = null;
this._recordStopTimeoutMs = this.options.recordStopTimeoutMs || RECORD_STOP_TIMEOUT_MS;
```

将 ready 链改为销毁安全，并增加查询方法：

```js
this._readyPromise = Promise.all([this.encoder.ready(), this.decoder.ready()])
  .then(() => {
    if (this._destroyed || !this.encoder) return this;
    this._codecMode = this.encoder.mode;
    this._readySettled = true;
    if (this._codecMode === 'stub') {
      this._emitError(new Error('Opus 编码器回退到 stub，真 Opus 不可用'), 'codec');
    }
    return this;
  })
  .catch((err) => {
    this._readySettled = true;
    this._readyError = err;
    throw err;
  });

isReady() { return this._readySettled && !this._readyError; }
isUsable() { return this.isReady() && this._codecMode === 'wasm' && !this._destroyed; }
getRecordState() { return this._recordState; }
```

`startRecord()` 只在 `idle` 且 codec 可用时把状态置为 `starting`；native `onStart` 在 `_stopRequested` 存在时转为 `stopping` 并立即调用 `recorder.stop()`，否则转为 `recording` 后发布 `onRecordStart`。`stopRecord(options)` 统一调用下列 deferred：

```js
stopRecord(options) {
  const opts = Object.assign({ flush: true, reason: 'stop' }, options || {});
  if (this._recordRetryTimer) {
    clearTimeout(this._recordRetryTimer);
    this._recordRetryTimer = null;
  }
  this._retryAfterStop = false;
  return this._requestRecordStop(opts);
}

_requestRecordStop(opts) {
  if (this._recordState === 'idle') {
    return Promise.resolve({ reason: opts.reason, flushedFrames: 0, timedOut: false });
  }
  if (this._stopDeferred) return this._stopDeferred.promise;

  let resolveStop;
  let rejectStop;
  const promise = new Promise((resolve, reject) => {
    resolveStop = resolve;
    rejectStop = reject;
  });
  this._stopRequested = opts;
  this._stopDeferred = { promise, resolve: resolveStop, reject: rejectStop };
  promise.catch(() => {});
  if (this._recordState === 'recording' || this._recordState === 'retrying') {
    this._recordState = 'stopping';
    try { this._recorder.stop(); } catch (err) { this._failRecordStop(err); }
  }
  this._recordStopTimer = setTimeout(() => {
    this._failRecordStop(new Error('recorder onStop timeout'));
  }, this._recordStopTimeoutMs);
  return promise;
}
```

native `onStop` 必须先按 `flush` 决定补帧或清 backlog，再清 timer、转 `idle`、resolve `StopResult`，最后只在非 `internal-retry` reason 时发布 `onRecordStop`。`_failRecordStop` 必须清 backlog/timer/deferred、回到 idle、reject Promise 并调用 `_emitError(err, 'record')`。

file-error 首次重试改成：设置 `_recordState='retrying'` 和 `_retryAfterStop=true`，调用内部 `_requestRecordStop({flush:false, reason:'internal-retry'})`（不能经过会把 `_retryAfterStop` 清零的公共 wrapper），在其 resolve 后等待 300ms；只有 `_retryAfterStop` 仍为 true、实例未销毁时才能再次 `startRecord()`。用户在 internal onStop 前或 timer 窗口调用公共 `stopRecord()` 都会把该 flag 清零。二次 file-error 或 onStop timeout 直接 error。

- [ ] **Step 4: 运行 AudioManager 测试和覆盖率**

Run: `node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-include='main/miniprogram/utils/audio.js' main/miniprogram/utils/audio.test.js`

Expected: PASS；`audio.js` line coverage ≥ 80%；快速松手最终 `idle`，cancel 不产生尾帧。

- [ ] **Step 5: 检查并提交独立模块**

```bash
git add main/miniprogram/utils/audio.js main/miniprogram/utils/audio.test.js
git diff --cached --check
git diff --cached --stat
git commit -m "fix: make recorder stop await native completion"
```

---

### Task 2: WebSocket 每轮串行发送链与 connection generation

**Files:**
- Modify: `main/miniprogram/utils/websocket.js`
- Create: `main/miniprogram/utils/websocket.test.js`

**Interfaces:**
- Consumes: 已完成 hello 的具体 `SocketTask` 及其 `send({data, success, fail})` 回调。
- Produces: `getConnectionGeneration()`、`beginVoiceTurn(turnId)`、`sendVoiceFrame(turnId, frame)`、`finishVoiceTurn(turnId)`、`abortVoiceTurn(turnId)`，全部返回 Promise；旧 `sendListenStart/Stop/sendAudioFrame/sendAbort` 保持可用。

- [ ] **Step 1: 创建可控 SocketTask 测试**

`websocket.test.js` 创建 `makeSocketTask()`，其 `send` 只保存参数，由测试显式调用 `success/fail`。覆盖以下断言：

```js
const ws = new WebSocketManager({ onMessage: (msg) => messages.push(msg) });
ws.socket = task;
ws.state = 'connected';

const start = ws.beginVoiceTurn('m-1');
const audio = ws.sendVoiceFrame('m-1', frame);
const end = ws.finishVoiceTurn('m-1');
assert.strictEqual(task.sent.length, 1, 'only Start may be in flight');
task.succeed(0);
await start;
assert.strictEqual(task.sent.length, 2, 'Audio waits for Start success');
task.succeed(1);
await audio;
assert.strictEqual(task.sent.length, 3, 'End waits for tail Audio success');
task.succeed(2);
await end;
assert.deepStrictEqual(task.payloads(), [
  { type: 'listen', mode: 'manual', state: 'start', turn_id: 'm-1' },
  frame,
  { type: 'listen', mode: 'manual', state: 'stop', turn_id: 'm-1' },
]);
```

再测试 Audio 的异步 `fail`：End Promise 必须 reject 且 `task.sent` 不增加；`abortVoiceTurn` 仍能直接 best-effort 发送一次。调用 `_teardownSocket(false)` 后换新 task，旧轮 queued Audio/End 必须 reject，不能出现在新 task。旧 task 的迟到 `onClose` 不得把新 task 置空。解析 listen JSON 后必须 dispatch：

```js
{ type: 'listen', state: 'stopped', turnId: 'm-1', reason: '' }
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `node main/miniprogram/utils/websocket.test.js`

Expected: FAIL，`beginVoiceTurn` 尚不存在，且当前 `SocketTask.send` 的 async fail 不会进入返回值。

- [ ] **Step 3: 实现 generation 和 `_enqueueVoiceSend`**

构造器新增：

```js
this._connectionGeneration = 0;
this._voiceTurns = new Map();
```

每次绑定新 SocketTask 前递增 generation；teardown/close 只让绑定同一 task+generation 的轮次失败。核心 session 和发送函数固定为：

```js
getConnectionGeneration() { return this._connectionGeneration; }

_createVoiceSession(turnId) {
  if (!turnId || this.state !== 'connected' || !this.socket) {
    throw new Error('voice socket unavailable');
  }
  if (this._voiceTurns.has(turnId)) throw new Error('duplicate voice turn');
  const session = {
    turnId,
    task: this.socket,
    generation: this._connectionGeneration,
    tail: Promise.resolve(),
    failure: null,
    closed: false,
  };
  this._voiceTurns.set(turnId, session);
  return session;
}

_sendOnSession(session, data) {
  const operation = session.tail.then(() => {
    if (session.failure) throw session.failure;
    if (session.closed || this.socket !== session.task ||
        this._connectionGeneration !== session.generation || this.state !== 'connected') {
      throw new Error('stale voice socket generation');
    }
    return new Promise((resolve, reject) => {
      session.task.send({
        data,
        success: resolve,
        fail: (err) => reject(new Error('voice send fail: ' + ((err && err.errMsg) || 'unknown'))),
      });
    });
  });
  session.tail = operation.catch((err) => {
    if (!session.failure) session.failure = err;
    throw session.failure;
  });
  return session.tail;
}
```

公开方法精确使用这个 session：

```js
beginVoiceTurn(turnId) {
  const session = this._createVoiceSession(turnId);
  return this._sendOnSession(session, JSON.stringify({
    type: 'listen', mode: 'manual', state: 'start', turn_id: turnId,
  }));
}

sendVoiceFrame(turnId, frame) {
  const session = this._voiceTurns.get(turnId);
  if (!session || !frame) return Promise.reject(new Error('voice turn unavailable'));
  return this._sendOnSession(session, frame);
}

finishVoiceTurn(turnId) {
  const session = this._voiceTurns.get(turnId);
  if (!session) return Promise.reject(new Error('voice turn unavailable'));
  const done = this._sendOnSession(session, JSON.stringify({
    type: 'listen', mode: 'manual', state: 'stop', turn_id: turnId,
  }));
  return done.finally(() => {
    session.closed = true;
    this._voiceTurns.delete(turnId);
  });
}
```

`abortVoiceTurn` 不链接 rejected tail；仅当 captured task/generation 仍匹配时直接 `_sendOnTaskAsync` 一次 `{"type":"abort","turn_id":...}`，随后关闭并删除 session。`_bindSocketHandlers(task, generation)` 的所有回调先检查 task/generation；陈旧 `onClose` 只清自己的引用，不可清当前新连接。

- [ ] **Step 4: 解析 listen feedback 并保持旧 API**

`_handleMessage` 新增 case：

```js
case 'listen':
  this._dispatch({
    type: 'listen',
    state: msg.state || '',
    turnId: msg.turn_id === undefined ? null : String(msg.turn_id),
    reason: msg.reason || '',
    raw: msg,
  });
  break;
```

旧 `sendListenStart(mode)`、`sendListenStop()`、`sendAudioFrame(frame)` 和 `sendAbort()` 继续走原有即时发送路径，供 voice-call `auto` 模式使用；不得将 auto 音频放入 manual turn session。

- [ ] **Step 5: 运行测试与覆盖率并提交**

```bash
node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-include='main/miniprogram/utils/websocket.js' main/miniprogram/utils/websocket.test.js
git add main/miniprogram/utils/websocket.js main/miniprogram/utils/websocket.test.js
git diff --cached --check
git diff --cached --stat
git commit -m "fix: serialize voice frames on one socket generation"
```

Expected: PASS；line coverage ≥ 80%；任何 failed/stale session 都不在新 socket 上补发。

---

### Task 3: VoiceInputController 单轮次状态机

**Files:**
- Create: `main/miniprogram/utils/voice-input-controller.js`
- Create: `main/miniprogram/utils/voice-input-controller.test.js`

**Interfaces:**
- Consumes: Task 1 的 AudioManager API、Task 2 的 manual voice API。
- Produces: `press()`、`setCancelled(boolean)`、`handleRecordStart()`、`handleAudioFrame(frame)`、`handleAudioFailure(scope)`、`release()`、`cancel(reason)`、`handleSocketState(state,generation)`、`handleMessage(msg)`、`destroy()`、`getState()`。

- [ ] **Step 1: 写完整竞态矩阵测试**

用 deferred Audio/Socket fake 覆盖：

```js
const controller = new VoiceInputController({
  audio,
  socket,
  ackTimeoutMs: 10,
  responseTimeoutMs: 15,
  onStateChange: (state) => states.push(state),
  onWaiting: () => waiting.push(true),
  onTerminal: (outcome) => outcomes.push(outcome),
  onUserError: (message) => errors.push(message),
});

assert.strictEqual(controller.press(), true);
assert.strictEqual(controller.getState(), 'starting');
const earlyRelease = controller.release();
audio.resolveStop({ reason: 'released-before-start', flushedFrames: 0, timedOut: false });
await earlyRelease;
controller.handleRecordStart();
assert.deepStrictEqual(socket.calls, [], 'late start after release creates no server turn');
assert.strictEqual(controller.getState(), 'idle');
```

正常路径必须精确得到 `begin(m-1) -> frame -> tail frame -> finish(m-1)`；release 在 Audio stop Promise resolve 前不能调用 finish。上滑取消必须 `flush:false` 且 Abort 最多一次。还需覆盖：0 frame、codec pending/stub、record/encode error、native stop reject/timeout、begin/audio/finish async fail、generation 改变、hide/unload 两次 cancel、旧 turn 回调、5 秒 ack timeout、30 秒 response timeout、`stopped` 非终态、`no_speech/error/cancelled` 终态，以及 STT/TTS start 清理当前轮次。专门让 `stopped` 在 `finishVoiceTurn()` Promise resolve 前到达，断言它被记录而不是丢弃，finish resolve 后直接启动 response timer，不再启动 ack timer。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `node main/miniprogram/utils/voice-input-controller.test.js`

Expected: FAIL，模块不存在。

- [ ] **Step 3: 创建状态机与不可变 turn snapshot**

新文件导出 class 与状态常量；活动轮次对象只用 `Object.assign({}, turn, patch)` 更新：

```js
const ACTIVE_AUDIO_STATES = new Set(['recording', 'stopping']);

class VoiceInputController {
  constructor(options) {
    this.audio = options.audio;
    this.socket = options.socket;
    this.options = options;
    this.state = 'idle';
    this.turn = null;
    this._turnSeed = 0;
    this._ackTimer = null;
    this._responseTimer = null;
    this._destroyed = false;
  }

  getState() { return this.state; }

  press() {
    if (this._destroyed || this.state !== 'idle') return false;
    if (!this.audio || !this.audio.isReady()) {
      this._userError('语音引擎准备中，请稍后重试');
      return false;
    }
    if (!this.audio.isUsable()) {
      this._userError('语音引擎加载失败，请重启微信后重试');
      return false;
    }
    const id = 'm-' + (++this._turnSeed);
    this.turn = Object.freeze({
      id,
      generation: this.socket.getConnectionGeneration(),
      serverStarted: false,
      frameCount: 0,
      cancelled: false,
      ackReceived: false,
      pendingTerminal: null,
    });
    this._setState('starting');
    if (!this.audio.startRecord()) {
      this._finishLocal('error', 'record_start_rejected');
      return false;
    }
    return true;
  }
}
```

`handleRecordStart()` 捕获 turnId/generation；只在当前 `starting` 时调用 `socket.beginVoiceTurn(id)`，先把 `serverStarted` 置 true、状态置 `recording`，Promise reject 交给 `_failTurn(id,'send_failed')`。`handleAudioFrame` 只接受 `recording/stopping` 且未取消的当前 turn，递增 frameCount 后调用 `sendVoiceFrame`；任何异步 reject 都按捕获的 id 收口，旧 id 不得改变新轮。

`handleAudioFailure(scope)` 对 `record` 或 `encode` 调用当前 turn 的幂等 `_failTurn`；codec 初始化错误不创建 turn，由下次 `press()` 的 `isUsable()` gate 给用户明确提示。

- [ ] **Step 4: 实现 release/cancel/反馈和定时器**

正常 release 固定顺序：

```js
async release() {
  const turn = this.turn;
  if (!turn || !['starting', 'recording'].includes(this.state)) return;
  if (turn.cancelled) return this.cancel('slide-cancel');
  const id = turn.id;
  this._setState('stopping');
  let stop;
  try {
    stop = await this.audio.stopRecord({
      flush: turn.serverStarted,
      reason: turn.serverStarted ? 'release' : 'released-before-start',
    });
  } catch (_err) {
    await this._failTurn(id, 'record_stop_failed');
    return;
  }
  if (!this._isCurrent(id)) return;
  if (!turn.serverStarted) return this._finishLocal('cancelled', 'released-before-start');
  const current = this.turn;
  if (!current || current.frameCount === 0 || stop.timedOut) {
    await this._abortCurrent(id, 'no_audio');
    return;
  }
  await this.socket.finishVoiceTurn(id);
  if (!this._isCurrent(id)) return;
  this._setState('waiting');
  if (this.turn.ackReceived) this._startResponseTimer(id);
  else this._startAckTimer(id);
  if (this.options.onWaiting) this.options.onWaiting(id);
}
```

实现时不得使用上面捕获的旧 `turn.frameCount` 判断尾帧；必须在 stop resolve 后重新读取 `this.turn.frameCount`，因为 `onStop` flush 会同步调用 `handleAudioFrame`。`cancel(reason)` 先把 turn 标 cancelled/状态 `cancelling`，再 await `stopRecord({flush:false})`，若 serverStarted 则 best-effort `abortVoiceTurn`，最后仅在 id 仍匹配时回 idle。

`handleMessage` 的规则：匹配 `stopped` 时，即使当前仍是 `stopping`（服务端反馈早于 SocketTask success callback），也要把 immutable turn 的 `ackReceived` 置 true；进入 `waiting` 后再启动 response timer。匹配的 `no_speech/error/cancelled` 若在 `stopping` 早到，则写入 `pendingTerminal`，finish resolve 后立即终止；在 `waiting` 到达则直接终止。匹配 STT 或 TTS start 视为 recognized，并同样允许在 stopping 早到后延迟收口。无 turnId 的旧服务端 STT/TTS 只在当前恰好是 `waiting` 且 connection generation 未变时接受。所有 timeout、send fail、native stop reject 和断线都调用同一个幂等 `_failTurn`。

- [ ] **Step 5: 运行覆盖率并提交**

```bash
node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-include='main/miniprogram/utils/voice-input-controller.js' main/miniprogram/utils/voice-input-controller.test.js
git add main/miniprogram/utils/voice-input-controller.js main/miniprogram/utils/voice-input-controller.test.js
git diff --cached --check
git diff --cached --stat
git commit -m "feat: add deterministic voice input controller"
```

Expected: PASS；line coverage ≥ 80%；竞态矩阵每条最终都为 idle 或 waiting 后收到确定终态。

---

### Task 4: 聊天页面接入控制器并保留现有权限修复

**Files:**
- Modify: `main/miniprogram/pages/index/index.js`
- Modify: `main/miniprogram/pages/index/index.test.js`

**Interfaces:**
- Consumes: Task 3 `VoiceInputController`；页面既有 touch/lifecycle/WS callbacks。
- Produces: UI `recording/recordCancelled/chatState` 与控制器状态一致；页面不再直接拼 Start/Audio/End/Abort。

- [ ] **Step 1: 先改 page mock 并写失败接线测试**

在 `index.test.js` mock `../../utils/voice-input-controller`，记录 `press/setCancelled/release/cancel/handleRecordStart/handleAudioFrame/handleAudioFailure/handleSocketState/handleMessage/destroy`。断言：

```js
page.onVoiceTouchStart({ touches: [{ clientY: 100 }] });
assert.deepStrictEqual(controllerCalls, ['press']);
page.onVoiceTouchMove({ touches: [{ clientY: 0 }] });
assert.deepStrictEqual(controllerCalls, ['press', ['setCancelled', true]]);
await page.onVoiceTouchEnd();
assert.deepStrictEqual(controllerCalls, ['press', ['setCancelled', true], 'release']);
page.onVoiceTouchCancel();
page._handleAppHide();
page.onUnload();
assert.strictEqual(controllerCalls.filter((x) => x === 'cancel').length, 2);
assert.strictEqual(controllerCalls.includes('destroy'), true);
```

保留并继续通过现有两个权限测试：切换输入模式不预调用 `wx.authorize`；录音权限明确拒绝时打开设置 modal。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `node main/miniprogram/pages/index/index.test.js`

Expected: FAIL，当前页面仍直接调用 manager 的 send/start/stop。

- [ ] **Step 3: 接入 VoiceInputController**

顶部新增 require 和非响应式字段：

```js
const VoiceInputController = require('../../utils/voice-input-controller');

voiceInputController: null,
```

`AudioManager` callbacks 只转发：

```js
onAudioFrame: (frame) => {
  if (this.voiceInputController) this.voiceInputController.handleAudioFrame(frame);
},
onRecordStart: () => {
  if (this.voiceInputController) this.voiceInputController.handleRecordStart();
},
onRecordStop: () => {},
```

在 Audio/WebSocket 都创建后调用 `_initVoiceInputController()`：

```js
this.voiceInputController = new VoiceInputController({
  audio: this.audioManager,
  socket: this.wsManager,
  onStateChange: (state) => this.setData({
    recording: state === 'starting' || state === 'recording' || state === 'stopping',
    recordCancelled: state === 'cancelling' ? true : this.data.recordCancelled,
  }),
  onWaiting: () => this.setData({
    recording: false,
    recordCancelled: false,
    chatState: STATE_THINKING,
  }),
  onTerminal: (outcome) => {
    if (outcome.state !== 'recognized') this.setData({ chatState: STATE_IDLE });
    this.setData({ recording: false, recordCancelled: false });
  },
  onUserError: (title) => wx.showToast({ title, icon: 'none' }),
});
```

AudioManager 的 `onError` 在保留当前 `_handleRecordError` 权限提示和 codec toast 的同时，对 `record/encode` 调用 `voiceInputController.handleAudioFailure(scope)`，确保用户未松手时发生 recorder/encoder 错误也会 Abort 并回 idle。触摸方法仅做页面 guard、Y 位移与 controller 委托；不得直接调用 `sendListenStart/Stop/sendAbort/sendAudioFrame`。WebSocket `onMessage` 先传 `voiceInputController.handleMessage(msg)` 再走原聊天渲染；`onStateChange` 传入 state 和 `getConnectionGeneration()`。`onHide`、`_handleAppHide`、`_teardown` 均调用幂等 cancel；teardown 先 destroy controller，再 destroy managers。

- [ ] **Step 4: 运行页面、控制器和语音通话回归**

```bash
node main/miniprogram/pages/index/index.test.js
node main/miniprogram/utils/voice-input-controller.test.js
node main/miniprogram/pages/voice-call/voice-call.test.js
node main/miniprogram/utils/voice-call-manager.test.js
```

Expected: 全部 PASS；voice-call 继续走旧 auto API；页面权限测试保持 PASS。

- [ ] **Step 5: 检查脏文件边界**

Run: `git diff -- main/miniprogram/pages/index/index.js main/miniprogram/pages/index/index.test.js main/miniprogram/utils/request.js`

Expected: `index.js/index.test.js` 同时包含既有权限改动和本任务接线；`request.js` 只保留用户的 BASE_URL 改动。此任务不整文件 `git add`，待最终交付时明确列为“已修改、未替用户提交”的重叠文件。

---

### Task 5: 服务端不可变 VoiceTurn 领域模型

**Files:**
- Create: `main/xiaozhi-server/core/voice_turn.py`
- Create: `main/xiaozhi-server/tests/__init__.py`
- Create: `main/xiaozhi-server/tests/test_voice_turn.py`

**Interfaces:**
- Consumes: raw `turn_id`、connection generation、manual bytes/control ingress。
- Produces: `VoiceInputKind`、`VoiceInputEvent`、`TurnContext`、`VoiceTurnOutcome`、`VoiceTurnCoordinator.receive_start/receive_audio/receive_end/receive_abort/receive_queue_overflow/snapshot/is_active/complete`。

- [ ] **Step 1: 写纯状态机失败测试**

使用 `unittest.IsolatedAsyncioTestCase` 覆盖：合法 ID/string/int、>64/复杂对象生成内部 ID、旧客户端完全省略 turnId 时生成并复用内部 ID、Start conflict、Audio-before-Start、End 后 Audio、turn mismatch Stop/Abort、queue overflow 只产生一次 Error 且关闭 ingress、不可变 frames tuple、Abort 后迟到结果、complete 只能一次。

```python
class VoiceTurnCoordinatorTest(unittest.TestCase):
    def test_end_snapshot_is_immutable_and_rejects_late_audio(self):
        turns = VoiceTurnCoordinator(connection_generation=7)
        start = turns.receive_start("m-1")
        self.assertEqual(start.turn_id, "m-1")
        turns.consume_start(start)
        turns.consume_audio(turns.receive_audio(b"a"))
        turns.consume_audio(turns.receive_audio(b"b"))
        end = turns.receive_end("m-1")
        snapshot = turns.consume_end(end)
        self.assertEqual(snapshot.frames, (b"a", b"b"))
        self.assertEqual(snapshot.state, "ended")
        self.assertIsNone(turns.receive_audio(b"late"))
        with self.assertRaises(dataclasses.FrozenInstanceError):
            snapshot.frame_count = 9
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd main/xiaozhi-server && .venv/bin/python -m unittest tests.test_voice_turn -v`

Expected: ERROR，`core.voice_turn` 不存在。

- [ ] **Step 3: 创建 immutable dataclasses 和接收/消费双边界**

新模块核心类型固定为：

```python
from dataclasses import dataclass, replace
from enum import Enum
from typing import Callable

class VoiceInputKind(str, Enum):
    START = "start"
    AUDIO = "audio"
    END = "end"
    ABORT = "abort"
    ERROR = "error"

@dataclass(frozen=True)
class VoiceInputEvent:
    kind: VoiceInputKind
    turn_id: str
    payload: bytes | None = None
    reason: str = ""

@dataclass(frozen=True)
class TurnContext:
    turn_id: str
    state: str
    frames: tuple[bytes, ...]
    cancelled: bool
    frame_count: int
    connection_generation: int

@dataclass(frozen=True)
class VoiceTurnOutcome:
    turn_id: str
    state: str
    text: str = ""
    reason: str = ""
```

`VoiceTurnCoordinator` 同时维护 ingress `_receiving_turn_id` 与 consumer `_active`。`receive_start/audio/end/abort` 只创建事件并立即关闭 End 后 ingress；`receive_queue_overflow()` 关闭 ingress 并恰好返回一个 `ERROR(queue_overflow)` event，重复调用返回 None；`consume_*` 只通过 `replace` 创建新 context。`snapshot(turn_id)` 只返回匹配的 immutable context；`is_active(turn_id)` 在 receiving/ended/finalizing 状态均为 true，Abort、Error 或 `complete` 后 false；`complete` 返回 bool，只有首次匹配完成返回 true。

非法 raw id 使用 `internal-{generation}-{monotonic sequence}`；不得把原始复杂对象写日志。Start conflict 返回 `VoiceTurnOutcome(state='error', reason='turn_conflict')`，不覆盖旧活动 turn。

- [ ] **Step 4: 运行测试、trace 覆盖率并提交**

```bash
cd main/xiaozhi-server
.venv/bin/python -m unittest tests.test_voice_turn -v
.venv/bin/python -m trace --count --summary --coverdir /tmp/xiaozhi-voice-turn-coverage --module unittest tests.test_voice_turn
cd ../..
git add main/xiaozhi-server/core/voice_turn.py main/xiaozhi-server/tests/__init__.py main/xiaozhi-server/tests/test_voice_turn.py
git diff --cached --check
git diff --cached --stat
git commit -m "feat: add immutable manual voice turns"
```

Expected: PASS；`core.voice_turn` 可执行行覆盖率 ≥ 80%。

---

### Task 6: manual Start/Audio/End/Abort 统一 FIFO

**Files:**
- Create: `main/xiaozhi-server/core/handle/voiceTurnHandle.py`
- Modify: `main/xiaozhi-server/core/connection.py`
- Modify: `main/xiaozhi-server/core/providers/asr/base.py`
- Modify: `main/xiaozhi-server/core/handle/receiveAudioHandle.py`
- Modify: `main/xiaozhi-server/core/handle/textHandler/listenMessageHandler.py`
- Modify: `main/xiaozhi-server/core/handle/textHandler/abortMessageHandler.py`
- Create: `main/xiaozhi-server/tests/test_manual_voice_pipeline.py`

**Interfaces:**
- Consumes: Task 5 `VoiceInputEvent`；auto 模式仍消费 raw `bytes`。
- Produces: `process_voice_input_event(conn,event)`、`send_listen_feedback(conn,outcome)`；`asr_audio_queue` 类型为 `bytes | VoiceInputEvent`。

- [ ] **Step 1: 写消费者延迟、顺序和容量失败测试**

测试 fake provider 在 Audio 消费时 `await asyncio.sleep(0)`，向 queue 依次放 Start、A、B、End，断言日志严格为 `start,audio:a,audio:b,end` 且 stopped ack 在 B 之后。把 100 个 Audio 填满 audio budget，再放第 101 个，断言整轮只产生 `error(queue_overflow)`，End/Error sentinel 仍成功入队；不得出现“丢第一帧后继续”。自动模式 raw bytes 仍调用 `handleAudioMessage`。

handler 测试直接调用：

```python
await ListenTextMessageHandler().handle(conn, {
    "type": "listen", "mode": "manual", "state": "start", "turn_id": "m-1"
})
conn._put_asr_audio(b"tail")
await ListenTextMessageHandler().handle(conn, {
    "type": "listen", "mode": "manual", "state": "stop", "turn_id": "m-1"
})
self.assertEqual([item.kind for item in queued], [
    VoiceInputKind.START, VoiceInputKind.AUDIO, VoiceInputKind.END,
])
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd main/xiaozhi-server && .venv/bin/python -m unittest tests.test_manual_voice_pipeline -v`

Expected: FAIL；当前 Stop 直接检查 `conn.asr_audio`，不在 queue 中。

- [ ] **Step 3: 改造 connection ingress 与保留容量**

`ConnectionHandler.__init__`：

```python
self.connection_generation = 1
self.voice_turns = VoiceTurnCoordinator(self.connection_generation)
self.asr_audio_queue = queue.Queue(maxsize=104)
self.manual_audio_queue_limit = 100
self.manual_pending_audio = 0
```

`_put_asr_audio` 分流：manual 模式调用 `voice_turns.receive_audio(audio_data)`；若无活动 ingress 只累加安全计数并丢弃。`manual_pending_audio` 达 100 时调用 `voice_turns.receive_queue_overflow()`，拒绝后续 Audio 并用 `put_nowait(ERROR event)`；成功入队 Audio 时加一，consumer 在 `finally` 中减一并调用 `queue.task_done()`。因此 Start 不占音频 budget，104 总容量始终至少为 Start/End/Abort/Error 保留 4 格。auto 模式保留 raw bytes 与既有 drop-oldest 策略；若队头意外是 `VoiceInputEvent` 则 fail safe 拒绝本次 auto frame，不得弹出控制事件。

新增 `_put_voice_control(event)` 使用 `put_nowait`；若预留容量也异常耗尽，记录错误并通过 event loop 直接发送 error，不能静默。

- [ ] **Step 4: handler 只做 ingress，不做最终化**

`listenMessageHandler` 的 `detect` 分支保持原样。manual start/stop 改为：

```python
if state == "start" and conn.client_listen_mode == "manual":
    event_or_error = conn.voice_turns.receive_start(msg_json.get("turn_id"))
    await conn.enqueue_voice_ingress(event_or_error)
    return
if state == "stop" and conn.client_listen_mode == "manual":
    event_or_error = conn.voice_turns.receive_end(msg_json.get("turn_id"))
    await conn.enqueue_voice_ingress(event_or_error)
    return
```

manual abort 携带 turn_id 时创建 Abort event；没有活动 manual turn 或 legacy 全局打断仍调用既有 `handleAbortMessage(conn)`，保证设备/语音通话兼容。

- [ ] **Step 5: consumer 识别 union 并发送 stopped ack**

`ASRProviderBase.asr_text_priority_thread`：

```python
message = conn.asr_audio_queue.get(timeout=1)
handler = (
    process_voice_input_event(conn, message)
    if isinstance(message, VoiceInputEvent)
    else handleAudioMessage(conn, message)
)
future = asyncio.run_coroutine_threadsafe(handler, conn.loop)
future.result()
```

上述处理放在 `try/finally`：若 `message` 是 AUDIO event，则调用 `conn.release_manual_audio_slot()`；所有成功 get 的元素最终都调用 `conn.asr_audio_queue.task_done()`。

`send_listen_feedback` 不读取当前全局音频状态，只序列化给定 outcome：

```python
async def send_listen_feedback(conn, outcome: VoiceTurnOutcome) -> None:
    payload = {
        "type": "listen",
        "state": outcome.state,
        "turn_id": outcome.turn_id,
    }
    if outcome.reason:
        payload["reason"] = outcome.reason
    await conn.websocket.send(json.dumps(payload, ensure_ascii=False))
```

`process_voice_input_event` 中 Start 重置 VAD 状态但不清 FIFO，并把 `conn.just_woken_up=False`、取消尚未完成的 `vad_resume_task`，避免 manual 新轮前 2 秒被旧会话的唤醒保护静默丢弃；Audio 先 append immutable frame 再调用 `handleAudioMessage(conn,payload,turn_id)`；End 取得完整 snapshot，发送 stopped ack 的时机交给 Task 7/8 finalizer；Abort 先使 context 无效，再清理匹配 provider 并发送 cancelled。所有清理都传 turn_id。

`handleAudioMessage` 与 base provider 扩展可选参数但保持旧调用兼容：

```python
async def handleAudioMessage(conn, audio, turn_id: str | None = None):
    have_voice = conn.vad.is_vad(conn, audio)
    if turn_id is None and getattr(conn, "just_woken_up", False):
        # existing auto-mode wake protection follows
        return
    await no_voice_close_connect(conn, have_voice)
    await conn.asr.receive_audio(conn, audio, have_voice, turn_id=turn_id)

async def receive_audio(self, conn, audio, audio_have_voice, turn_id=None):
    if turn_id is not None and conn.client_listen_mode == "manual":
        return
    # existing auto-mode buffering and VAD finalization follows
```

四个 stream override 在 Task 8 接收同一可选参数；在那之前 Task 6 的 fake provider 证明 FIFO，不调用远程 provider。

- [ ] **Step 6: 运行测试并提交**

```bash
cd main/xiaozhi-server
.venv/bin/python -m unittest tests.test_voice_turn tests.test_manual_voice_pipeline -v
cd ../..
git add main/xiaozhi-server/core/handle/voiceTurnHandle.py main/xiaozhi-server/core/connection.py main/xiaozhi-server/core/providers/asr/base.py main/xiaozhi-server/core/handle/receiveAudioHandle.py main/xiaozhi-server/core/handle/textHandler/listenMessageHandler.py main/xiaozhi-server/core/handle/textHandler/abortMessageHandler.py main/xiaozhi-server/tests/test_manual_voice_pipeline.py
git diff --cached --check
git diff --cached --stat
git commit -m "fix: order manual voice controls with audio"
```

Expected: PASS；延迟消费者也无法让 End 越过 Audio；auto bytes 回归通过。

---

### Task 7: 非流式 ASR 最终化与下游 turn 门控

**Files:**
- Modify: `main/xiaozhi-server/core/providers/asr/base.py`
- Modify: `main/xiaozhi-server/core/handle/voiceTurnHandle.py`
- Modify: `main/xiaozhi-server/core/handle/receiveAudioHandle.py`
- Modify: `main/xiaozhi-server/core/handle/reportHandle.py`
- Modify: `main/xiaozhi-server/core/handle/sendAudioHandle.py`
- Create: `main/xiaozhi-server/tests/test_asr_voice_turn.py`

**Interfaces:**
- Consumes: End 时的 immutable `TurnContext`。
- Produces: `recognize_manual_turn(conn,turn)->VoiceTurnOutcome`；turn-aware report/STT/startToChat。

- [ ] **Step 1: 写完整快照、空结果、异常和陈旧结果测试**

Fake non-stream provider 记录收到的 frames；End 后立刻建立 Abort/新 turn，让旧 ASR future 延迟返回。断言：完整 `(a,b,tail)` 只传一次；recognized 仅在 turn 仍 active 时调用 report/STT/chat；空文本发送 `listen:no_speech`；异常发送 `listen:error reason=asr_failed`；旧结果不触发任何副作用。

```python
outcome = await provider.recognize_manual_turn(conn, turn)
self.assertEqual(outcome.state, "recognized")
self.assertEqual(outcome.turn_id, "m-1")
self.assertEqual(provider.received_frames, (b"a", b"b", b"tail"))
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd main/xiaozhi-server && .venv/bin/python -m unittest tests.test_asr_voice_turn.NonStreamVoiceTurnTest -v`

Expected: FAIL，当前 `handle_voice_stop` 吞异常且没有 outcome/turnId。

- [ ] **Step 3: 从既有 handle_voice_stop 抽出 manual outcome**

`ASRProviderBase.recognize_manual_turn` 使用 `list(turn.frames)` 调现有 decode/speech_to_text/voiceprint 逻辑，但不直接 report/chat；统一返回：

```python
if not turn.frames:
    return VoiceTurnOutcome(turn.turn_id, "no_speech", reason="empty_audio")
try:
    raw_text, speaker = await self._recognize_audio(conn, list(turn.frames))
except Exception:
    return VoiceTurnOutcome(turn.turn_id, "error", reason="asr_failed")
enhanced_text, text_len = self._normalize_asr_result(raw_text, speaker)
if text_len == 0:
    return VoiceTurnOutcome(turn.turn_id, "no_speech", reason="empty_result")
return VoiceTurnOutcome(turn.turn_id, "recognized", text=enhanced_text)
```

原 auto VAD 的 `handle_voice_stop` 继续调用同一 `_recognize_audio/_normalize_asr_result` helper 并保持原阈值与行为。

End 消费者先发送 stopped ack，然后 `asyncio.create_task(finalize_non_stream_turn(conn,turn))`；任务取得 outcome 后先 `voice_turns.is_active(turn_id)`，recognized 才依次执行 report 和 `await startToChat(...,turn_id)`，最后 `complete`；其余 outcome 先 `complete` 再发对应 listen 终态。

- [ ] **Step 4: 给三处副作用增加可选 turn_id**

签名固定为：

```python
def enqueue_asr_report(conn, text, opus_data, turn_id: str | None = None):
    if turn_id is not None and not conn.voice_turns.is_active(turn_id):
        return
    # existing report enqueue follows

async def send_stt_message(conn, text, turn_id: str | None = None):
    if turn_id is not None and not conn.voice_turns.is_active(turn_id):
        return
    message = {"type": "stt", "text": stt_text, "session_id": conn.session_id}
    if turn_id is not None:
        message["turn_id"] = turn_id
    await conn.websocket.send(json.dumps(message))

async def startToChat(conn, text, turn_id: str | None = None):
    if turn_id is not None and not conn.voice_turns.is_active(turn_id):
        return
    intent_handled = await handle_user_intent(conn, actual_text)
    if turn_id is not None and not conn.voice_turns.is_active(turn_id):
        return
    if intent_handled:
        return
    await send_stt_message(conn, actual_text, turn_id=turn_id)
    if turn_id is not None and not conn.voice_turns.is_active(turn_id):
        return
    conn.client_abort = False
    conn.executor.submit(conn.chat, actual_text)
```

保留所有原调用默认 `turn_id=None`。manual 新日志不输出 `text`。

- [ ] **Step 5: 运行测试与提交**

```bash
cd main/xiaozhi-server
.venv/bin/python -m unittest tests.test_voice_turn tests.test_manual_voice_pipeline tests.test_asr_voice_turn.NonStreamVoiceTurnTest -v
cd ../..
git add main/xiaozhi-server/core/providers/asr/base.py main/xiaozhi-server/core/handle/voiceTurnHandle.py main/xiaozhi-server/core/handle/receiveAudioHandle.py main/xiaozhi-server/core/handle/reportHandle.py main/xiaozhi-server/core/handle/sendAudioHandle.py main/xiaozhi-server/tests/test_asr_voice_turn.py
git diff --cached --check
git diff --cached --stat
git commit -m "fix: scope asr finalization to one voice turn"
```

Expected: PASS；Abort/新 turn 后的旧 ASR 结果没有 report、STT 或 chat。

---

### Task 8: 四个流式 ASR provider 的统一 End/outcome 合约

**Files:**
- Modify: `main/xiaozhi-server/core/providers/asr/base.py`
- Modify: `main/xiaozhi-server/core/handle/voiceTurnHandle.py`
- Modify: `main/xiaozhi-server/core/providers/asr/xunfei_stream.py`
- Modify: `main/xiaozhi-server/core/providers/asr/aliyun_stream.py`
- Modify: `main/xiaozhi-server/core/providers/asr/aliyunbl_stream.py`
- Modify: `main/xiaozhi-server/core/providers/asr/doubao_stream.py`
- Modify: `main/xiaozhi-server/tests/test_asr_voice_turn.py`

**Interfaces:**
- Consumes: `end_turn(conn,turn,on_stop_written)->VoiceTurnOutcome`；provider-specific final response。
- Produces: stopped ack 只在 stop request 写入云端后发送；recognized/no_speech/error outcome 恰好一次。

- [ ] **Step 1: 为公共 contract 和四个 provider 写失败测试**

Fake websocket 的 `send` 用 Event 阻塞，断言 coordinator 在 Event 放行前没有发送 stopped；放行后先 stopped，再最终 outcome。每个真实 provider 通过 mock response 测：

- Xunfei：`status == 2` 且 text / 空 text / code error。
- Aliyun：StopTranscription 已写、SentenceEnd text、TranscriptionCompleted 空结果、非成功 status。
- AliyunBL：finish-task 已写、result-generated text、task-finished 空文本、task-failed。
- Doubao：last audio frame 已写、最终 utterance、code 1013、连接关闭/异常。

每例都断言 outcome future 只 resolve 一次，旧 `finally` 不调用无条件 `conn.reset_audio_states()`。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd main/xiaozhi-server && .venv/bin/python -m unittest tests.test_asr_voice_turn.StreamVoiceTurnTest -v`

Expected: FAIL；provider 仍依赖 `conn.client_voice_stop/conn.asr_audio` 和全局 `self.text`。

- [ ] **Step 3: 在 ASRProviderBase 增加 per-turn stream future**

公共 contract：

```python
def begin_stream_turn(self, turn_id: str) -> None:
    loop = asyncio.get_running_loop()
    self._stream_turn_id = turn_id
    self._stream_outcome = loop.create_future()
    self._stream_end_requested = False

def resolve_stream_turn(self, outcome: VoiceTurnOutcome) -> bool:
    if outcome.turn_id != getattr(self, "_stream_turn_id", None):
        return False
    future = getattr(self, "_stream_outcome", None)
    if future is None or future.done():
        return False
    future.set_result(outcome)
    return True

async def end_turn(self, conn, turn: TurnContext, on_stop_written) -> VoiceTurnOutcome:
    if turn.turn_id != getattr(self, "_stream_turn_id", None):
        on_stop_written()
        return VoiceTurnOutcome(turn.turn_id, "cancelled", reason="turn_mismatch")
    self._stream_end_requested = True
    if not getattr(self, "asr_ws", None):
        on_stop_written()
        return VoiceTurnOutcome(turn.turn_id, "no_speech", reason="stream_not_started")
    try:
        await self._send_stop_request()
        on_stop_written()
        return await asyncio.wait_for(asyncio.shield(self._stream_outcome), 30.0)
    except asyncio.TimeoutError:
        return VoiceTurnOutcome(turn.turn_id, "error", reason="asr_timeout")
    except Exception:
        return VoiceTurnOutcome(turn.turn_id, "error", reason="asr_failed")
```

`abort_turn(turn_id)` 只在 `_stream_turn_id` 匹配时 resolve cancelled 并关闭本 provider session；cleanup 接收 expected turn_id，只有匹配才清 `_stream_turn_id/_stream_outcome/self.text`。

`process_voice_input_event` 消费 stream Start 时调用 `conn.asr.begin_stream_turn(turn_id)`；Abort 时调用 `await conn.asr.abort_turn(turn_id)`。这两个调用都在 FIFO consumer 上发生，不能在 ingress handler 抢先执行。

- [ ] **Step 4: provider-specific result 只 resolve outcome**

四个 provider 的 `receive_audio` 增加可选 `turn_id` 并在 manual 首帧时校验 `_stream_turn_id`。删除 manual 路径对 `conn.client_voice_stop`、`conn.asr_audio` 的读取；缓存帧来自 `conn.voice_turns.snapshot(turn_id).frames[-10:]`。

最终事件统一执行；manual 分支不再打印识别文本，既有 auto 分支日志保持不变：

```python
state = "recognized" if self.text.strip() else "no_speech"
self.resolve_stream_turn(VoiceTurnOutcome(
    turn_id=turn_id,
    state=state,
    text=self.text.strip(),
    reason="" if state == "recognized" else "empty_result",
))
```

1013、task-finished 无文本、TranscriptionCompleted 无文本 resolve `no_speech`；协议错误、超时、连接在 End 后关闭 resolve `error`。中间结果只更新本 turn 的文本快照，不启动 chat。provider 的 finally 调 `_cleanup(expected_turn_id=turn_id)`，不得重置 connection 新 turn。

- [ ] **Step 5: coordinator 分离 stop-written ack 和最终 outcome**

stream End：

```python
stop_written = asyncio.Event()
end_task = asyncio.create_task(
    conn.asr.end_turn(conn, turn, stop_written.set)
)
await asyncio.wait_for(stop_written.wait(), timeout=2.0)
await send_listen_feedback(conn, VoiceTurnOutcome(turn.turn_id, "stopped"))
asyncio.create_task(finalize_stream_turn(conn, turn, end_task))
```

stop request 写失败/2 秒内未写，取消 end task 并完成 error；`finalize_stream_turn` 复用 Task 7 的 publish 门控。

- [ ] **Step 6: 运行测试、语法检查和提交**

```bash
cd main/xiaozhi-server
.venv/bin/python -m unittest tests.test_voice_turn tests.test_manual_voice_pipeline tests.test_asr_voice_turn -v
.venv/bin/python -m compileall -q core tests
cd ../..
git add main/xiaozhi-server/core/providers/asr/base.py main/xiaozhi-server/core/handle/voiceTurnHandle.py main/xiaozhi-server/core/providers/asr/xunfei_stream.py main/xiaozhi-server/core/providers/asr/aliyun_stream.py main/xiaozhi-server/core/providers/asr/aliyunbl_stream.py main/xiaozhi-server/core/providers/asr/doubao_stream.py main/xiaozhi-server/tests/test_asr_voice_turn.py
git diff --cached --check
git diff --cached --stat
git commit -m "fix: finalize streaming asr by voice turn"
```

Expected: 全部 PASS；四个 provider 的 stop-written 与 outcome 顺序一致；compileall 无输出。

---

### Task 9: 端到端竞态回归、覆盖率、图谱与审查

**Files:**
- Modify as required by failing tests only: Task 1–8 listed files
- Update generated graph: `graphify-out/`

**Interfaces:**
- Consumes: 完整客户端/服务端实现。
- Produces: 对设计中 6 条不变量的可重复验证证据。

- [ ] **Step 1: 增加跨组件顺序测试**

客户端测试用真实 `VoiceInputController` + fake AudioManager + 真实 `WebSocketManager`/fake SocketTask，模拟 `touchend -> native onStart -> native onStop tail -> send callbacks`，断言 wire 顺序严格为 Start、所有 Audio、End；断线后旧 callbacks 不向新 Socket 发送。

服务端测试用真实 coordinator + queue consumer + fake nonstream/stream provider，消费者在 Audio 处主动等待 Event；End 已入 queue 时 ASR finalizer 仍不可运行，放行 Audio 后才依次出现 End、stopped、outcome。

- [ ] **Step 2: 运行全部相关小程序测试**

```bash
node main/miniprogram/utils/audio.test.js
node main/miniprogram/utils/websocket.test.js
node main/miniprogram/utils/voice-input-controller.test.js
node main/miniprogram/pages/index/index.test.js
node main/miniprogram/pages/voice-call/voice-call.test.js
node main/miniprogram/utils/voice-call-manager.test.js
```

Expected: 全部退出码 0，输出 PASS；无未处理 Promise rejection。

- [ ] **Step 3: 运行小程序新增模块 80% coverage gate**

```bash
node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-include='main/miniprogram/utils/audio.js' --test-coverage-include='main/miniprogram/utils/websocket.js' --test-coverage-include='main/miniprogram/utils/voice-input-controller.js' main/miniprogram/utils/audio.test.js main/miniprogram/utils/websocket.test.js main/miniprogram/utils/voice-input-controller.test.js main/miniprogram/pages/index/index.test.js
```

Expected: PASS；三个 production module 合计 line coverage ≥ 80%。

- [ ] **Step 4: 运行服务端全套 voice turn 测试和 targeted trace**

```bash
cd main/xiaozhi-server
.venv/bin/python -m unittest discover -s tests -p 'test_*voice*.py' -v
.venv/bin/python -m trace --count --summary --coverdir /tmp/xiaozhi-voice-turn-coverage --module unittest discover -s tests -p 'test_*voice*.py'
.venv/bin/python -m compileall -q core tests
cd ../..
```

Expected: unittest 全部 PASS；`core/voice_turn.py` 与 `core/handle/voiceTurnHandle.py` 各自可执行行覆盖率 ≥ 80%；compileall 无输出。

- [ ] **Step 5: 逐条审计设计不变量**

把每条映射到测试名并核对当前输出：

1. `test_started_turn_has_exactly_one_end_or_abort`。
2. `test_end_waits_for_all_audio_consumption`。
3. `test_audio_after_end_is_rejected`。
4. `test_only_one_manual_turn_per_connection` 与 controller overlap test。
5. error/cancel/disconnect/timeout parameterized terminal test。
6. auto voice-call regressions 与四 provider contract tests。

任何一条只有代码推断、没有测试输出时都不得宣称完成，必须先补测试。

- [ ] **Step 6: 更新 graphify 图谱**

先完整读取 `/Users/minwang/.agents/skills/graphify/SKILL.md`，按其 update 路由更新已有 `graphify-out/`，然后查询 `VoiceInputController -> WebSocketManager -> VoiceTurnCoordinator -> ASRProviderBase` 链路，确认新增文件和调用边都可检索。不得手工编辑生成图谱。

- [ ] **Step 7: 执行代码、语言和安全审查**

按项目规则依次使用：

- `code-reviewer`：全量 diff、状态机不变量、错误路径。
- `typescript-reviewer`：小程序 JS 的 Promise、timer、陈旧 callback 和 SocketTask 安全。
- `python-reviewer`：dataclass、asyncio/thread bridge、异常传播和资源清理。
- `security-reviewer`：turn_id 输入校验、日志脱敏、队列 DoS 与连接隔离。

每个 actionable finding 先写失败测试再修复；修复后重跑 Step 2–4。

- [ ] **Step 8: 最终工作区与提交边界检查**

```bash
git diff --check
git status --short
git diff -- main/miniprogram/utils/request.js
git diff -- main/miniprogram/pages/index/index.js main/miniprogram/pages/index/index.test.js
git log --oneline -10
```

Expected: 无 whitespace error；`request.js` 仍只有用户 BASE_URL 改动；两个 index 文件保留用户权限改动和本次 controller 接线；提交历史均为小而聚焦的 conventional commits，没有误收用户文件。

- [ ] **Step 9: 完成前验证**

使用 `superpowers:verification-before-completion` 重新运行其要求的最新验证命令，以当前输出而不是先前结果作为完成证据。只有所有设计要求、测试、coverage、审查和 graphify 均有直接证据时，才把目标标记 complete。
