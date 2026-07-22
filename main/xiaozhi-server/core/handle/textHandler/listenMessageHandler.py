import time
import uuid
import asyncio
from typing import Dict, Any, TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

from core.utils.dialogue import Message
from core.providers.asr.dto.dto import InterfaceType
from core.handle.receiveAudioHandle import startToChat
from core.handle.reportHandle import enqueue_asr_report
from core.handle.sendAudioHandle import send_stt_message, send_tts_message
from core.handle.textMessageHandler import TextMessageHandler
from core.handle.textMessageType import TextMessageType
from core.utils.util import remove_punctuation_and_length
from core.providers.tts.dto.dto import ContentType, TTSMessageDTO, SentenceType
from core.voice_turn import normalize_turn_id
from core.handle.voiceTurnHandle import (
    begin_voice_turn,
    disable_voice_turn_v2,
    finish_voice_turn,
)


TAG = __name__
VALID_LISTEN_MODES = {"auto", "manual", "realtime"}
VALID_LISTEN_STATES = {"start", "stop", "detect"}

class ListenTextMessageHandler(TextMessageHandler):
    """Listen消息处理器"""

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.LISTEN

    async def handle(self, conn: "ConnectionHandler", msg_json: Dict[str, Any]) -> None:
        mode = msg_json.get("mode", conn.client_listen_mode)
        state = msg_json.get("state")
        turn_id = normalize_turn_id(msg_json.get("turn_id"))
        if mode not in VALID_LISTEN_MODES or state not in VALID_LISTEN_STATES:
            conn.logger.bind(tag=TAG).warning("拒绝非法拾音控制消息")
            return
        is_v2_start = (
            mode == "manual"
            and state == "start"
            and "turn_id" in msg_json
        )

        # Drain the old v2 FIFO before changing the shared listen mode. Otherwise
        # its queued audio could be consumed as auto/legacy input.
        if (
            conn.voice_turn_v2_enabled
            and not is_v2_start
            and (state == "start" or mode != "manual")
        ):
            await disable_voice_turn_v2(conn)

        if "mode" in msg_json:
            conn.client_listen_mode = mode
            conn.logger.bind(tag=TAG).debug(
                f"客户端拾音模式：{conn.client_listen_mode}"
            )

        if is_v2_start:
            if turn_id is None:
                conn.logger.bind(tag=TAG).warning("拒绝非法语音轮次标识")
                return
            await begin_voice_turn(conn, turn_id)
            return

        if conn.voice_turn_v2_enabled:
            if mode == "manual" and state == "stop":
                if turn_id is not None:
                    await finish_voice_turn(conn, turn_id)
                elif conn.voice_turn_id:
                    from core.handle.voiceTurnHandle import send_voice_turn_ack
                    await send_voice_turn_ack(conn, conn.voice_turn_id, "error")
                return
        if state == "start":
            # 设备从播放模式切回录音模式,清除所有音频状态和缓冲区
            conn.reset_audio_states()
        elif state == "stop":
            conn.client_voice_stop = True
            if conn.asr.interface_type == InterfaceType.STREAM:
                # 流式模式下，发送结束请求
                asyncio.create_task(conn.asr._send_stop_request())
            else:
                # 非流式模式：直接触发ASR识别
                if len(conn.asr_audio) > 0:
                    asr_audio_task = conn.asr_audio.copy()
                    conn.reset_audio_states()

                    if len(asr_audio_task) > 0:
                        await conn.asr.handle_voice_stop(conn, asr_audio_task)
        elif state == "detect":
            conn.client_have_voice = False
            conn.reset_audio_states()
            if "text" in msg_json:
                conn.last_activity_time = time.time() * 1000
                original_text = msg_json["text"]  # 保留原始文本
                filtered_len, filtered_text = remove_punctuation_and_length(
                    original_text
                )

                # 检查是否是设备呼叫指令 [device_call]
                if original_text.startswith("[device_call]"):
                    # 提取 tag 后的文本
                    call_text = original_text[len("[device_call]"):].strip()
                    conn.logger.bind(tag=TAG).info(f"收到设备呼叫指令: {call_text}")

                    # 标记为来电接听模式
                    conn.incoming_call = True

                    # 准备开始新会话
                    conn.sentence_id = uuid.uuid4().hex

                    await send_stt_message(conn, call_text)
                    conn.tts.store_tts_text(conn.sentence_id, call_text)
                    conn.tts.tts_text_queue.put(TTSMessageDTO(sentence_id=conn.sentence_id, sentence_type=SentenceType.FIRST, content_type=ContentType.ACTION))
                    conn.tts.tts_one_sentence(conn, ContentType.TEXT, content_detail=call_text)
                    conn.tts.tts_text_queue.put(TTSMessageDTO(sentence_id=conn.sentence_id, sentence_type=SentenceType.LAST, content_type=ContentType.ACTION))

                    # 添加到对话历史，让模型理解上下文
                    conn.dialogue.put(Message(role="assistant", content=call_text))
                    return

                # 识别是否是唤醒词
                is_wakeup_words = filtered_text in conn.config.get("wakeup_words")
                # 是否开启唤醒词回复
                enable_greeting = conn.config.get("enable_greeting", True)

                if is_wakeup_words and not enable_greeting:
                    # 如果是唤醒词，且关闭了唤醒词回复，就不用回答
                    await send_stt_message(conn, original_text)
                    await send_tts_message(conn, "stop", None)
                    conn.client_is_speaking = False
                elif is_wakeup_words:
                    conn.just_woken_up = True
                    # 上报纯文字数据（复用ASR上报功能，但不提供音频数据）
                    enqueue_asr_report(conn, "嘿，你好呀", [])
                    await startToChat(conn, "嘿，你好呀")
                else:
                    conn.just_woken_up = True
                    # 上报纯文字数据（复用ASR上报功能，但不提供音频数据）
                    enqueue_asr_report(conn, original_text, [])
                    # 否则需要LLM对文字内容进行答复
                    await startToChat(conn, original_text)
