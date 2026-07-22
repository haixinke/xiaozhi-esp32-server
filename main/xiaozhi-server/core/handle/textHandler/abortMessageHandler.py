from typing import Dict, Any

from core.handle.abortHandle import handleAbortMessage
from core.handle.textMessageHandler import TextMessageHandler
from core.handle.textMessageType import TextMessageType
from core.voice_turn import normalize_turn_id
from core.handle.voiceTurnHandle import abort_voice_turn, disable_voice_turn_v2


class AbortTextMessageHandler(TextMessageHandler):
    """Abort消息处理器"""

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.ABORT

    async def handle(self, conn, msg_json: Dict[str, Any]) -> None:
        turn_id = normalize_turn_id(msg_json.get("turn_id"))
        if "turn_id" in msg_json and turn_id is None:
            return
        if conn.voice_turn_v2_enabled and turn_id is not None:
            await abort_voice_turn(conn, turn_id)
            return
        if conn.voice_turn_v2_enabled:
            await disable_voice_turn_v2(conn)
        await handleAbortMessage(conn)
