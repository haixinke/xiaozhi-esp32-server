import asyncio
import importlib


__all__ = [
    "test_blocked_input_clears_stale_abort_before_trusted_speech",
    "test_chat_debug_logs_only_redacted_operational_metadata",
    "test_recursive_response_creation_error_discards_parent_gate_buffer",
    "test_top_level_response_creation_error_uses_trusted_fallback_and_ends_once",
]


def _base():
    return importlib.import_module("tests.content_safety.test_chat_integration")


def test_blocked_input_clears_stale_abort_before_trusted_speech(
    connection_module, monkeypatch
):
    base = _base()
    handler = importlib.import_module("core.handle.intentHandler")
    conn = base.InputConnection(base.SafetyDecision.BLOCK)
    conn.client_abort = True
    monkeypatch.setattr(
        base.receiveAudioHandle,
        "speak_trusted_text",
        handler.speak_trusted_text,
    )

    asyncio.run(base.receiveAudioHandle.startToChat(conn, "受限内容"))

    assert conn.client_abort is False
    assert conn.tts.spoken_texts == ["输入已拒绝"]
    assert conn.tts.last_action_count == 1


class SingleToolCallLLM:
    def __init__(self, arguments):
        self.arguments = arguments

    def response_with_functions(self, *_args, **_kwargs):
        base = _base()
        return iter(
            [
                (
                    None,
                    [
                        base.ToolCallDelta(
                            id="call-secret",
                            function=base.FunctionDelta(
                                name="lookup", arguments=self.arguments
                            ),
                        )
                    ],
                )
            ]
        )


def test_chat_debug_logs_only_redacted_operational_metadata(
    connection_module, monkeypatch
):
    base = _base()

    class RecordingLogger(base.NullLogger):
        def __init__(self):
            self.debug_messages = []

        def debug(self, message, *_args, **_kwargs):
            self.debug_messages.append(
                message() if callable(message) else str(message)
            )

    arguments = '{"token":"argument-secret"}'
    conn = base.chat_connection(connection_module, outputs=[], decisions=[])
    conn.logger = RecordingLogger()
    conn.intent_type = "function_call"
    conn.loop = object()
    conn.llm = SingleToolCallLLM(arguments)
    tool_result = connection_module.ActionResponse(
        action=connection_module.Action.RESPONSE,
        response="tool-secret-response",
    )

    class FunctionHandler:
        def get_functions(self):
            return []

        async def handle_llm_function_call(self, *_args):
            return tool_result

    conn.func_handler = FunctionHandler()

    def immediate_tool_result(coroutine, _loop):
        coroutine.close()
        return base.ImmediateFuture(tool_result)

    monkeypatch.setattr(
        connection_module.asyncio,
        "run_coroutine_threadsafe",
        immediate_tool_result,
    )

    conn.chat(
        "user-secret-input",
        safety_context=base.ContentSafetyContext("turn-log"),
    )

    debug_log = "\n".join(conn.logger.debug_messages)
    assert "argument-secret" not in debug_log
    assert "user-secret-input" not in debug_log
    assert "tool-secret-response" not in debug_log
    assert "chat_id=turn-log" in debug_log
    assert "function_name=lookup" in debug_log
    assert "function_id=call-secret" in debug_log
    assert f"argument_chars={len(arguments)}" in debug_log
    assert "dialogue_messages=2" in debug_log
    assert "roles=assistant:1,user:1" in debug_log


class ResponseCreationFailureLLM:
    def response(self, *_args, **_kwargs):
        raise RuntimeError("response creation failed")


def test_top_level_response_creation_error_uses_trusted_fallback_and_ends_once(
    connection_module,
):
    base = _base()
    conn = base.chat_connection(connection_module, outputs=[], decisions=[])
    conn.llm = ResponseCreationFailureLLM()

    result = conn.chat(
        "用户输入",
        safety_context=base.ContentSafetyContext("turn-create-error"),
    )

    assert result is False
    assert conn.tts.middle_texts == ["系统繁忙"]
    assert conn.tts.last_action_count == 1


class RecursiveResponseCreationFailureLLM:
    def __init__(self):
        self.calls = 0

    def response_with_functions(self, *_args, **_kwargs):
        self.calls += 1
        if self.calls > 1:
            raise RuntimeError("recursive response creation failed")
        base = _base()
        return iter(
            [
                ("未完成", None),
                (
                    None,
                    [
                        base.ToolCallDelta(
                            id="call-1",
                            function=base.FunctionDelta(
                                name="lookup", arguments="{}"
                            ),
                        )
                    ],
                ),
            ]
        )


def test_recursive_response_creation_error_discards_parent_gate_buffer(
    connection_module, monkeypatch
):
    base = _base()
    conn = base.chat_connection(
        connection_module,
        outputs=[],
        decisions=[base.SafetyDecision.ALLOW],
    )
    conn.intent_type = "function_call"
    conn.loop = object()
    conn.llm = RecursiveResponseCreationFailureLLM()
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
        return base.ImmediateFuture(tool_result)

    monkeypatch.setattr(
        connection_module.asyncio,
        "run_coroutine_threadsafe",
        immediate_tool_result,
    )

    result = conn.chat(
        "用户输入",
        safety_context=base.ContentSafetyContext("turn-recursive-create"),
    )

    assert result is False
    assert conn.tts.middle_texts == ["系统繁忙"]
    assert "未完成" not in conn.dialogue.contents
    assert conn.tts.last_action_count == 1
