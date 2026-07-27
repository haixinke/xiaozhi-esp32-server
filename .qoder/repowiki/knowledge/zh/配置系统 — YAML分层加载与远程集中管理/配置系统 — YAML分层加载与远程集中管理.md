---
kind: configuration_system
name: 配置系统 — YAML分层加载与远程集中管理
category: configuration_system
scope:
    - '**'
source_files:
    - main/xiaozhi-server/config/config_loader.py
    - main/xiaozhi-server/config/settings.py
    - main/xiaozhi-server/config.yaml
    - main/xiaozhi-server/config_from_api.yaml
    - main/xiaozhi-server/app.py
---

## 1. 系统/框架概述
小智 ESP32 语音交互系统的配置系统基于 Python + YAML，采用「默认配置 + 用户覆盖 + 可选远程集中管理」的分层加载模式。核心由 `config/config_loader.py`、`config/settings.py`、`config.yaml`、`config_from_api.yaml` 等文件组成，并通过 `app.py` 在应用启动时统一加载。

## 2. 关键文件与包
- `main/xiaozhi-server/app.py`：应用入口，调用 `load_config()` 完成配置加载，并初始化 WebSocket/HTTP 服务。
- `main/xiaozhi-server/config/config_loader.py`：配置加载核心逻辑，包括 YAML 读取、合并、目录创建、从 manager-api 拉取配置等。
- `main/xiaozhi-server/config/settings.py`：配置文件存在性检查与“从 API 读取”模式的校验提示。
- `main/xiaozhi-server/config/manage_api_client.py`：与 Java 管理端（manager-api）通信的客户端封装，用于获取服务器配置、设备私有配置、热词等。
- `main/xiaozhi-server/config.yaml`：默认配置模板，包含 server、log、ASR/TTS/LLM/VAD/Memory/插件等全部模块的示例配置。
- `main/xiaozhi-server/config_from_api.yaml`：轻量模板，仅含 manager-api 连接信息，复制为 `data/.config.yaml` 后启用远程配置模式。
- `main/xiaozhi-server/data/.config.yaml`：用户自定义覆盖配置（通过 gitignore 保护），优先级高于 `config.yaml`。

## 3. 架构与设计决策
- **分层覆盖**：
  - 第一层：`config.yaml` 提供完整默认值。
  - 第二层：`data/.config.yaml` 只写需要覆盖的字段，使用递归 `merge_configs` 合并。
  - 第三层（可选）：当 `data/.config.yaml` 中配置了 `manager-api.url` 时，优先从 Java 管理端通过 HTTP API 拉取完整配置，本地仅保留 `server.*` 中的 ip/port/http_port/vision_explain/auth_key 以及 prompt_template 等少数本地项。
- **运行时缓存**：加载后的配置会写入全局 cache_manager（`CacheType.CONFIG`），避免重复 IO。
- **目录自动创建**：`ensure_directories` 会根据 log_dir、各模块 output_dir、selected_module 对应的模型目录自动创建所需路径，失败时打印警告而非中断。
- **安全与密钥**：
  - `auth_key` 优先级：配置文件 `server.auth_key` > `manager-api.secret` > 随机生成 UUID。
  - 敏感字段（如 api_key、secret）以占位符形式出现在默认配置中，要求用户自行替换。
- **多模块配置结构**：所有能力模块（ASR、TTS、LLM、VAD、Memory、Intent、plugins 等）均以「类型名 → 具体实现配置」的字典形式组织，通过 `selected_module` 选择当前启用的实现。

## 4. 约定与约束
- **配置文件位置与命名**：
  - 默认配置必须存在于 `config.yaml`。
  - 用户覆盖配置必须放在 `data/.config.yaml`，且该文件不存在时会抛出 `FileNotFoundError` 强制引导用户创建。
- **远程配置开关**：
  - 仅在 `data/.config.yaml` 中配置了 `manager-api.url` 时才启用远程配置模式；否则走本地 YAML 合并流程。
  - 若同时存在本地 `selected_module` 与远程配置，`settings.check_config_file` 会报错并提示将 `config_from_api.yaml` 复制到 `data/.config.yaml`。
- **目录权限**：`ensure_directories` 遇到无权限创建目录时仅打印警告，不阻断启动，但可能导致后续模块运行异常。
- **配置一致性**：`read_config_from_api` 为 True 时，禁止在本地配置中再定义 `selected_module`，以避免本地与远程配置冲突。
- **日志与数据路径**：`log_dir`、`data_dir` 及各类 `output_dir` 均支持相对路径，最终会被解析为项目根目录下的绝对路径。

## 5. 与其他子项目的关系
- Manager API（Java）作为集中式配置源，xiaozhi-server 通过 `manage_api_client` 异步拉取服务器级配置与设备私有配置（如 agent models、correct_words）。
- 前端（manager-web、manager-mobile、miniprogram）通过 REST API 修改这些配置，形成「前端编辑 → 后端存储 → 服务端运行时拉取」的闭环。

## 6. 扩展点
- 新增模块（如新的 ASR/TTS/LLM 提供商）只需在 `config.yaml` 中添加对应配置块，并在代码中按 `type` 字段注册适配器即可。
- 可通过实现 `manage_api_client` 中的接口来接入其他配置中心或远端配置服务。
