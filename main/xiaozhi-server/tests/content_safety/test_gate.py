from dataclasses import dataclass

import pytest

from core.providers.content_safety import SafetyDecision, SafetyDirection, SafetyResult


@dataclass(frozen=True)
class ProviderCall:
    direction: SafetyDirection
    content: str
    chat_id: str
    session_id: str | None
    done: bool


class RecordingProvider:
    def __init__(self, *, allow=False, results=()):
        self.calls: list[ProviderCall] = []
        self._results = list(results)
        self._default = allowed() if allow else None

    def check(self, direction, content, chat_id, session_id=None, done=False):
        self.calls.append(ProviderCall(direction, content, chat_id, session_id, done))
        if self._results:
            return self._results.pop(0)
        if self._default is not None:
            return self._default
        raise AssertionError("Provider received more checks than scripted")


def allowed():
    return SafetyResult(decision=SafetyDecision.ALLOW)


def blocked():
    return SafetyResult(decision=SafetyDecision.BLOCK, suggestion="block")


def errored():
    return SafetyResult(decision=SafetyDecision.ERROR, error_kind="api_error")


def config(**overrides):
    defaults = {
        "max_request_chars": 8,
        "output_chunk_chars": 4,
        "mode": "enforce",
        "input_block_message": ["输入受限"],
        "output_block_message": ["输出受限"],
        "system_error_response": "系统繁忙",
    }
    return defaults | overrides


def test_input_split_keeps_the_same_chat_id_and_never_exceeds_request_limit():
    from core.content_safety import ContentSafetyContext, ContentSafetyGate

    provider = RecordingProvider(allow=True)
    gate = ContentSafetyGate(provider, config(max_request_chars=4))

    result = gate.check_input("甲乙丙丁戊", ContentSafetyContext("turn-1"))

    assert result.allowed
    assert [call.content for call in provider.calls] == ["甲乙丙丁", "戊"]
    assert {call.chat_id for call in provider.calls} == {"turn-1"}
    assert all(len(call.content) <= 4 for call in provider.calls)
    assert all(call.direction is SafetyDirection.INPUT for call in provider.calls)


def test_input_exactly_at_unicode_limit_makes_one_request():
    from core.content_safety import ContentSafetyContext, ContentSafetyGate

    provider = RecordingProvider(allow=True)
    result = ContentSafetyGate(provider, config(max_request_chars=4)).check_input(
        "a你😀b", ContentSafetyContext("turn-1")
    )

    assert result.allowed
    assert [call.content for call in provider.calls] == ["a你😀b"]


def test_input_stops_at_the_first_block_or_error():
    from core.content_safety import ContentSafetyContext, ContentSafetyGate

    provider = RecordingProvider(results=[allowed(), errored(), allowed()])
    result = ContentSafetyGate(
        provider, config(max_request_chars=2, output_chunk_chars=2)
    ).check_input(
        "甲乙丙丁戊", ContentSafetyContext("turn-1")
    )

    assert result.blocked
    assert result.audit == errored()
    assert [call.content for call in provider.calls] == ["甲乙", "丙丁"]


def test_enforce_mode_blocks_input_when_provider_reports_block():
    from core.content_safety import ContentSafetyContext, ContentSafetyGate

    result = ContentSafetyGate(
        RecordingProvider(results=[blocked()]), config()
    ).check_input("危险", ContentSafetyContext("turn-1"))

    assert result.blocked is True
    assert result.audit == blocked()


def test_observe_mode_does_not_block_input_when_provider_reports_error():
    from core.content_safety import ContentSafetyContext, ContentSafetyGate

    result = ContentSafetyGate(
        RecordingProvider(results=[errored()]), config(mode="observe")
    ).check_input("暂时异常", ContentSafetyContext("turn-1"))

    assert result.blocked is False
    assert result.audit == errored()


def test_output_releases_on_punctuation_before_the_size_limit():
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    provider = RecordingProvider(allow=True)
    gate = OutputSafetyGate(
        provider, config(output_chunk_chars=6), ContentSafetyContext("turn-1"), "sentence-1"
    )

    result = gate.feed("安全。")

    assert result.released_parts == ("安全。",)
    assert provider.calls == [
        ProviderCall(SafetyDirection.OUTPUT, "安全。", "turn-1", "sentence-1", False)
    ]


def test_output_does_not_release_a_blocked_chunk_to_tts():
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    provider = RecordingProvider(results=[allowed(), blocked()])
    gate = OutputSafetyGate(
        provider, config(output_chunk_chars=3), ContentSafetyContext("turn-1"), "sentence-1"
    )

    assert gate.feed("安全。").released_parts == ("安全。",)
    blocked_result = gate.feed("危险。")

    assert blocked_result.blocked is True
    assert blocked_result.released_parts == ()


