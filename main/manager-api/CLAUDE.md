# CLAUDE.md

本文档为 Claude Code (claude.ai/code) 提供在本仓库中工作时的参考指引。

## 项目概述

`manager-api` 是 `xiaozhi-esp32-server` 生态系统的 Java Spring Boot 管理后台。它为 `manager-web`（Vue.js 管理控制台，端口 8001）和 `manager-mobile`（uni-app）前端提供 RESTful API，同时也是 `xiaozhi-server`（Python AI 核心，端口 8000）的配置数据来源。本服务运行在 **端口 8002**，上下文路径为 `/xiaozhi`。

## 技术栈

- Java 21，Spring Boot 3.4.3
- Maven 3.8+（无 wrapper，直接使用系统 `mvn`）
- MyBatis-Plus 3.5.5 + MySQL 8.0+
- Apache Shiro 2.0.2（Jakarta classifier）负责认证
- Druid 连接池
- Redis 5.0+（缓存、会话数据）
- Liquibase 管理数据库迁移
- Knife4j 4.6.0 / SpringDoc OpenAPI 生成接口文档

## 常用命令

```bash
# 编译
mvn compile

# 打包（默认跳过测试，由 surefire 插件配置决定）
mvn package

# 运行应用（需要 MySQL、Redis；读取 application-dev.yml）
mvn spring-boot:run

# 运行全部测试（覆盖 skipTests）
mvn test -DskipTests=false

# 运行单个测试类
mvn test -Dtest=DeviceTest -DskipTests=false

# 运行单个测试方法
mvn test -Dtest=DeviceTest#testWriteDeviceInfo -DskipTests=false
```

服务启动后，接口文档地址：`http://localhost:8002/xiaozhi/doc.html`

## 架构

### 包结构

```
xiaozhi
├── AdminApplication.java          # 入口类
├── common                         # 共享基础设施
│   ├── annotation                 # @LogOperation、@DataFilter
│   ├── aspect                     # Redis 缓存切面
│   ├── config                     # MybatisPlus、Swagger、Async、RestTemplate
│   ├── entity/BaseEntity.java     # id、creator、createDate
│   ├── exception/ErrorCode.java   # 集中式 5 位错误码
│   ├── redis/RedisUtils.java      # Redis 操作封装
│   ├── service/{Base,Crud}Service.java
│   ├── utils/Result.java          # 标准接口信封：{code, msg, data}
│   └── xss/                       # XSS 过滤与 SQL 注入过滤
└── modules                        # 业务模块
    ├── agent                      # 智能体、聊天记录、声纹、MCP
    ├── config                     # 向 xiaozhi-server 暴露的运行时配置
    ├── device                     # ESP32 设备注册、OTA
    ├── knowledge                  # RAG 知识库（RAGFlow 适配器）
    ├── llm                        # LLM 服务集成
    ├── model                      # 模型供应商与配置管理
    ├── security                   # Shiro 配置、OAuth2 过滤器、密码工具
    ├── sms                        # 阿里云短信
    ├── sys                        # 用户、参数、字典、服务端管理
    ├── timbre                     # TTS 音色
    └── voiceclone                 # 声音克隆（火山引擎）
```

### 模块内部结构

每个模块通常遵循：
- `controller/` — REST 端点，返回 `Result<T>`
- `service/` — 业务逻辑接口 + 实现
- `dao/` — MyBatis-Plus Mapper（通常继承 `BaseMapper` 的接口）
- `entity/` — 数据库实体，继承 `BaseEntity`
- `dto/` — 请求/传输对象
- `vo/` — 响应视图对象
- `mapper/*.xml` — 自定义 SQL，位于 `src/main/resources/mapper/`

### 认证模型

`ShiroConfig` 中配置了两套独立的认证过滤器：

1. **用户 OAuth2 过滤器**（`oauth2`）— 校验 admin/web 用户的 Bearer Token。默认应用到所有路径（`/**`）。
2. **服务密钥过滤器**（`server`）— 校验预共享的服务密钥，用于 `xiaozhi-server` 的机对机调用。应用到 `/config/**`、`/agent/chat-history/report`、`/agent/chat-summary/**`、`/agent/chat-title/**`。

公开（匿名）端点包括登录、注册、验证码、OTA、doc.html，以及少量播放/下载 URL。

### 数据库与迁移

- **Liquibase** 驱动 schema 变更。主变更日志位于 `src/main/resources/db/changelog/db.changelog-master.yaml`。每个 changeset 引用一个带日期的 SQL 文件。修改表时，**新增 changeset + SQL 文件**；不要编辑已有 changeset。
- **MyBatis-Plus** 配置：`id-type: ASSIGN_ID`，`map-underscore-to-camel-case: true`。实体扫描包：`xiaozhi.modules.*.entity`。Mapper XML 位置：`classpath*:/mapper/**/*.xml`。

### 配置模式

系统参数保存在 `sys_params` 表中，并缓存到 Redis。`SysParamsService` 提供 `getValue(String paramCode, Boolean fromCache)` 和 `getValueObject(String paramCode, Class<T> clazz)` 用于类型化读取。`ConfigService` 将这些参数聚合成结构化配置载荷，供 `xiaozhi-server` 消费。

### 错误码

所有错误码集中在 `xiaozhi.common.exception.ErrorCode`。格式：5 位数字，前 2 位标识模块，后 3 位标识具体错误。错误消息通过 i18n 解析（`i18n/messages*.properties`）。

### 基础模式

- 所有实体继承 `BaseEntity`（字段：`id`、`creator`、`createDate`）。
- 所有接口响应使用 `Result<T>`，`code = 0` 表示成功。
- 校验分组：`AddGroup`、`UpdateGroup`、`DefaultGroup`。
- 实体/DTO/VO 中广泛使用 Lombok `@Data`。

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- ALWAYS read graphify-out/GRAPH_REPORT.md before reading any source files, running grep/glob searches, or answering codebase questions. The graph is your primary map of the codebase.
- IF graphify-out/wiki/index.md EXISTS, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
