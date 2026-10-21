# Sandcastle 使用指南

> 来源：https://github.com/mattpocock/sandcastle （Matt Pocock / AI Hero，MIT）
> 用途：在隔离沙箱中编排 AI coding agent（Claude Code / Codex / Pi / Cursor / OpenCode / Copilot），支持并行 AFK 任务、review 流水线、结构化输出。

## 1. 概述

Sandcastle 是 TypeScript 库 + CLI，核心能力：

1. `sandcastle.run()` 调用 agent
2. 按分支策略在沙箱（Docker/Podman/Vercel microVM）中隔离执行
3. agent 的提交自动合并回当前仓库

**前置条件：**
- Git 仓库（隔离靠 git worktree / 分支）
- 沙箱 provider 之一：Docker Desktop / Podman / Vercel，或自定义
- Node.js（host 侧跑编排脚本）

## 2. 安装

两种方式，按用法选一：

| 用法 | 命令 |
|---|---|
| 只用 CLI / npx 跑 workflow | 无需安装，`npx` 每次拉临时副本 |
| 写 TypeScript 脚本调库 API | `npm install --save-dev @ai-hero/sandcastle` |

推荐（要持久用）：

```bash
npm install --save-dev @ai-hero/sandcastle
```

零安装（一次性）：

```bash
npx @ai-hero/sandcastle init
```

## 3. 初始化配置

每个 git 仓库执行一次：

```bash
npx @ai-hero/sandcastle init
cp .sandcastle/.env.example .sandcastle/.env
```

`init` 交互式提问：sandbox provider（docker/podman）、agent、model、模板、issue tracker。每项都有对应 flag，可无交互：

```bash
npx @ai-hero/sandcastle init \
  --sandbox docker \
  --agent claude-code \
  --model claude-opus-4-8 \
  --template simple-loop \
  --issue-tracker github-issues
```

常用 flags：

| Flag | 说明 | 默认 |
|---|---|---|
| `--image-name` | 镜像名 | `sandcastle:<repo-dir-name>` |
| `--agent` | agent provider | 交互选择 |
| `--model` | 模型 | 交互选择 |
| `--sandbox` | `docker` \| `podman` | 交互选择 |
| `--template` | 工作流模板 | 交互选择 |
| `--issue-tracker` | `github-issues` \| `beads` \| `custom` | 交互选择 |
| `--build-image` | init 后立即构建镜像 | — |
| `--create-label` | 创建 GitHub label | — |

**注意：** `init` 在 `.sandcastle/` 已存在时会报错。

### 3.1 生成的目录结构

```
.sandcastle/
├── Dockerfile      # 沙箱环境，可定制（保留 Node22/git/curl/jq/gh/Claude CLI/agent 用户）
├── main.mts        # agent 入口脚本（ESM 包中为 main.ts）
├── prompt.md       # 给 agent 的指令
├── .env.example    # token 占位
└── .gitignore      # 忽略 .env、logs/、worktrees/
```

**没有 `sandcastle.config.ts`** —— 配置即代码：`run()` 的 options 对象就是配置。

### 3.2 凭证配置（`.sandcastle/.env`）

二选一：

```bash
# 方式 A：OAuth token（推荐）
claude setup-token        # host 上执行，拿到 token
# 写入 .sandcastle/.env:
CLAUDE_CODE_OAUTH_TOKEN=sk-ant-oat01-xxx

# 方式 B：API key
ANTHROPIC_API_KEY=sk-ant-xxx
```

env 变量自动从 `.sandcastle/.env` 和 `process.env` 解析。**不要把 `.env` 提交进 git**（生成器自带 .gitignore 已排除）。

### 3.3 模板

init 时选择：

| 模板 | 用途 |
|---|---|
| `blank` | 空起点 |
| `simple-loop` | 单 agent 循环迭代 |
| `sequential-reviewer` | agent 写完 → reviewer 审查 |
| `parallel-planner` | 多 planner 并行出方案 |
| `parallel-planner-with-review` | 并行 planner + review |

planner 类模板需要 host 装 `zod`：`npm install --save-dev zod`

## 4. 日常使用

### 4.1 最快路径：跑 init 生成的入口

```bash
npx tsx .sandcastle/main.mts
```

### 4.2 库 API：`run()`

