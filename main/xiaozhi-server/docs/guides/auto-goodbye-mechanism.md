# 自动告别机制技术文档

## 功能概述

当用户在对话过程中长时间（默认 120 秒）不说话时，系统会自动发起告别对话，优雅地结束本次会话。

### 核心特性

- ✅ **基于 VAD 检测**：使用 Voice Activity Detection 算法判断是否有人声
- ✅ **事件驱动架构**：无需定时任务，每个音频帧都触发检测
- ✅ **优雅降级**：可配置是否启用结束语
- ✅ **自动保存记忆**：告别后会触发记忆保存流程

---

## 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端（客户端）                              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AudioRecorder (recorder.js)                             │   │
│  │  • 持续采集麦克风音频（16kHz, 单声道）                   │   │
│  │  • 每 960 个采样点（约 60ms）编码成一帧 Opus 数据         │   │
│  │  • 通过 WebSocket 持续发送音频帧                         │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────────┘
                         │ WebSocket 音频流
                         │ (60ms/帧，持续发送)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    服务端（xiaozhi-server）                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 1. WebSocket 接收层 (connection.py)                      │   │
│  │    async for message in websocket:                      │   │
│  │      asr_audio_queue.put(message)                       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 2. ASR 处理线程 (base.py)                                │   │
│  │    while True:                                          │   │
│  │      audio = asr_audio_queue.get()                      │   │
│  │      handleAudioMessage(conn, audio)                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 3. 音频消息处理 (receiveAudioHandle.py)                 │   │
│  │    handleAudioMessage():                                │   │
│  │      • VAD 检测当前帧是否有人声                          │   │
│  │      • 调用 no_voice_close_connect() 检查超时            │   │
│  │      • 传递给 ASR 进行语音识别                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 4. 超时检测逻辑 (receiveAudioHandle.py:99-123)         │   │
│  │    no_voice_close_connect():                            │   │
│  │      if have_voice:                                     │   │
│  │        last_activity_time = 当前时间                     │   │
│  │      else:                                              │   │
│  │        no_voice_time = 当前时间 - last_activity_time    │   │
│  │        if no_voice_time > 120秒:                         │   │
│  │          • 发送告别提示词给 LLM                          │   │
│  │          • 设置 close_after_chat = True                 │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 核心流程

### 流程图

```
开始
  │
  ▼
┌─────────────────────────┐
│  用户拨号建立 WebSocket 连接  │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ 前端持续采集并发送音频帧  │
│ (60ms/帧，持续发送)      │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ 服务端接收音频帧        │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ VAD 检测当前帧是否有人声 │
└──────────┬──────────────┘
           │
     ┌──────┴──────┐
     │             │
   有人声        无人声
     │             │
     ▼             ▼
┌─────────────┐  ┌──────────────────────┐
│更新活动时间戳│  │计算沉默时长            │
│last_        │  │no_voice_time =        │
│activity_time│  │当前时间 - last_        │
└─────────────┘  │activity_time         │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │no_voice_time > 120秒？│
                 └──────────┬───────────┘
                            │
                      ┌──────┴──────┐
                      │             │
                     否            是
                      │             │
                      │             ▼
                      │    ┌──────────────────┐
                      │    │发送告别提示词给LLM│
                      │    │close_after_chat  │
                      │    │= True           │
                      │    └────────┬─────────┘
                      │             │
                      │             ▼
                      │    ┌──────────────────┐
                      │    │LLM 生成告别回复   │
                      │    └────────┬─────────┘
                      │             │
                      │             ▼
                      │    ┌──────────────────┐
                      │    │TTS 播放告别音频    │
                      │    └────────┬─────────┘
                      │             │
                      │             ▼
                      │    ┌──────────────────┐
                      │    │播放完成，自动挂断 │
                      │    │→ 保存记忆        │
                      │    └──────────────────┘
                      │
                      └──────────────► (继续下一帧)
```

---

## 代码实现

### 前端：持续采集和发送音频

**文件**：`test/js/core/audio/recorder.js`

#### 1. PCM 音频处理

