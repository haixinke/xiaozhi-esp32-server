import subprocess
import sys
from pathlib import Path

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


def test_noop_factory_does_not_import_alibaba_sdk_in_a_clean_process():
    """Catches noop creation loading Alibaba SDK code at module import time."""
    script = """
import importlib.abc
import sys

class AlibabaSdkBlocker(importlib.abc.MetaPathFinder):
    def find_spec(self, fullname, path=None, target=None):
        if fullname.startswith('alibabacloud'):
            raise AssertionError('noop factory imported Alibaba SDK')
        return None

sys.meta_path.insert(0, AlibabaSdkBlocker())
from core.utils.content_safety import create_content_safety_provider
provider = create_content_safety_provider({'content_safety': {'enabled': True, 'provider': 'noop'}})
assert provider.check.__name__ == 'check'
"""

    result = subprocess.run(
        [sys.executable, "-c", script],
        cwd=Path(__file__).parents[2],
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 0, result.stderr


def test_enabled_noop_factory_returns_allowing_provider():
    """Catches explicitly enabled noop configuration being rejected."""
    provider = create_content_safety_provider(
        {"content_safety": {"enabled": True, "provider": "noop"}}
    )

    assert (
        provider.check(SafetyDirection.INPUT, "任意文本", "chat-1").decision
        is SafetyDecision.ALLOW
    )
