from collections.abc import Mapping
from typing import Any

from core.providers.content_safety.base import ContentSafetyProviderBase
from core.providers.content_safety.noop import NoopContentSafetyProvider


def create_content_safety_provider(
    config: Mapping[str, Any],
) -> ContentSafetyProviderBase:
    safety_config = config.get("content_safety", {})
    if not safety_config.get("enabled", False):
        return NoopContentSafetyProvider()
    provider_name = safety_config.get("provider")
    if provider_name == "noop":
        return NoopContentSafetyProvider()
    if provider_name == "aliyun":
        from core.providers.content_safety.aliyun import AliyunContentSafetyProvider

        return AliyunContentSafetyProvider(safety_config, config.get("aliyun", {}))
    raise ValueError("Unsupported content_safety.provider")
