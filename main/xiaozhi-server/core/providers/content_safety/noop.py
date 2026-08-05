from .base import (
    ContentSafetyProviderBase,
    SafetyDecision,
    SafetyDirection,
    SafetyResult,
)


class NoopContentSafetyProvider(ContentSafetyProviderBase):
    def check(
        self,
        direction: SafetyDirection,
        content: str,
        chat_id: str,
        session_id: str | None = None,
        done: bool = False,
    ) -> SafetyResult:
        return SafetyResult(decision=SafetyDecision.ALLOW)