```javascript
// Line 148-160
processPCMBuffer(buffer) {
    if (!this.isRecording) return;

    const newBuffer = new Int16Array(this.pcmDataBuffer.length + buffer.length);
    newBuffer.set(this.pcmDataBuffer);
    newBuffer.set(buffer, self.pcmDataBuffer.length);
    self.pcmDataBuffer = newBuffer;

    const samplesPerFrame = 960;  // 每 960 个采样点为一帧（约 60ms @ 16kHz）
    while (this.pcmDataBuffer.length >= samplesPerFrame) {
        const frameData = this.pcmDataBuffer.slice(0, samplesPerFrame);
        this.pcmDataBuffer = this.pcmDataBuffer.slice(samplesPerFrame);
        this.encodeAndSendOpus(frameData);  // ← 编码并发送
    }
}
```

#### 2. Opus 编码和发送

```javascript
// Line 163-180
encodeAndSendOpus(pcmData = null) {
    if (pcmData) {
        const opusData = this.opusEncoder.encode(pcmData);  // Opus 编码
        if (opusData && opusData.length > 0) {
            this.audioBuffers.push(opusData.buffer);
            this.totalAudioSize += opusData.length;

            // ← 关键：持续发送，不管是否有人声
            if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                try {
                    this.websocket.send(opusData.buffer);
                } catch (error) {
                    log(`WebSocket发送错误: ${error.message}`, 'error');
                }
            }
        }
    }
}
```

#### 3. 麦克风持续采集

```javascript
// Line 202-276
async start() {
    // 获取麦克风权限
    const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
            echoCancellation: true,
            noiseSuppression: true,
            sampleRate: 16000,   // 16kHz 采样率
            channelCount: 1      // 单声道
        }
    });

    // 创建音频处理器
    const processorResult = await this.createAudioProcessor();
    this.audioProcessor = processorResult.node;

    // 连接音频流
    this.audioSource = this.audioContext.createMediaStreamSource(stream);
    this.audioSource.connect(this.audioProcessor);

    this.isRecording = true;  // ← 开始持续采集

    // AudioWorklet/ScriptProcessor 会持续调用 processPCMBuffer
}
```

---

### 服务端：接收和检测

#### 1. WebSocket 消息接收

**文件**：`core/connection.py`

```python
# Line 219-220
async def handle_connection(self, ws):
    async for message in self.websocket:  # ← 持续接收消息
        await self._route_message(message)

# Line 342-355
async def _route_message(self, message):
    if isinstance(message, str):
        await handleTextMessage(self, message)
    elif isinstance(message, bytes):
        # ← 音频消息：放入 ASR 队列
        self.asr_audio_queue.put(message)
```

#### 2. ASR 音频处理线程

**文件**：`core/providers/asr/base.py`

```python
# Line 46-51
while True:
    message = conn.asr_audio_queue.get(timeout=1)  # ← 取出音频帧
    future = asyncio.run_coroutine_threadsafe(
        handleAudioMessage(conn, message),          # ← 处理音频帧
        conn.loop,
    )
    future.result()
```

#### 3. 音频消息处理

**文件**：`core/handle/receiveAudioHandle.py`

```python
# Line 17-30
async def handleAudioMessage(conn: "ConnectionHandler", audio):
    # VAD 检测：当前音频帧是否有人声
    have_voice = conn.vad.is_vad(conn, audio)

    # ← 关键：每个音频帧都检查一次
    await no_voice_close_connect(conn, have_voice)

    # 传递给 ASR 进行语音识别
    await conn.asr.receive_audio(conn, audio, have_voice)
```

#### 4. 超时检测逻辑

**文件**：`core/handle/receiveAudioHandle.py`

