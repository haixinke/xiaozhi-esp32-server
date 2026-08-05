import asyncio
from dataclasses import dataclass
from enum import Enum
import importlib
import sys
from types import ModuleType

import pytest

from core.content_safety import ContentSafetyContext, ContentSafetyGate, OutputSafetyGate
from core.providers.tts.dto.dto import SentenceType

sys.modules.setdefault("opuslib_next", ModuleType("opuslib_next"))
pydub_stub = ModuleType("pydub")
pydub_stub.AudioSegment = object
sys.modules.setdefault("pydub", pydub_stub)
intent_handler_stub = ModuleType("core.handle.intentHandler")


async def _unused_intent_handler(_conn, _text):
    raise AssertionError("test must replace the intent handler")


intent_handler_stub.handle_user_intent = _unused_intent_handler


def _unused_trusted_speech(_conn, _text):
    raise AssertionError("test must replace trusted speech")


intent_handler_stub.speak_trusted_text = _unused_trusted_speech
sys.modules.setdefault("core.handle.intentHandler", intent_handler_stub)
send_audio_stub = ModuleType("core.handle.sendAudioHandle")


async def _unused_send_stt(_conn, _text):
    raise AssertionError("test must replace send_stt_message")


send_audio_stub.send_stt_message = _unused_send_stt
send_audio_stub.SentenceType = SentenceType
sys.modules.setdefault("core.handle.sendAudioHandle", send_audio_stub)

from core.handle import receiveAudioHandle

sys.modules.pop("core.handle.intentHandler", None)
sys.modules.pop("core.handle.sendAudioHandle", None)
sys.modules.pop("opuslib_next", None)
sys.modules.pop("pydub", None)
from core.providers.content_safety import SafetyDecision, SafetyResult


class NullLogger:
    def bind(self, **_kwargs):
        return self

    def info(self, *_args, **_kwargs):
        return None

    def debug(self, *_args, **_kwargs):
        return None

    def warning(self, *_args, **_kwargs):
        return None

    def error(self, *_args, **_kwargs):
        return None


class DialogueRecorder:
    def __init__(self):
        self.messages = []

    @property
    def contents(self):
        return [message.content for message in self.messages if message.content]

    def put(self, message):
        self.messages.append(message)

    def get_llm_dialogue_with_memory(self, *_args):
        return [
            {"role": message.role, "content": message.content}
            for message in self.messages
            if message.content is not None
        ]

    def get_llm_dialogue(self):
        return self.get_llm_dialogue_with_memory()


class QueueRecorder:
    def __init__(self):
        self.items = []

    def put(self, item):
        self.items.append(item)


class TTSRecorder:
    def __init__(self):
        self.tts_text_queue = QueueRecorder()
        self.spoken_texts = []
        self.stored_texts = []

    def tts_one_sentence(self, _conn, _content_type, content_detail):
        self.spoken_texts.append(content_detail)

    def store_tts_text(self, sentence_id, text):
        self.stored_texts.append((sentence_id, text))

    @property
    def middle_texts(self):
        return [
            item.content_detail
            for item in self.tts_text_queue.items
            if item.sentence_type is SentenceType.MIDDLE
        ] + self.spoken_texts

    @property
    def last_action_count(self):
        return sum(
            item.sentence_type is SentenceType.LAST
            for item in self.tts_text_queue.items
        )


class ExecutorRecorder:
    def __init__(self):
        self.submissions = []

    def submit(self, fn, *args):
        self.submissions.append(Submission(fn, args))


@dataclass(frozen=True)
class Submission:
    fn: object
    args: tuple


class InputSafetyProvider:
    def __init__(self, decision):
        self.decision = decision
        self.calls = []

    def check(self, direction, content, chat_id, session_id=None, done=False):
        self.calls.append(
            {
                "direction": direction,
                "content": content,
                "chat_id": chat_id,
                "session_id": session_id,
                "done": done,
            }
        )
        return SafetyResult(decision=self.decision)


def safety_config():
    return {
        "system_error_response": "系统繁忙",
        "content_safety": {
            "mode": "enforce",
            "max_request_chars": 2000,
            "output_chunk_chars": 2000,
            "input_block_message": ["输入已拒绝"],
            "output_block_message": ["输出已拒绝"],
        },
    }


