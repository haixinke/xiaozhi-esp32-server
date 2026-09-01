# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

本项目是 **xiaozhi-esp32-server**，为 [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32) 开源智能硬件项目提供的后端服务。它为 ESP32 设备提供实时语音 AI 助手服务器，由 `main/` 下的七个子项目组成。

## 子项目

在本项目中，七个子项目有约定的简称：
- **聊天服务** → `main/xiaozhi-server/`
- **后端服务** → `main/manager-api/`
- **智控台** → `main/manager-web/`
- **移动服务** → `main/manager-mobile/`
- **数字人项目** → `main/digital-human/`
- **蛋宝宝小程序** → `main/egg-miniprogram/`
- **蛋宝宝UI静态项目** → `main/eggbabe-miniprogram/`

| 子项目 | 语言 / 技术栈 | 端口 | 用途 |
|---|---|---|---|
| `main/xiaozhi-server/` | Python 3.10 | 8000 (WS), 8003 (HTTP) | AI 核心：语音流水线 (ASR → LLM → TTS)，WebSocket 设备连接 |
| `main/manager-api/` | Java 21 / Spring Boot 3.4.3 | 8002 (`/xiaozhi`) | 管理后台 REST API，设备注册，Python 服务端的配置来源 |
| `main/manager-web/` | Vue.js 2 / Vue CLI | 8001 (dev) | Web 管理控制台 ("智控台") |
| `main/manager-mobile/` | Uni-app / Vue 3 / Vite | — | 移动端管理后台 (H5、微信小程序、iOS、Android) |
| `main/digital-human/` | HTML / CSS / JS / Python |  | 数字人项目：模拟 ESP32 终端设备，用于测试和演示语音交互功能 |
| `main/egg-miniprogram/` | 微信小程序 (WXML/WXSS/JS) | — | "蛋宝宝"微信小程序：孵化类AI宠物 |
| `main/eggbabe-miniprogram/` | 微信小程序 (WXML/WXSS/JS) | — | "蛋宝宝"微信小程序的UI静态设计项目，非实际运行 |

每个子项目都有自己的 `CLAUDE.md`，包含详细的架构说明和常用命令。

## 官方文档目录

`main/official-docs/` 用于存放项目的官方文档（如软著、专利等材料）。

## 高层架构

```
┌──────────────┐     WebSocket      ┌─────────────────┐     HTTP      ┌─────────────────┐
│ ESP32 设备    │◄──────────────────►│ xiaozhi-server  │◄───────────►│ LLM / TTS / ASR │
│              │      端口 8000      │ (Python AI)     │   API       │   服务商         │
└──────────────┘                    └────────┬────────┘             └─────────────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  manager-api    │◄──── REST ────┐
                                    │  (Java Spring)  │               │
                                    │  端口 8002       │◄── Oceanbase + Redis
                                    └────────┬────────┘               │
                                             │                        │
                                   ┌─────────┴──────────┐             │
                                   │                    │             │
                             ┌─────▼─────┐      ┌──────▼──────┐      │
                             │manager-web│      │manager-     │      │
                             │(Vue.js)   │      │mobile      │      │
                             │端口 8001   │      │(Uni-app)   │      │
                             └───────────┘      └─────────────┘      │
                                                                      │
                                                          ┌───────────▼──────────┐
                                                          │  mqtt-gateway (可选) │
                                                          │  MQTT + UDP 桥接     │
                                                          └──────────────────────┘
```

### 数据流 (语音交互)

ESP32 设备 → WebSocket → `receiveAudioHandle` → ASR → 意图识别 → LLM → TTS → `sendAudioHandle` → ESP32 设备

Python 服务端对所有 AI 流水线组件 (ASR、TTS、LLM、VAD、意图识别、记忆) 采用 **Provider 模式**。Provider 位于 `core/providers/`，通过 `core/utils/` 中的工厂函数实例化。

### 配置加载流程

`xiaozhi-server` 从三层配置读取 (后层覆盖前层)：
1. `config.yaml` (已提交的默认配置)
2. `data/.config.yaml` (本地密钥和覆盖配置，已加入 gitignore)
3. 远程 `manager-api` 配置 (如果在 `data/.config.yaml` 中设置了 `manager-api.url`)

Java API 向 Python 服务端暴露运行时配置，使得管理控制台无需重启即可调整 AI 参数。

## 关键文件

| 文件 | 用途 |
|---|---|
| `main/xiaozhi-server/config.yaml` | 服务端基础配置 (已提交) |
| `main/xiaozhi-server/data/.config.yaml` | 本地覆盖配置和密钥 (gitignore，启动时必须存在) |
| `main/manager-api/src/main/resources/application-dev.yml` | Java 开发环境配置 (Oceanbase（兼容MySQL） / Redis) |
| `main/manager-web/vue.config.js` | Vue 构建配置，代理 `/xiaozhi` 到 `localhost:8002` |
| `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase 迁移日志 |

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

**所有新增代码必须添加注释。**

- 类、接口、枚举：添加 Javadoc 说明其职责与业务语义。
- 实体字段：沿用项目风格添加 `@Schema(description = "...")`。
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

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Agent skills

### Issue tracker

Issues 跟踪在 GitHub Issues（origin: haixinke/xiaozhi-esp32-server），用 gh CLI 操作。See `docs/agents/issue-tracker.md`.

### Triage labels

默认五角色同名标签：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。See `docs/agents/triage-labels.md`.

### Domain docs

单上下文布局：根 `CONTEXT.md` + `docs/adr/`（按需懒创建）。See `docs/agents/domain.md`.

