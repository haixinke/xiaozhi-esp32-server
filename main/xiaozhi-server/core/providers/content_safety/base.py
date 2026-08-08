from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Enum


class SafetyDirection(str, Enum):
    INPUT = "input"
    OUTPUT = "output"


class SafetyDecision(str, Enum):
    ALLOW = "allow"
    BLOCK = "block"
    ERROR = "error"


@dataclass(frozen=True)
class SafetyResult:
    decision: SafetyDecision
    suggestion: str | None = None
    request_id: str | None = None
    labels: tuple[str, ...] = ()
    levels: tuple[str, ...] = ()
    error_kind: str | None = None


class ContentSafetyProviderBase(ABC):
    @abstractmethod
    def check(
        self,
        direction: SafetyDirection,
        content: str,
        chat_id: str,
        session_id: str | None = None,
        done: bool = False,
    ) -> SafetyResult:
        """Evaluate one user input or generated output chunk."""
