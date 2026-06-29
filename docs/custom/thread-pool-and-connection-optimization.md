# Xiaozhi-Server 线程池与连接限流优化方案

> 本文档记录了对 xiaozhi-server 聊天服务的全局线程池参数优化与 WebSocket 连接数限流保护的完整技术方案，涵盖问题分析、代码实现、SAE 弹性伸缩策略配置及容量规划。

---

## 目录

1. [背景与问题分析](#1-背景与问题分析)
2. [技术方案](#2-技术方案)
3. [代码实现详情](#3-代码实现详情)
4. [配置项说明](#4-配置项说明)
5. [SAE 部署配合](#5-sae-部署配合)
6. [弹性伸缩策略配置](#6-弹性伸缩策略配置)
7. [容量规划与参数调优](#7-容量规划与参数调优)
8. [Code Review 修复记录](#8-code-review-修复记录)
9. [运维监控要点](#9-运维监控要点)

---

## 1. 背景与问题分析

### 1.1 原始线程池配置

优化前，`core/connection.py` 中全局线程池采用模块级硬编码初始化：

```python
# 优化前（已废弃）
_GLOBAL_EXECUTOR = ThreadPoolExecutor(
    max_workers=min(32, (os.cpu_count() or 4) * 4),
    thread_name_prefix="xiaozhi-worker"
)
```

**存在的问题：**

| 问题 | 影响 |
|------|------|
| 线程池大小不可配置 | 无法根据部署环境（容器规格、ASR/TTS类型）灵活调整 |
| 模块级即初始化 | import 时立即创建线程池，即使服务未启动也占用资源 |
| 倍率系数偏低（×4） | 1核容器仅 4 线程，I/O 密集型场景并发能力不足 |
| 无连接数上限保护 | 连接数无限增长可导致 OOM |

### 1.2 1核2G容器承载能力分析

以 1 核 2G 容器为例（优化前 `cpu_count * 4` 倍率）：

```
线程池大小 = min(32, 1 * 4) = 4 个工作线程
```

| 场景 | 空闲连接 | 活跃对话 | 说明 |
|------|---------|---------|------|
| 理论上限 | ~20-50 | — | 受内存限制（每连接 ~15-40MB） |
| 实际可用 | ~15-20 | 3-4 | 4 线程同时处理 3-4 个活跃对话即满载 |
| 高峰风险 | — | >4 时排队 | 线程池队列堆积，响应延迟增大 |

**结论：** 1 核 2G 容器可承载 15-20 个 WebSocket 连接，但仅支持 3-4 个**同时活跃**的语音对话。对于生产部署，并发能力严重不足。

### 1.3 I/O 密集型服务特征

xiaozhi-server 的核心工作流为：

```
用户语音 → ASR(网络I/O) → LLM(网络I/O) → TTS(网络I/O) → 音频回传
```

每个环节都是**网络 I/O 密集型**操作，线程在等待外部 API 响应时处于阻塞状态。这意味着：

- CPU 使用率**不能**反映实际负载（线程大部分时间在等待 I/O）
- 线程池需要更多线程来"填补"等待间隙（线程数 > CPU 核心数）
- 传统的 CPU 阈值弹性伸缩策略**不适用**

---

## 2. 技术方案

### 2.1 方案总览

| 优化项 | 内容 | 目标 |
|--------|------|------|
| 线程池可配置化 | 支持从 `config.yaml` 读取 `thread_pool_size` | 灵活调整并发能力 |
| 延迟初始化 | 双检锁懒加载，首次调用时创建 | 避免无用资源占用 |
| 倍率优化 | 默认公式从 `×4` 改为 `×8` | 提升 I/O 密集型并发 |
| 连接数上限 | `max_connections` 限制 WebSocket 总连接数 | 防止 OOM，触发弹性扩容 |
| 告警阈值 | `max_connections_warning_ratio` 提前预警 | 运维感知，配合 SAE 扩容 |
| 优雅关闭 | `shutdown_global_executor()` 清理线程池 | 避免资源泄漏 |

### 2.2 ThreadPoolExecutor 队列机制

Python `ThreadPoolExecutor` 内部使用 `SimpleQueue`（无界队列）：

- **无界队列：** 所有提交的任务都会排队，不会拒绝
- **无拒绝策略：** 不像 Java 的 `ThreadPoolExecutor` 可配置 `AbortPolicy`、`CallerRunsPolicy` 等
- **风险：** 任务无限堆积 → 内存溢出

因此，连接数限制（`max_connections`）是替代"拒绝策略"的关键保护手段：在连接进入时就进行准入控制，而非在任务队列层面。

### 2.3 架构设计

```
                    ┌─────────────────────────────────┐
                    │       WebSocketServer           │
                    │  ┌───────────────────────────┐  │
  新连接 ──────────►│  │ _handle_connection()      │  │
                    │  │                           │  │
                    │  │ 1. 连接数检查 (>= max?)   │  │
                    │  │    ├─ YES → close(1013)   │  │
                    │  │    └─ NO  → +1 计数       │  │
                    │  │                           │  │
                    │  │ 2. 认证                    │  │
                    │  │ 3. ConnectionHandler       │  │
                    │  │ 4. finally: -1 计数        │  │
                    │  └───────────────────────────┘  │
                    │               │                  │
                    │               ▼                  │
                    │  ┌───────────────────────────┐  │
                    │  │   全局线程池 (共享)         │  │
                    │  │   max_workers = N          │  │
                    │  │   ┌──┐┌──┐┌──┐┌──┐...     │  │
                    │  │   │W1││W2││W3││W4│        │  │
                    │  │   └──┘└──┘└──┘└──┘        │  │
                    │  │   [无界任务队列]           │  │
                    │  └───────────────────────────┘  │
                    │               │                  │
                    │               ▼                  │
                    │     ASR / LLM / TTS (网络I/O)    │
                    └─────────────────────────────────┘
```

---

## 3. 代码实现详情

### 3.1 修改文件清单

| 文件 | 修改内容 |
|------|---------|
| `core/connection.py` | 线程池延迟初始化、可配置化、优雅关闭 |
| `core/websocket_server.py` | 连接数计数器、限流检查、告警日志 |
| `core/.../config.yaml` | 新增 3 个配置项 |
| `app.py` | 添加 `shutdown_global_executor()` 调用 |

### 3.2 线程池延迟初始化（connection.py）

```python
import threading
from concurrent.futures import ThreadPoolExecutor

# 全局共享线程池，避免每连接创建独立线程池导致线程爆炸
# 延迟初始化：首次调用时从配置读取线程池大小
_GLOBAL_EXECUTOR = None
_EXECUTOR_LOCK = threading.Lock()


def _get_thread_pool_size() -> int:
    """获取线程池大小，优先从配置读取"""
    try:
        from config.config_loader import load_config
        config = load_config()
        size = config.get("server", {}).get("thread_pool_size", None)
        if size is not None:
            size = int(size)
            if 1 <= size <= 128:
                return size
    except Exception as e:
        setup_logging().bind(tag=TAG).warning(
            f"读取线程池配置失败，使用默认值: {e}"
        )
    # 默认: I/O 密集型公式，上限32
    return min(32, (os.cpu_count() or 4) * 8)


def get_global_executor() -> ThreadPoolExecutor:
    """获取全局线程池实例（线程安全的懒初始化）"""
    global _GLOBAL_EXECUTOR
    if _GLOBAL_EXECUTOR is None:
        with _EXECUTOR_LOCK:
            if _GLOBAL_EXECUTOR is None:
                max_workers = _get_thread_pool_size()
                _GLOBAL_EXECUTOR = ThreadPoolExecutor(
                    max_workers=max_workers,
                    thread_name_prefix="xiaozhi-worker"
                )
                setup_logging().bind(tag=TAG).info(
                    f"全局线程池已初始化: max_workers={max_workers}"
                )
    return _GLOBAL_EXECUTOR


def shutdown_global_executor():
    """服务器关闭时调用，清理全局线程池"""
    global _GLOBAL_EXECUTOR
    if _GLOBAL_EXECUTOR is not None:
        _GLOBAL_EXECUTOR.shutdown(wait=True, cancel_futures=True)
        _GLOBAL_EXECUTOR = None
```

**关键设计点：**

- **双检锁（Double-Checked Locking）：** 先无锁检查 `_GLOBAL_EXECUTOR is None`，命中后再加锁二次检查，避免每次调用都获取锁
- **范围校验：** 配置值校验 `1 <= size <= 128`，非法值回退默认公式
- **异常日志：** 配置读取失败时记录 warning 日志，而非静默忽略
- **倍率从 ×4 提升到 ×8：** 2 核容器从 8 线程提升到 16 线程

### 3.3 连接数限流（websocket_server.py）

`WebSocketServer.__init__` 中初始化连接数管理：

```python
# 连接数管理
server_config = self.config["server"]
self._max_connections = max(1, int(server_config.get("max_connections", 50)))
self._warning_ratio = min(1.0, max(0.0, float(server_config.get("max_connections_warning_ratio", 0.8))))
self._active_connections = 0
self._warning_threshold = int(self._max_connections * self._warning_ratio)
```

`_handle_connection` 方法采用三层 try/finally 结构：

```python
async def _handle_connection(self, websocket):
    # ... header 解析 ...

    # --- 第1层：连接数限制检查 ---
    if self._active_connections >= self._max_connections:
        self.logger.bind(tag=TAG).warning(
            f"连接数已达上限({self._active_connections}/{self._max_connections})，拒绝新连接"
        )
        try:
            await websocket.close(1013, "服务器连接数已满，请稍后重试")
        except Exception:
            pass
        return

    # 计数器 +1（从此处开始，所有退出路径都必须 -1）
    self._active_connections += 1
    # 告警日志
    if self._active_connections >= self._warning_threshold:
        self.logger.bind(tag=TAG).warning(
            f"连接数接近上限: {self._active_connections}/{self._max_connections}"
        )
    else:
        self.logger.bind(tag=TAG).info(
            f"新连接接入，当前连接数: {self._active_connections}/{self._max_connections}"
        )

    # --- 第2层：认证 + 连接处理 ---
    try:
        try:
            await self._handle_auth(websocket)
        except AuthenticationError:
            await websocket.send("认证失败")
            await websocket.close()
            return
        handler = ConnectionHandler(...)
        try:
            await handler.handle_connection(websocket)
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"处理连接时出错: {e}")
        finally:
            # 强制关闭连接
            ...
    # --- 第3层：计数器递减（保证所有路径都执行） ---
    finally:
        self._active_connections -= 1
        self.logger.bind(tag=TAG).debug(
            f"连接断开，当前连接数: {self._active_connections}/{self._max_connections}"
        )
```

**关键设计点：**

- **WebSocket Close Code 1013 (Try Again Later)：** 标准协议码，告知客户端"服务器暂时无法处理，请稍后重试"
- **三层 try/finally：** 确保无论认证失败、处理异常还是正常关闭，计数器都能正确递减
- **预计算告警阈值：** `self._warning_threshold` 在 `__init__` 中一次性计算，避免每次连接重复计算
- **线程安全性：** asyncio 事件循环单线程模型，`_active_connections` 的读写天然安全，无需加锁

### 3.4 优雅关闭（app.py）

```python
from core.connection import shutdown_global_executor

async def main():
    ...
    try:
        await wait_for_exit()
    except asyncio.CancelledError:
        print("任务被取消，清理资源中...")
    finally:
        await gc_manager.stop()
        shutdown_global_executor()  # ← 关闭全局线程池
        stdin_task.cancel()
        ws_task.cancel()
        ...
```

---

## 4. 配置项说明

在 `config.yaml` 的 `server:` 段下新增以下配置：

```yaml
server:
  # ... 其他配置 ...

  # 线程池大小（用于LLM对话、TTS等I/O密集型任务）
  # 不配置时使用默认公式: min(32, CPU核心数 * 8)
  # thread_pool_size: 16

  # WebSocket最大连接数，超过后拒绝新连接（返回 close code 1013）
  # 配合 SAE 弹性伸缩使用，作为单实例的保护性上限
  max_connections: 80

  # 连接数告警阈值比例（0.0-1.0），达到该比例时输出警告日志
  # 不配置时默认 0.8（即 max_connections 的 80%）
  # max_connections_warning_ratio: 0.8
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `thread_pool_size` | int | `min(32, cpu×8)` | 全局线程池工作线程数，范围 1-128 |
| `max_connections` | int | 0 | WebSocket 最大并发连接数 |
| `max_connections_warning_ratio` | float | 0.8 | 告警阈值比例，范围 0.0-1.0 |

> **配置优先级：** `data/.config.yaml` > `config.yaml` > 代码默认值

---

## 5. SAE 部署配合

### 5.1 推荐容器规格

| 规格 | vCPU | 内存 | 线程池(默认) | 推荐 max_connections | 月费用(估) |
|------|------|------|-------------|---------------------|-----------|
| 入门 | 1 | 2G | 8 | 30-40 | 低 |
| **推荐** | **2** | **4G** | **16** | **80** | **中** |
| 高配 | 4 | 8G | 32 | 150-200 | 高 |

**推荐 2 核 4G：**

- 线程池默认 16 线程，可同时处理 16 个活跃 I/O 请求
- `max_connections: 80`，按 20% 活跃率计算 ≈ 16 活跃对话，刚好匹配线程池
- 80 连接 × 15MB/连接 ≈ 1200MB，4G 内存充裕
- 性价比最优，单实例可支撑 60-80 个终端

### 5.2 max_connections 与 SAE 弹性伸缩的协同关系

```
SAE 弹性扩容触发点 = max_connections × max_connections_warning_ratio

示例：
  max_connections = 80
  warning_ratio = 0.8（默认）
  → SAE 目标值 = 80 × 0.8 = 64

当单实例 TCP 活跃连接数 ≥ 64 时 → SAE 自动扩容
当单实例 TCP 活跃连接数 ≥ 80 时 → 拒绝新连接（保护实例不 OOM）
```

**设计意图：** 在实例达到 `max_connections` 硬上限之前，SAE 弹性伸缩已经触发扩容，新实例启动后承接流量。形成"软告警 → 弹性扩容 → 硬拒绝"的三级保护。

### 5.3 SAE 部署清单

| 配置项 | 推荐值 | 说明 |
|--------|--------|------|
| 实例规格 | 2 vCPU / 4 GB | 见上表分析 |
| 最小实例数 | 2 | 保证高可用，单实例宕机不影响服务 |
| 最大实例数 | 5-10 | 根据总用户量评估 |
| 缩容稳定窗口 | 300 秒 | 避免频繁扩缩容抖动 |
| 健康检查 | TCP 8000 端口 | WebSocket 服务端口 |
| 优雅下线 | 开启 | 等待存量连接处理完毕 |

---

## 6. 弹性伸缩策略配置

### 6.1 指标选择

SAE 提供以下弹性指标，针对 I/O 密集型聊天服务的选型分析：

| 指标 | 适用性 | 原因 |
|------|--------|------|
| **TCP 活跃连接数** | ✅ **首选** | 直接反映实例负载，与 max_connections 联动 |
| CPU 使用率 | ❌ 不适用 | I/O 密集型服务 CPU 大部分时间空闲，无法反映真实负载 |
| Mem 使用率 | ⚠️ 辅助 | 可作为二级告警指标，但 Python 内存波动大 |
| TCP 总连接数 | ⚠️ 辅助 | 包含 TIME_WAIT 等非活跃连接，不如"活跃连接数"准确 |
| 应用 QPS | ❌ 不适用 | 聊天服务非请求-响应模型，无明确 QPS 概念 |
| 应用响应时间(RT) | ⚠️ 辅助 | 受 LLM/TTS 外部 API 延迟影响大，非实例自身瓶颈 |

**结论：** 以 **TCP 活跃连接数** 为主指标，Mem 使用率作为辅助指标。

### 6.2 弹性策略详细配置

#### 主策略：TCP 活跃连接数

```yaml
弹性策略:
  指标: TCP活跃连接数
  目标值: 64          # = max_connections(80) × warning_ratio(0.8)
  最小实例数: 2
  最大实例数: 10
  扩容冷却时间: 60秒
  缩容冷却时间: 300秒
```

**目标值计算公式：**

```
目标值 = max_connections × max_connections_warning_ratio

示例（2核4G, max_connections=80）:
  目标值 = 80 × 0.8 = 64
```

**扩容逻辑：**
- 当所有实例的平均 TCP 活跃连接数 ≥ 64 时，触发扩容
- SAE 自动增加实例数，使平均连接数回落到 64 以下
- 新实例启动后，SLB 自动分发流量

#### 辅助策略：Mem 使用率（保护性）

```yaml
辅助策略:
  指标: Mem使用率
  阈值: 85%
  动作: 扩容
  说明: 内存使用率持续超过85%时扩容，防止OOM
```

### 6.3 不同规格的弹性策略参考

| 容器规格 | max_connections | SAE 目标值 | 最小实例 | 最大实例 |
|---------|-----------------|-----------|---------|---------|
| 1c2g | 40 | 32 | 2 | 10 |
| **2c4g** | **80** | **64** | **2** | **10** |
| 4c8g | 150 | 120 | 2 | 5 |

### 6.4 弹性伸缩工作流程

```
                        ┌─────────────────────────┐
                        │  SAE 弹性控制器           │
                        │  监控: TCP活跃连接数       │
                        └───────────┬─────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
              连接数 < 64     连接数 ≈ 64     连接数 ≥ 80
                    │               │               │
                    ▼               ▼               ▼
              ┌──────────┐  ┌────────────┐  ┌──────────────┐
              │ 正常运行  │  │ 触发扩容    │  │ 拒绝新连接   │
              │ 无需操作  │  │ 新增实例    │  │ close(1013) │
              └──────────┘  └────────────┘  └──────────────┘
                                   │                │
                                   ▼                ▼
                            ┌────────────┐  ┌──────────────┐
                            │ SLB 分发   │  │ 客户端重试   │
                            │ 新流量到   │  │ 连接到新实例  │
                            │ 新实例     │  └──────────────┘
                            └────────────┘
```

---

## 7. 容量规划与参数调优

### 7.1 资源消耗估算

每个 WebSocket 连接的资源消耗：

| 状态 | 内存消耗 | CPU 消耗 | 说明 |
|------|---------|---------|------|
| 空闲连接 | 5-10 MB | ~0% | 仅维持 WebSocket 心跳 |
| 活跃对话 | 15-40 MB | 5-15% | ASR + LLM + TTS 流水线 |
| 峰值对话 | 40-60 MB | 15-25% | 含音频缓冲、TTS 队列 |

> **活跃率假设：** 生产环境中，约 15-25% 的连接同时处于活跃对话状态。

### 7.2 2核4G容量计算

```
内存预算：
  总内存:        4096 MB
  系统预留:      -512 MB（OS + Python 运行时）
  可用内存:      3584 MB
  单连接均值:    15 MB（含空闲+活跃混合）
  max_connections = 3584 / 15 ≈ 238 → 保守取 80

线程池约束：
  默认线程池:    min(32, 2 × 8) = 16 个线程
  活跃对话数:    80 × 20% = 16 个
  匹配度:        16 线程 ↔ 16 活跃对话 ✓
```

### 7.3 参数调优建议

#### thread_pool_size

| 场景 | 推荐值 | 说明 |
|------|--------|------|
| 默认（不配置） | `min(32, cpu×8)` | 2核=16, 4核=32 |
| EdgeTTS（免费） | 默认值 | EdgeTTS 无并发限制 |
| DoubaoTTS（付费） | `min(32, cpu×8)` | 注意 TTS 并发授权数 |
| 本地 ASR（FunASR） | `cpu×4` | 本地推理消耗 CPU，不宜过大 |

> 当使用本地 ASR/TTS（如 FunASR、PaddleSpeech）时，I/O 变为 CPU 密集型，线程数不宜过大，建议手动设置为 `cpu×4` 或更低。

#### max_connections

| 容器规格 | 推荐 max_connections | SAE 目标值 | 依据 |
|---------|---------------------|-----------|------|
| 1c2g | 40 | 32 | 内存 2048MB / 15MB ≈ 136，保守取 40 |
| **2c4g** | **80** | **64** | 内存 4096MB / 15MB ≈ 273，保守取 80 |
| 4c8g | 150 | 120 | 内存 8192MB / 15MB ≈ 546，保守取 150 |

#### max_connections_warning_ratio

| 场景 | 推荐值 | 说明 |
|------|--------|------|
| 默认 | 0.8 | 80% 时告警，留 20% 缓冲 |
| 激进扩容 | 0.7 | 更早触发扩容，适合对延迟敏感的场景 |
| 保守扩容 | 0.9 | 更晚触发扩容，适合实例启动慢的场景 |

### 7.4 调优检查清单

- [ ] `max_connections × warning_ratio` = SAE 弹性目标值
- [ ] `max_connections × 活跃率(20%)` ≤ `thread_pool_size`
- [ ] `max_connections × 15MB` ≤ `可用内存 × 0.6`（留 40% 余量）
- [ ] SAE 最小实例数 ≥ 2（高可用）
- [ ] SAE 缩容稳定窗口 ≥ 300 秒（避免抖动）
- [ ] TTS 服务的并发授权数 ≥ `thread_pool_size`（使用付费 TTS 时）

---

## 8. Code Review 修复记录

代码实现后经过 Code Review，发现并修复了以下 4 个问题：

| # | 严重度 | 问题 | 修复方案 |
|---|--------|------|---------|
| 1 | HIGH | `_get_thread_pool_size()` 缺少输入范围校验，极端配置值可能导致线程池异常 | 添加 `if 1 <= size <= 128` 检查，非法值回退默认公式 |
| 2 | MEDIUM | `except Exception: pass` 静默吞掉异常，配置读取失败无任何感知 | 改为 `except Exception as e` + `warning` 日志记录 |
| 3 | MEDIUM | `max_connections` 和 `warning_ratio` 无边界校验，负数或超大值导致异常 | `max(1, ...)` 和 `min(1.0, max(0.0, ...))` 约束 |
| 4 | LOW | `warning_threshold` 每次新连接时重复计算 | 移到 `__init__` 中预计算为 `self._warning_threshold` |

---

## 9. 运维监控要点

### 9.1 关键日志

```
# 正常连接
[INFO] 新连接接入，当前连接数: 5/80

# 告警阈值
[WARNING] 连接数接近上限: 65/80

# 拒绝连接
[WARNING] 连接数已达上限(80/80)，拒绝新连接

# 线程池初始化
[INFO] 全局线程池已初始化: max_workers=16

# 配置异常
[WARNING] 读取线程池配置失败，使用默认值: ...

# 连接断开
[DEBUG] 连接断开，当前连接数: 79/80
```

### 9.2 监控告警建议

| 指标 | 阈值 | 告警级别 | 说明 |
|------|------|---------|------|
| 拒绝连接次数 | > 0/min | P1 | 实例已达上限，需立即扩容 |
| 连接数接近上限告警频率 | > 5次/h | P2 | 接近容量，预扩容 |
| 线程池配置读取失败 | 出现即告警 | P3 | 配置文件异常 |
| 单实例 TCP 活跃连接数 | > 64（2c4g） | P2 | 触发弹性扩容 |
| Mem 使用率 | > 85% | P1 | 内存不足，可能 OOM |

### 9.3 常见问题排查

**Q: 连接被拒绝（close code 1013）怎么办？**

A: 说明实例连接数已达 `max_connections` 上限。检查：
1. SAE 弹性伸缩是否正常触发（查看扩容日志）
2. `max_connections` 配置是否合理（参考容量规划）
3. 是否有实例异常导致流量集中（检查实例健康状态）

**Q: CPU 使用率很低但连接数很高，正常吗？**

A: 正常。I/O 密集型服务的大部分时间在等待外部 API（ASR/LLM/TTS）响应，CPU 使用率不代表实际负载。应关注 TCP 活跃连接数而非 CPU。

**Q: 如何判断线程池大小是否足够？**

A: 观察线程池队列堆积情况。如果活跃对话数接近 `thread_pool_size`，且响应延迟增大，说明线程池可能不足。可通过 `thread_pool_size` 配置项调大（但不超过 128）。

**Q: 本地部署（非 SAE）需要配置 max_connections 吗？**

A: 建议配置。即使不使用 SAE 弹性伸缩，`max_connections` 也能防止单实例连接数过多导致 OOM。根据服务器内存设置合理上限即可。

---

## 附录：完整配置示例

### 2核4G SAE 生产配置

**config.yaml:**
```yaml
server:
  ip: 0.0.0.0
  port: 8000
  # 线程池使用默认公式: min(32, 2×8) = 16
  # thread_pool_size: 16    # 可不配置，使用默认值
  max_connections: 80
  # max_connections_warning_ratio: 0.8    # 可不配置，使用默认值
```

**SAE 弹性策略:**
```yaml
策略类型: 自定义指标
监控指标: TCP活跃连接数
目标值: 64
最小实例数: 2
最大实例数: 10
扩容冷却: 60s
缩容冷却: 300s
```

**SAE 实例规格:**
```yaml
CPU: 2 vCPU
内存: 4 GB
最小实例: 2
最大实例: 10
```
