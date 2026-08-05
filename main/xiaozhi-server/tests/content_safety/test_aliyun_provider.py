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


def make_aliyun_provider(monkeypatch, mode="enforce"):
    import core.providers.content_safety.aliyun as aliyun_module

    monkeypatch.setattr(aliyun_module, "Client", FakeClient)
    provider = AliyunContentSafetyProvider(
        {
            "mode": mode,
            "region_id": "cn-shanghai",
            "endpoint": "green-cip.cn-shanghai.aliyuncs.com",
            "input_service": "query_security_check_pro",
            "output_service": "chat_detection",
            "max_qps": 100,
            "connect_timeout_ms": 1000,
            "read_timeout_ms": 2000,
        },
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
