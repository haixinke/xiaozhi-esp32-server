from dataclasses import dataclass
from enum import Enum
from typing import Optional
import re


_TURN_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


class VoiceInputEventType(Enum):
    START = "start"
    AUDIO = "audio"
    END = "end"
    ABORT = "abort"


@dataclass(frozen=True)
class VoiceInputEvent:
    event_type: VoiceInputEventType
    turn_id: str
    audio: Optional[bytes] = None
    terminal_state: Optional[str] = None


@dataclass(frozen=True)
class TurnContext:
    turn_id: str
    state: str
    frames: tuple[bytes, ...]


@dataclass(frozen=True)
class VoiceTurnOutcome:
    turn_id: str
    state: str
    reason: str = ""


def normalize_turn_id(value) -> Optional[str]:
    if not isinstance(value, str) or not _TURN_ID_PATTERN.fullmatch(value):
        return None
    return value
