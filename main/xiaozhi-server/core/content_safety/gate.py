import logging
import random
import time
from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
from typing import Any

from core.providers.content_safety import (
    ContentSafetyProviderBase,
    SafetyDecision,
    SafetyDirection,
    SafetyResult,
)


_FLUSH_PUNCTUATION = "。！？!?；;\n"
_SAFE_SUGGESTIONS = frozenset({"pass", "block", "watch", "mask"})
logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ContentSafetyContext:
    chat_id: str


@dataclass(frozen=True)
class GateResult:
    released_parts: tuple[str, ...]
    blocked: bool
    audit: SafetyResult | None
    # crisis=True 表示内容命中危机标签（如轻生），即使被判定 block 也放行给 LLM 共情回复
    crisis: bool = False

    @property
    def allowed(self) -> bool:
        return not self.blocked


class ContentSafetyGate:
    def __init__(
        self,
        provider: ContentSafetyProviderBase,
        config: Mapping[str, Any],
        audit_log: Callable[[str], None] | None = None,
    ) -> None:
        safety_config = config.get("content_safety", config)
        self._provider = provider
        self._max_request_chars = safety_config.get("max_request_chars", 2000)
        self._output_chunk_chars = safety_config.get("output_chunk_chars", 2000)
        self._mode = safety_config.get("mode", "enforce")
        self._audit_log = audit_log or logger.info
        self._validate_config()
        fallback = config.get(
            "system_error_response", safety_config.get("system_error_response", "")
        )
        self._input_messages = self._trusted_messages(
            safety_config.get("input_block_message"), fallback
        )
        self._output_messages = self._trusted_messages(
            safety_config.get("output_block_message"), fallback
        )
        # 危机标签（如轻生 inappropriate_suicide）：命中后输入不拦截，放行给 LLM
        self._crisis_labels = self._normalize_labels(safety_config.get("crisis_labels"))
        # 危机上下文下 LLM 输出被出口护栏拦截时的专用兜底话术
        self._crisis_fallback_messages = self._trusted_messages(
            safety_config.get("crisis_output_fallback_message"), fallback
        )

    def check_input(self, text: str, context: ContentSafetyContext) -> GateResult:
        last_audit: SafetyResult | None = None
        crisis = False
        for offset in range(0, len(text), self._max_request_chars):
            chunk = text[offset : offset + self._max_request_chars]
            if not chunk:
                continue
            started_at = time.perf_counter()
            result = self._provider.check(SafetyDirection.INPUT, chunk, context.chat_id)
            self._record_audit(
                SafetyDirection.INPUT,
                result,
                len(chunk),
                time.perf_counter() - started_at,
            )
            last_audit = result
            # 危机内容不拦截：标记 crisis 后继续检测剩余分片，非危机 block 仍然拦截
            crisis = crisis or self._is_crisis(result)
            if self._enforces_block(result) and not self._is_crisis(result):
                return GateResult((), True, result)
        return GateResult((), False, last_audit, crisis)

    def is_crisis(self, result: SafetyResult | None) -> bool:
        return result is not None and self._is_crisis(result)

    def output_block_message(self) -> str:
        return random.choice(self._output_messages)

    def input_block_message(self) -> str:
        return random.choice(self._input_messages)

    def crisis_output_fallback(self) -> str:
        return random.choice(self._crisis_fallback_messages)

    def _is_crisis(self, result: SafetyResult) -> bool:
        if not self._crisis_labels:
            return False
        return any(label in self._crisis_labels for label in result.labels)

    @staticmethod
    def _normalize_labels(value: object) -> frozenset[str]:
        # 兼容数组(["a","b"])与分号分隔字符串("a;b")两种配置形态
        if isinstance(value, str):
            items: Sequence = value.split(";")
        elif isinstance(value, Sequence):
            items = value
        else:
            return frozenset()
        return frozenset(
            item.strip() for item in items if isinstance(item, str) and item.strip()
        )

    def _enforces_block(self, result: SafetyResult) -> bool:
        return self._mode == "enforce" and result.decision in {
            SafetyDecision.BLOCK,
            SafetyDecision.ERROR,
        }

    def _record_audit(
        self,
        direction: SafetyDirection,
        result: SafetyResult,
        character_count: int,
        elapsed_seconds: float,
    ) -> None:
        fields = [
            f"direction={direction.value}",
            f"mode={self._mode}",
            f"decision={result.decision.value}",
            f"chars={character_count}",
            f"elapsed_ms={round(elapsed_seconds * 1000)}",
            f"crisis={'true' if self._is_crisis(result) else 'false'}",
        ]
        fields.extend(
            (
                f"suggestion={self._suggestion_summary(result.suggestion)}",
                f"categories={self._count_summary(result.labels)}",
                f"levels={self._count_summary(result.levels)}",
                f"request_id={self._request_id_summary(result.request_id)}",
            )
        )
        self._audit_log("content_safety_audit " + " ".join(fields))

    @staticmethod
    def _suggestion_summary(value: object) -> str:
        if value is None:
            return "none"
        if isinstance(value, str) and value in _SAFE_SUGGESTIONS:
            return value
        return "redacted"

    @staticmethod
    def _count_summary(value: object) -> str:
        if isinstance(value, tuple):
            return f"count:{len(value)}"
        return "redacted"

    @staticmethod
    def _request_id_summary(value: object) -> str:
        if value is None:
            return "none"
        return "redacted"

    def _validate_config(self) -> None:
        if not (
            isinstance(self._output_chunk_chars, int)
            and isinstance(self._max_request_chars, int)
            and not isinstance(self._output_chunk_chars, bool)
            and not isinstance(self._max_request_chars, bool)
            and 1 <= self._output_chunk_chars <= self._max_request_chars <= 2000
        ):
            raise ValueError(
                "content_safety requires 1 <= output_chunk_chars <= max_request_chars <= 2000"
            )
        if self._mode not in {"observe", "enforce"}:
            raise ValueError("content_safety.mode must be 'observe' or 'enforce'")

    @staticmethod
    def _trusted_messages(messages: object, fallback: object) -> tuple[str, ...]:
        if not isinstance(messages, Sequence) or isinstance(messages, str):
            messages = ()
        trusted = tuple(
            message
            for message in messages
            if isinstance(message, str) and message.strip()
        )
        if trusted:
            return trusted
        if not isinstance(fallback, str) or not fallback.strip():
            raise ValueError("system_error_response must be a non-blank string")
        return (fallback,)