class InputConnection:
    def __init__(self, decision):
        self.config = safety_config()
        self.logger = NullLogger()
        self.dialogue = DialogueRecorder()
        self.tts = TTSRecorder()
        self.executor = ExecutorRecorder()
        self.introduced_speakers = set()
        self.current_speaker = None
        self.need_bind = False
        self.max_output_size = 0
        self.client_is_speaking = False
        self.client_listen_mode = "auto"
        self.client_abort = False
        self.sentence_id = ""
        self.provider = InputSafetyProvider(decision)
        self.content_safety_gate = ContentSafetyGate(self.provider, self.config)

    def chat(self, *_args):
        raise AssertionError("executor recorder must not run submitted work")


def _module(name, **attributes):
    module = ModuleType(name)
    for key, value in attributes.items():
        setattr(module, key, value)
    return module


async def _async_false(*_args, **_kwargs):
    return False


async def _async_none(*_args, **_kwargs):
    return None


@pytest.fixture(scope="module")
def connection_module():
    class Action(Enum):
        ERROR = -1
        NOTFOUND = 0
        NONE = 1
        RESPONSE = 2
        REQLLM = 3
        RECORD = 4

    class ActionResponse:
        def __init__(self, action, result=None, response=None):
            self.action = action
            self.result = result
            self.response = response

    stubs = {
        "opuslib_next": _module("opuslib_next"),
        "websockets": _module("websockets", ServerConnection=object),
        "core.utils.modules_initialize": _module(
            "core.utils.modules_initialize",
            initialize_modules=lambda *_args, **_kwargs: {},
            initialize_tts=lambda *_args, **_kwargs: None,
            initialize_asr=lambda *_args, **_kwargs: None,
        ),
        "core.handle.reportHandle": _module(
            "core.handle.reportHandle",
            report=lambda *_args, **_kwargs: None,
            enqueue_tool_report=lambda *_args, **_kwargs: None,
        ),
        "core.handle.helloHandle": _module(
            "core.handle.helloHandle", checkWakeupWords=_async_false
        ),
        "core.handle.sendAudioHandle": _module(
            "core.handle.sendAudioHandle",
            send_stt_message=_async_none,
            SentenceType=SentenceType,
        ),
        "core.providers.tts.default": _module(
            "core.providers.tts.default", DefaultTTS=object
        ),
        "core.handle.textHandle": _module(
            "core.handle.textHandle", handleTextMessage=lambda *_args: None
        ),
        "core.providers.tools.unified_tool_handler": _module(
            "core.providers.tools.unified_tool_handler", UnifiedToolHandler=object
        ),
        "plugins_func.loadplugins": _module(
            "plugins_func.loadplugins", auto_import_modules=lambda *_args: None
        ),
        "plugins_func.register": _module(
            "plugins_func.register", Action=Action, ActionResponse=ActionResponse
        ),
        "plugins_func.functions.play_music": _module(
            "plugins_func.functions.play_music",
            initialize_music_handler=lambda *_args: None,
        ),
        "core.auth": _module(
            "core.auth", AuthenticationError=type("AuthenticationError", (Exception,), {})
        ),
        "config.config_loader": _module(
            "config.config_loader", get_private_config_from_api=lambda *_args: None
        ),
        "config.logger": _module(
            "config.logger",
            setup_logging=lambda *_args, **_kwargs: NullLogger(),
            build_module_string=lambda *_args: "",
            create_connection_logger=lambda *_args: NullLogger(),
        ),
        "config.manage_api_client": _module(
            "config.manage_api_client",
            DeviceNotFoundException=type("DeviceNotFoundException", (Exception,), {}),
            DeviceBindException=type("DeviceBindException", (Exception,), {}),
            generate_and_save_chat_title=lambda *_args, **_kwargs: None,
        ),
        "core.utils.prompt_manager": _module(
            "core.utils.prompt_manager", PromptManager=object
        ),
        "core.utils.voiceprint_provider": _module(
            "core.utils.voiceprint_provider", VoiceprintProvider=object
        ),
        "core.utils.current_time": _module(
            "core.utils.current_time",
            get_current_time_info=lambda: (
                "12:00",
                "2026-08-05",
                "星期三",
                "六月廿三",
            ),
        ),
    }
    originals = {name: sys.modules.get(name) for name in stubs}
    sys.modules.update(stubs)
    try:
        yield importlib.import_module("core.connection")
    finally:
        for name, original in originals.items():
            if original is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = original


class StreamingLLM:
    def __init__(self, outputs):
        self.outputs = outputs

    def response(self, *_args, **_kwargs):
        return iter(self.outputs)


