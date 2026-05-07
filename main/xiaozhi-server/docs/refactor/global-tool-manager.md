# 全局工具管理器重构方案

## 📋 文档信息

- **创建日期**: 2026-05-03
- **状态**: 待实施
- **优先级**: 中（性能优化）
- **影响范围**: `core/providers/tools/`, `core/connection.py`, `app.py`

---

## 🔍 当前问题分析

### 问题描述

**每个设备连接都创建独立的 ToolManager 实例**，导致服务端 MCP 服务被重复初始化多次。

### 问题代码

```python
# core/connection.py:899
class ConnectionHandler:
    def __init__(...):
        # ❌ 每个连接都创建一个新的 UnifiedToolHandler
        self.func_handler = UnifiedToolHandler(self)
```

### 实际影响

假设有 **10 个 ESP32 设备同时连接**，服务端配置了 **5 个 MCP 服务**：

```
设备1 → UnifiedToolHandler(1) → ToolManager(1) → ServerMCPManager(1)
                                                    ├── filesystem客户端(1)
                                                    ├── playwright客户端(1)
                                                    └── Home Assistant客户端(1)

设备2 → UnifiedToolHandler(2) → ToolManager(2) → ServerMCPManager(2)
                                                    ├── filesystem客户端(2)  ❌ 重复
                                                    ├── playwright客户端(2)  ❌ 重复
                                                    └── Home Assistant客户端(2)  ❌ 重复

...

设备10 → UnifiedToolHandler(10) → ToolManager(10) → ServerMCPManager(10)
```

**资源浪费统计**：

- **内存浪费**: 每个连接重复加载工具定义和客户端实例
- **连接浪费**: 每个 MCP 服务被创建 10 个客户端连接
- **时间浪费**: 每个连接都要等待 MCP 服务初始化（可能超过 10 秒）
- **风险**: 可能超出 MCP 服务的最大连接数限制

### 验证方法

```bash
# 启动服务后，查看 MCP 服务进程的连接数
# 如果每个连接都创建了独立的客户端，会看到大量重复连接

# 查看 filesystem MCP 的 npx 进程
ps aux | grep "server-filesystem"

# 查看 playwright MCP 的连接
lsof -i :<port> | grep playwright
```

---

## ✅ 目标设计

### 设计原则

1. **单例模式**: 服务端 MCP 和插件只初始化一次
2. **作用域分离**: 全局共享 vs Per-Connection
3. **依赖注入**: 通过构造函数注入全局依赖
4. **引用计数**: 安全的资源管理和清理

### 作用域划分

| 工具类型 | 当前作用域 | 目标作用域 | 原因 |
|---------|-----------|-----------|------|
| **ServerMCP** | Per-Connection | 🌍 全局单例 | 服务端 MCP 服务不依赖具体连接，所有连接共享 |
| **ServerPlugin** | Per-Connection | 🌍 全局单例 | Python 函数插件，无状态，可共享 |
| **DeviceMCP** | Per-Connection | 🔗 Per-Connection | 每个 ESP32 设备有自己的 MCP 服务和能力 |
| **DeviceIoT** | Per-Connection | 🔗 Per-Connection | 每个设备的 IoT 能力不同 |
| **MCPEndpoint** | Per-Connection | 🔗 Per-Connection (可选) | 某些场景需要不同 endpoint 配置或认证 |

---

## 🏗️ 重构方案

