# 阿里云 AI 安全护栏接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `xiaozhi-server` 在调用 LLM 前审核真实用户文本、在 LLM 文本进入 TTS 前审核生成内容，并通过 `manager-api` 下发可插拔的阿里云 AI 安全护栏配置。

**Architecture:** 新增一个小型内容安全 provider 契约与 `aliyun`/`noop` 两个实现。每一轮对话创建不可变的安全上下文：输入仅取用户文本（JSON 输入时仅取 `content`），输出在进入 TTS 前由会话级缓冲器分段审核；普通流、`direct_answer` 和 `IntentProvider.replyResult` 的 LLM 结果均走同一出口。`manager-api` 的 `sys_params` 仍是唯一配置来源，客户端不保存密钥。

**Tech Stack:** Python 3.12、`alibabacloud_green20220302==3.2.4`、阿里云 `MultiModalGuard` API、pytest、Java 21/JUnit 5、Liquibase、Vue 2。

**Official API basis:** [多模态 API 接入指南](https://help.aliyun.com/zh/document_detail/2937221.html) and [多模态 Python SDK 参考](https://help.aliyun.com/zh/document_detail/2937220.html).

## Global Constraints

- 输入服务固定由配置项 `content_safety.input_service` 下发，默认值必须是 `query_security_check_pro`；输出服务默认值必须是 `response_security_check_pro`，不得使用 `response_security_check_hp`。
- 仅审核用户实际输入。音频经 ASR 后的文本和文字消息统一在 `startToChat` 审核；对 `{"speaker": ..., "content": ...}` 仅审核 `content`，绝不审核系统提示词、记忆、工具描述、few-shot、对话历史或其他固定提示词。
- 每次用户输入创建一个 UUID `chat_id`，输入审核与该轮及其递归工具调用后的输出审核均使用该 `chat_id`；输出流使用当前 `sentence_id` 作为 `session_id`。
- API 单次 `content` 最大长度由 `content_safety.max_request_chars` 控制，迁移默认 `2000`，实现必须拒绝大于 2000 的配置值；每个非空分片不超过该值。
- 审核 API 的 `Suggestion == "block"` 为阻断；`pass`、`watch`、`mask` 放行但记录脱敏元数据。`mode=observe` 永远放行，`mode=enforce` 对 `block` 和 API 异常均阻断（fail-closed）。`enabled=false` 不创建远程请求且保持原行为。
- `input_block_message`、`output_block_message` 是来自 `manager-api` 的数组，应用每次阻断时随机选择一条；不得把用户可见的兜底文案硬编码在 Python 中。数组缺失时使用现有 `system_error_response` 配置作为可信本地兜底。
- 可信本地安全提示和 `system_error_response` 不再经过输出审核，避免递归；被阻断的用户文本、未放行的 LLM 文本及 AccessKey/Secret 均不得写入新增日志、TTS 文本缓存或对话历史。
- 所有新增日志仅含方向、模式、决定、字符数、类别/等级摘要、耗时和阿里云 `RequestId`；不得输出原文、`RiskWords`、敏感样本、请求体或凭据。将本次路径上现有的原始 `query` 错误/信息日志改为这些可审计摘要。
- 仅实现 `aliyun` 和 `noop` 两个 provider；provider 选择、服务名、地域、端点、超时、QPS、分块大小、模式、开关和提示文案全部从 manager-api 配置读取。不要加入运行时插件扫描、数据库表或配置热更新机制。
- 配置在 xiaozhi-server 启动/既有配置刷新时读取；生产修改 `sys_params` 后以重启 xiaozhi-server 作为生效步骤。多实例部署时需把各实例 `max_qps` 配置为总和不超过阿里云账户 50 QPS 限额。
- 严格 TDD：每个生产模块先写并观察对应失败测试，再实现最小代码；所有新增 Python 内容安全模块的行覆盖率不低于 80%。

---

## 已核实的现有入口

| 路径 | 当前职责 | 本次接入点 |
| --- | --- | --- |
| `main/xiaozhi-server/core/handle/receiveAudioHandle.py:startToChat` | ASR 和文字输入汇合；随后意图识别及 `conn.chat` | 解析出用户正文后、`handle_user_intent` 前审核输入 |
| `main/xiaozhi-server/core/connection.py:chat` | 主 LLM 流、`direct_answer`、递归工具调用、TTS 入队 | 用每轮 `OutputSafetyGate` 替代两个直接 `MIDDLE TEXT` 入队点 |
| `main/xiaozhi-server/core/providers/intent/intent_llm/intent_llm.py:replyResult` | 调用 `response_no_stream` 生成给用户的意图回复 | 返回带 `generated` 标记的结果，供调用方决定是否审核 |
| `main/xiaozhi-server/core/handle/intentHandler.py:speak_txt` | 把文本一次性送 TTS 并写入对话历史 | 为 LLM 结果增加受保护入口；工具/配置静态回复仍走原入口 |
| `main/manager-api/.../ConfigServiceImpl.java:buildConfig` | 将点号 `sys_params` 组织成嵌套配置 | 已支持 string/number/boolean/array，无需改生产逻辑，仅补默认参数和覆盖测试 |

## 文件结构

- Create: `main/xiaozhi-server/core/providers/content_safety/base.py` — provider 契约、方向/决定/结果值对象。
- Create: `main/xiaozhi-server/core/providers/content_safety/noop.py` — 关闭时的无远程请求实现。
- Create: `main/xiaozhi-server/core/providers/content_safety/aliyun.py` — `MultiModalGuard` SDK 适配、QPS 限制、响应归一化。
- Create: `main/xiaozhi-server/core/content_safety/gate.py` — 单轮输入切片与输出流缓冲、可信阻断提示选择。
- Create: `main/xiaozhi-server/core/utils/content_safety.py` — 现有工厂风格的 provider 创建入口。
- Modify: `main/xiaozhi-server/core/websocket_server.py` — 初始化并共享一个安全 provider 给所有连接。
- Modify: `main/xiaozhi-server/core/connection.py` — 安全上下文随递归调用传递，普通流和 `direct_answer` 统一走输出闸门。
- Modify: `main/xiaozhi-server/core/handle/receiveAudioHandle.py` — 只对真实用户正文做输入审核并创建上下文。
- Modify: `main/xiaozhi-server/core/providers/intent/intent_llm/intent_llm.py` — 用 `IntentReply` 标识 LLM 成功结果与静态故障兜底。
- Modify: `main/xiaozhi-server/core/handle/intentHandler.py` — 仅让 `IntentReply.generated=True` 走受保护输出入口。
- Modify: `main/xiaozhi-server/requirements.txt` — 添加官方 Green SDK。
- Create: `main/xiaozhi-server/requirements-dev.txt`、`main/xiaozhi-server/tests/content_safety/...` — 独立、可重复的 Python 测试体系。
- Create: `main/manager-api/src/main/resources/db/changelog/202608051500.sql` — 默认安全配置。
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` — 注册新 changeset。
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/config/service/impl/ConfigServiceImplTest.java` — 覆盖嵌套数组及凭据键下发。
- Modify: `main/manager-web/src/views/ParamsManagement.vue` — 将 `access_key_id` 加入既有敏感参数遮罩规则。
- Create: `main/xiaozhi-server/docs/guides/aliyun-content-safety.md` — RAM 授权、配置、启用、回滚及验收运行手册。

### Task 1: Provider 契约、阿里云适配与测试基础

**Files:**
- Create: `main/xiaozhi-server/core/providers/content_safety/__init__.py`
- Create: `main/xiaozhi-server/core/providers/content_safety/base.py`
- Create: `main/xiaozhi-server/core/providers/content_safety/noop.py`
- Create: `main/xiaozhi-server/core/providers/content_safety/aliyun.py`
- Create: `main/xiaozhi-server/core/utils/content_safety.py`
- Create: `main/xiaozhi-server/requirements-dev.txt`
- Modify: `main/xiaozhi-server/requirements.txt`
- Test: `main/xiaozhi-server/tests/content_safety/test_aliyun_provider.py`
- Test: `main/xiaozhi-server/tests/content_safety/test_provider_factory.py`

**Interfaces:**
- Produces `SafetyDirection.INPUT`/`SafetyDirection.OUTPUT` and immutable `SafetyResult(decision, suggestion, request_id, labels, levels, error_kind)`.
- Produces `ContentSafetyProviderBase.check(direction, content, chat_id, session_id=None, done=False) -> SafetyResult`; callers use `result.decision` rather than provider-specific policy helpers.
- Produces `create_content_safety_provider(config: Mapping[str, Any]) -> ContentSafetyProviderBase`.
- Consumes `config["content_safety"]` and top-level `config["aliyun"]`; no caller passes an AccessKey.

- [ ] **Step 1: Add the Python test-only dependencies and package markers.**

Create `requirements-dev.txt` with exact test tooling, create `tests/__init__.py` and `tests/content_safety/__init__.py`, then add tests which import the as-yet absent factory and contract.

```text
# requirements-dev.txt
pytest==8.3.5
pytest-cov==6.0.0
```

- [ ] **Step 2: Write failing tests for the observable provider contract.**

Use a fake SDK client factory so tests never contact Alibaba Cloud. Name the mutation each test catches: wrong service by direction, a block result accidentally allowed, an API failure accidentally allowed in enforce mode, or a disabled feature making a network call.

```python
def test_aliyun_input_uses_configured_query_pro_service_and_normalizes_block():
    provider, client = make_aliyun_provider(mode="enforce")
    client.reply = api_reply(suggestion="block", request_id="req-1")

    result = provider.check(SafetyDirection.INPUT, "用户正文", "chat-1")

    assert result.decision is SafetyDecision.BLOCK
    assert client.requests[0].service == "query_security_check_pro"
    assert json.loads(client.requests[0].service_parameters)["content"] == "用户正文"
    assert result.request_id == "req-1"

def test_aliyun_error_blocks_only_in_enforce_mode():
    provider, client = make_aliyun_provider(mode="enforce")
    client.raise_error = TimeoutError("network")

    assert provider.check(SafetyDirection.OUTPUT, "回复", "chat-1").decision is SafetyDecision.ERROR

def test_disabled_factory_returns_noop_without_sdk_client():
    provider = create_content_safety_provider({"content_safety": {"enabled": False}})

    assert provider.check(SafetyDirection.INPUT, "任意文本", "chat-1").decision is SafetyDecision.ALLOW
```

- [ ] **Step 3: Run the tests to verify they fail for missing contract/provider code.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety/test_aliyun_provider.py tests/content_safety/test_provider_factory.py -q`

Expected: FAIL with import errors for `core.providers.content_safety` or missing `create_content_safety_provider`; do not continue until the failures are caused by absent production behavior rather than test setup.

- [ ] **Step 4: Implement the smallest provider contract and no-op provider.**

Keep the result value object small and immutable. `noop` returns `ALLOW` and never imports or initializes the Alibaba SDK. The factory accepts only `noop` and `aliyun`; an enabled unknown provider raises a configuration error rather than silently disabling safety.

```python
class ContentSafetyProviderBase(ABC):
    @abstractmethod
    def check(
        self,
        direction: SafetyDirection,
        content: str,
        chat_id: str,
        session_id: str | None = None,
        done: bool = False,
    ) -> SafetyResult: ...

def create_content_safety_provider(config: Mapping[str, Any]) -> ContentSafetyProviderBase:
    safety_config = config.get("content_safety", {})
    if not safety_config.get("enabled", False):
        return NoopContentSafetyProvider()
    if safety_config.get("provider") == "aliyun":
        return AliyunContentSafetyProvider(safety_config, config.get("aliyun", {}))
    raise ValueError("Unsupported content_safety.provider")
```

- [ ] **Step 5: Implement the Alibaba Cloud adapter using the official SDK, not raw request signing.**

Add `alibabacloud_green20220302==3.2.4` to `requirements.txt`. Construct one reusable `Client(Config(...))` from `aliyun.access_key_id`, `aliyun.access_key_secret`, `content_safety.region_id`, `content_safety.endpoint`, `connect_timeout_ms`, and `read_timeout_ms`. Build `MultiModalGuardRequest` with the configured service and a JSON `service_parameters` containing `content`, `chatId`, optional `sessionId`, and `done` only for a nonempty final content chunk. Never read SDK credentials from process environment.

```python
service_parameters = {"content": content, "chatId": chat_id}
if session_id:
    service_parameters["sessionId"] = session_id
if done:
    service_parameters["done"] = True
request = models.MultiModalGuardRequest(
    service=self._service_for(direction),
    service_parameters=json.dumps(service_parameters, ensure_ascii=False),
)
response = self._client.multi_modal_guard(request)
```

Enforce process-local monotonic throttling at `min(content_safety.max_qps, 50)`, never retry a request automatically, and normalize any SDK exception, non-200 HTTP status, malformed body, or `body.code != 200` to `SafetyDecision.ERROR`. Extract only labels/levels (not risk words or sensitive samples) for audit metadata. Return `BLOCK` only for API suggestion `block`; `watch` and `mask` are `ALLOW` with their suggestion preserved.

- [ ] **Step 6: Run the provider tests and coverage gate.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety/test_aliyun_provider.py tests/content_safety/test_provider_factory.py --cov=core.providers.content_safety --cov-report=term-missing -q`

Expected: PASS; the new provider package reports at least 80% line coverage. Confirm tests assert request payload behavior and returned decisions, not calls to a mock alone.

- [ ] **Step 7: Commit the isolated provider deliverable.**

```bash
git add main/xiaozhi-server/requirements.txt main/xiaozhi-server/requirements-dev.txt \
  main/xiaozhi-server/core/providers/content_safety main/xiaozhi-server/core/utils/content_safety.py \
  main/xiaozhi-server/tests/content_safety
git commit -m "feat(xiaozhi-server): add pluggable content safety provider"
```

### Task 2: 会话安全闸门与可信阻断回复

**Files:**
- Create: `main/xiaozhi-server/core/content_safety/__init__.py`
- Create: `main/xiaozhi-server/core/content_safety/gate.py`
- Test: `main/xiaozhi-server/tests/content_safety/test_gate.py`

**Interfaces:**
- Consumes `ContentSafetyProviderBase`, `SafetyResult`, `content_safety.max_request_chars`, `content_safety.output_chunk_chars`, `content_safety.mode` and two message arrays from Task 1.
- Produces immutable `ContentSafetyContext(chat_id: str)`, `ContentSafetyGate.check_input(text, context) -> GateResult`, and `OutputSafetyGate.feed(text) -> GateResult`, `OutputSafetyGate.finish() -> GateResult`.
- `GateResult` exposes `released_parts: tuple[str, ...]`, `blocked: bool`, and `audit: SafetyResult`; callers enqueue only `released_parts`.

- [ ] **Step 1: Write failing gate tests for boundaries and blocking.**

Use a deterministic fake provider that records arguments and returns scripted results. These tests must cover Chinese text, Unicode, exactly-at-limit input, just-over-limit splitting, punctuation-driven output release, a blocked second output chunk, and a final empty buffer.

```python
def test_input_split_keeps_the_same_chat_id_and_never_exceeds_request_limit():
    provider = RecordingProvider(allow=True)
    gate = ContentSafetyGate(provider, config(max_request_chars=4))

    result = gate.check_input("甲乙丙丁戊", ContentSafetyContext("turn-1"))

    assert result.allowed
    assert [call.content for call in provider.calls] == ["甲乙丙丁", "戊"]
    assert {call.chat_id for call in provider.calls} == {"turn-1"}

def test_output_does_not_release_a_blocked_chunk_to_tts():
    provider = RecordingProvider(results=[allow(), block()])
    gate = OutputSafetyGate(provider, config(output_chunk_chars=3), ContentSafetyContext("turn-1"), "sentence-1")

    assert gate.feed("安全。").released_parts == ("安全。",)
    blocked = gate.feed("危险。")

    assert blocked.blocked is True
    assert blocked.released_parts == ()

def test_finish_never_submits_an_invalid_empty_content_request():
    provider = RecordingProvider(allow=True)
    gate = OutputSafetyGate(provider, config(output_chunk_chars=2), ContentSafetyContext("turn-1"), "sentence-1")
    gate.feed("通过")

    assert gate.finish().released_parts == ()
    assert all(call.content for call in provider.calls)
```

- [ ] **Step 2: Run the gate tests to verify they fail.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety/test_gate.py -q`

Expected: FAIL because `ContentSafetyGate`, `OutputSafetyGate`, and `GateResult` do not yet exist.

- [ ] **Step 3: Implement input checking and output buffering without TTS knowledge.**

`ContentSafetyGate.check_input` slices by Python characters, calls the input direction for every nonempty slice using `context.chat_id`, and stops at the first block/error. `OutputSafetyGate` accumulates deltas using the same context; it flushes once it reaches `output_chunk_chars` or the buffer ends with `。！？!?；;\n`. Each flush calls output checking before returning text to the caller. `finish()` flushes any nonempty remainder with `done=True`; if there is no remainder it performs no empty API call, because the API requires content and treats `done` only as recommended.

```python
def _flush(self, *, done: bool) -> GateResult:
    if not self._buffer or self._blocked:
        return GateResult((), self._blocked, None)
    chunk, self._buffer = self._buffer, ""
    result = self._provider.check(
        SafetyDirection.OUTPUT, chunk, self._context.chat_id, self._session_id, done=done
    )
    blocked = self._mode == "enforce" and result.decision in {
        SafetyDecision.BLOCK, SafetyDecision.ERROR
    }
    return GateResult((), True, result) if blocked else GateResult((chunk,), False, result)
```

Validate at construction that `1 <= output_chunk_chars <= max_request_chars <= 2000` and mode is `observe` or `enforce`. Normalize a missing or all-blank message array to the configured `system_error_response` as its one trusted local candidate. Use `random.choice(tuple(messages))` only after a block decision; never store the random result as global state.

- [ ] **Step 4: Add tests for observe mode, API errors, and random configured replies.**

```python
def test_observe_mode_releases_content_when_provider_reports_block():
    gate = OutputSafetyGate(RecordingProvider(results=[block()]), config(mode="observe"), ContentSafetyContext("turn-1"), "s-1")

    assert gate.feed("可观察文本。").released_parts == ("可观察文本。",)

def test_block_message_is_selected_from_the_configured_output_array(monkeypatch):
    gate = ContentSafetyGate(RecordingProvider(results=[block()]), config(output_messages=["甲", "乙"]))
    monkeypatch.setattr("core.content_safety.gate.random.choice", lambda values: values[1])

    assert gate.output_block_message() == "乙"
```

- [ ] **Step 5: Run all gate tests and the expanded coverage gate.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety/test_gate.py tests/content_safety/test_aliyun_provider.py tests/content_safety/test_provider_factory.py --cov=core.content_safety --cov=core.providers.content_safety --cov-report=term-missing -q`

Expected: PASS with at least 80% line coverage for both new packages; the blocked-output test demonstrates there is no released unsafe text.

- [ ] **Step 6: Commit the gate deliverable.**

```bash
git add main/xiaozhi-server/core/content_safety main/xiaozhi-server/tests/content_safety/test_gate.py
git commit -m "feat(xiaozhi-server): add content safety stream gate"
```

### Task 3: 接入输入、主 LLM 输出、direct_answer 与 Intent LLM 输出

**Files:**
- Modify: `main/xiaozhi-server/core/websocket_server.py:43-61,148-156,211-240`
- Modify: `main/xiaozhi-server/core/connection.py:65-150,1055-1410,1465-1510`
- Modify: `main/xiaozhi-server/core/handle/receiveAudioHandle.py:43-103`
- Modify: `main/xiaozhi-server/core/providers/intent/intent_llm/intent_llm.py:133-149`
- Modify: `main/xiaozhi-server/core/handle/intentHandler.py:70-245`
- Test: `main/xiaozhi-server/tests/content_safety/test_chat_integration.py`

**Interfaces:**
- Consumes `create_content_safety_provider`, `ContentSafetyContext`, `ContentSafetyGate`, and `OutputSafetyGate` from Tasks 1–2.
- `ConnectionHandler.__init__` gains a trailing optional `content_safety_provider` dependency; `WebSocketServer` owns the shared instance.
- `ConnectionHandler.chat(query, depth=0, safety_context=None, output_gate=None)` receives the exact context created at input; `_handle_function_result(..., safety_context, output_gate)` passes both unchanged to recursive `chat`.
- `IntentProvider.replyResult` returns `IntentReply(text: str, generated: bool)`; only `generated=True` calls the protected intent speech helper.

- [ ] **Step 1: Write failing integration tests for the input boundary.**

Build minimal connection fakes with an executor recorder, an intent recorder, a TTS queue recorder and an injected safety provider. Do not import real ASR, TTS, network, or SDK implementations. The break each test catches is an unsafe input reaching intent/LLM, or speaker wrapper text replacing the checked content.

```python
@pytest.mark.asyncio
async def test_blocked_json_user_content_never_reaches_intent_or_chat():
    conn = fake_connection(input_result=block(), input_messages=["输入已拒绝"])

    await startToChat(conn, '{"speaker":"小明","content":"受限内容"}')

    assert conn.intent_inputs == []
    assert conn.executor.submissions == []
    assert conn.spoken_texts == ["输入已拒绝"]
    assert "受限内容" not in conn.dialogue.contents

@pytest.mark.asyncio
async def test_first_speaker_turn_checks_only_content_but_preserves_existing_llm_payload():
    conn = fake_connection(input_result=allow())
    raw = '{"speaker":"小明","content":"你好"}'

    await startToChat(conn, raw)

    assert conn.safety.calls[0].content == "你好"
    assert conn.executor.submissions[0].args[0] == raw
```

- [ ] **Step 2: Run input integration tests to verify they fail.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety/test_chat_integration.py -k 'input or speaker' -q`

Expected: FAIL because `startToChat` currently calls `handle_user_intent` before any safety gate and does not construct a context.

- [ ] **Step 3: Wire one shared provider through WebSocketServer and check input before any LLM path.**

In `WebSocketServer.__init__`, create a single provider from the loaded server config and pass it as the final `ConnectionHandler` argument. In `startToChat`, preserve the existing JSON/speaker behavior but separately compute `checked_user_content`: JSON `content` when present, otherwise the original text. After the bind/output-quota early returns and before abort/intent/STT/chat, generate `ContentSafetyContext(chat_id=uuid.uuid4().hex)` and asynchronously run its input gate with `await asyncio.to_thread(...)`. On enforce block/error, send only a trusted randomly selected input message using a helper that emits `FIRST`/text/`LAST`, then return without STT echo, intent, executor submission, or dialogue write.

```python
checked_user_content = actual_content if speaker_name is not None else text
safety_context = ContentSafetyContext(chat_id=uuid.uuid4().hex)
input_result = await asyncio.to_thread(
    conn.content_safety_gate.check_input, checked_user_content, safety_context
)
if input_result.blocked:
    speak_trusted_text(conn, conn.content_safety_gate.input_block_message())
    return
conn.executor.submit(conn.chat, actual_text, 0, safety_context)
```

- [ ] **Step 4: Write failing tests for the two main streaming output branches.**

Tests must assert the TTS queue receives only gate-released text, not just that a safety method is called. Cover a normal streamed completion, a midstream block, `direct_answer` streamed chunks plus final remainder, and preserving the same `chat_id` across recursive function-call output.

```python
def test_normal_llm_stream_releases_only_checked_chunks_before_last_action():
    conn = chat_connection(outputs=["安全。", "继续。"], safety=[allow(), allow()])

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == ["安全。", "继续。"]
    assert {call.chat_id for call in conn.safety.calls} == {"turn-1"}
    assert conn.tts.last_action_count == 1

def test_direct_answer_block_replaces_unreleased_content_with_trusted_output_message():
    conn = direct_answer_connection(safety=[block()], output_messages=["输出已拒绝"])

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == ["输出已拒绝"]
    assert "危险" not in conn.dialogue.contents
```

- [ ] **Step 5: Run output integration tests to verify they fail.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety/test_chat_integration.py -k 'stream or direct_answer or recursive' -q`

Expected: FAIL because `ConnectionHandler.chat` currently puts normal and `direct_answer` content straight into `tts_text_queue`.

- [ ] **Step 6: Replace direct TTS writes in `ConnectionHandler.chat` with one gate-owned release path.**

Create one `OutputSafetyGate` per top-level chat from the supplied context and `current_sentence_id`; pass the same gate/context through `_handle_function_result` into `self.chat(None, depth + 1, safety_context, output_gate)`. Route ordinary token content and `_extract_direct_answer_response` deltas through `feed`; enqueue only `released_parts`. At every successful terminal path call `finish()` before `LAST`. If it blocks, set `client_abort=True`, do not append or store unapproved content, emit the configured trusted output message, emit exactly one `LAST`, and return. Existing static `get_system_error_response(self.config)` remains a trusted local branch and is not sent to the provider.

```python
gate_result = output_gate.feed(content)
if gate_result.blocked:
    self.client_abort = True
    self._speak_trusted_output_block(current_sentence_id, output_gate.output_block_message())
    self._enqueue_last_action(current_sentence_id)
    return True
for released in gate_result.released_parts:
    response_message.append(released)
    self._enqueue_checked_tts_text(current_sentence_id, released)
```

Do not change TTS provider implementations. The action markers remain in `ConnectionHandler`; only `MIDDLE` LLM text is delayed. Replace the two raw-query logs in this code path with safe summaries (`chat_id`, character count, decision/request id when available).

- [ ] **Step 7: Write failing tests for IntentProvider reply provenance.**

The test must distinguish an actual `response_no_stream` result (needs output check) from `get_system_error_response` (must bypass output check), instead of guessing by matching text.

```python
@pytest.mark.asyncio
async def test_generated_intent_reply_is_checked_before_speech():
    reply = await provider_with_llm("模型回复").replyResult("上下文", "用户问题")

    assert reply == IntentReply(text="模型回复", generated=True)

@pytest.mark.asyncio
async def test_static_intent_fallback_bypasses_output_provider():
    conn = intent_connection(reply=IntentReply("系统繁忙", generated=False))

    await process_intent_result(conn, reqlm_intent(), "用户问题")

    assert conn.safety.calls == []
    assert conn.tts.middle_texts == ["系统繁忙"]
```

- [ ] **Step 8: Implement intent output provenance and the protected speech helper.**

Define a frozen `IntentReply` in the intent LLM module. Return `IntentReply(llm_result, generated=True)` on successful `response_no_stream`; return `IntentReply(get_system_error_response(...), generated=False)` only in the exception handler. Update both `replyResult` call sites in `process_intent_result`: generated replies are checked with a one-shot `OutputSafetyGate` using a new `ContentSafetyContext`; static fallbacks and tool-produced `Action.RESPONSE`/`Action.ERROR` text continue to use existing `speak_txt` unchanged.

```python
def speak_generated_llm_text(conn: ConnectionHandler, reply: IntentReply) -> None:
    if not reply.generated:
        speak_txt(conn, reply.text)
        return
    context = ContentSafetyContext(uuid.uuid4().hex)
    gate = OutputSafetyGate(conn.content_safety_provider, conn.config, context, conn.sentence_id)
    first, final = gate.feed(reply.text), gate.finish()
    if first.blocked or final.blocked:
        speak_trusted_text(conn, gate.output_block_message())
        return
    speak_txt(conn, "".join((*first.released_parts, *final.released_parts)))
```

- [ ] **Step 9: Run the full Python content safety suite and targeted syntax checks.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety --cov=core.content_safety --cov=core.providers.content_safety --cov-report=term-missing -q`

Run: `cd main/xiaozhi-server && python3.12 -m compileall -q core/content_safety core/providers/content_safety core/handle/receiveAudioHandle.py core/handle/intentHandler.py core/connection.py`

Expected: all tests PASS, all new content-safety modules at least 80% line coverage, and compileall has no output. Manually inspect test logs to ensure neither a fake secret nor raw unsafe fixture text appears in production log assertions.

- [ ] **Step 10: Commit the server integration deliverable.**

```bash
git add main/xiaozhi-server/core/websocket_server.py main/xiaozhi-server/core/connection.py \
  main/xiaozhi-server/core/handle/receiveAudioHandle.py main/xiaozhi-server/core/handle/intentHandler.py \
  main/xiaozhi-server/core/providers/intent/intent_llm/intent_llm.py \
  main/xiaozhi-server/tests/content_safety/test_chat_integration.py
git commit -m "feat(xiaozhi-server): guard LLM input and output content"
```

### Task 4: manager-api 配置下发、密钥遮罩与回归测试

**Files:**
- Create: `main/manager-api/src/main/resources/db/changelog/202608051500.sql`
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/config/service/impl/ConfigServiceImplTest.java`
- Modify: `main/manager-web/src/views/ParamsManagement.vue`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/config/service/impl/ConfigServiceImplTest.java`

**Interfaces:**
- Produces `/config/server-base` branches `aliyun.access_key_id`, `aliyun.access_key_secret`, and `content_safety.*` through the existing `ConfigServiceImpl.buildConfig`.
- No controller, DTO, Java service, or manager-api endpoint signature changes.
- Admin parameter UI masks both `aliyun.access_key_id` and `aliyun.access_key_secret` by adding `access_key_id` to its existing substring list.

- [ ] **Step 1: Write a failing `ConfigServiceImplTest` for the exact nested payload.**

Extend the existing pure Mockito test class with parameters corresponding to every type used by this feature. This catches a rename such as `content_safety.aliyun.access_key_id`, a string instead of array block message, or an incorrectly nested credential.

```java
@Test
void contentSafetyParametersAreNestedWithTopLevelAliyunCredentials() {
    SysParamsService params = mock(SysParamsService.class);
    when(params.list(anyMap())).thenReturn(List.of(
        parameter("aliyun.access_key_id", "id-value", "string"),
        parameter("aliyun.access_key_secret", "secret-value", "string"),
        parameter("content_safety.enabled", "false", "boolean"),
        parameter("content_safety.input_block_message", "甲;乙", "array"),
        parameter("content_safety.output_chunk_chars", "120", "number")
    ));

    Map<String, Object> config = new HashMap<>();
    ReflectionTestUtils.invokeMethod(newService(params, mock(RedisUtils.class)), "buildConfig", config);

    assertEquals("id-value", ((Map<?, ?>) config.get("aliyun")).get("access_key_id"));
    Map<?, ?> safety = (Map<?, ?>) config.get("content_safety");
    assertEquals(List.of("甲", "乙"), safety.get("input_block_message"));
    assertEquals(120, safety.get("output_chunk_chars"));
}
```

- [ ] **Step 2: Run the focused Java characterization test.**

Run: `cd main/manager-api && mvn test -DskipTests=false -Dtest=ConfigServiceImplTest#contentSafetyParametersAreNestedWithTopLevelAliyunCredentials`

Expected: PASS against the existing generic nesting logic. This is intentionally a characterization test: the production behavior for dot-nested `string`/`number`/`boolean`/`array` parameters already exists, while the new behavior in this task is a Liquibase data migration. Do not alter `ConfigServiceImpl.buildConfig` merely because this test exercises it.

- [ ] **Step 3: Add one idempotent Liquibase migration with all operational defaults.**

Create `202608051500.sql`, first delete by exact `param_code`, then insert IDs `630`–`645` with empty credential values. Semicolon-separated message values become manager-api arrays.

```sql
delete from sys_params where param_code in (
  'aliyun.access_key_id', 'aliyun.access_key_secret',
  'content_safety.enabled', 'content_safety.provider', 'content_safety.mode',
  'content_safety.region_id', 'content_safety.endpoint',
  'content_safety.connect_timeout_ms', 'content_safety.read_timeout_ms',
  'content_safety.max_qps', 'content_safety.max_request_chars',
  'content_safety.output_chunk_chars', 'content_safety.input_service',
  'content_safety.output_service', 'content_safety.input_block_message',
  'content_safety.output_block_message'
);

INSERT INTO sys_params
(id, param_code, param_value, value_type, param_type, remark, creator, create_date, updater, update_date)
VALUES
(630, 'aliyun.access_key_id', '', 'string', 1, '阿里云通用AccessKey ID（AI安全护栏使用）', NULL, NULL, NULL, NULL),
(631, 'aliyun.access_key_secret', '', 'string', 1, '阿里云通用AccessKey Secret（AI安全护栏使用）', NULL, NULL, NULL, NULL),
(632, 'content_safety.enabled', 'false', 'boolean', 1, '是否启用LLM输入输出内容安全审核', NULL, NULL, NULL, NULL),
(633, 'content_safety.provider', 'aliyun', 'string', 1, '内容安全服务提供商', NULL, NULL, NULL, NULL),
(634, 'content_safety.mode', 'enforce', 'string', 1, '审核模式：observe仅观察，enforce阻断', NULL, NULL, NULL, NULL),
(635, 'content_safety.region_id', 'cn-shanghai', 'string', 1, '阿里云AI安全护栏地域', NULL, NULL, NULL, NULL),
(636, 'content_safety.endpoint', 'green-cip.cn-shanghai.aliyuncs.com', 'string', 1, '阿里云AI安全护栏接入端点', NULL, NULL, NULL, NULL),
(637, 'content_safety.connect_timeout_ms', '3000', 'number', 1, '内容安全连接超时毫秒', NULL, NULL, NULL, NULL),
(638, 'content_safety.read_timeout_ms', '10000', 'number', 1, '内容安全读取超时毫秒', NULL, NULL, NULL, NULL),
(639, 'content_safety.max_qps', '45', 'number', 1, '单进程内容安全最大QPS，不能超过50', NULL, NULL, NULL, NULL),
(640, 'content_safety.max_request_chars', '2000', 'number', 1, '单次内容安全审核最大字符数', NULL, NULL, NULL, NULL),
(641, 'content_safety.output_chunk_chars', '120', 'number', 1, 'LLM输出审核缓冲字符数', NULL, NULL, NULL, NULL),
(642, 'content_safety.input_service', 'query_security_check_pro', 'string', 1, 'LLM输入内容安全审核服务', NULL, NULL, NULL, NULL),
(643, 'content_safety.output_service', 'response_security_check_pro', 'string', 1, 'LLM输出内容安全审核服务', NULL, NULL, NULL, NULL),
(644, 'content_safety.input_block_message', '抱歉，这个内容我不能处理;换个话题聊聊吧', 'array', 1, '输入内容被拦截时随机回复', NULL, NULL, NULL, NULL),
(645, 'content_safety.output_block_message', '抱歉，这个回复不能继续提供;我们换个话题吧', 'array', 1, '输出内容被拦截时随机回复', NULL, NULL, NULL, NULL);
```

Use `value_type` `string`, `boolean`, `number`, and `array` exactly as implied above. Keep both AccessKey values empty; operators fill them in the parameter management UI or secure deployment path after migration. Append changeset id `202608051500` to the end of `db.changelog-master.yaml`; do not modify prior migrations.

- [ ] **Step 4: Extend existing parameter masking rather than create a new UI.**

Modify only the `sensitive_keys` array in `ParamsManagement.vue` so its existing `isSensitiveParam` method masks the new global ID as well as the already-matched secret.

```javascript
sensitive_keys: [
  "api_key", "personal_access_token", "access_token", "token", "secret",
  "access_key_id", "access_key_secret", "secret_key", "password",
  "mqtt_signature_key", "private_key"
]
```

- [ ] **Step 5: Run focused manager-api test and static migration/UI checks.**

Run: `cd main/manager-api && mvn test -DskipTests=false -Dtest=ConfigServiceImplTest`

Run: `git diff --check -- main/manager-api/src/main/resources/db/changelog main/manager-api/src/test/java/xiaozhi/modules/config/service/impl/ConfigServiceImplTest.java main/manager-web/src/views/ParamsManagement.vue`

Run: `cd main/manager-web && npm run test:unit`

Expected: Maven test PASS, `git diff --check` prints nothing, and existing Node tests PASS. Do not run Liquibase against a shared database; deployment applies the new changeset normally.

- [ ] **Step 6: Commit the configuration deliverable.**

```bash
git add main/manager-api/src/main/resources/db/changelog/202608051500.sql \
  main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml \
  main/manager-api/src/test/java/xiaozhi/modules/config/service/impl/ConfigServiceImplTest.java \
  main/manager-web/src/views/ParamsManagement.vue
git commit -m "feat(manager-api): publish content safety configuration"
```

### Task 5: 运维手册、真实环境验收与最终回归

**Files:**
- Create: `main/xiaozhi-server/docs/guides/aliyun-content-safety.md`
- Modify: `main/xiaozhi-server/docs/production-readiness-assessment.md:69-74` (add the guarded LLM-to-TTS boundary only)

**Interfaces:**
- Documents the exact `sys_params` names from Task 4, not secrets or example real credentials.
- Documents API service choice, runtime mode, restart requirement, RAM permission, rollout and rollback.

- [ ] **Step 1: Write the operational guide with concrete prerequisite and rollout commands.**

The guide must state that the RAM principal needs `AliyunYundunGreenWebFullAccess`, that the service is `MultiModalGuard`, and that the Python SDK is `alibabacloud_green20220302==3.2.4`. Include the exact configuration tree and safe lifecycle:

```yaml
aliyun:
  access_key_id: ""
  access_key_secret: ""
content_safety:
  enabled: false
  provider: aliyun
  mode: enforce
  input_service: query_security_check_pro
  output_service: response_security_check_pro
  max_request_chars: 2000
  max_qps: 45
  output_chunk_chars: 120
```

Describe the activation sequence: deploy migration; enter credentials through the masked parameter UI; set `enabled=true`; restart each xiaozhi-server process; test safe text, blocked input, and blocked output; observe only metadata. Describe rollback as `enabled=false` followed by restart. State that `observe` calls the API and records decisions but never blocks, while `enforce` blocks API `block` and API failures.

- [ ] **Step 2: Add the single architecture boundary note.**

Update the outbound-flow section of `production-readiness-assessment.md` from `LLM → TTS` to `LLM → content safety gate → TTS`, with the invariant that no unapproved LLM text is put into `tts_text_queue`. Do not rewrite unrelated readiness content.

- [ ] **Step 3: Perform local verification without real credentials.**

Run: `cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety --cov=core.content_safety --cov=core.providers.content_safety --cov-report=term-missing -q`

Run: `cd main/manager-api && mvn test -DskipTests=false -Dtest=ConfigServiceImplTest`

Run: `cd main/manager-web && npm run test:unit`

Run: `git diff --check`

Expected: all automated checks PASS, the coverage report is at least 80% for new Python safety packages, and whitespace check has no output. This plan deliberately does not make an external Guardrails API request without a user-provided non-production RAM credential.

- [ ] **Step 4: Define the staging acceptance script before enabling production enforcement.**

Use a staging `manager-api` and RAM user. Execute the following manual cases while inspecting only redacted logs and the device result:

1. Send a normal text message and an ASR-derived message: both reach the LLM and receive a normal spoken reply.
2. Send a configured-block input: no intent/LLM request is issued, no raw STT echo is sent, and one randomly chosen input block message is spoken.
3. Make a staging LLM return a configured-block response, including through `direct_answer`: no unsafe text is spoken or retained, and one configured output block message is spoken.
4. Exercise `IntentProvider.replyResult`: an LLM-generated reply is checked; a forced `system_error_response` fallback is spoken without any API request.
5. Set `mode=observe`, restart, and repeat a blocked fixture: the result is logged as blocked metadata but content is delivered; restore `mode=enforce` afterwards.
6. Set `enabled=false`, restart, and repeat a normal message: no Guardrails requests occur and the prior chat behavior is unchanged.

- [ ] **Step 5: Commit the runbook deliverable.**

```bash
git add main/xiaozhi-server/docs/guides/aliyun-content-safety.md \
  main/xiaozhi-server/docs/production-readiness-assessment.md
git commit -m "docs: add aliyun content safety rollout guide"
```

## Plan self-review

- **Spec coverage:** Tasks 1–2 implement the pluggable component and official API behavior; Task 3 covers ASR/text input, normal LLM, `direct_answer`, recursive tool output and `IntentProvider.replyResult`; Task 4 provides manager-api-only configuration, credential renaming, array messages and UI masking; Task 5 provides activation, rollback and staging verification.
- **Input scope:** The plan explicitly checks only JSON `content` or raw user text and keeps prompts/history outside the provider request.
- **Safety behavior:** `observe`, `enforce`, `enabled`, fail-closed errors, output buffering, static fallback bypass and no raw content persistence are all specified with tests.
- **Placeholder scan:** no implementation step relies on an unresolved value or a generic “add tests” instruction; service names, config keys, migration IDs, defaults, files and verification commands are concrete.
- **Type consistency:** `ContentSafetyContext`, `ContentSafetyGate`, `OutputSafetyGate`, `SafetyResult`, `IntentReply`, `chat(..., safety_context=..., output_gate=...)` and `create_content_safety_provider(...)` are introduced once and used consistently by later tasks.
