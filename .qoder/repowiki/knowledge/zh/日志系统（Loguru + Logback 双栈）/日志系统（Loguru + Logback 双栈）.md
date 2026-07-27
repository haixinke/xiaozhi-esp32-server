---
kind: logging_system
name: 日志系统（Loguru + Logback 双栈）
category: logging_system
scope:
    - '**'
source_files:
    - main/xiaozhi-server/config/logger.py
    - main/xiaozhi-server/app.py
    - main/xiaozhi-server/core/connection.py
    - main/xiaozhi-server/config.yaml
    - main/manager-api/src/main/resources/logback-spring.xml
    - main/digital-human/wakeword_runtime/config/logging_setup.py
---

本仓库采用「Python 侧 Loguru + Java 侧 Logback」的双栈日志体系，分别服务于小智语音服务器（xiaozhi-server）与管理 API（manager-api），并在数字人唤醒词运行时中复用 Python 标准 logging。

## 1. 使用的框架与工具
- xiaozhi-server（Python）：基于 loguru 实现统一日志初始化、格式化与多输出端；通过 config.yaml 的 log.* 节点动态配置级别、格式、文件路径等。
- manager-api（Java/Spring Boot）：基于 logback-spring.xml 定义控制台、全量日志文件、错误日志文件三个 Appender，并通过 dev/test/prod Profile 切换输出策略。
- digital-human/wakeword_runtime：使用 Python 标准库 logging.getLogger(__name__)，由 wakeword_runtime/config/logging_setup.py 中的 setup_logging() 集中初始化。

## 2. 核心文件与位置
- main/xiaozhi-server/config/logger.py：Loguru 初始化入口，提供 setup_logging()、build_module_string()、create_connection_logger()。
- main/xiaozhi-server/app.py：进程启动时调用 setup_logging() 完成全局日志器注册。
- main/xiaozhi-server/core/connection.py：每个 WebSocket 连接通过 create_connection_logger(selected_module_str) 绑定独立模块字符串，形成按连接隔离的日志上下文。
- main/xiaozhi-server/config.yaml：log 段定义 log_format、log_format_file、log_level、log_dir、log_file、data_dir、selected_module 等。
- main/manager-api/src/main/resources/logback-spring.xml：Logback 配置，定义 CONSOLE / FILE / ERROR_FILE 三个 Appender 及环境 Profile。
- main/digital-human/wakeword_runtime/config/logging_setup.py：标准 logging 的集中初始化。

## 3. 架构与约定
- 统一的初始化入口：Python 侧所有模块通过 from config.logger import setup_logging 获取已配置的 loguru logger，避免重复配置；Java 侧通过 Spring 自动加载 logback-spring.xml。
- 结构化字段与上下文：Loguru 通过 extra 注入 tag（模块名）、selected_module（VAD/ASR/LLM/TTS/Memory/Intent/VLLM 缩写串），在 formatter 中补齐默认值并透传到 {selected_module} 占位符；连接级日志器通过 logger.bind(selected_module=...) 为每次会话绑定不同模块组合。
- 多输出端与轮转策略：
  - Python：同时写入 sys.stdout 与文件（可配置关闭），文件按 10MB 轮转、保留 30 天、异步安全（enqueue=True）。
  - Java：控制台 + 全量日志 manager-api.log（10MB/天，最多 30 份，总上限 2GB）+ 仅错误 error.log（10MB/天，最多 30 份，总上限 1GB）。
- 容器化适配：Python 侧可通过 log_to_file: false 仅输出 stdout 由运行时采集；Java 生产 Profile 仅启用 CONSOLE，由 SAE 日志采集。

## 4. 约定与约束
- 日志级别：Python 默认 INFO，可在 config.yaml 的 log.log_level 覆盖；Java dev/test 打印 xiaozhi DEBUG/INFO，prod 仅 INFO 且 Spring Web 仅 ERROR。
- 模块标识：通过 selected_module 拼接 VAD/ASR/LLM/TTS/Memory/Intent/VLLM 的后缀两字符缩写，便于在海量日志中快速定位当前链路使用的模型组合。
- 标签约定：所有业务日志通过 logger.bind(tag=TAG).info(...) 形式记录，TAG 通常为 __name__，用于区分来源模块。
- 线程安全：Python 文件输出开启 enqueue=True 与 backtrace=True，确保高并发下日志不丢失且异常堆栈完整。
- 目录规范：Python 日志默认输出到 tmp/server.log，数据文件到 data/；Java 日志统一输出到项目根 logs/ 目录下。