### 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                   Application (app.py)                  │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │         GlobalToolManager (单例)                 │   │
│  │                                                  │   │
│  │  ┌──────────────────────────────────────────┐   │   │
│  │  │  GlobalServerMCPManager (单例)            │   │   │
│  │  │   - filesystem                            │   │   │
│  │  │   - playwright                            │   │   │
│  │  │   - Home Assistant                        │   │   │
│  │  └──────────────────────────────────────────┘   │   │
│  │                                                  │   │
│  │  ┌──────────────────────────────────────────┐   │   │
│  │  │  ServerPluginRegistry (单例)              │   │   │
│  │  │   - get_weather                           │   │   │
│  │  │   - play_music                            │   │   │
│  │  │   - search_from_ragflow                   │   │   │
│  │  └──────────────────────────────────────────┘   │   │
│  │                                                  │   │
│  │  ┌──────────────────────────────────────────┐   │   │
│  │  │  RefCounter (引用计数)                    │   │   │
│  │  │   - connection_count: 10                  │   │   │
│  │  └──────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
│  get_connection_tools(conn) → ConnectionToolManager     │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│           ConnectionHandler (每个连接)                  │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │       ConnectionToolManager                      │   │
│  │                                                  │   │
│  │  共享:                                           │   │
│  │  ├─ global_server_mcp (只读)                     │   │
│  │  └─ global_plugins (只读)                        │   │
│  │                                                  │   │
│  │  独立:                                           │   │
│  │  ├─ device_mcp_client (每个连接独立)             │   │
│  │  ├─ device_iot_tools (每个连接独立)              │   │
│  │  └─ mcp_endpoint_client (每个连接独立)           │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 📝 实现步骤

### 第一步：创建全局工具管理器

**文件**: `core/providers/tools/global_tool_manager.py`

```python
"""全局工具管理器 - 单例模式"""

import asyncio
from typing import Optional, Dict, Any
from config.logger import setup_logging
from .server_mcp.mcp_manager import ServerMCPManager

TAG = __name__
logger = setup_logging()


class GlobalToolManager:
    """
    全局工具管理器（单例）

    负责管理所有连接共享的服务端MCP服务和插件
    """

    _instance: Optional['GlobalToolManager'] = None
    _lock = asyncio.Lock()
    _initialized = False

    def __new__(cls):
        """双检锁实现单例"""
        if cls._instance is None:
            # 使用同步锁保护创建过程
            import threading
            with threading.Lock():
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        """初始化全局工具管理器（只执行一次）"""
        if GlobalToolManager._initialized:
            return

        logger.info("初始化全局工具管理器")

        # 服务端MCP管理器（全局单例）
        self.server_mcp_manager: Optional[ServerMCPManager] = None

        # 服务端插件注册表（全局单例）
        self.server_plugins: Dict[str, Any] = {}

        # 引用计数（追踪活跃连接数）
        self._connection_count = 0
        _connection_lock = asyncio.Lock()

        GlobalToolManager._initialized = True

    async def initialize(self) -> None:
        """
        初始化全局服务

        ⚠️ 注意：此方法应在应用启动时调用一次，不要在每个连接中调用
        """
        if self.server_mcp_manager is not None:
            logger.warning("全局工具管理器已经初始化，跳过重复初始化")
            return

        try:
            logger.info("开始初始化服务端MCP服务...")

            # 初始化服务端MCP管理器（不需要conn参数）
            self.server_mcp_manager = ServerMCPManager(conn=None)
            await self.server_mcp_manager.initialize_servers()

            logger.info("服务端MCP服务初始化完成")

            # 加载服务端插件
            await self._load_server_plugins()

            logger.info("全局工具管理器初始化完成")

        except Exception as e:
            logger.error(f"全局工具管理器初始化失败: {e}")
            raise

    async def _load_server_plugins(self) -> None:
        """加载服务端插件"""
        try:
            from plugins_func.loadplugins import auto_import_modules

            # 自动导入插件模块
            auto_import_modules("plugins_func/functions")

            logger.info("服务端插件加载完成")
        except Exception as e:
            logger.error(f"加载服务端插件失败: {e}")

    async def create_connection_manager(self, conn) -> 'ConnectionToolManager':
        """
        为每个连接创建工具管理器

        Args:
            conn: ConnectionHandler 实例

        Returns:
            ConnectionToolManager: 该连接专用的工具管理器
        """
        # 增加引用计数
        async with self._connection_lock:
            self._connection_count += 1
            logger.debug(f"连接数增加: {self._connection_count}")

        # 创建连接专用的工具管理器
        return ConnectionToolManager(
            conn=conn,
            global_server_mcp=self.server_mcp_manager,
            global_plugins=self.server_plugins,
            on_close=self._on_connection_close
        )

    async def _on_connection_close(self) -> None:
        """连接关闭回调"""
        async with self._connection_lock:
            self._connection_count -= 1
            logger.debug(f"连接数减少: {self._connection_count}")

        # 当没有活跃连接时，可以选择清理资源
        # 这里保持全局管理器运行，避免频繁重新初始化

    async def cleanup(self) -> None:
        """清理全局资源"""
        try:
            if self.server_mcp_manager:
                await self.server_mcp_manager.cleanup_all()
                logger.info("全局MCP管理器已清理")
        except Exception as e:
            logger.error(f"清理全局工具管理器失败: {e}")

    @property
    def connection_count(self) -> int:
        """获取当前活跃连接数"""
        return self._connection_count

    def get_statistics(self) -> Dict[str, Any]:
        """获取统计信息"""
        return {
            "connection_count": self._connection_count,
            "server_mcp_initialized": self.server_mcp_manager is not None,
            "plugin_count": len(self.server_plugins),
        }


# 全局实例（应用启动时初始化）
_global_instance: Optional[GlobalToolManager] = None


async def initialize_global_tool_manager() -> GlobalToolManager:
    """
    初始化全局工具管理器

    Returns:
        GlobalToolManager: 全局工具管理器实例
    """
    global _global_instance

    if _global_instance is None:
        _global_instance = GlobalToolManager()
        await _global_instance.initialize()

    return _global_instance


def get_global_tool_manager() -> Optional[GlobalToolManager]:
    """获取全局工具管理器实例"""
    return _global_instance
```

