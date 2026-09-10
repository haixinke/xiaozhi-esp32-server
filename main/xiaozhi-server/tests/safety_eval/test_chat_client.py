"""ChatClient 的集成测试：起一个假的 xiaozhi-server，验证消息序列与容错。

不用 pytest-asyncio（项目未声明该依赖），用 asyncio.run 把异步场景包成同步用例。
"""

import asyncio
import json

import websockets

from safety_eval.chat_client import ChatClient, build_url, generate_token


class FakeServer:
    """
    最小化模拟服务端行为：握手回 hello，收到 listen/detect 后按脚本下发消息。

    script 里的元素：dict 走 JSON，bytes 当音频帧发，float 表示 sleep 秒数。
    """

    def __init__(self, script, hello_delay=0.0):
        self.script = script
        self.hello_delay = hello_delay
        self.received = []
        self.request_paths = []
        self._server = None

    @property
    def url(self):
        host, port = self._server.sockets[0].getsockname()[:2]
        return f"ws://127.0.0.1:{port}/xiaozhi/v1/"

    async def __aenter__(self):
        self._server = await websockets.serve(self._handle, "127.0.0.1", 0)
        return self

    async def __aexit__(self, *exc_info):
        self._server.close()
        await self._server.wait_closed()

    async def _handle(self, ws):
        self.request_paths.append(ws.request.path)
        async for raw in ws:
            message = json.loads(raw)
            self.received.append(message)
            if message.get("type") == "hello":
                if self.hello_delay:
                    await asyncio.sleep(self.hello_delay)
                await ws.send(json.dumps({"type": "hello", "session_id": "fake-session"}))
            elif message.get("type") == "listen":
                for item in self.script:
                    if isinstance(item, float):
                        await asyncio.sleep(item)
                    elif isinstance(item, bytes):
                        await ws.send(item)
                    else:
                        await ws.send(json.dumps(item))


def _sentence(text):
    return {"type": "tts", "state": "sentence_start", "text": text}


STOP = {"type": "tts", "state": "stop"}


async def _ask(script, *, timeout=5.0, device_id="dev-1", client_id="safety-eval"):
    """起假服务端问一题，返回 (result, server)。"""
    async with FakeServer(script) as server:
        client = ChatClient(
            server.url,
            device_id=device_id,
            secret="s3cret",
            client_id=client_id,
            timeout=timeout,
        )
        result = await client.ask("SC-0001", "问题一")
    return result, server


def test_collects_all_sentences_until_stop():
    script = [
        {"type": "stt", "text": "题目原文"},
        {"type": "tts", "state": "start"},
        _sentence("第一句。"),
        b"\x00\x01fake-opus",
        _sentence("第二句。"),
        STOP,
    ]

    result, _ = asyncio.run(_ask(script))

    assert result.error is None
    assert result.reply == "第一句。第二句。"
    assert result.sentences == ["第一句。", "第二句。"]
    assert result.stt == "题目原文"
    assert result.session_id == "fake-session"
    assert result.elapsed_ms >= 0


def test_sends_hello_then_listen_detect_like_the_miniprogram():
    _, server = asyncio.run(_ask([_sentence("好的。"), STOP]))

    hello, listen = server.received
    assert hello["type"] == "hello"
    assert hello["audio_params"]["format"] == "opus"
    assert listen == {
        "type": "listen",
        "mode": "manual",
        "state": "detect",
        "text": "问题一",
    }


def test_auth_and_device_go_through_url_query():
    _, server = asyncio.run(_ask([STOP], device_id="dev-42"))

    path = server.request_paths[0]
    assert "device-id=dev-42" in path
    assert "client-id=safety-eval" in path
    assert "authorization=Bearer" in path.replace("%20", " ")


def test_timeout_keeps_partial_reply():
    # 只发一句就不再发 stop，客户端应超时但保留已收到的内容
    result, _ = asyncio.run(_ask([_sentence("说了一半")], timeout=0.5))

    assert result.error == "timeout"
    assert result.reply == "说了一半"


def test_goodbye_ends_the_turn():
    result, _ = asyncio.run(_ask([_sentence("再见。"), {"type": "goodbye"}]))

    assert result.error is None
    assert result.reply == "再见。"


def test_connection_failure_is_reported_not_raised():
    client = ChatClient(
        "ws://127.0.0.1:1/xiaozhi/v1/", device_id="dev-1", secret="s3cret", timeout=1
    )

    result = asyncio.run(client.ask("SC-0001", "问题一"))

    assert result.error
    assert result.reply == ""


def test_generate_token_matches_auth_manager_algorithm():
    # 与 core/auth.py::AuthManager.verify_token 对齐
    from core.auth import AuthManager

    token = generate_token("s3cret", "client-1", "device-1", ts=1_700_000_000)

    assert AuthManager("s3cret").verify_token(
        token, client_id="client-1", username="device-1"
    ) is False  # 时间戳过期，签名算法一致但已超出有效期
    fresh = generate_token("s3cret", "client-1", "device-1")
    assert AuthManager("s3cret").verify_token(
        fresh, client_id="client-1", username="device-1"
    )
    assert not AuthManager("other").verify_token(
        fresh, client_id="client-1", username="device-1"
    )


def test_build_url_appends_with_correct_separator():
    assert build_url("ws://h/x/", "d", "c", None) == "ws://h/x/?device-id=d&client-id=c"
    assert build_url("ws://h/x/?a=1", "d", "c", None).startswith("ws://h/x/?a=1&device-id=d")
