---
kind: error_handling
name: 错误处理体系：Java统一异常与Python进程级容错
category: error_handling
scope:
    - '**'
source_files:
    - main/manager-api/src/main/java/xiaozhi/common/exception/RenException.java
    - main/manager-api/src/main/java/xiaozhi/common/exception/RenExceptionHandler.java
    - main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java
    - main/xiaozhi-server/app.py
    - main/xiaozhi-server/config/manage_api_client.py
    - main/xiaozhi-server/core/api/ota_handler.py
---

本仓库包含两个独立的后端服务，各自采用不同的错误处理策略：

**1. Java管理后端（manager-api）——基于Spring的集中式异常处理**
- 自定义异常基类 `RenException`：携带5位数字错误码（前2位模块码+后3位业务码）和国际化消息，支持构造参数插值
- 全局异常处理器 `RenExceptionHandler`：通过 `@RestControllerAdvice` 统一捕获 `RenException`、`DuplicateKeyException`、`UnauthorizedException`、`MethodArgumentNotValidException`、`NoResourceFoundException` 等，并统一返回 `Result` 包装体
- 错误码枚举 `ErrorCode`：集中定义所有业务错误码（如 `DB_RECORD_EXISTS=10002`、`DEVICE_NOT_FOUND=10194`、`PAY_ORDER_DUPLICATE=10303` 等），覆盖设备、支付、订阅、知识库、声纹、语音克隆等全部模块
- 校验失败时提取第一个非空验证消息作为用户提示

**2. Python语音服务器（xiaozhi-server）——进程级容错 + 局部try/except**
- 启动入口 `app.py`：使用 `asyncio` 事件循环，通过信号监听（SIGINT/SIGTERM）和 `KeyboardInterrupt` 优雅关闭，在 `finally` 块中取消任务、等待超时清理资源
- 配置加载器 `config_loader.py`：对配置文件缺失、API调用失败等场景直接 `raise Exception`，由上层捕获
- HTTP客户端 `manage_api_client.py`：定义领域异常 `DeviceNotFoundException`、`DeviceBindException`；对网络异常（连接超时、HTTP 408/429/5xx）实现指数退避重试机制；对manager-api返回的业务错误码（10041/10042）映射为特定异常
- 各Handler（如 `ota_handler.py`）广泛使用 `try/except Exception` 包裹外部调用，失败时记录日志并返回降级结果
- 无统一的错误类型或中间件，错误通过异常冒泡 + 日志记录传播

**3. 数字人模块（digital-human）**
- 使用标准Python异常（`ValueError`、`RuntimeError`、`ImportError`）进行配置校验和运行时错误处理
- 队列操作捕获 `queue.Full`/`queue.Empty` 避免阻塞

**关键约束**
- Java端所有业务异常必须继承 `RenException` 并通过 `ErrorCode` 指定错误码
- Python端对外部依赖调用必须包裹 `try/except`，禁止异常未捕获导致进程崩溃
- WebSocket/HTTP请求失败需区分网络异常（可重试）与业务异常（不可重试）