### 第二步：创建连接工具管理器

**文件**: `core/providers/tools/connection_tool_manager.py`

```python
"""连接工具管理器 - 每个连接专用"""

from typing import Dict, Any, Optional, Callable
from config.logger import setup_logging
from plugins_func.register import Action, ActionResponse
from .base import ToolType, ToolDefinition

TAG = __name__
logger = setup_logging()


class ConnectionToolManager:
    """
    每个连接专用的工具管理器

    职责:
    - 管理该连接专用的工具（DeviceMCP, DeviceIoT, MCPEndpoint）
    - 引用全局共享的工具（ServerMCP, ServerPlugin）
    """

    def __init__(
        self,
        conn,
        global_server_mcp,  # 全局共享
        global_plugins,     # 全局共享
        on_close: Callable  # 连接关闭回调
    ):
        self.conn = conn
        self.logger = setup_logging()

        # 全局共享的服务端MCP（只读引用）
        self.global_server_mcp = global_server_mcp

        # 全局共享的插件注册表（只读引用）
        self.global_plugins = global_plugins

        # 连接关闭回调
        self._on_close = on_close

        # 每个连接独立的工具
        self.device_mcp_client = None  # 设备端MCP客户端
        self.device_iot_tools = {}     # 设备IoT工具
        self.mcp_endpoint_client = None  # MCP接入点客户端

        # 工具缓存
        self._cached_tools: Optional[Dict[str, ToolDefinition]] = None
        self._cached_function_descriptions = None

    async def initialize(self) -> None:
        """初始化连接专用的工具"""
        try:
            # 初始化设备端MCP
            await self._initialize_device_mcp()

            # 初始化MCP接入点（如果配置了）
            await self._initialize_mcp_endpoint()

            # 初始化设备IoT工具
            await self._initialize_device_iot()

            self.logger.debug("连接工具管理器初始化完成")

        except Exception as e:
            self.logger.error(f"连接工具管理器初始化失败: {e}")
            raise

    async def _initialize_device_mcp(self) -> None:
        """初始化设备端MCP客户端"""
        try:
            from .device_mcp.mcp_handler import MCPClient

            self.device_mcp_client = MCPClient()

            # 发送初始化消息到设备
            from .device_mcp.mcp_handler import send_mcp_initialize_message
            await send_mcp_initialize_message(self.conn)

            self.logger.debug("设备端MCP客户端初始化完成")

        except Exception as e:
            self.logger.error(f"设备端MCP客户端初始化失败: {e}")

    async def _initialize_mcp_endpoint(self) -> None:
        """初始化MCP接入点"""
        try:
            mcp_endpoint_url = self.conn.config.get("mcp_endpoint", "")

            if not mcp_endpoint_url or "你的" in mcp_endpoint_url or mcp_endpoint_url == "null":
                return

            from .mcp_endpoint.mcp_endpoint_handler import connect_mcp_endpoint

            self.logger.info(f"正在初始化MCP接入点: {mcp_endpoint_url}")
            self.mcp_endpoint_client = await connect_mcp_endpoint(
                mcp_endpoint_url, self.conn
            )

            if self.mcp_endpoint_client:
                self.logger.info("MCP接入点初始化成功")
            else:
                self.logger.warning("MCP接入点初始化失败")

        except Exception as e:
            self.logger.error(f"初始化MCP接入点失败: {e}")

    async def _initialize_device_iot(self) -> None:
        """初始化设备IoT工具"""
        # 设备IoT工具由设备动态注册
        pass

    def get_all_tools(self) -> Dict[str, ToolDefinition]:
        """获取所有可用工具（全局 + 连接专用）"""
        if self._cached_tools is not None:
            return self._cached_tools

        all_tools = {}

        # 1. 全局服务端MCP工具
        if self.global_server_mcp:
            try:
                mcp_tools = self.global_server_mcp.get_all_tools()
                for tool in mcp_tools:
                    func_def = tool.get("function", {})
                    tool_name = func_def.get("name", "")
                    if tool_name:
                        all_tools[tool_name] = ToolDefinition(
                            name=tool_name,
                            description=tool,
                            tool_type=ToolType.SERVER_MCP
                        )
            except Exception as e:
                self.logger.error(f"获取服务端MCP工具失败: {e}")

        # 2. 全局服务端插件
        # （从插件注册表获取）

        # 3. 设备端MCP工具
        if self.device_mcp_client:
            try:
                device_tools = self.device_mcp_client.get_available_tools()
                for tool_def in device_tools:
                    tool_name = tool_def.get("name", "")
                    if tool_name:
                        all_tools[tool_name] = ToolDefinition(
                            name=tool_name,
                            description=tool_def,
                            tool_type=ToolType.DEVICE_MCP
                        )
            except Exception as e:
                self.logger.error(f"获取设备端MCP工具失败: {e}")

        # 4. 设备IoT工具
        all_tools.update(self.device_iot_tools)

        # 5. MCP接入点工具
        if self.mcp_endpoint_client:
            try:
                endpoint_tools = self.mcp_endpoint_client.get_available_tools()
                for tool_def in endpoint_tools:
                    tool_name = tool_def.get("name", "")
                    if tool_name:
                        all_tools[tool_name] = ToolDefinition(
                            name=tool_name,
                            description=tool_def,
                            tool_type=ToolType.MCP_ENDPOINT
                        )
            except Exception as e:
                self.logger.error(f"获取MCP接入点工具失败: {e}")

        self._cached_tools = all_tools
        return all_tools

    async def execute_tool(
        self, tool_name: str, arguments: Dict[str, Any]
    ) -> ActionResponse:
        """执行工具调用"""
        try:
            tools = self.get_all_tools()
            tool_def = tools.get(tool_name)

            if not tool_def:
                return ActionResponse(
                    action=Action.NOTFOUND,
                    response=f"工具 {tool_name} 不存在"
                )

            # 根据工具类型路由到对应的执行器
            tool_type = tool_def.tool_type

            if tool_type == ToolType.SERVER_MCP:
                return await self._execute_server_mcp(tool_name, arguments)
            elif tool_type == ToolType.DEVICE_MCP:
                return await self._execute_device_mcp(tool_name, arguments)
            elif tool_type == ToolType.MCP_ENDPOINT:
                return await self._execute_mcp_endpoint(tool_name, arguments)
            elif tool_type == ToolType.DEVICE_IOT:
                return await self._execute_device_iot(tool_name, arguments)
            else:
                return ActionResponse(
                    action=Action.ERROR,
                    response=f"未知工具类型: {tool_type}"
                )

        except Exception as e:
            self.logger.error(f"执行工具 {tool_name} 失败: {e}")
            return ActionResponse(action=Action.ERROR, response=str(e))

    async def _execute_server_mcp(
        self, tool_name: str, arguments: Dict[str, Any]
    ) -> ActionResponse:
        """执行服务端MCP工具（使用全局管理器）"""
        if not self.global_server_mcp:
            return ActionResponse(
                action=Action.ERROR,
                response="服务端MCP管理器未初始化"
            )

        try:
            result = await self.global_server_mcp.execute_tool(tool_name, arguments)
            return ActionResponse(action=Action.REQLLM, result=str(result))
        except Exception as e:
            return ActionResponse(action=Action.ERROR, response=str(e))

    async def _execute_device_mcp(
        self, tool_name: str, arguments: Dict[str, Any]
    ) -> ActionResponse:
        """执行设备端MCP工具"""
        if not self.device_mcp_client:
            return ActionResponse(
                action=Action.ERROR,
                response="设备端MCP客户端未初始化"
            )

        try:
            from .device_mcp.mcp_handler import call_mcp_tool

            result = await call_mcp_tool(
                self.conn, self.device_mcp_client, tool_name, str(arguments)
            )
            return ActionResponse(action=Action.REQLLM, result=result)
        except Exception as e:
            return ActionResponse(action=Action.ERROR, response=str(e))

    async def _execute_mcp_endpoint(
        self, tool_name: str, arguments: Dict[str, Any]
    ) -> ActionResponse:
        """执行MCP接入点工具"""
        if not self.mcp_endpoint_client:
            return ActionResponse(
                action=Action.ERROR,
                response="MCP接入点客户端未初始化"
            )

        try:
            from .mcp_endpoint.mcp_endpoint_handler import call_mcp_endpoint_tool

            result = await call_mcp_endpoint_tool(
                self.mcp_endpoint_client, tool_name, str(arguments)
            )
            return ActionResponse(action=Action.REQLLM, result=result)
        except Exception as e:
            return ActionResponse(action=Action.ERROR, response=str(e))

    async def _execute_device_iot(
        self, tool_name: str, arguments: Dict[str, Any]
    ) -> ActionResponse:
        """执行设备IoT工具"""
        # TODO: 实现设备IoT工具执行
        return ActionResponse(
            action=Action.NOTFOUND,
            response=f"IoT工具 {tool_name} 暂未实现"
        )

    def refresh_tools(self) -> None:
        """刷新工具缓存"""
        self._cached_tools = None
        self._cached_function_descriptions = None
        self.logger.debug("工具缓存已刷新")

    async def cleanup(self) -> None:
        """清理连接专用资源"""
        try:
            # 清理MCP接入点连接
            if self.mcp_endpoint_client:
                await self.mcp_endpoint_client.close()

            # 调用关闭回调
            if self._on_close:
                await self._on_close()

            self.logger.debug("连接工具管理器清理完成")

        except Exception as e:
            self.logger.error(f"清理连接工具管理器失败: {e}")
```