def chat_connection(connection_module, outputs, decisions):
    conn = connection_module.ConnectionHandler.__new__(
        connection_module.ConnectionHandler
    )
    conn.config = safety_config()
    conn.session_id = "session-1"
    conn.logger = NullLogger()
    conn.dialogue = DialogueRecorder()
    conn.tts = TTSRecorder()
    conn.llm = StreamingLLM(outputs)
    conn.memory = None
    conn.intent_type = "nointent"
    conn.current_speaker = None
    conn.system_introduced_speakers = set()
    conn.features = {}
    conn.client_abort = False
    conn.sentence_id = ""
    conn.provider = ScriptedSafetyProvider(decisions)
    conn.content_safety_provider = conn.provider
    return conn


class ScriptedSafetyProvider(InputSafetyProvider):
    def __init__(self, decisions):
        self.decisions = list(decisions)
        self.calls = []

    def check(self, direction, content, chat_id, session_id=None, done=False):
        if not self.decisions:
            raise AssertionError("provider received more checks than scripted")
        self.calls.append(
            {
                "direction": direction,
                "content": content,
                "chat_id": chat_id,
                "session_id": session_id,
                "done": done,
            }
        )
        return SafetyResult(decision=self.decisions.pop(0))


def test_blocked_input_json_user_content_never_reaches_intent_or_chat(monkeypatch):
    conn = InputConnection(SafetyDecision.BLOCK)
    intent_inputs = []
    stt_inputs = []

    async def record_intent(_conn, text):
        intent_inputs.append(text)
        return False

    async def record_stt(_conn, text):
        stt_inputs.append(text)

    def record_trusted_speech(_conn, text):
        conn.tts.spoken_texts.append(text)

    monkeypatch.setattr(receiveAudioHandle, "handle_user_intent", record_intent)
    monkeypatch.setattr(receiveAudioHandle, "send_stt_message", record_stt)
    monkeypatch.setattr(
        receiveAudioHandle, "speak_trusted_text", record_trusted_speech
    )

    asyncio.run(
        receiveAudioHandle.startToChat(
            conn, '{"speaker":"小明","content":"受限内容"}'
        )
    )

    assert intent_inputs == []
    assert conn.executor.submissions == []
    assert stt_inputs == []
    assert conn.tts.spoken_texts == ["输入已拒绝"]
    assert "受限内容" not in conn.dialogue.contents


def test_first_speaker_turn_checks_only_content_but_preserves_llm_payload(monkeypatch):
    conn = InputConnection(SafetyDecision.ALLOW)
    raw = '{"speaker":"小明","content":"你好"}'

    async def no_intent(_conn, _text):
        return False

    async def ignore_stt(_conn, _text):
        return None

    monkeypatch.setattr(receiveAudioHandle, "handle_user_intent", no_intent)
    monkeypatch.setattr(receiveAudioHandle, "send_stt_message", ignore_stt)

    asyncio.run(receiveAudioHandle.startToChat(conn, raw))

    assert [call["content"] for call in conn.provider.calls] == ["你好"]
    assert conn.executor.submissions[0].args[0] == raw


def test_blocked_speaker_turn_does_not_consume_first_allowed_llm_payload(monkeypatch):
    conn = InputConnection(SafetyDecision.ALLOW)
    conn.provider = ScriptedSafetyProvider(
        [SafetyDecision.BLOCK, SafetyDecision.ALLOW]
    )
    conn.content_safety_gate = ContentSafetyGate(conn.provider, conn.config)
    safe_raw = '{"speaker":"小明","content":"你好"}'

    async def no_intent(_conn, _text):
        return False

    async def ignore_stt(_conn, _text):
        return None

    monkeypatch.setattr(receiveAudioHandle, "handle_user_intent", no_intent)
    monkeypatch.setattr(receiveAudioHandle, "send_stt_message", ignore_stt)
    monkeypatch.setattr(
        receiveAudioHandle, "speak_trusted_text", lambda *_args: None
    )

    asyncio.run(
        receiveAudioHandle.startToChat(
            conn, '{"speaker":"小明","content":"受限内容"}'
        )
    )
    asyncio.run(receiveAudioHandle.startToChat(conn, safe_raw))

    assert len(conn.executor.submissions) == 1
    assert conn.executor.submissions[0].args[0] == safe_raw


def test_normal_llm_stream_releases_only_checked_chunks_before_last_action(
    connection_module,
):
    conn = chat_connection(
        connection_module,
        outputs=["安全。", "继续。"],
        decisions=[SafetyDecision.ALLOW, SafetyDecision.ALLOW],
    )

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == ["安全。", "继续。"]
    assert {call["chat_id"] for call in conn.provider.calls} == {"turn-1"}
    assert conn.tts.last_action_count == 1


