# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 工作原则

### ⚠️ 代码修改前必须先征求用户同意

**铁律**：在进行任何代码修改之前，**必须先提出方案并等待用户选择**，不得直接修改代码。

**适用场景**：
- 修改配置文件（config.yaml、.config.yaml 等）
- 修改核心代码文件
- 修改数据库相关操作
- 任何可能影响系统行为的更改

**正确做法**：
1. 分析问题，提出多个解决方案
2. 说明每个方案的优缺点和影响范围
3. 使用 AskUserQuestion 工具或文本方式让用户选择
4. **等待用户明确确认后**再执行修改

**错误做法**：
- ❌ 直接使用 Edit/Write 工具修改文件
- ❌ 先修改再告知用户
- ❌ 假设用户会接受某个方案

### 例外情况

只有在以下情况下可以跳过征求用户意见：
- 用户明确要求"直接修复"、"不用问我"
- 修复明显的拼写错误或注释
- 紧急安全漏洞修复（仍需在修复后说明）

## 项目概述

本项目是 **xiaozhi-esp32-server**，一个面向 ESP32 智能设备的 Python 语音助手后端服务器。它对外提供 WebSocket 端点用于实时音频对话，以及 HTTP 端点用于 OTA 固件升级和视觉分析。

## 产品定位

### AI 虚拟宠物 · 情感陪伴

**核心功能**：提供 AI 驱动的虚拟宠物，为用户提供情感陪伴和个性化互动体验。

**产品特点**：

- 🐾 **虚拟宠物人格**：AI 宠物拥有独特的性格，可以与用户进行情感化对话
- 💝 **情感陪伴**：理解用户情绪状态，提供安慰、鼓励、倾听等情感支持
- 🎭 **个性化互动**：根据用户画像调整对话风格和回复策略
- 🧠 **记忆系统**：使用 PowerMem 记忆用户的偏好、情绪状态、对话历史等
- 📊 **用户画像**：持续学习和更新用户的心理特征、互动偏好、情感需求

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

- 推荐 Python 版本：**3.10**
- `requirements.txt` 中固定了较重的 ML 依赖：`torch==2.2.2`、`funasr==1.2.7`、`sherpa_onnx==1.12.29`、`silero_vad==6.1.0`
- 使用 `loguru` 进行结构化日志记录
- 使用 `websockets` 作为设备通信协议，`aiohttp` 作为 HTTP 服务框架

### 数据库

本地开发环境的 **OceanBase** 通过 **Docker Compose** 运行，用于 PowerMem 记忆模块的向量存储和知识图谱存储（需要 pyobvector 客户端）。

#### 重启

**重启**（⚠️ 重要）：

```bash
# ✅ 正确：仅重启容器，保留数据
docker-compose -f docker-compose-oceanbase.yml restart

# ❌ 错误：不要重复执行 init-powermem.sh，会删除所有存量数据！
```

**手动启动/停止**：
```bash
docker-compose -f docker-compose-oceanbase.yml up -d    # 启动
docker-compose -f docker-compose-oceanbase.yml down     # 停止
```

#### 修改密码

**⚠️ 注意**：`docker-compose-oceanbase.yml` 中的 `OB_ROOT_PASSWORD=123456` 环境变量**不起作用**，必须手动修改：

```bash
# 等待容器启动后（约 60-90 秒），执行：
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@sys -e "SET PASSWORD = '123456';"

# 验证新密码
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@sys -p123456 -e "SELECT 1"
```

密码修改后会持久化存储在 `./oceanbase/data` 目录中。

#### 连接信息

```bash
主机: 127.0.0.1
端口: 2881
用户: root@sys
密码: 123456
数据库: powermem
```

#### PowerMem

⚠️ **重要**：PowerMem 集成存在已知问题，详见 [PowerMem 集成问题清单](docs/PowerMem-Issues.md)。

关键要点：
- SDK Bug 需要 monkey-patch 修复（已在 `core/providers/memory/powermem/powermem.py` 中实现）
- 配置必须包含 `enabled` 和 `embedding_model_dims` 字段
- 数据库密码必须为字符串类型（`'123456'` 而非 `123456`）

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

## 用户画像设计最佳实践

### 用户画像存储（user_profiles 表）

**推荐方案**：使用 `profile_type="content"` 存储非结构化情感描述

```python
# 存储用户画像
user_memory.add(
    messages=conversation,
    user_id="user123",
    profile_type="content",  # 非结构化存储
    native_language="zh"     # 指定输出语言
)
```
