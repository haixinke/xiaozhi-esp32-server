from collections.abc import Mapping
from typing import Any

from core.providers.content_safety.aliyun import AliyunContentSafetyProvider
from core.providers.content_safety.base import ContentSafetyProviderBase
from core.providers.content_safety.noop import NoopContentSafetyProvider


def create_content_safety_provider(
    config: Mapping[str, Any],
) -> ContentSafetyProviderBase:
    safety_config = config.get("content_safety", {})
    if not safety_config.get("enabled", False):
        return NoopContentSafetyProvider()
    if safety_config.get("provider") == "aliyun":
        return AliyunContentSafetyProvider(safety_config, config.get("aliyun", {}))
    raise ValueError("Unsupported content_safety.provider")