def test_midstream_block_never_releases_or_persists_unchecked_output(
    connection_module,
):
    conn = chat_connection(
        connection_module,
        outputs=["安全。", "危险。", "不会继续。"],
        decisions=[SafetyDecision.ALLOW, SafetyDecision.BLOCK],
    )

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == ["安全。", "输出已拒绝"]
    assert "危险。" not in conn.dialogue.contents
    assert "不会继续。" not in conn.dialogue.contents
    assert conn.tts.last_action_count == 1
    assert conn.client_abort is False


@dataclass
class FunctionDelta:
    name: str
    arguments: str


@dataclass
class ToolCallDelta:
    id: str
    function: FunctionDelta
    index: int = 0


class DirectAnswerLLM:
    def __init__(self, argument_chunks):
        self.argument_chunks = argument_chunks

    def response_with_functions(self, *_args, **_kwargs):
        return iter(
            (
                None,
                [
                    ToolCallDelta(
                        id="call-1" if index == 0 else "",
                        function=FunctionDelta(
                            name="direct_answer" if index == 0 else "",
                            arguments=arguments,
                        ),
                    )
                ],
            )
            for index, arguments in enumerate(self.argument_chunks)
        )


def direct_answer_connection(connection_module, decisions):
    conn = chat_connection(connection_module, outputs=[], decisions=decisions)
    conn.intent_type = "function_call"
    conn.func_handler = type(
        "FunctionRegistry", (), {"get_functions": lambda self: []}
    )()
    conn.llm = DirectAnswerLLM(['{"response":"安全回答', '结束"}'])
    return conn


def test_direct_answer_stream_and_remainder_share_one_output_gate(connection_module):
    conn = direct_answer_connection(connection_module, [SafetyDecision.ALLOW])

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == ["安全回答结束"]
    assert [call["content"] for call in conn.provider.calls] == ["安全回答结束"]
    assert conn.dialogue.contents[-1] == "安全回答结束"
    assert conn.tts.last_action_count == 1


def test_direct_answer_block_replaces_unreleased_content_with_trusted_message(
    connection_module,
):
    conn = direct_answer_connection(connection_module, [SafetyDecision.BLOCK])

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == ["输出已拒绝"]
    assert "安全回答结束" not in conn.dialogue.contents
    assert conn.tts.last_action_count == 1


class InvalidFunctionCallLLM:
    def response_with_functions(self, *_args, **_kwargs):
        return iter([("<tool_call>invalid", None)])


def test_invalid_function_call_payload_is_not_spoken_or_persisted(connection_module):
    conn = chat_connection(
        connection_module, outputs=[], decisions=[SafetyDecision.ALLOW]
    )
    conn.intent_type = "function_call"
    conn.func_handler = type(
        "FunctionRegistry", (), {"get_functions": lambda self: []}
    )()
    conn.llm = InvalidFunctionCallLLM()

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == []
    assert "<tool_call>invalid" not in conn.dialogue.contents


def test_recursive_function_result_reuses_context_and_output_gate(connection_module):
    context = ContentSafetyContext("turn-1")
    provider = ScriptedSafetyProvider([SafetyDecision.ALLOW])
    output_gate = OutputSafetyGate(
        provider, safety_config(), context, "sentence-1"
    )
    recursive_calls = []

    class RecursiveConnection:
        def __init__(self):
            self.dialogue = DialogueRecorder()

        def chat(self, *args, **kwargs):
            recursive_calls.append((args, kwargs))

    conn = RecursiveConnection()
    tool_result = connection_module.ActionResponse(
        action=connection_module.Action.REQLLM, result="工具结果"
    )
    tool_call = {"id": "call-1", "name": "tool", "arguments": "{}"}

    connection_module.ConnectionHandler._handle_function_result(
        conn,
        [(tool_result, tool_call)],
        depth=0,
        safety_context=context,
        output_gate=output_gate,
    )

    assert recursive_calls == [
        ((None,), {"depth": 1, "safety_context": context, "output_gate": output_gate})
    ]


class RecursiveStreamFailureLLM:
    def __init__(self):
        self.calls = 0

    def response_with_functions(self, *_args, **_kwargs):
        self.calls += 1
        if self.calls == 1:
            return iter(
                [
                    (
                        None,
                        [
                            ToolCallDelta(
                                id="call-1",
                                function=FunctionDelta(
                                    name="lookup", arguments="{}"
                                ),
                            )
                        ],
                    )
                ]
            )

        def failing_stream():
            yield ("未完成", None)
            raise RuntimeError("stream failed")

        return failing_stream()


