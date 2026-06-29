# 聊天服务线程池与连接限流优化

## Context

聊天服务部署在阿里云 SAE (2c4g 容器)，使用 TCP 活跃连接数作为弹性伸缩指标。当前线程池硬编码为 `min(32, cpu*4)`，2核时仅 8 线程，对 I/O 密集型任务偏少。且无连接数上限保护，单实例可能因连接过多导致服务质量下降。

## Task 1: 线程池参数可配置化

**文件**: `main/xiaozhi-server/core/connection.py`

将线程池从模块级硬编码改为延迟初始化（lazy singleton），从 config.yaml 读取大小：

```python
_GLOBAL_EXECUTOR = None
_EXECUTOR_LOCK = threading.Lock()

def _get_thread_pool_size() -> int:
    try:
        from config.config_loader import load_config
        config = load_config()
        size = config.get("server", {}).get("thread_pool_size", None)
        if size is not None:
            return int(size)
    except Exception:
        pass
    return min(32, (os.cpu_count() or 4) * 8)  # 默认改为 cpu*8

def get_global_executor() -> ThreadPoolExecutor:
    global _GLOBAL_EXECUTOR
    if _GLOBAL_EXECUTOR is None:
        with _EXECUTOR_LOCK:
            if _GLOBAL_EXECUTOR is None:
                max_workers = _get_thread_pool_size()
                _GLOBAL_EXECUTOR = ThreadPoolExecutor(
                    max_workers=max_workers,
                    thread_name_prefix="xiaozhi-worker"
                )
    return _GLOBAL_EXECUTOR
```

同步修改：
- `ConnectionHandler.__init__` 中 `self.executor = get_global_executor()`
- `shutdown_global_executor()` 增加 None 检查

## Task 2: WebSocket 连接数上限保护

**文件**: `main/xiaozhi-server/core/websocket_server.py`

在 `WebSocketServer.__init__` 中添加：
```python
self._max_connections = int(server_config.get("max_connections", 50))
self._warning_ratio = float(server_config.get("max_connections_warning_ratio", 0.8))
self._active_connections = 0
```

在 `_handle_connection` 中（device-id 检查之后、认证之前）：
- 检查 `_active_connections >= _max_connections`，超限返回 `close(1013, "服务器连接数已满，请稍后重试")`
- 通过后 `_active_connections += 1`，用 `try/finally` 保证退出时 `-1`
- 达到 80% 时输出 warning 日志

## Task 3: 添加配置项

**文件**: `main/xiaozhi-server/config.yaml`

在 `server:` 段添加（注释状态，不影响默认行为）：
```yaml
  # 线程池大小（I/O密集型任务），默认: min(32, CPU核心数*8)
  # thread_pool_size: 16
  # WebSocket最大连接数，超过后拒绝新连接
  # max_connections: 50
  # 连接数告警阈值比例（0.0-1.0）
  # max_connections_warning_ratio: 0.8
```

## 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 线程池初始化时机 | 延迟初始化 (lazy) | 避免模块 import 时 config 还未加载 |
| 连接计数线程安全 | 无锁（asyncio 事件循环单线程） | 所有连接 handler 在同一事件循环中 |
| 拒绝连接 close code | 1013 (Try Again Later) | RFC 6455 语义匹配 |
| 默认线程池公式 | `min(32, cpu*8)` | I/O 密集型，CPU 大部分时间等网络 |

## 与 SAE 弹性伸缩协同

```
客户端连接 → [max_connections=50 保护] → 线程池(16线程) → 响应
                    ↓ 超限 close(1013)
            客户端重连 → SLB 路由到新实例 ← SAE TCP活跃连接数扩容
```

## 验证方式

1. **线程池**: 启动服务，检查日志中线程池大小输出是否符合配置
2. **连接限制**: 使用 `wscat` 或测试脚本连续创建超过 max_connections 个连接，验证超限时返回 1013
3. **回归**: 不配置新参数时，确认默认值正常工作（向后兼容）
