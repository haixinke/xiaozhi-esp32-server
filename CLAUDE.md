# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

本项目是 **xiaozhi-esp32-server**，为 [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32) 开源智能硬件项目提供的后端服务。它为 ESP32 设备提供实时语音 AI 助手服务器，由 `main/` 下的七个子项目组成。

## 子项目

在本项目中，七个子项目有约定的简称：
- **聊天服务** → `main/xiaozhi-server/`
- **后端服务** → `main/manager-api/`
- **智控台** → `main/manager-web/`
- **蛋宝宝小程序** → `main/egg-miniprogram/`
- **蛋宝宝UI静态项目** → `main/eggbabe-miniprogram/`

| 子项目 | 语言 / 技术栈 | 端口 | 用途 |
|---|---|---|---|
| `main/xiaozhi-server/` | Python 3.10 | 8000 (WS), 8003 (HTTP) | AI 核心：语音流水线 (ASR → LLM → TTS)，WebSocket 设备连接 |
| `main/manager-api/` | Java 21 / Spring Boot 3.4.3 | 8002 (`/xiaozhi`) | 管理后台 REST API，设备注册，Python 服务端的配置来源 |
| `main/manager-web/` | Vue.js 2 / Vue CLI | 8001 (dev) | Web 管理控制台 ("智控台") |
| `main/egg-miniprogram/` | 微信小程序 (WXML/WXSS/JS) | — | "蛋宝宝"微信小程序：孵化类AI宠物 |
| `main/eggbabe-miniprogram/` | 微信小程序 (WXML/WXSS/JS) | — | "蛋宝宝"微信小程序的UI静态设计项目，非实际运行 |

每个子项目的架构说明和常用命令见其目录下的 `CLAUDE.md`（`xiaozhi-server`、`manager-api`、`manager-web`、`egg-miniprogram` 已有；另见 `main/miniprogram/CLAUDE.md`）。

## 高层架构

```
ESP32 设备 ──WebSocket(实时音频)──► xiaozhi-server (Python AI 核心)
egg-miniprogram (蛋宝宝小程序) ──WebSocket(OTA 取凭证后直连,破壳后语音对话)──► xiaozhi-server
egg-miniprogram ──HTTP REST(领养/孵化/设备绑定)──► manager-api (Java Spring)
manager-api ──HTTP(运行时配置下发)──► xiaozhi-server
xiaozhi-server ──HTTP API──► 外部服务商 (LLM / TTS / ASR)
manager-api ──JDBC──► Oceanbase (业务库)
manager-api ──► Redis (缓存/会话)
xiaozhi-server ──PowerMem SDK──► Oceanbase (记忆: 向量/图谱存储)
```

模块角色：`xiaozhi-server` 语音流水线（ASR→LLM→TTS）；`manager-api` 管理后台 + 配置源；`egg-miniprogram` 客户端，两条链路（领养孵化走 manager-api，语音对话直连 xiaozhi-server）。

### 数据流 (语音交互)

ESP32 设备 → WebSocket → `receiveAudioHandle` → ASR → 意图识别 → LLM → TTS → `sendAudioHandle` → ESP32 设备

Python 服务端对所有 AI 流水线组件 (ASR、TTS、LLM、VAD、意图识别、记忆) 采用 **Provider 模式**。Provider 位于 `core/providers/`，通过 `core/utils/` 中的工厂函数实例化。

### 配置加载流程

`xiaozhi-server` 三层配置（`config.yaml` → `data/.config.yaml` → 远程 `manager-api`）细节见 `main/xiaozhi-server/CLAUDE.md`。Java API 向 Python 服务端暴露运行时配置，使管理控制台无需重启即可调整 AI 参数。

## 关键文件

| 文件 | 用途 |
|---|---|
| `main/xiaozhi-server/config.yaml` | 服务端基础配置 (已提交) |
| `main/xiaozhi-server/data/.config.yaml` | 本地覆盖配置和密钥 (gitignore，启动时必须存在) |
| `main/manager-api/src/main/resources/application-dev.yml` | Java 开发环境配置 (Oceanbase（兼容MySQL） / Redis) |
| `main/manager-web/vue.config.js` | Vue 构建配置，代理 `/xiaozhi` 到 `localhost:8002` |
| `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase 迁移日志 |

## 常用工具

| 工具 | 用途 |
|---|---|
| `main/xiaozhi-server/venv/bin/python scripts/db-query.py "SELECT ..."` | 查开发库（Oceanbase）。密码从 `application-dev.yml` 读取，**禁止**在命令行 inline DB 密码。仅只读语句 (SELECT/SHOW/DESCRIBE/DESC/EXPLAIN)。本机未装 mysql 客户端。 |

## Behavioral Guidelines (All Languages)

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:

- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:

- "Add validation" -> "Write tests for invalid inputs, then make them pass"
- "Fix the bug" -> "Write a test that reproduces it, then make it pass"
- "Refactor X" -> "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```
1. [Step] -> verify: [check]
2. [Step] -> verify: [check]
3. [Step] -> verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

### 5. Comment New Code

**所有新增代码需要添加注释。**

- 类、接口、枚举：添加 Javadoc 说明其职责与业务语义。
- 关键业务规则（概率、状态流转、并发幂等、边界条件等）：在对应代码处添加行内注释说明“为什么”，而非仅复述“做什么”。
- 注释使用简洁中文；不改动既有逻辑，注释不得泄露密钥、token 等敏感信息。

## Security Checklist (All Sub-projects)

Before ANY commit:

- No hardcoded secrets (API keys, passwords, tokens)
- All user inputs validated
- SQL injection prevention (parameterized queries)
- XSS prevention (sanitized HTML output)
- Error messages do not leak sensitive data
- Authentication/authorization verified on protected endpoints

## graphify

本项目在 `graphify-out/` 维护了代码知识图谱（用法由 PreToolUse hook 强制执行）。修改代码后运行 `graphify update .` 保持图谱最新（AST-only，无 API 成本）。

## Agent skills

### Issue tracker

Issues 跟踪在本地 `.scratch/` 下的 markdown 文件（单人维护，不走 GitHub Issues）。See `docs/agents/issue-tracker.md`.

### Triage labels

默认五角色同名标签：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。See `docs/agents/triage-labels.md`.

### Domain docs

单上下文布局：根 `GLOSSARY.md` + `docs/adr/`（按需懒创建）。See `docs/agents/domain.md`.