```python
# Line 99-123
async def no_voice_close_connect(conn: "ConnectionHandler", have_voice):
    # 情况 1：检测到人声
    if have_voice:
        # ← 更新最后活动时间戳（重置计时）
        conn.last_activity_time = time.time() * 1000
        return

    # 情况 2：无人声，检查是否超时
    # 只有在时间戳已初始化的情况下才检查
    if conn.last_activity_time > 0.0:
        no_voice_time = time.time() * 1000 - conn.last_activity_time
        close_connection_no_voice_time = int(
            conn.config.get("close_connection_no_voice_time", 120)  # 默认 120 秒
        )

        # ← 关键判断：是否超时
        if (
            not conn.close_after_chat  # 避免重复触发
            and no_voice_time > 1000 * close_connection_no_voice_time
        ):
            # 标记：聊天结束后关闭连接
            conn.close_after_chat = True
            conn.client_abort = False

            # 检查是否启用结束语
            end_prompt = conn.config.get("end_prompt", {})
            if end_prompt and end_prompt.get("enable", True) is False:
                conn.logger.bind(tag=TAG).info("结束对话，无需发送结束提示语")
                await conn.close()
                return

            # 获取结束语提示词
            prompt = end_prompt.get("prompt")
            if not prompt:
                # ← 默认结束语
                prompt = "请你以```时间过得真快```未来头，用富有感情、依依不舍的话来结束这场对话吧。！"

            # ← 发送给 LLM，触发告别对话
            await startToChat(conn, prompt)
```

#### 5. 发送告别提示词

```python
# Line 39-96
async def startToChat(conn: "ConnectionHandler", text):
    # ...（省略 JSON 解析和意图处理）

    # ← 关键：作为普通用户消息发送给 LLM
    await send_stt_message(conn, actual_text)

    # 准备开始新会话
    conn.client_abort = False

    # ← 提交给聊天处理（调用 LLM）
    conn.executor.submit(conn.chat, actual_text)
```

---

## 配置说明

### 配置文件

**文件**：`config.yaml`

```yaml
# Line 61: 无声音自动断连时间（秒）
close_connection_no_voice_time: 120

# Line 212-216: 结束语配置
end_prompt:
  enable: true  # 是否开启结束语
  prompt: |
    请你以"时间过得真快"未来头，用富有感情、依依不舍的话来结束这场对话吧！
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `close_connection_no_voice_time` | int | 120 | 无声音超时时间（秒），超过此时间自动触发告别 |
| `end_prompt.enable` | bool | true | 是否启用结束语，false 则直接关闭连接 |
| `end_prompt.prompt` | string | (见默认值) | 发送给 LLM 的告别提示词 |

---

## 关键变量

### ConnectionHandler 类属性

**文件**：`core/connection.py`

| 变量 | 类型 | 初始值 | 说明 |
|------|------|--------|------|
| `last_activity_time` | float | `0.0` | 最后活动时间戳（毫秒），每次检测到人声时更新 |
| `first_activity_time` | float | (当前时间) | 首次活动时间戳（毫秒），连接建立时初始化 |
| `close_after_chat` | bool | `False` | 标记是否在聊天结束后自动关闭连接 |
| `timeout_seconds` | int | `180` | 连接总超时时间（秒），120 秒无声音 + 60 秒缓冲 |

### 初始化时机

```python
# Line 127-128
self.last_activity_time = 0.0  # ← 初始为 0.0，表示未初始化
self.vad_last_voice_time = 0.0

# Line 202-203
self.first_activity_time = time.time() * 1000
self.last_activity_time = time.time() * 1000  # ← 连接建立时初始化

# Line 206
self.timeout_task = asyncio.create_task(self._check_timeout())  # ← 启动超时检查任务
```

---

## 工作机制详解

### 1. 事件驱动 vs 定时任务

**当前实现**：**事件驱动**

| 特性 | 事件驱动（当前） | 定时任务 |
|------|-----------------|----------|
| **触发方式** | 每次收到音频帧时检查 | 每隔固定时间检查（如每秒） |
| **检查频率** | 动态（约 16.7 Hz，60ms/帧） | 固定（如 1 Hz） |
| **CPU 占用** | 无空转（有音频才检查） | 有空转（即使无音频也在检查） |
| **实时性** | 几乎实时（帧级别） | 最多延迟一个定时周期 |
| **实现复杂度** | 低（复用音频处理流程） | 中（需要独立定时器） |

**为什么不用定时任务？**

1. 音频流本身是持续的（60ms 间隔）
2. 每个音频帧都要处理（VAD 检测、ASR 识别）
3. 顺手检查超时，无需额外开销
4. 更实时（无需等待定时器触发）

---

### 2. VAD（Voice Activity Detection）

**作用**：判断音频帧是否包含人声

**实现**：`core/providers/vad/silero.py`（基于 Silero VAD 模型）

```python
def is_vad(self, conn: "ConnectionHandler", audio):
    # 调用 Silero VAD 模型
    # 返回：True（有人声）/ False（无人声）
