import json
import threading
import time
from collections.abc import Mapping
from typing import Any

from alibabacloud_green20220302 import models
from alibabacloud_green20220302.client import Client
from alibabacloud_tea_openapi.models import Config

from .base import (
    ContentSafetyProviderBase,
    SafetyDecision,
    SafetyDirection,
    SafetyResult,
)


class AliyunContentSafetyProvider(ContentSafetyProviderBase):
    def __init__(
        self, safety_config: Mapping[str, Any], aliyun_config: Mapping[str, Any]
    ) -> None:
        self._input_service = safety_config["input_service"]
        self._output_service = safety_config["output_service"]
        configured_qps = float(safety_config.get("max_qps", 50))
        if configured_qps <= 0:
            raise ValueError("content_safety.max_qps must be positive")
        self._min_interval_seconds = 1 / min(configured_qps, 50)
        self._throttle_lock = threading.Lock()
        self._last_request_at = 0.0
        self._client = Client(
            Config(
                access_key_id=aliyun_config["access_key_id"],
                access_key_secret=aliyun_config["access_key_secret"],
                region_id=safety_config["region_id"],
                endpoint=safety_config["endpoint"],
                connect_timeout=safety_config.get("connect_timeout_ms"),
                read_timeout=safety_config.get("read_timeout_ms"),
            )
        )

    def check(
        self,
        direction: SafetyDirection,
        content: str,
        chat_id: str,
        session_id: str | None = None,
        done: bool = False,
    ) -> SafetyResult:
        service_parameters: dict[str, Any] = {"content": content, "chatId": chat_id}
        if session_id:
            service_parameters["sessionId"] = session_id
        if done and content:
            service_parameters["done"] = True

        request = models.MultiModalGuardRequest(
            service=self._service_for(direction),
            service_parameters=json.dumps(service_parameters, ensure_ascii=False),
        )
        try:
            self._throttle()
            response = self._client.multi_modal_guard(request)
            return self._normalize_response(response)
        except Exception:
            return SafetyResult(
                decision=SafetyDecision.ERROR,
                error_kind="api_error",
            )

    def _service_for(self, direction: SafetyDirection) -> str:
        if direction is SafetyDirection.INPUT:
            return self._input_service
        if direction is SafetyDirection.OUTPUT:
            return self._output_service
        raise ValueError("Unsupported safety direction")

    def _throttle(self) -> None:
        with self._throttle_lock:
            now = time.monotonic()
            wait_seconds = self._min_interval_seconds - (now - self._last_request_at)
            if wait_seconds > 0:
                time.sleep(wait_seconds)
            self._last_request_at = time.monotonic()

    def _normalize_response(self, response: Any) -> SafetyResult:
        body = getattr(response, "body", None)
        if getattr(response, "status_code", None) != 200 or getattr(body, "code", None) != 200:
            return self._error_result("api_error", body)

        data = getattr(body, "data", None)
        suggestion = getattr(data, "suggestion", None)
        if data is None or suggestion is None:
            return self._error_result("malformed_response", body)

        labels, levels = self._audit_metadata(data)
        return SafetyResult(
            decision=(
                SafetyDecision.BLOCK
                if suggestion == "block"
                else SafetyDecision.ALLOW
            ),
            suggestion=suggestion,
            request_id=getattr(body, "request_id", None),
            labels=labels,
            levels=levels,
        )

    @staticmethod
    def _error_result(error_kind: str, body: Any) -> SafetyResult:
        return SafetyResult(
            decision=SafetyDecision.ERROR,
            request_id=getattr(body, "request_id", None),
            error_kind=error_kind,
        )

    @staticmethod
    def _audit_metadata(data: Any) -> tuple[tuple[str, ...], tuple[str, ...]]:
        labels: list[str] = []
        levels: list[str] = []
        for detail in getattr(data, "detail", None) or ():
            if getattr(detail, "level", None):
                levels.append(detail.level)
            for result in getattr(detail, "result", None) or ():
                if getattr(result, "label", None):
                    labels.append(result.label)
                if getattr(result, "level", None):
                    levels.append(result.level)
        return tuple(labels), tuple(levels)