### 第三步：修改 ServerMCPManager

**文件**: `core/providers/tools/server_mcp/mcp_manager.py`

```python
"""服务端MCP管理器 - 支持全局单例模式"""

class ServerMCPManager:
    """管理多个服务端MCP服务的集中管理器"""

    def __init__(self, conn) -> None:
        """
        初始化MCP管理器

        Args:
            conn: ConnectionHandler 实例（全局模式下为 None）
        """
        self.conn = conn
        self.config_path = get_project_dir() + "data/.mcp_server_settings.json"

        if not os.path.exists(self.config_path):
            self.config_path = ""
            logger.bind(tag=TAG).warning(
                f"请检查mcp服务配置文件：data/.mcp_server_settings.json"
            )

        self.clients: Dict[str, ServerMCPClient] = {}
        self.tools = []
        self._init_lock = asyncio.Lock()

        # ⚠️ 重要：全局模式下不需要conn，日志回调可以直接使用全局logger
        self.logging_callback = self._default_logging_callback
        self.progress_callback = self._default_progress_callback

    async def _default_logging_callback(self, params):
        """默认日志回调（全局模式）"""
        logger.bind(tag=TAG).info(f"[Server Log - {params.level.upper()}] {params.data}")

    async def _default_progress_callback(self, progress, total, message):
        """默认进度回调（全局模式）"""
        logger.bind(tag=TAG).info(f"[Progress {progress}/{total}]: {message}")

    # ... 其他方法保持不变
```