```typescript
import { run, claudeCode } from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";

const result = await run({
  agent: claudeCode("claude-opus-4-8"),
  sandbox: docker(),
  promptFile: ".sandcastle/prompt.md",
});

console.log(result.iterations.length); // 迭代次数
console.log(result.commits);           // 产生的提交 [{ sha }]
console.log(result.branch);            // 目标分支名
```

关键 options：

| Option | 说明 | 默认 |
|---|---|---|
| `agent` | agent provider + model | 必填 |
| `sandbox` | 沙箱 provider | 必填 |
| `prompt` / `promptFile` | 二选一 | 必填其一 |
| `promptArgs` | `{{KEY}}` 替换参数 | — |
| `maxIterations` | 循环次数 | 1 |
| `branchStrategy` | 分支策略 | 按 provider 类型默认 |
| `completionSignal` | 完成信号 | `<promise>COMPLETE</promise>` |
| `idleTimeoutSeconds` | 空闲超时 | 600 |
| `completionTimeoutSeconds` | 完成超时 | 60 |
| `hooks` | 生命周期钩子 | — |
| `copyToWorktree` | 拷贝文件到 worktree | — |
| `output` | 结构化输出 schema | — |
| `resumeSession` | 恢复会话 | — |
| `logging` | 日志 | — |

### 4.3 分支策略

| 策略 | 行为 | 默认场景 |
|---|---|---|
| `head` | agent 直接写 host 工作目录，无 worktree | bind-mount provider |
| `merge-to-head` | worktree 临时分支，完成后合并回 HEAD | isolated provider |
| `branch` | 提交落到命名分支，重跑复用 worktree | — |

```typescript
branchStrategy: { type: "branch", branch: "agent/fix-42" }
```

**注意：** `createWorktree()` 只支持 `branch` 和 `merge-to-head`；`head` 是编译期错误。

### 4.4 沙箱 provider

```typescript
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";
import { podman } from "@ai-hero/sandcastle/sandboxes/podman";
import { vercel } from "@ai-hero/sandcastle/sandboxes/vercel";
import { noSandbox } from "@ai-hero/sandcastle/sandboxes/no-sandbox";
```

Docker 常用 options：

```typescript
sandbox: docker({
  imageName: "sandcastle:local",
  mounts: [
    { hostPath: "~/.npm", sandboxPath: "/home/agent/.npm", readonly: true },
  ],
  network: "my-network",
  groups: ["docker", 999],
  devices: ["/dev/kvm"],
  cpus: 2,
  env: { DOCKER_SPECIFIC: "value" },
}),
```

类型区分：
- **bind-mount**（docker/podman）：host 目录挂进容器
- **isolated**（vercel）：Firecracker microVM，完全隔离
- **noSandbox**：直接跑在 host（危险，仅调试用）

自定义 provider：实现 `exec` / `close` / `copyFileOut` / `worktreePath` 接口，用 `createBindMountSandboxProvider` 或 `createIsolatedSandboxProvider` 包装。参考实现：`src/sandboxes/docker.ts`、`vercel.ts`、`podman.ts`。

### 4.5 可复用沙箱：`createSandbox()`

同一沙箱里跑多轮 agent + 命令：

```typescript
import { createSandbox, claudeCode } from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";

await using sandbox = await createSandbox({
  branch: "agent/fix-42",
  sandbox: docker(),
  hooks: {
    sandbox: { onSandboxReady: [{ command: "npm install" }] },
  },
});

// 第一轮：实现
await sandbox.run({
  agent: claudeCode("claude-opus-4-8"),
  promptFile: ".sandcastle/implement.md",
  maxIterations: 5,
});

// 跑测试
const tests = await sandbox.exec("npm test");
if (tests.exitCode !== 0) throw new Error(`Tests failed:\n${tests.stdout}`);

// 第二轮：review
await sandbox.run({
  agent: claudeCode("claude-sonnet-4-6"),
  prompt: "Review the changes and fix any issues.",
});
```

`await using` 自动关闭；脏 worktree 保留，干净的删除。

### 4.6 结构化输出

```typescript
import { run, Output, claudeCode } from "@ai-hero/sandcastle";
import { z } from "zod";

const result = await run({
  agent: claudeCode("claude-opus-4-8"),
  sandbox: docker(),
  prompt: `Analyze the code, and output the result as JSON inside <result> tags.`,
  output: Output.object({
    tag: "result",
    schema: z.object({ summary: z.string(), score: z.number() }),
    maxRetries: 2,  // 重试恢复同一 agent 会话
  }),
});

console.log(result.output.summary);  // 类型安全
```