class OutputSafetyGate:
    def __init__(
        self,
        provider: ContentSafetyProviderBase,
        config: Mapping[str, Any],
        context: ContentSafetyContext,
        session_id: str,
        audit_log: Callable[[str], None] | None = None,
    ) -> None:
        self._input_gate = ContentSafetyGate(provider, config, audit_log)
        self._provider = provider
        self._context = context
        self._session_id = session_id
        self._buffer = ""
        self._blocked = False

    def feed(self, text: str) -> GateResult:
        if self._blocked:
            return GateResult((), True, None)
        self._buffer += text
        released_parts: list[str] = []
        last_audit: SafetyResult | None = None
        if (
            self._buffer.endswith(tuple(_FLUSH_PUNCTUATION))
            and len(self._buffer) <= self._input_gate._max_request_chars
        ):
            result = self._flush(done=False)
            return result
        while len(self._buffer) >= self._input_gate._output_chunk_chars:
            result = self._flush(
                done=False, chunk_size=self._input_gate._output_chunk_chars
            )
            last_audit = result.audit
            if result.blocked:
                return GateResult(tuple(released_parts), True, result.audit)
            released_parts.extend(result.released_parts)
        return GateResult(tuple(released_parts), False, last_audit)

    def finish(self) -> GateResult:
        return self._flush(done=True)

    def output_block_message(self) -> str:
        return self._input_gate.output_block_message()

    def _flush(self, *, done: bool, chunk_size: int | None = None) -> GateResult:
        if not self._buffer or self._blocked:
            return GateResult((), self._blocked, None)
        if chunk_size is None:
            chunk, self._buffer = self._buffer, ""
        else:
            chunk, self._buffer = self._buffer[:chunk_size], self._buffer[chunk_size:]
        started_at = time.perf_counter()
        result = self._provider.check(
            SafetyDirection.OUTPUT,
            chunk,
            self._context.chat_id,
            self._session_id,
            done=done,
        )
        self._input_gate._record_audit(
            SafetyDirection.OUTPUT,
            result,
            len(chunk),
            time.perf_counter() - started_at,
        )
        if self._input_gate._enforces_block(result):
            self._blocked = True
            self._buffer = ""
            return GateResult((), True, result)
        return GateResult((chunk,), False, result)