### 第四步：修改应用启动流程

**文件**: `app.py`

```python
from core.providers.tools.global_tool_manager import initialize_global_tool_manager

async def main():
    # ... 其他初始化代码

    # ✅ 初始化全局工具管理器（只执行一次）
    try:
        global_tool_mgr = await initialize_global_tool_manager()
        logger.info("全局工具管理器初始化成功")
    except Exception as e:
        logger.error(f"全局工具管理器初始化失败: {e}")
        # 可以选择继续运行，只是MCP工具不可用

    # ... 启动WebSocket服务器

async def on_connection(websocket, path):
    """处理新的WebSocket连接"""
    # 创建连接处理器
    conn = ConnectionHandler(websocket, path)

    # ✅ 从全局管理器获取连接专用的工具管理器
    global_tool_mgr = get_global_tool_manager()
    if global_tool_mgr:
        conn.func_handler = await global_tool_mgr.create_connection_manager(conn)
    else:
        # 降级：使用旧的方式创建
        logger.warning("全局工具管理器未初始化，使用本地工具管理器")
        conn.func_handler = UnifiedToolHandler(conn)

    # 处理连接
    await conn.handle()

async def cleanup():
    """应用关闭时的清理"""
    global_tool_mgr = get_global_tool_manager()
    if global_tool_mgr:
        await global_tool_mgr.cleanup()
```

