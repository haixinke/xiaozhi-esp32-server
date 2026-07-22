import json
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler
from core.handle.textMessageHandlerRegistry import TextMessageHandlerRegistry

TAG = __name__


class TextMessageProcessor:
    """消息处理器主类"""

    def __init__(self, registry: TextMessageHandlerRegistry):
        self.registry = registry

    async def process_message(self, conn: "ConnectionHandler", message: str) -> None:
        """处理消息的主入口"""
        try:
            # 解析JSON消息
            msg_json = json.loads(message)

            # 处理JSON消息
            if isinstance(msg_json, dict):
                message_type = msg_json.get("type")

                # 记录日志
                if message_type in {"listen", "abort"}:
                    safe_fields = {
                        key: (
                            msg_json[key]
                            if isinstance(msg_json[key], str) and len(msg_json[key]) <= 32
                            else "<invalid>"
                        )
                        for key in ("type", "mode", "state")
                        if key in msg_json
                    }
                    conn.logger.bind(tag=TAG).info(
                        f"收到{message_type}消息：{safe_fields}"
                    )
                else:
                    conn.logger.bind(tag=TAG).info(f"收到{message_type}消息：{message}")

                # 获取并执行处理器
                handler = self.registry.get_handler(message_type)
                if handler:
                    await handler.handle(conn, msg_json)
                else:
                    conn.logger.bind(tag=TAG).error(f"收到未知类型消息：{message}")
            # 处理纯数字消息
            elif isinstance(msg_json, int):
                conn.logger.bind(tag=TAG).info(f"收到数字消息：{message}")
                await conn.websocket.send(message)

        except json.JSONDecodeError:
            # 非JSON消息直接转发
            conn.logger.bind(tag=TAG).error(f"解析到错误的消息：{message}")
            await conn.websocket.send(message)
