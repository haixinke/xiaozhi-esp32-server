# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

本项目是 **xiaozhi-esp32-server**，一个面向 ESP32 智能设备的 Python 语音助手后端服务器。它对外提供 WebSocket 端点用于实时音频对话，以及 HTTP 端点用于 OTA 固件升级和视觉分析。

## 常用命令

- **运行服务器**：`python app.py`
- **安装依赖**：`pip install -r requirements.txt`
- **运行性能基准测试**：`python performance_tester.py`
- **浏览器 WebSocket 测试**：启动服务器后，在浏览器中打开 `test/test_page.html`

本仓库没有 pytest 测试套件或正式的测试运行器。`test/` 目录仅包含前端测试页面。

## 架构

### 双服务模式

`app.py` 启动两个并行的 asyncio 服务：

1. **WebSocketServer** (`core/websocket_server.py`，默认端口 `8000`) —— 处理来自 ESP32 设备的持久 WebSocket 连接。每个连接都会分配一个独立的 `ConnectionHandler` 实例。
2. **SimpleHttpServer** (`core/http_server.py`，默认端口 `8003`) —— 一个 aiohttp 应用，提供：
   - `/xiaozhi/ota/` —— OTA 固件元数据和 `.bin` 下载端点
   - `/mcp/vision/explain` —— 视觉分析端点

### Provider 模式

服务器围绕 Provider 模式构建语音 AI 流水线组件。所有 Provider 位于 `core/providers/` 下，并通过 `core/utils/` 中的工厂函数实例化（`asr.py`、`tts.py`、`llm.py`、`vad.py`、`intent.py`、`memory.py`）。

| 组件 | 基类 | 工厂函数 | 在 `config.yaml` 中的配置键 |
|-----------|------------|---------|-------------------------------|
| ASR | `ASRProviderBase` | `asr.create_instance()` | `selected_module.ASR` |
| TTS | `TTSProviderBase` | `tts.create_instance()` | `selected_module.TTS` |
| LLM | `LLMProviderBase` | `llm.create_instance()` | `selected_module.LLM` |
| VAD | `VADProviderBase` | `vad.create_instance()` | `selected_module.VAD` |
| Intent | `IntentProviderBase` | `intent.create_instance()` | `selected_module.Intent` |
| Memory | `MemoryProviderBase` | `memory.create_instance()` | `selected_module.Memory` |

模块在 `core/utils/modules_initialize.py` 中按需懒加载初始化。

### 连接生命周期

`ConnectionHandler` (`core/connection.py`) 是每台设备的状态机。它负责管理：
- 音频 I/O 队列和线程
- TTS 文本/音频队列及句子切分
- 对话历史 (`core/utils/dialogue.py`)
- 通过 `UnifiedToolHandler` 执行工具/函数调用
- 认证和设备绑定状态

### 消息处理

通过 WebSocket 接收的文本消息由 `TextMessageHandlerRegistry` (`core/handle/textMessageHandlerRegistry.py`) 分发。支持的消息类型包括 `hello`、`listen`、`abort`、`iot`、`mcp`、`server`、`ping`。

音频消息的流转路径为：`receiveAudioHandle.py` → ASR Provider → 意图识别 → LLM Provider → TTS Provider → `sendAudioHandle.py`。

### 工具 / 函数

`plugins_func/` 实现了一个自动发现的函数注册表：
- `plugins_func.loadplugins.auto_import_modules("plugins_func.functions")` 会自动扫描并导入 `plugins_func/functions/` 下的所有模块
- 使用 `@register_function(name, desc, type=ToolType.X)` 装饰器来暴露新能力
- `UnifiedToolHandler` (`core/providers/tools/unified_tool_handler.py`) 负责协调 IoT 工具、MCP 工具、服务端插件以及服务端 MCP 工具

### 配置

配置采用三层机制（后层覆盖前层）：
1. **基础配置**：`config.yaml`（已提交到仓库，包含默认配置和注释说明）
2. **本地覆盖**：`data/.config.yaml`（不受版本控制，用于存放密钥和本地覆盖配置）
3. **智控台 API 覆盖**：如果 `data/.config.yaml` 中包含 `manager-api.url`，启动时会从远程 API 获取配置

配置加载器在 `config/config_loader.py` 中递归合并配置，并缓存结果。

**重要**：
- 应用启动时必须存在 `data/.config.yaml` 文件
- 如果你只想使用 `config.yaml` 的默认配置，可创建一个空文件 `data/.config.yaml`
- 密钥、API Key 等敏感信息应配置在 `data/.config.yaml` 中，不要提交到版本控制
- `data/.config.yaml` 已加入 `.gitignore`，不会被 git 跟踪

### 关键目录

- `core/` —— 服务器逻辑、Provider、消息处理器、工具类
- `config/` —— 配置加载、日志设置、智控台 API 客户端
- `plugins_func/functions/` —— 自动发现的工具函数
- `performance_tester/` —— ASR/LLM/TTS 的独立基准测试脚本
- `test/` —— 基于浏览器的 WebSocket 测试页面（仅前端）
- `models/` —— 本地模型文件（例如 `SenseVoiceSmall`）

## 依赖与环境

- 推荐 Python 版本：**3.12**
- `requirements.txt` 中固定了较重的 ML 依赖：`torch==2.2.2`、`funasr==1.2.7`、`sherpa_onnx==1.12.29`、`silero_vad==6.1.0`
- 使用 `loguru` 进行结构化日志记录
- 使用 `websockets` 作为设备通信协议，`aiohttp` 作为 HTTP 服务框架

### 数据库

本地开发环境的 **OceanBase** 通过 **Docker** 容器运行（容器名 `seekdb`），用于 PowerMem 记忆模块的向量存储和知识图谱存储（需要 pyobvector 客户端）。

### PowerMem SDK 源代码

**PowerMem SDK 源代码位置**：`~/codes/github/powermem-1.1.0`

这是 PowerMem v1.1.0 的完整源代码，包含：
- 核心实现（`powermem/core/`）
- 存储、LLM、Embedding 提供商（`powermem/storage/`、`powermem/llm/`、`powermem/embedding/`）
- UserMemory 和 AsyncMemory 类
- 向量存储和知识图谱存储逻辑

**用途**：

- 深入理解 PowerMem 的工作原理
- 调试和排查 SDK 层面的问题
- 查看用户画像提取和记忆存储的具体实现

**关键文件**：

- `powermem/core/async_memory.py` - AsyncMemory 类实现
- `powermem/core/memory.py` - Memory 类实现
- `powermem/storage/oceanbase/oceanbase_graph.py` - OceanBase 图谱存储
- `powermem/storage/oceanbase/oceanbase_vector.py` - OceanBase 向量存储
- `powermem/user_memory/user_memory.py` - UserMemory 类实现（用户画像）
- `powermem/prompts/user_profile_prompts.py` - 用户画像提取提示词