```

**特性**：
- 基于深度学习模型
- 准确率高，能区分人声和背景噪音
- 对静音、音乐、环境音有较好的抗干扰能力

---

### 3. 时间戳更新机制

**更新时机**：每次 VAD 检测到人声

```python
if have_voice:
    conn.last_activity_time = time.time() * 1000  # ← 更新为当前时间
```

**不更新的情况**：
- VAD 检测为无人声（静音、背景噪音）
- 音频流中断（网络问题、设备静音）

---

### 4. 超时判断逻辑

```python
no_voice_time = time.time() * 1000 - conn.last_activity_time

if no_voice_time > 1000 * close_connection_no_voice_time:
    # ← 超时了！触发告别
```

**关键点**：
- 使用毫秒级时间戳（`time.time() * 1000`）
- 单位转换：`close_connection_no_voice_time` 是秒，乘以 1000 转为毫秒
- 避免重复触发：检查 `not conn.close_after_chat`

---

### 5. 自动关闭流程

```
触发超时
  │
  ▼
设置 close_after_chat = True
  │
  ▼
发送告别提示词给 LLM
  │
  ▼
LLM 生成告别回复
  │
  ▼
TTS 播放告别音频
  │
  ▼
检测到 close_after_chat = True
  │
  ▼
调用 conn.close()
  │
  ▼
触发 _save_and_close()
  │
  ▼
保存对话记忆到 PowerMem
  │
  ▼
关闭 WebSocket 连接
```

---

## 时序图

### 正常对话流程

```
用户      前端       WebSocket    服务端      VAD      检测逻辑
 │         │            │           │         │         │
 │说话     │            │           │         │         │
 ├────────→│采集音频    │           │         │         │
 │         │编码 Opus   │           │         │         │
 │         ├──────────→│[帧1]      │         │         │
 │         │            │           ├────────→│ ✓有人声 │
 │         │            │           │         ├────────→│更新时间戳
 │         │            │           │         │         │00:00
 │         │            │           │         │         │
 │(沉默)   │采集音频    │           │         │         │
 ├────────→│编码 Opus   │           │         │         │
 │         ├──────────→│[帧2]      │         │         │
 │         │            │           ├────────→│ ✗无人声 │
 │         │            │           │         ├────────→│检查:0.06秒
 │         │            │           │         │         │
 │...      │...         │           │         │         │
 │         │            │           │         │         │
 │说话     │采集音频    │           │         │         │
 ├────────→│编码 Opus   │           │         │         │
 │         ├──────────→│[帧N]      │         │         │
 │         │            │           ├────────→│ ✓有人声 │
 │         │            │           │         ├────────→│更新时间戳
 │         │            │           │         │         │01:00
 │         │            │           │         │         │(重置计时)