### 第五步：修改 ConnectionHandler

**文件**: `core/connection.py`

```python
class ConnectionHandler:
    def __init__(self, websocket, path):
        # ... 其他初始化代码

        # ❌ 删除：每个连接都创建工具管理器
        # self.func_handler = UnifiedToolHandler(self)

        # ✅ 改为：由外部注入工具管理器
        self.func_handler = None  # 将在 on_connection 中注入

        # 异步初始化（如果使用降级模式）
        # if hasattr(self, "loop") and self.loop:
        #     asyncio.run_coroutine_threadsafe(
        #         self.func_handler._initialize(), self.loop
        #     )
```

---

## 🧪 测试验证

### 测试场景 1：单连接功能测试

```python
# test/test_single_connection.py
import asyncio
import pytest
from core.providers.tools.global_tool_manager import initialize_global_tool_manager

@pytest.mark.asyncio
async def test_single_connection():
    """测试单个连接的工具调用"""
    # 初始化全局管理器
    global_mgr = await initialize_global_tool_manager()

    # 模拟连接
    conn = MockConnectionHandler()

    # 创建连接工具管理器
    conn_mgr = await global_mgr.create_connection_manager(conn)
    await conn_mgr.initialize()

    # 获取所有工具
    tools = conn_mgr.get_all_tools()
    assert len(tools) > 0, "应该有可用的工具"

    # 测试工具调用
    if "filesystem_read_file" in tools:
        result = await conn_mgr.execute_tool(
            "filesystem_read_file",
            {"path": "/tmp/test.txt"}
        )
        assert result.action != Action.ERROR
```

