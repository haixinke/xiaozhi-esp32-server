# 文档目录结构

本文档目录按照功能和主题进行分类，便于快速查找相关资料。

## 📂 目录分类

### `powermem/` - PowerMem 记忆系统
PowerMem 记忆系统的核心文档，包括算法原理、问题分析、元数据说明等。

**核心文档**：
- `PowerMem-Issues.md` - PowerMem 集成问题清单（必读）
- `PowerMem-Ebbinghaus-Algorithm.md` - 艾宾浩斯遗忘曲线算法详解
- `PowerMem-Metadata-Fields.md` - 元数据字段说明
- `PowerMem-Metadata-Write-Timing.md` - 元数据写入时机
- `PowerMem-Plugin-vs-Infer.md` - 智能插件与智能添加模式对比

**深入分析**：
- `PowerMem-1.1.0-Analysis.md` - PowerMem SDK 1.1.0 分析
- `PowerMemory-Intelligent-Processing-Flow.md` - 智能处理流程
- `PowerMem-SQL生成原理详解.md` - SQL 生成原理
- `PowerMem-记忆处理原理详解.md` - 记忆处理原理

### `device/` - 设备集成
ESP32 设备的接入、认证、工作流程等相关文档。

- `device-onboarding-flow.md` - 设备接入流程
- `device-workflow.md` - 设备工作流程
- `device-integration-guide.md` - 设备集成指南
- `device-api-reference.md` - 设备 API 参考

### `websocket/` - WebSocket 和音频流
WebSocket 通信、音频处理流程等相关文档。

- `websocket_audio_flow.md` - WebSocket 音频流
- `websocket-address-from-config.md` - WebSocket 地址配置
- `ASR处理流程详解.md` - ASR（语音识别）处理流程

### `llm/` - LLM 和工具调用
大语言模型调用、工具函数注册、提示词架构等相关文档。

- `llm-tool-calling-flow.md` - LLM 工具调用流程
- `llm_prompt_architecture.md` - LLM 提示词架构
- `工具函数注册机制详解.md` - 工具函数注册机制

### `guides/` - 用户指南
面向新用户的入门指南和配置说明。

- `python_beginner_guide.md` - Python 初学者指南
- `oceanbase-connection-guide.md` - OceanBase 连接指南
- `auto-goodbye-mechanism.md` - 自动告别机制
- `DELETE-MONITOR-GUIDE.md` - 删除监控指南

### `architecture/` - 架构设计
系统架构设计文档。

- `Memory_System_Architecture_Blueprint.md` - 记忆系统架构蓝图
- `memory_system_design.md` - 记忆系统设计

### `issues/` - 问题追踪
重要问题的详细分析和解决记录。

- `001-critical-memories-data-loss.md` - 严重记忆数据丢失问题
- `002-memory-loss-root-cause-20260506.md` - 记忆丢失根因分析（2026-05-06）

### `refactor/` - 重构相关
代码重构的设计文档和记录。

- `global-tool-manager.md` - 全局工具管理器

## 🔍 快速查找指南

### 我想了解...

**如何解决记忆被自动删除的问题？**
→ `powermem/PowerMem-Issues.md` → 查看"问题 5"

**艾宾浩斯遗忘曲线如何工作？**
→ `powermem/PowerMem-Ebbinghaus-Algorithm.md`

**如何配置 PowerMem？**
→ `powermem/PowerMem-Plugin-vs-Infer.md` → 查看配置示例

**设备如何接入服务器？**
→ `device/device-onboarding-flow.md`

**LLM 工具调用是如何实现的？**
→ `llm/llm-tool-calling-flow.md`

**如何配置 OceanBase 数据库？**
→ `guides/oceanbase-connection-guide.md`

**系统架构是怎样的？**
→ `architecture/Memory_System_Architecture_Blueprint.md`

## 📝 文档命名规范

- 英文文档使用 `kebab-case` 命名（如 `device-workflow.md`）
- 中文文档使用中文命名（如 `ASR处理流程详解.md`）
- PowerMem 相关文档统一以 `PowerMem-` 或 `PowerMemory-` 开头
- 问题追踪文档以 `数字-` 开头（如 `001-*.md`）

## 🔄 文档更新

2026-05-07：完成文档分类整理，新增 5 个分类目录。