约束：
- `maxIterations` 必须为 1
- 任何 Standard Schema 校验器都行（zod/valibot/arktype）
- `Output.string({ tag })` 提取纯文本
- 失败抛 `StructuredOutputError`（带 `sessionId` / `commits` / `branch`）
- 重试只对可恢复 provider 有效：`claudeCode` / `codex` / `pi`

### 4.7 Hooks

```typescript
hooks: {
  host: {
    onWorktreeReady: [{ command: "cp .env.example .env" }],
    onSandboxReady: [{ command: "echo setup done" }],
  },
  sandbox: {
    onSandboxReady: [{ command: "npm install", timeoutMs: 300_000 }],
  },
},
```

执行顺序：`copyToWorktree` → `host.onWorktreeReady`（串行）→ 沙箱启动 → `host.onSandboxReady` + `sandbox.onSandboxReady`（并行）。任一非零退出即失败。沙箱 hook 可加 `sudo: true`。

### 4.8 Prompt 系统

`promptFile` 支持：

- `{{KEY}}` 占位符，用 `promptArgs` 填
- `` !`command` `` 沙箱内执行，stdout 注入 prompt（并行执行，非零退出即失败）
- 内置变量：`{{SOURCE_BRANCH}}` / `{{TARGET_BRANCH}}`

示例 `prompt.md`：

```markdown
# Task
Fix issue {{ISSUE_NUMBER}} on branch {{SOURCE_BRANCH}}.

## Repo state
!`git log --oneline -5`

## Current diff
!`git diff HEAD~1`
```

未用参数告警；缺参数 / 覆盖内置变量报错。

## 5. 镜像管理

改了 `.sandcastle/Dockerfile` 后重建：

```bash
# Docker
npx @ai-hero/sandcastle docker build-image
npx @ai-hero/sandcastle docker remove-image

# Podman
npx @ai-hero/sandcastle podman build-image
npx @ai-hero/sandcastle podman remove-image
```

Linux/macOS 构建自动传 `--build-arg AGENT_UID=$(id -u)` / `AGENT_GID=$(id -g)` 匹配 host UID。

## 6. 常见工作流

### 6.1 并行 AFK 任务

```typescript
import { run, claudeCode } from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";

await Promise.all([
  run({
    agent: claudeCode("claude-opus-4-8"),
    sandbox: docker(),
    branchStrategy: { type: "branch", branch: "agent/task-1" },
    promptFile: ".sandcastle/task-1.md",
  }),
  run({
    agent: claudeCode("claude-opus-4-8"),
    sandbox: docker(),
    branchStrategy: { type: "branch", branch: "agent/task-2" },
    promptFile: ".sandcastle/task-2.md",
  }),
]);
```

### 6.2 会话恢复 / fork

```typescript
const first = await run({
  agent: claudeCode("claude-opus-4-8"),
  sandbox: docker(),
  prompt: "Start refactoring the auth module.",
});

// 恢复同一会话继续
const second = await run({
  agent: claudeCode("claude-opus-4-8"),
  sandbox: docker(),
  resumeSession: first.sessionId,
  prompt: "Now add tests for what you just wrote.",
});
```

## 7. 排错

| 问题 | 排查 |
|---|---|
| `init` 报错 `.sandcastle/ already exists` | 删掉重来，或手动改文件 |
| 镜像构建失败 | 检查 Docker/Podman 是否在跑；保留 Dockerfile 里 Node22/git/gh/Claude CLI/agent 用户 |
| agent 不调工具 | 确认 `.sandcastle/.env` 里 token 有效；`claude setup-token` 重新生成 |
| 提交没合并回来 | 检查 `branchStrategy`；isolated provider 默认 `merge-to-head` 才有自动合并 |
| 结构化输出反复失败 | `maxRetries` 只对 `claudeCode`/`codex`/`pi` 有效；确认 schema 描述清晰 |

## 8. 与本仓（xiaozhi-esp32-server）结合建议

- 沙箱 Dockerfile 需装 Python 3.10 / JDK 21 / Node（按子项目）
- 把 `main/xiaozhi-server/data/.config.yaml` 通过 `copyToWorktree` 或 `host.onWorktreeReady` hook 拷进 worktree
- 禁止 agent 碰生产密钥：`.sandcastle/.env` 只放 agent 自己的 token，业务密钥留 host