### 测试场景 2：多连接资源测试

```python
# test/test_multiple_connections.py
@pytest.mark.asyncio
async def test_multiple_connections_share_mcp():
    """测试多个连接共享MCP客户端"""
    global_mgr = await initialize_global_tool_manager()

    # 创建多个连接
    connections = []
    for i in range(10):
        conn = MockConnectionHandler()
        conn_mgr = await global_mgr.create_connection_manager(conn)
        await conn_mgr.initialize()
        connections.append(conn_mgr)

    # 验证所有连接都能获取到服务端MCP工具
    for conn_mgr in connections:
        tools = conn_mgr.get_all_tools()
        server_mcp_tools = [
            name for name, tool in tools.items()
            if tool.tool_type == ToolType.SERVER_MCP
        ]
        assert len(server_mcp_tools) > 0, "每个连接都应该能看到服务端MCP工具"

    # 验证全局只有一个ServerMCPManager实例
    assert global_mgr.server_mcp_manager is not None
    # 所有连接的 global_server_mcp 应该指向同一个对象
    for conn_mgr in connections:
        assert conn_mgr.global_server_mcp is global_mgr.server_mcp_manager

    # 验证引用计数
    assert global_mgr.connection_count == 10

    # 清理
    for conn_mgr in connections:
        await conn_mgr.cleanup()

    assert global_mgr.connection_count == 0
```

### 测试场景 3：性能对比测试

```python
# test/test_performance.py
import time
import asyncio

@pytest.mark.asyncio
async def test_performance_improvement():
    """对比旧方案和新方案的性能"""
    # 旧方案：每个连接都初始化MCP
    start_time = time.time()
    for i in range(10):
        conn_mgr = UnifiedToolHandler(MockConnectionHandler())
        await conn_mgr._initialize()
    old_duration = time.time() - start_time

    # 新方案：全局初始化一次
    start_time = time.time()
    global_mgr = await initialize_global_tool_manager()
    for i in range(10):
        conn_mgr = await global_mgr.create_connection_manager(MockConnectionHandler())
        await conn_mgr.initialize()
    new_duration = time.time() - start_time

    # 新方案应该快得多
    print(f"旧方案耗时: {old_duration:.2f}秒")
    print(f"新方案耗时: {new_duration:.2f}秒")
    print(f"性能提升: {old_duration/new_duration:.1f}x")

    assert new_duration < old_duration, "新方案应该更快"
```

### 测试场景 4：并发压力测试

```python
# test/test_concurrent.py
@pytest.mark.asyncio
async def test_concurrent_connections():
    """测试并发连接场景"""
    global_mgr = await initialize_global_tool_manager()

    # 并发创建100个连接
    tasks = []
    for i in range(100):
        async def create_connection():
            conn = MockConnectionHandler()
            conn_mgr = await global_mgr.create_connection_manager(conn)
            await conn_mgr.initialize()

            # 执行一些工具调用
            tools = conn_mgr.get_all_tools()
            if tools:
                tool_name = list(tools.keys())[0]
                await conn_mgr.execute_tool(tool_name, {})

            return conn_mgr

        tasks.append(asyncio.create_task(create_connection()))

    # 等待所有连接创建完成
    connections = await asyncio.gather(*tasks)

    # 验证
    assert global_mgr.connection_count == 100

    # 清理
    for conn_mgr in connections:
        await conn_mgr.cleanup()

    assert global_mgr.connection_count == 0
```

---

## 📊 预期收益

### 资源节约