def test_finish_never_submits_an_invalid_empty_content_request():
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    provider = RecordingProvider(allow=True)
    gate = OutputSafetyGate(
        provider, config(output_chunk_chars=2), ContentSafetyContext("turn-1"), "sentence-1"
    )
    gate.feed("通过")

    assert gate.finish().released_parts == ()
    assert all(call.content for call in provider.calls)


def test_finish_flushes_remainder_with_done_true():
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    provider = RecordingProvider(allow=True)
    gate = OutputSafetyGate(
        provider, config(output_chunk_chars=5), ContentSafetyContext("turn-1"), "sentence-1"
    )

    assert gate.feed("通过").released_parts == ()
    assert gate.finish().released_parts == ("通过",)
    assert provider.calls[-1].done is True


def test_observe_mode_releases_content_when_provider_reports_block():
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    gate = OutputSafetyGate(
        RecordingProvider(results=[blocked()]),
        config(mode="observe"),
        ContentSafetyContext("turn-1"),
        "s-1",
    )

    assert gate.feed("可观察文本。").released_parts == ("可观察文本。",)


def test_observe_mode_releases_output_when_provider_reports_error():
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    result = OutputSafetyGate(
        RecordingProvider(results=[errored()]),
        config(mode="observe"),
        ContentSafetyContext("turn-1"),
        "s-1",
    ).feed("可观察文本。")

    assert result.blocked is False
    assert result.released_parts == ("可观察文本。",)


def test_output_blocks_when_provider_reports_an_api_error():
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    gate = OutputSafetyGate(
        RecordingProvider(results=[errored()]),
        config(output_chunk_chars=2),
        ContentSafetyContext("turn-1"),
        "s-1",
    )

    result = gate.feed("失败")

    assert result.blocked is True
    assert result.released_parts == ()
    assert result.audit == errored()


def test_block_message_uses_formal_nested_config_arrays(monkeypatch):
    from core.content_safety import ContentSafetyGate

    nested_config = {
        "system_error_response": "系统繁忙",
        "content_safety": config(
            input_block_message=["输入甲", "输入乙"],
            output_block_message=["输出甲", "输出乙"],
        ),
    }
    gate = ContentSafetyGate(
        RecordingProvider(results=[blocked()]), nested_config
    )
    monkeypatch.setattr("core.content_safety.gate.random.choice", lambda values: values[1])

    assert gate.input_block_message() == "输入乙"
    assert gate.output_block_message() == "输出乙"


def test_blocked_output_gate_exposes_its_configured_block_message(monkeypatch):
    from core.content_safety import ContentSafetyContext, OutputSafetyGate

    gate = OutputSafetyGate(
        RecordingProvider(results=[blocked()]),
        config(output_block_message=["甲", "乙"]),
        ContentSafetyContext("turn-1"),
        "s-1",
    )
    monkeypatch.setattr("core.content_safety.gate.random.choice", lambda values: values[1])

    assert gate.feed("危险。").blocked is True
    assert gate.output_block_message() == "乙"


@pytest.mark.parametrize("messages", [None, [], ["", "  "]])
def test_blank_message_arrays_fall_back_to_system_error_response(messages):
    from core.content_safety import ContentSafetyGate

    gate = ContentSafetyGate(
        RecordingProvider(allow=True),
        config(input_block_message=messages, output_block_message=messages),
    )

    assert gate.input_block_message() == "系统繁忙"
    assert gate.output_block_message() == "系统繁忙"


def test_string_message_config_falls_back_to_system_error_response():
    from core.content_safety import ContentSafetyGate

    gate = ContentSafetyGate(
        RecordingProvider(allow=True),
        config(input_block_message="错误", output_block_message="错误"),
    )

    assert gate.input_block_message() == "系统繁忙"
    assert gate.output_block_message() == "系统繁忙"


@pytest.mark.parametrize(
    "overrides",
    [
        {"max_request_chars": 0},
        {"output_chunk_chars": 0},
        {"output_chunk_chars": 9, "max_request_chars": 8},
        {"max_request_chars": 2001},
        {"max_request_chars": True},
        {"output_chunk_chars": True},
        {"mode": "disabled"},
    ],
)
def test_invalid_gate_configurations_are_rejected(overrides):
    from core.content_safety import ContentSafetyGate

    with pytest.raises(ValueError):
        ContentSafetyGate(RecordingProvider(allow=True), config(**overrides))
