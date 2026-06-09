# 服务端流水线卡死分析

**日期**: 2026-06-05
**现象**: 日志仅有 `发送音频消息: SentenceType.FIRST`，小程序端一直等待无响应
**分支**: f-mini-fist

---

## 完整调用链追踪

```
chat() depth=0
  → conn.sentence_id = new UUID
  → tts_text_queue.put(FIRST, ACTION)        ← 触发 tts_text_priority_thread 重置状态
  → for response in llm_responses:            ← LLM 流式输出文本
      → tts_text_queue.put(MIDDLE, TEXT)      ← 文本送入 TTS 队列

tts_text_priority_thread:
  → FIRST → 重置状态
  → MIDDLE + TEXT → 累积到 buffer → 找到标点断句 → to_tts_stream(text)

to_tts_stream():
  → tts_audio_queue.put(FIRST, None, text)    ← 先放占位消息
  → asyncio.run(self.text_to_speak(text))     ← 实际调 TTS API（无超时！）

_audio_play_priority_thread:
  → 取出 FIRST → sendAudioMessage()
    → 日志: "发送音频消息: SentenceType.FIRST"  ← 你看到的唯一日志
  → 等待后续 MIDDLE 帧（opus 音频数据）...       ← 永远等不到
```

**关键代码位置**:

| 文件 | 行号 | 作用 |
|------|------|------|
| `core/connection.py` | 938-1137 | `chat()` 方法，LLM 流式输出 |
| `core/providers/tts/base.py` | 368-409 | `tts_text_priority_thread` 消费文本队列 |
| `core/providers/tts/base.py` | 123-163 | `to_tts_stream()` 调用 TTS API |
| `core/providers/tts/base.py` | 411-469 | `_audio_play_priority_thread` 发送音频 |
| `core/handle/sendAudioHandle.py` | 20-54 | `sendAudioMessage()` 发送音频到客户端 |

---

## 根因分析（按可能性排序）

### 1. `to_tts_stream()` 中 `asyncio.run()` 无超时（最可能）

**位置**: `core/providers/tts/base.py:135`

```python
audio_bytes = asyncio.run(self.text_to_speak(text, None))
```

`asyncio.run(self.text_to_speak(text, None))` 没有任何超时保护。如果 TTS API（如硅基流动等外部服务）网络超时、服务不可用或返回慢，线程会永远阻塞。所有后续文本帧都堆积在 `tts_text_queue` 中无法处理。

**影响范围**: 整个 TTS 线程卡住 → 无音频帧产生 → 客户端收不到任何后续消息

### 2. LLM 流式响应卡住或返回空内容

**位置**: `core/connection.py:1054`

```python
for response in llm_responses:
```

如果 LLM API 挂起（网络问题、模型过载），循环永远不会结束，`SentenceType.LAST` 永远不会被放入队列，客户端收不到 `tts stop` 消息。

**影响范围**: chat() 方法阻塞 → 无 LAST 消息 → 客户端一直处于 speaking 状态

### 3. `sentence_id` 竞态条件导致消息被静默丢弃

**位置**:
- `core/providers/tts/base.py:376`
- `core/handle/sendAudioHandle.py:22`

```python
# base.py:376
if message.sentence_id != self.conn.sentence_id:
    continue

# sendAudioHandle.py:22
if sentence_id is not None and sentence_id != conn.sentence_id:
    return
```

两处都有 sentence_id 匹配检查。如果在处理过程中用户发送了新消息（或重连），`conn.sentence_id` 会被 `chat()` 更新，导致旧的消息被静默丢弃，无任何日志。

**影响范围**: 旧会话的音频帧被静默丢弃 → 客户端收不到音频 → 无错误提示

### 4. `_wait_for_audio_completion` 无限等待

**位置**: `core/handle/sendAudioHandle.py:68`

```python
await rate_controller.queue_empty_event.wait()
```

没有 timeout。如果 rate controller 的后台任务因异常退出，这个 event 永远不会 set，导致 `send_tts_message("stop")` 永远无法完成。

**影响范围**: stop 消息无法发送 → 客户端永远处于 speaking 状态

---

## 建议修复方案

### 修复 1: 给 `to_tts_stream` 加超时（最关键）

```python
# base.py to_tts_stream() 中
try:
    audio_bytes = asyncio.run(
        asyncio.wait_for(self.text_to_speak(text, None), timeout=self.tts_timeout)
    )
except asyncio.TimeoutError:
    logger.bind(tag=TAG).error(f"TTS 生成超时({self.tts_timeout}s): {original_text}")
    continue  # 跳过这句，继续处理下一句
```

**注意**: `tts_timeout` 已在 `__init__` 中定义（默认 15 秒），但从未在 `to_tts_stream` 中使用。

### 修复 2: 给 `_wait_for_audio_completion` 加超时

```python
# sendAudioHandle.py
try:
    await asyncio.wait_for(
        rate_controller.queue_empty_event.wait(),
        timeout=30.0  # 最大等待 30 秒
    )
except asyncio.TimeoutError:
    conn.logger.bind(tag=TAG).warning("等待音频发送完成超时")
```

### 修复 3: sentence_id 丢弃时加日志

```python
# base.py:376
if message.sentence_id != self.conn.sentence_id:
    logger.bind(tag=TAG).debug(
        f"跳过旧消息: msg_sid={message.sentence_id}, current_sid={self.conn.sentence_id}"
    )
    continue
```

```python
# sendAudioHandle.py:22
if sentence_id is not None and sentence_id != conn.sentence_id:
    conn.logger.bind(tag=TAG).debug(
        f"跳过旧音频: msg_sid={sentence_id}, current_sid={conn.sentence_id}"
    )
    return
```

---

## 排查建议

如果问题复现，检查以下日志：

1. `【LLM 提示词】` 日志是否出现 → 判断 LLM 是否被调用
2. `语音生成成功/失败` 日志 → 判断 TTS API 是否正常
3. `处理TTS文本失败` 日志 → 判断 tts_text_priority_thread 是否异常
4. `audio_play_priority_thread` 错误日志 → 判断音频线程是否异常
5. 检查外部服务状态：TTS API 和 LLM API 是否可达