| 指标 | 旧方案（10连接） | 新方案（10连接） | 改善 |
|-----|----------------|----------------|------|
| **MCP客户端数** | 10 × 5 = 50 | 5 | **90% ↓** |
| **初始化时间** | 10 × 10s = 100s | 10s | **90% ↓** |
| **内存占用** | 10 × 50MB = 500MB | 100MB | **80% ↓** |
| **并发连接能力** | 受MCP服务连接限制 | 无限制 | **10x ↑** |

### 代码质量

- ✅ 单一职责：全局管理器 vs 连接管理器
- ✅ 依赖注入：明确的依赖关系
- ✅ 可测试性：更容易单元测试
- ✅ 可维护性：清晰的作用域划分

---

## ⚠️ 风险与注意事项

### 1. 向后兼容性

**问题**: 现有代码可能依赖旧的 `UnifiedToolHandler`

**解决方案**:
- 保留 `UnifiedToolHandler` 作为降级方案
- 如果全局管理器未初始化，自动降级到旧模式
- 逐步迁移现有代码

### 2. 全局状态管理

**问题**: 单例模式可能导致全局状态污染

**解决方案**:
- 确保全局管理器是只读的（初始化后不修改）
- 连接管理器保持隔离
- 添加完善的单元测试

### 3. 清理时机

**问题**: 何时清理全局MCP客户端？

**解决方案**:
- 选项A: 应用退出时清理（推荐）
- 选项B: 引用计数归零时清理（可能频繁重新初始化）
- 选项C: 定时刷新（保持连接活跃）

### 4. 线程安全

**问题**: 多线程环境下的单例创建

**解决方案**:
- 使用双检锁（Double-Checked Locking）
- Python 3.7+ 可使用 `asyncio.Lock` + `threading.Lock`

---

## 📅 实施计划

### Phase 1: 准备阶段（1天）
- [ ] 创建 `global_tool_manager.py` 和 `connection_tool_manager.py`
- [ ] 编写单元测试
- [ ] 代码审查

### Phase 2: 集成阶段（2天）
- [ ] 修改 `app.py` 集成全局管理器
- [ ] 修改 `ConnectionHandler` 支持依赖注入
- [ ] 修改 `ServerMCPManager` 支持无conn模式
- [ ] 添加降级方案（向后兼容）

### Phase 3: 测试阶段（2天）
- [ ] 单元测试
- [ ] 集成测试
- [ ] 性能对比测试
- [ ] 并发压力测试

### Phase 4: 上线阶段（1天）
- [ ] 灰度发布（10% 流量）
- [ ] 监控指标
- [ ] 全量发布
- [ ] 回滚预案

---

## 🔧 回滚方案

如果上线后出现问题，可以快速回滚：

```python
# app.py
USE_GLOBAL_TOOL_MANAGER = os.getenv("USE_GLOBAL_TOOL_MANAGER", "true") == "true"

async def on_connection(websocket, path):
    conn = ConnectionHandler(websocket, path)

    if USE_GLOBAL_TOOL_MANAGER:
        # 新方案
        global_tool_mgr = get_global_tool_manager()
        if global_tool_mgr:
            conn.func_handler = await global_tool_mgr.create_connection_manager(conn)
        else:
            conn.func_handler = UnifiedToolHandler(conn)  # 降级
    else:
        # 旧方案
        conn.func_handler = UnifiedToolHandler(conn)

    await conn.handle()
```

通过环境变量 `USE_GLOBAL_TOOL_MANAGER` 快速切换。

---

## 📚 参考资料

- [Python单例模式最佳实践](https://refactoring.guru/design-patterns/singleton/python)
- [MCP协议规范](https://modelcontextprotocol.io/)
- [依赖注入模式](https://en.wikipedia.org/wiki/Dependency_injection)
- [项目现有MCP实现](../core/providers/tools/)

---

## 📝 变更日志

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|---------|------|
| 2026-05-03 | 1.0 | 创建重构方案文档 | Claude |

---

**下一步行动**: 请在实施前进行代码审查，并确认测试环境和回滚预案。
