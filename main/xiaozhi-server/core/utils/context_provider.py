import httpx
from typing import Dict, Any, List
from config.logger import setup_logging

TAG = __name__

class ContextDataProvider:
    """数据上下文填充，负责从配置的API获取数据"""
    
    def __init__(self, config: Dict[str, Any], logger=None):
        self.config = config
        self.logger = logger or setup_logging()
        self.context_data = ""

    def _manager_api_credentials(self):
        """获取 manager-api 基址与服务密钥，用于解析内部相对上下文源。

        优先取本地合并配置的 manager-api 段，缺失时回退到已初始化的 ManageApiClient 单例。
        """
        mgr = (self.config or {}).get("manager-api") or {}
        base = mgr.get("url") or ""
        secret = mgr.get("secret") or ""
        if not base:
            try:
                from config.manage_api_client import ManageApiClient

                client_cfg = ManageApiClient.config or {}
                base = client_cfg.get("url") or ""
                secret = secret or ManageApiClient._secret or ""
            except Exception:
                pass
        return base, secret

    def fetch_all(self, device_id: str) -> str:
        """获取所有配置的上下文数据"""
        context_providers = self.config.get("context_providers", [])
        if not context_providers:
            return ""

        formatted_lines = []
        for provider in context_providers:
            url = provider.get("url")
            headers = provider.get("headers", {})

            if not url:
                continue

            try:
                headers = headers.copy() if isinstance(headers, dict) else {}
                # 将 device_id 添加到请求头
                headers["device-id"] = device_id

                # 内部 manager-api 相对路径（以 / 开头）：用已配置的基址补全，并自动附带服务密钥
                if url.startswith("/"):
                    base, secret = self._manager_api_credentials()
                    if not base:
                        self.logger.bind(tag=TAG).warning(
                            f"相对上下文源 {url} 无法解析：未配置 manager-api 基址，跳过"
                        )
                        continue
                    url = base.rstrip("/") + url
                    if secret and "Authorization" not in headers:
                        headers["Authorization"] = "Bearer " + secret

                # 发送请求
                response = httpx.get(url, headers=headers, timeout=3)
                
                if response.status_code == 200:
                    result = response.json()
                    if isinstance(result, dict):
                        if result.get("code") == 0:
                            data = result.get("data")
                            # 格式化数据
                            if isinstance(data, dict):
                                for k, v in data.items():
                                    formatted_lines.append(f"- **{k}：** {v}")
                            elif isinstance(data, list):
                                for item in data:
                                    formatted_lines.append(f"- {item}")
                            else:
                                formatted_lines.append(f"- {data}")
                        else:
                            self.logger.bind(tag=TAG).warning(f"API {url} 返回错误码: {result.get('msg')}")
                    else:
                        self.logger.bind(tag=TAG).warning(f"API {url} 返回的不是JSON字典")
                else:
                    self.logger.bind(tag=TAG).warning(f"API {url} 请求失败: {response.status_code}")
            except Exception as e:
                self.logger.bind(tag=TAG).error(f"获取上下文数据 {url} 失败: {e}")
        
        # 将所有格式化后的行拼接成一个字符串
        self.context_data = "\n".join(formatted_lines)
        if self.context_data:
            self.logger.bind(tag=TAG).debug(f"已注入动态上下文数据:\n{self.context_data}")
        return self.context_data