```

### 超时触发流程

```
时间      前端       WebSocket    服务端      VAD      检测逻辑     LLM
 │         │            │           │         │         │         │
 │(沉默)   │采集音频    │           │         │         │         │
 ├────────→│编码 Opus   │           │         │         │         │
 │         ├──────────→│[帧X]      │         │         │         │
 │         │            │           ├────────→│ ✗无人声 │         │
 │         │            │           │         ├────────→│检查:90秒│
 │         │            │           │         │         │         │
 │...      │...         │           │         │         │         │
 │         │            │           │         │         │         │
 │(沉默)   │采集音频    │           │         │         │         │
 ├────────→│编码 Opus   │           │         │         │         │
 │         ├──────────→│[帧Z]      │         │         │         │
 │         │            │           ├────────→│ ✗无人声 │         │
 │         │            │           │         ├────────→│检查:120秒│
 │         │            │           │         │         │         │
 │         │            │           │         │         ├────────→│⚠️超时!
 │         │            │           │         │         │         │
 │         │            │           │         │         │发送提示词│
 │         │            │           │         │         ├────────→│
 │         │            │           │         │         │         │
 │         │            │           │         │         │         ├─→生成告别
 │         │            │           │         │         │         │   回复
 │         │            │           │         │         │         │
 │         │            │           │         │         │         ├─→TTS播放
 │         │            │←─────────音频─────────────────│         │
 │         │←────────播放────────────────────────────────│         │
 │         │            │           │         │         │         │
 │         │            │           │         │         │         │
 │挂断     │            │           │         │         │         │
 │         │发送空帧    │           │         │         │         │
 ├────────→│            │           │         │         │         │
 │         ├──────────→│[空帧]      │         │         │         │
 │         │            │           │         │         │         │
 │         │            │           │         │         │         │
 │         │            │           │         │         │关闭连接│
 │         │            │←───────────┴─────────┴─────────┴─────────│
 │         │            │           │         │         │保存记忆 │
 │         │            │           │         │         │         │
```

---

## 常见问题

### Q1: 为什么不用定时任务检查超时？

**A**: 因为音频流本身就是持续发送的（60ms/帧），每个音频帧都要处理（VAD 检测、ASR 识别）。在处理音频帧时顺手检查超时，无需额外的定时任务，效率更高。

### Q2: 如果音频流中断会怎样？

**A**: 如果音频流中断（网络问题、设备故障），服务端不会再收到音频帧，`no_voice_close_connect()` 也不会被调用。但服务端还有一道保障：`_check_timeout()` 定时任务（connection.py:206），会在 `timeout_seconds`（默认 180 秒）后强制关闭连接。

### Q3: VAD 误判怎么办？（如把背景音判断为人声）

**A**: Silero VAD 模型准确率较高，但如果背景音持续被判定为人声，会导致 `last_activity_time` 持续更新，无法触发超时。可以通过以下方式优化：
- 调整 VAD 阈值
- 使用更高质量的 VAD 模型
- 在前端进行音频预处理（降噪、回声消除）

### Q4: 能否自定义告别提示词？

**A**: 可以。修改 `config.yaml` 中的 `end_prompt.prompt` 配置项，或通过智控台 API 动态配置。

### Q5: 如何禁用自动告别功能？

**A**: 两种方式：
1. 设置 `close_connection_no_voice_time: 0`（永不超时）
2. 设置 `end_prompt.enable: false`（超时直接关闭，不发告别语）

### Q6: 用户主动挂断和自动超时挂断有什么区别？

**A**:
- **用户主动挂断**：前端发送空帧（`new Uint8Array(0)`），服务端正常关闭连接
- **自动超时挂断**：服务端检测到超时，发送告别提示词给 LLM，等待 LLM 回复完成后关闭连接，并保存记忆

两者都会触发 `_save_and_close()` 保存对话记忆。

---

## 相关文件清单

| 文件路径 | 功能描述 |
|---------|---------|
| `test/js/core/audio/recorder.js` | 前端音频采集和发送 |
| `core/connection.py` | WebSocket 连接处理、消息路由 |
| `core/handle/receiveAudioHandle.py` | 音频消息处理、超时检测 |
| `core/providers/asr/base.py` | ASR 音频处理线程 |
| `core/providers/vad/silero.py` | VAD 人声检测 |
| `config.yaml` | 超时时间、结束语配置 |

---

## 参考资料

- [VAD (Voice Activity Detection)](https://en.wikipedia.org/wiki/Voice_activity_detection)
- [Silero VAD Model](https://github.com/snakers4/silero-vad)
- [Opus Audio Codec](https://opus-codec.org/)
- [WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
