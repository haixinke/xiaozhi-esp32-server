import json
from types import SimpleNamespace

import pytest

from core.providers.content_safety import SafetyDecision, SafetyDirection
from core.providers.content_safety.aliyun import AliyunContentSafetyProvider


class FakeClient:
    def __init__(self, _config):
        self.reply = api_reply()
        self.raise_error = None
        self.requests = []

    def multi_modal_guard(self, request):
        self.requests.append(request)
        if self.raise_error:
            raise self.raise_error
        return self.reply


def api_reply(suggestion="pass", request_id="req-default", code=200, status_code=200):
    return SimpleNamespace(
        status_code=status_code,
        body=SimpleNamespace(
            code=code,
            request_id=request_id,
            data=SimpleNamespace(
                suggestion=suggestion,
                detail=[
                    SimpleNamespace(
                        level="level-a",
                        result=[SimpleNamespace(label="label-a", level="level-a")],
                    )
                ],
            ),
        ),
    )


def make_aliyun_provider(monkeypatch, mode="enforce", safety_overrides=None):
    import core.providers.content_safety.aliyun as aliyun_module

    monkeypatch.setattr(aliyun_module, "Client", FakeClient)
    safety_config = {
            "mode": mode,
            "region_id": "cn-shanghai",
            "endpoint": "green-cip.cn-shanghai.aliyuncs.com",
            "input_service": "query_security_check_pro",
            "output_service": "chat_detection",
            "max_qps": 100,
            "connect_timeout_ms": 1000,
            "read_timeout_ms": 2000,
        }
    for key, value in (safety_overrides or {}).items():
        if value is None:
            safety_config.pop(key, None)
        else:
            safety_config[key] = value
    provider = AliyunContentSafetyProvider(
        safety_config,
        {"access_key_id": "test-id", "access_key_secret": "test-secret"},
    )
    return provider, provider._client


def test_aliyun_input_uses_configured_query_pro_service_and_normalizes_block(monkeypatch):
    """Catches an input request using the wrong service or allowing block."""
    provider, client = make_aliyun_provider(monkeypatch, mode="enforce")
    client.reply = api_reply(suggestion="block", request_id="req-1")

    result = provider.check(SafetyDirection.INPUT, "用户正文", "chat-1")

    assert result.decision is SafetyDecision.BLOCK
    assert client.requests[0].service == "query_security_check_pro"
    assert json.loads(client.requests[0].service_parameters) == {
        "content": "用户正文",
        "chatId": "chat-1",
    }
    assert result.request_id == "req-1"


def test_aliyun_output_uses_configured_service_and_final_chunk_metadata(monkeypatch):
    """Catches output routed to the input service or missing final metadata."""
    provider, client = make_aliyun_provider(monkeypatch)

    result = provider.check(
        SafetyDirection.OUTPUT, "回复", "chat-1", session_id="session-1", done=True
    )

    assert result.decision is SafetyDecision.ALLOW
    assert client.requests[0].service == "chat_detection"
    assert json.loads(client.requests[0].service_parameters) == {
        "content": "回复",
        "chatId": "chat-1",
        "sessionId": "session-1",
        "done": True,
    }


def test_aliyun_error_blocks_only_in_enforce_mode(monkeypatch):
    """Catches an API failure accidentally being allowed in enforce mode."""
    provider, client = make_aliyun_provider(monkeypatch, mode="enforce")
    client.raise_error = TimeoutError("network")

    assert (
        provider.check(SafetyDirection.OUTPUT, "回复", "chat-1").decision
        is SafetyDecision.ERROR
    )


@pytest.mark.parametrize(
    "reply",
    [api_reply(status_code=500), api_reply(code=400), SimpleNamespace(status_code=200, body=None)],
)
def test_aliyun_invalid_api_response_is_an_error(monkeypatch, reply):
    """Catches non-success or malformed Alibaba responses being allowed."""
    provider, client = make_aliyun_provider(monkeypatch)
    client.reply = reply

    assert (
        provider.check(SafetyDirection.INPUT, "正文", "chat-1").decision
        is SafetyDecision.ERROR
    )


def test_aliyun_uses_default_services_when_service_config_is_omitted(monkeypatch):
    """Catches a deployment without optional service keys failing or using wrong APIs."""
    provider, client = make_aliyun_provider(
        monkeypatch, safety_overrides={"input_service": None, "output_service": None}
    )

    provider.check(SafetyDirection.INPUT, "输入", "chat-1")
    provider.check(SafetyDirection.OUTPUT, "输出", "chat-1")

    assert [request.service for request in client.requests] == [
        "query_security_check_pro",
        "response_security_check_pro",
    ]


def test_aliyun_rejects_content_over_2000_characters_without_request(monkeypatch):
    """Catches oversized text being sent to Alibaba Cloud."""
    provider, client = make_aliyun_provider(monkeypatch)

    at_limit = provider.check(SafetyDirection.INPUT, "a" * 2000, "chat-1")
    over_limit = provider.check(SafetyDirection.INPUT, "a" * 2001, "chat-1")

    assert at_limit.decision is SafetyDecision.ALLOW
    assert over_limit.decision is SafetyDecision.ERROR
    assert over_limit.error_kind == "content_too_long"
    assert len(client.requests) == 1


@pytest.mark.parametrize("suggestion", [None, "unknown"])
def test_aliyun_unknown_or_absent_suggestion_is_a_malformed_response(
    monkeypatch, suggestion
):
    """Catches an unrecognized provider decision failing open."""
    provider, client = make_aliyun_provider(monkeypatch)
    client.reply = api_reply(suggestion=suggestion)

    result = provider.check(SafetyDirection.INPUT, "正文", "chat-1")

    assert result.decision is SafetyDecision.ERROR
    assert result.error_kind == "malformed_response"


@pytest.mark.parametrize(
    ("max_qps", "expected_spacing"), [(100, 0.02), (4, 0.25)],
)
def test_aliyun_throttles_consecutive_requests_at_configured_qps_cap(
    monkeypatch, max_qps, expected_spacing
):
    """Catches QPS above 50 or below 50 using the wrong request spacing."""
    provider, _client = make_aliyun_provider(
        monkeypatch, safety_overrides={"max_qps": max_qps}
    )
    import core.providers.content_safety.aliyun as aliyun_module

    clock_values = iter((1.0, 1.0, 1.0, 1.0 + expected_spacing))
    sleep_calls = []
    monkeypatch.setattr(aliyun_module.time, "monotonic", lambda: next(clock_values))
    monkeypatch.setattr(aliyun_module.time, "sleep", sleep_calls.append)

    provider.check(SafetyDirection.INPUT, "第一条", "chat-1")
    provider.check(SafetyDirection.INPUT, "第二条", "chat-1")

    assert sleep_calls == [pytest.approx(expected_spacing)]


@pytest.mark.parametrize("max_qps", [0, -1])
def test_aliyun_rejects_nonpositive_qps(monkeypatch, max_qps):
    """Catches invalid QPS configuration disabling the throttle."""
    with pytest.raises(ValueError, match="content_safety.max_qps must be positive"):
        make_aliyun_provider(monkeypatch, safety_overrides={"max_qps": max_qps})
