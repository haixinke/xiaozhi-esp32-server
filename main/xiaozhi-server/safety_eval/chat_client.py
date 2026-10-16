"""
无声的小程序客户端：一次问答 = 一条 WebSocket 连接。

复刻 main/egg-miniprogram/miniprogram/utils/websocket.js 的交互序列，
但不播放音频：二进制帧直接丢弃，回复文字取自 tts/sentence_start。

为什么文字要从 TTS 消息里取：服务端的回复文本是在 TTS 合成成功后，
由 core/providers/tts/base.py 入队、core/handle/sendAudioHandle.py 下发的，
没有独立的纯文本回复通道。
"""

import asyncio
import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional
from urllib.parse import quote

import websockets

# 与小程序保持一致的握手参数
HELLO_PAYLOAD: Dict[str, Any] = {
    "type": "hello",
    "version": 1,
    "transport": "websocket",
    "audio_params": {
        "format": "opus",
        "sample_rate": 24000,
        "channels": 1,
        "frame_duration": 60,
    },
}

DEFAULT_CLIENT_ID = "safety-eval"
HANDSHAKE_TIMEOUT_S = 15.0
DEFAULT_TIMEOUT_S = 60.0


@dataclass
class ChatResult:
    qid: str
    prompt: str
    reply: str = ""
    sentences: List[str] = field(default_factory=list)
    stt: Optional[str] = None
    session_id: Optional[str] = None
    elapsed_ms: int = 0
    error: Optional[str] = None
    started_at: str = ""

    def to_dict(self) -> Dict[str, Any]:
        return {
            "qid": self.qid,
            "prompt": self.prompt,
            "reply": self.reply,
            "sentences": self.sentences,
            "stt": self.stt,
            "session_id": self.session_id,
            "elapsed_ms": self.elapsed_ms,
            "error": self.error,
            "started_at": self.started_at,
        }


def generate_token(secret: str, client_id: str, device_id: str, ts: Optional[int] = None) -> str:
    """
    按 core/auth.py::AuthManager 的算法签发 token。
    签名内容为 "client_id|device_id|时间戳"，token 形如 "<签名>.<时间戳>"。
    """
    timestamp = int(time.time()) if ts is None else ts
    content = f"{client_id}|{device_id}|{timestamp}"
    signature = hmac.new(
        secret.encode("utf-8"), content.encode("utf-8"), hashlib.sha256
    ).digest()
    encoded = base64.urlsafe_b64encode(signature).decode("utf-8").rstrip("=")
    return f"{encoded}.{timestamp}"


def build_url(ws_url: str, device_id: str, client_id: str, token: Optional[str]) -> str:
    """小程序无法自定义 WebSocket Header，鉴权信息走 URL query，这里照抄同一形态。"""
    separator = "&" if "?" in ws_url else "?"
    url = (
        f"{ws_url}{separator}device-id={quote(device_id)}"
        f"&client-id={quote(client_id)}"
    )
    if token:
        url += f"&authorization={quote('Bearer ' + token)}"
    return url


class ChatClient:
    """一个连接问一道题。无状态，可并发复用同一实例。"""

    def __init__(
        self,
        ws_url: str,
        device_id: str,
        secret: Optional[str] = None,
        client_id: str = DEFAULT_CLIENT_ID,
        timeout: float = DEFAULT_TIMEOUT_S,
    ):
        self.ws_url = ws_url
        self.device_id = device_id
        self.secret = secret
        self.client_id = client_id
        self.timeout = timeout

    async def ask(self, qid: str, prompt: str) -> ChatResult:
        result = ChatResult(
            qid=qid, prompt=prompt, started_at=datetime.now().astimezone().isoformat()
        )
        started = time.monotonic()
        token = generate_token(self.secret, self.client_id, self.device_id) if self.secret else None
        url = build_url(self.ws_url, self.device_id, self.client_id, token)

        try:
            async with websockets.connect(url, open_timeout=HANDSHAKE_TIMEOUT_S) as ws:
                await ws.send(json.dumps(HELLO_PAYLOAD))
                await self._await_hello(ws, result)
                await ws.send(
                    json.dumps(
                        {
                            "type": "listen",
                            "mode": "manual",
                            "state": "detect",
                            "text": prompt,
                        }
                    )
                )
                await self._collect_reply(ws, result)
        except asyncio.TimeoutError:
            result.error = result.error or "timeout"
        except websockets.exceptions.InvalidStatus as exc:
            # 握手被拒，多数是 token 或 device-id 不对
            result.error = f"auth_or_handshake: {exc}"
        except Exception as exc:  # 单题失败不应中断整轮测试
            result.error = f"{type(exc).__name__}: {exc}"

        result.elapsed_ms = int((time.monotonic() - started) * 1000)
        result.reply = "".join(result.sentences).strip()
        return result

    async def _await_hello(self, ws, result: ChatResult) -> None:
        deadline = time.monotonic() + HANDSHAKE_TIMEOUT_S
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise asyncio.TimeoutError("等待服务端 hello 超时")
            raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
            message = _parse_json(raw)
            if message and message.get("type") == "hello":
                result.session_id = message.get("session_id") or message.get("sessionId")
                return

    async def _collect_reply(self, ws, result: ChatResult) -> None:
        """累积 tts/sentence_start 文本，直到 tts/stop 或 goodbye。超时保留已收到的部分。"""
        deadline = time.monotonic() + self.timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                result.error = "timeout"
                return
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
            except asyncio.TimeoutError:
                result.error = "timeout"
                return

            if isinstance(raw, (bytes, bytearray)):
                # TTS 音频帧，文字模式下丢弃
                continue

            message = _parse_json(raw)
            if not message:
                continue

            kind = message.get("type")
            if kind == "stt":
                result.stt = message.get("text") or result.stt
            elif kind == "tts":
                state = message.get("state")
                if state == "sentence_start":
                    text = message.get("text") or ""
                    if text:
                        result.sentences.append(text)
                elif state == "stop":
                    return
            elif kind == "goodbye":
                return


def _parse_json(raw: Any) -> Optional[Dict[str, Any]]:
    if isinstance(raw, (bytes, bytearray)):
        return None
    try:
        message = json.loads(raw)
    except (TypeError, ValueError):
        return None
    return message if isinstance(message, dict) else None
