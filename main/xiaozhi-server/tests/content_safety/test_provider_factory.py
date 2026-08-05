import pytest

from core.providers.content_safety import SafetyDecision, SafetyDirection
from core.utils.content_safety import create_content_safety_provider


def test_disabled_factory_returns_noop_without_sdk_client(monkeypatch):
    """Catches a disabled feature initializing the SDK or making a network call."""
    import core.providers.content_safety.aliyun as aliyun_module

    monkeypatch.setattr(
        aliyun_module.Client,
        "__init__",
        lambda *_args: pytest.fail("disabled safety must not initialize an SDK client"),
    )
    provider = create_content_safety_provider({"content_safety": {"enabled": False}})

    assert (
        provider.check(SafetyDirection.INPUT, "任意文本", "chat-1").decision
        is SafetyDecision.ALLOW
    )


def test_enabled_unknown_provider_is_a_configuration_error():
    """Catches an unsupported configured provider silently disabling safety."""
    with pytest.raises(ValueError, match="Unsupported content_safety.provider"):
        create_content_safety_provider(
            {"content_safety": {"enabled": True, "provider": "unknown"}}
        )
