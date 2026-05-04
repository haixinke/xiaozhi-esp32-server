import json
import time
import traceback
import requests
from typing import Optional, Dict, Any

from ..base import MemoryProviderBase, logger
from mem0 import MemoryClient
from core.utils.util import check_model_key

TAG = __name__


class MemoryProvider(MemoryProviderBase):
    """火山引擎 Mem0 记忆提供商"""

    def __init__(self, config: Dict[str, Any], summary_memory=None):
        super().__init__(config)

        # 从配置中获取参数
        self.api_key = config.get("api_key", "")
        self.host = config.get("host", "")
        self.async_mode = config.get("async_mode", True)
        self.timeout = config.get("timeout", 60)  # 默认超时60秒（火山引擎异步任务可能需要更长时间）

        # 验证必要参数
        if not self.api_key:
            msg = "火山引擎 Mem0: 缺少 api_key 配置"
            logger.bind(tag=TAG).error(msg)
            self.use_mem0 = False
            return

        if not self.host:
            msg = "火山引擎 Mem0: 缺少 host 配置"
            logger.bind(tag=TAG).error(msg)
            self.use_mem0 = False
            return

        # 检查 API Key（仅用于日志记录）
        model_key_msg = check_model_key("火山引擎 Mem0", self.api_key)
        if model_key_msg:
            logger.bind(tag=TAG).warning(model_key_msg)

        try:
            # 初始化火山引擎 Mem0 客户端
            self.client = MemoryClient(host=self.host, api_key=self.api_key)
            self.use_mem0 = True
            logger.bind(tag=TAG).info(f"成功连接到火山引擎 Mem0 服务 (host={self.host}, async_mode={self.async_mode})")
        except Exception as e:
            logger.bind(tag=TAG).error(f"连接到火山引擎 Mem0 服务时发生错误: {str(e)}")
            logger.bind(tag=TAG).debug(f"详细错误: {traceback.format_exc()}")
            self.use_mem0 = False

    def _check_job_status(self, event_id: str) -> bool:
        """
        轮询任务状态直到完成或超时

        Args:
            event_id: 任务事件ID

        Returns:
            bool: True 表示任务成功完成，False 表示超时或失败
        """
        start_time = time.time()
        job_status = "PENDING"

        logger.bind(tag=TAG).info(f"开始轮询任务状态，event_id: {event_id}, timeout: {self.timeout}s")

        poll_count = 0
        while job_status != "SUCCEEDED":
            poll_count += 1
            # 检查超时
            elapsed = time.time() - start_time
            if elapsed > self.timeout:
                logger.bind(tag=TAG).warning(f"轮询任务超时 (event_id={event_id}, elapsed={elapsed:.2f}s, polled={poll_count}次)")
                return False

            try:
                # 发送请求查询任务状态
                response = requests.get(
                    f"{self.host}/v1/job/{event_id}/",
                    headers={"Authorization": f"Token {self.api_key}"},
                    timeout=5
                )

                if response.status_code == 200:
                    job_info = response.json()
                    job_status = job_info.get("status", "UNKNOWN")

                    if job_status == "SUCCEEDED":
                        logger.bind(tag=TAG).info(f"任务成功完成 (event_id={event_id}, elapsed={elapsed:.2f}s, polled={poll_count}次)")
                        logger.bind(tag=TAG).debug(f"任务完整信息: {json.dumps(job_info, ensure_ascii=False)}")
                        return True
                    elif job_status in ["FAILED", "CANCELLED"]:
                        logger.bind(tag=TAG).error(f"任务失败 (event_id={event_id}, status={job_status})")
                        logger.bind(tag=TAG).error(f"失败详情: {json.dumps(job_info, ensure_ascii=False)}")
                        return False
                    else:
                        # 每5次轮询输出一次进度日志
                        if poll_count % 5 == 0:
                            logger.bind(tag=TAG).info(f"任务进行中 (event_id={event_id}, status={job_status}, elapsed={elapsed:.2f}s, polled={poll_count}次)")
                else:
                    logger.bind(tag=TAG).warning(f"查询任务状态失败 (status_code={response.status_code}, response={response.text[:100]})")

            except Exception as e:
                logger.bind(tag=TAG).error(f"轮询任务状态时发生错误: {str(e)}")
                logger.bind(tag=TAG).debug(f"详细错误: {traceback.format_exc()}")

            # 每秒轮询一次
            time.sleep(1)

        return False

    async def save_memory(self, msgs, session_id=None):
        """
        保存记忆到火山引擎 Mem0

        Args:
            msgs: 消息列表
            session_id: 会话ID（可选）

        Returns:
            None
        """
        try:
            if not self.use_mem0 or len(msgs) < 2:
                return None

            # 格式化消息内容
            messages = []
            for message in msgs:
                # 跳过 system 和 tool 消息
                if message.role in ["system", "tool"]:
                    continue

                content = message.content
                if content is None:
                    continue

                # 提取 JSON 格式中的 content 字段（如果有）
                try:
                    if content.strip().startswith("{") and content.strip().endswith("}"):
                        data = json.loads(content)
                        if "content" in data:
                            content = data["content"]
                except (json.JSONDecodeError, KeyError, TypeError):
                    # 解析失败，使用原始内容
                    pass

                messages.append({"role": message.role, "content": content})

            if not messages:
                return None

            # 调用火山引擎 Mem0 API 添加记忆
            result = self.client.add(
                messages,
                user_id=self.role_id,
                async_mode=self.async_mode
            )

            logger.bind(tag=TAG).info(f"保存记忆结果: {json.dumps(result, ensure_ascii=False)}")

            # 如果是异步模式，需要轮询任务状态
            if self.async_mode and result:
                try:
                    results = result.get("results", [])
                    if results and len(results) > 0:
                        event_id = results[0].get("event_id")
                        if event_id:
                            logger.bind(tag=TAG).info(f"开始轮询异步任务 (event_id={event_id}, timeout={self.timeout}s)")
                            # 轮询任务状态
                            success = self._check_job_status(event_id)
                            if not success:
                                logger.bind(tag=TAG).warning(f"异步保存记忆任务未成功完成 (event_id={event_id})")
                            else:
                                logger.bind(tag=TAG).info(f"异步保存记忆任务成功完成 (event_id={event_id})")
                    else:
                        logger.bind(tag=TAG).warning(f"异步保存记忆返回结果为空: {result}")
                except Exception as e:
                    logger.bind(tag=TAG).error(f"处理异步任务结果时发生错误: {str(e)}")
                    logger.bind(tag=TAG).debug(f"详细错误: {traceback.format_exc()}")

        except Exception as e:
            logger.bind(tag=TAG).error(f"保存记忆失败: {str(e)}")
            logger.bind(tag=TAG).debug(f"详细错误: {traceback.format_exc()}")

        return None

    async def query_memory(self, query: str) -> str:
        """
        从火山引擎 Mem0 查询记忆

        Args:
            query: 查询文本

        Returns:
            str: 格式化的记忆文本，按时间倒序排列
        """
        if not self.use_mem0:
            return ""

        try:
            if not getattr(self, "role_id", None):
                return ""

            # 解析查询文本（提取 JSON 中的 content）
            search_query = query
            try:
                if query.strip().startswith("{") and query.strip().endswith("}"):
                    data = json.loads(query)
                    if "content" in data:
                        search_query = data["content"]
            except (json.JSONDecodeError, KeyError):
                pass

            # 调用火山引擎 Mem0 API 搜索记忆
            results = self.client.search(search_query, user_id=self.role_id)

            if not results or "results" not in results:
                return ""

            # 格式化记忆条目，包含时间戳（精确到分钟）
            memories = []
            for entry in results["results"]:
                timestamp = entry.get("updated_at", "")
                if timestamp:
                    try:
                        # 移除毫秒部分，格式化时间戳
                        dt = timestamp.split(".")[0]
                        formatted_time = dt.replace("T", " ")
                    except Exception:
                        formatted_time = timestamp

                memory = entry.get("memory", "")
                if timestamp and memory:
                    # 存储元组用于排序
                    memories.append((timestamp, f"[{formatted_time}] {memory}"))

            # 按时间戳倒序排列（最新的在前）
            memories.sort(key=lambda x: x[0], reverse=True)

            # 提取格式化后的字符串
            memories_str = "\n".join(f"- {memory[1]}" for memory in memories)
            logger.bind(tag=TAG).debug(f"查询结果: {memories_str}")
            return memories_str

        except Exception as e:
            logger.bind(tag=TAG).error(f"查询记忆失败: {str(e)}")
            logger.bind(tag=TAG).debug(f"详细错误: {traceback.format_exc()}")
            return ""
