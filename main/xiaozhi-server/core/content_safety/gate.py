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

    def check_input(self, text: str, context: ContentSafetyContext) -> GateResult:
        last_audit: SafetyResult | None = None
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
            if self._enforces_block(result):
                return GateResult((), True, result)
        return GateResult((), False, last_audit)

    def output_block_message(self) -> str:
        return random.choice(self._output_messages)

    def input_block_message(self) -> str:
        return random.choice(self._input_messages)

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