def test_recursive_stream_error_discards_shared_gate_buffer_and_ends_once(
    connection_module, monkeypatch
):
    conn = chat_connection(
        connection_module, outputs=[], decisions=[SafetyDecision.ALLOW]
    )
    conn.intent_type = "function_call"
    conn.loop = object()
    conn.llm = RecursiveStreamFailureLLM()
    tool_result = connection_module.ActionResponse(
        action=connection_module.Action.REQLLM, result="工具结果"
    )

    class FunctionHandler:
        def get_functions(self):
            return []

        async def handle_llm_function_call(self, *_args):
            return tool_result

    conn.func_handler = FunctionHandler()

    def immediate_tool_result(coroutine, _loop):
        coroutine.close()
        return ImmediateFuture(tool_result)

    monkeypatch.setattr(
        connection_module.asyncio,
        "run_coroutine_threadsafe",
        immediate_tool_result,
    )

    conn.chat("用户输入", safety_context=ContentSafetyContext("turn-1"))

    assert conn.tts.middle_texts == ["系统繁忙"]
    assert "未完成" not in conn.dialogue.contents
    assert conn.tts.last_action_count == 1


class NoStreamLLM:
    def __init__(self, result=None, error=None):
        self.result = result
        self.error = error

    def response_no_stream(self, **_kwargs):
        if self.error is not None:
            raise self.error
        return self.result


def intent_llm_module(connection_module):
    return importlib.import_module(
        "core.providers.intent.intent_llm.intent_llm"
    )


def test_generated_intent_reply_records_llm_provenance(connection_module):
    module = intent_llm_module(connection_module)
    provider = module.IntentProvider.__new__(module.IntentProvider)
    provider.llm = NoStreamLLM(result="模型回复")
    provider.config = safety_config()

    reply = asyncio.run(provider.replyResult("上下文", "用户问题"))

    assert reply.text == "模型回复"
    assert reply.generated is True


def test_static_intent_reply_records_fallback_provenance(connection_module):
    module = intent_llm_module(connection_module)
    provider = module.IntentProvider.__new__(module.IntentProvider)
    provider.llm = NoStreamLLM(error=RuntimeError("offline"))
    provider.config = safety_config()

    reply = asyncio.run(provider.replyResult("上下文", "用户问题"))

    assert reply.text == "系统繁忙"
    assert reply.generated is False


@dataclass(frozen=True)
class ReplyProvenance:
    text: str
    generated: bool


class ImmediateExecutor:
    def submit(self, fn, *args):
        fn(*args)


class ImmediateFuture:
    def __init__(self, value):
        self.value = value

    def result(self, *_args, **_kwargs):
        return self.value


def intent_connection(reply, decisions):
    conn = type("IntentConnection", (), {})()
    conn.logger = NullLogger()
    conn.dialogue = DialogueRecorder()
    conn.tts = TTSRecorder()
    conn.executor = ImmediateExecutor()
    conn.loop = object()
    conn.intent = type("Intent", (), {"replyResult": _async_none})()
    conn.client_abort = False
    conn.sentence_id = "intent-sentence"
    conn.config = safety_config()
    conn.provider = ScriptedSafetyProvider(decisions)
    conn.content_safety_provider = conn.provider
    conn.reply = reply
    return conn


def test_generated_intent_result_is_checked_before_speech(
    connection_module, monkeypatch
):
    handler = importlib.import_module("core.handle.intentHandler")
    reply = ReplyProvenance("模型回复", generated=True)
    conn = intent_connection(reply, [SafetyDecision.ALLOW])

    def immediate_reply(coroutine, _loop):
        coroutine.close()
        return ImmediateFuture(reply)

    monkeypatch.setattr(handler.asyncio, "run_coroutine_threadsafe", immediate_reply)

    asyncio.run(
        handler.process_intent_result(
            conn,
            '{"function_call":{"name":"result_for_context"}}',
            "用户问题",
        )
    )

    assert [call["content"] for call in conn.provider.calls] == ["模型回复"]
    assert conn.tts.middle_texts == ["模型回复"]


def test_static_intent_fallback_bypasses_output_provider(
    connection_module, monkeypatch
):
    handler = importlib.import_module("core.handle.intentHandler")
    reply = ReplyProvenance("系统繁忙", generated=False)
    conn = intent_connection(reply, [])

    def immediate_reply(coroutine, _loop):
        coroutine.close()
        return ImmediateFuture(reply)

    monkeypatch.setattr(handler.asyncio, "run_coroutine_threadsafe", immediate_reply)

    asyncio.run(
        handler.process_intent_result(
            conn,
            '{"function_call":{"name":"result_for_context"}}',
            "用户问题",
        )
    )

    assert conn.provider.calls == []
    assert conn.tts.middle_texts == ["系统繁忙"]
