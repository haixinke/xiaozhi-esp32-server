# Sandcastle 处理流程详解（`.sandcastle/main.mts`）

本文逐行级说明 `.sandcastle/main.mts` 编排的完整流程：每个阶段跑哪个函数、用哪个提示词文件、是否新开 Claude CLI 进程、中间产物存在哪里、如何在不同进程之间传递。

面向第一次接触本项目 RALPH 流水线的人。读完应能回答：

- 现在跑到哪一步了？
- 这一步是谁在干活（host 脚本 / 容器内 Claude / 容器内普通命令）？
- 上一步产出的东西，这一步从哪里读？
- 失败了会怎样，下次重跑从哪继续？

---

## 1. 一句话总览

`main.mts` 是一个 **host 侧编排脚本**。它按 `.scratch/` 下 markdown issue 的顺序，一次处理一个 issue：

```
装 sandbox → 容器内 Claude 实现 → host 跑测试门禁 → 容器内 Claude 审查（可修一次）
→ host 复验 → 合并到 host → 下一个 issue → 全部完成后对整批做最终审查
→ 有问题就让容器内 Claude 修 → 再审查 → 合并 → 再最终审查（最多 2 轮修复）
```

---

## 2. 角色与进程模型

理解这个流程的关键：**有三类执行体，权限和职责完全不同。**

| 执行体 | 跑在哪 | 由谁启动 | 能改代码吗 | 用途 |
|---|---|---|---|---|
| **host 编排脚本** (`main.mts`，Node/tsx) | 你的 Mac | 你手动 `npx tsx .sandcastle/main.mts` | 只能 `git merge`，不写业务代码 | 调度、门禁、合并、落盘报告 |
| **容器内 Claude CLI** | Docker 容器 `/home/agent/workspace` | `sandbox.run(...)`，每次 **新进程** | 可以，且在独立分支上 | 实现 / 审查 / 修复 |
| **容器内普通命令** | 同上 | `sandbox.exec(cmd)`，每次 **新进程** | 不改代码，只跑测试 | 测试门禁（`mvn`、`npm`、`python3.12`） |

要点：

- **每一次 `sandbox.run()` 都是一次全新的 `claude` CLI 进程**。会话不共享、上下文不共享。上一轮 Claude 说过的话，下一轮 Claude 完全看不到。它只能看到仓库里的文件（代码、issue、spec）和 `promptArgs` 注入的字符串。
- `sandbox.exec()` 不是 Claude，就是普通 shell 命令，跑完拿 `stdout`/`exitCode`。
- 所有"记忆"都靠 **文件**：git 提交、`.scratch/*.md`、`.sandcastle/logs/`。

### 2.1 每个 Claude 进程看到的上下文

`Sandbox.run()` 做的事（按顺序）：

1. 在 host 读 `promptFile`（路径相对于 `process.cwd()`，即仓库根）。**不读容器里的那份。**
2. 用 `promptArgs` 做 `{{KEY}}` 文本替换。
3. 处理提示词里的 shell 表达式（例如 `` !`bash .sandcastle/bin/scratch-issues.sh view ID` ``），在**沙箱内**执行并把输出嵌进提示词。
4. 把最终 prompt 通过 stdin 或 argv 传给容器里的 `claude` CLI（默认带 `--dangerously-skip-permissions`，`--print` 非交互模式）。
5. 流式解析 stdout，匹配 `completionSignal` 提前结束，收集本轮产生的 commit。
6. 会话 JSONL 可选回传到 host（Claude provider 默认开启）。

所以每个提示词文件都必须**自包含**：把需要的路径、SHA、上一步的测试结果都通过 `promptArgs` 传进去。

### 2.2 沙箱长什么样

`createSandbox()`（`main.mts:330`）每次调用做两件事：

1. 在 host 的 `.sandcastle/worktrees/` 下按指定分支创建 **git worktree**。
2. 启动 Docker 容器，把 worktree bind-mount 到 `/home/agent/workspace`。

容器配置：

```ts
sandbox: docker({
  mounts: [
    { hostPath: ~/.m2,  sandboxPath: "/home/agent/.m2",  readonly: false }, // Maven 缓存复用
    { hostPath: ~/.npm, sandboxPath: "/home/agent/.npm", readonly: false }, // npm tar 包复用
  ],
  env: {
    DB_HOST: "host.docker.internal",       // 容器内 127.0.0.1 是它自己
    REDIS_HOST: "host.docker.internal",
    ANTHROPIC_MODEL: AGENT_MODEL,          // 与 host 侧路由一致
  },
}),
hooks: { sandbox: { onSandboxReady: [
  { command: "cd main/manager-web && npm ci --prefer-offline --no-audit --no-fund", timeoutMs: 600_000 },
]}},
```

镜像见 `.sandcastle/Dockerfile`：JDK 21 + Maven + Node 22 + Python 3.12 + 全局 `@anthropic-ai/claude-code`，用户 `agent`，`ENTRYPOINT sleep infinity`。

**worktree 是独立的**：每个 sandbox 从**当前 host HEAD** 切出来。合并不进 host 之前，容器里发生的一切对下一个 sandbox 不可见。

### 2.3 为什么必须两个 sandbox 才够

一个 issue 的处理里其实用了 **1 个 sandbox**（实现 + 审查在同一个 worktree 上串行），但 **2 个 Claude 进程**。最终阶段每一轮用 **3 个 sandbox**（final-review 一个，final-fix 一个；下一轮又是新的 final-review）。原因：

- 审查要基于实现后的同一个 worktree，所以复用。
- 最终审查要对齐 host HEAD，而修复在另一个分支上做，所以拆开。
- sandbox 关闭后 worktree 一并清理，所以下一轮必然是新 worktree。

---

## 3. 启动与批次加载

### 3.1 入口

```bash
npx tsx .sandcastle/main.mts          # 或 cd .sandcastle && npm run sandcastle
```

可选环境变量：

| 变量 | 作用 |
|---|---|
| `SANDCASTLE_MODEL` | 覆盖 `AGENT_MODEL`，默认 `Kimi/k3`（`main.mts:49`）。必须命中 CCR 路由表。 |
| `SANDCASTLE_FEATURE` | 只处理某个 feature 目录下的 issue，例如 `game-management`（`main.mts:50`）。只允许 `[a-zA-Z0-9_-]`。 |

### 3.2 `loadBatch()`（`main.mts:122`）

**第一步永远是 `requireClean(repoRoot)`（`main.mts:276`）**：host 工作区必须干净（`git status --porcelain --untracked-files=all` 为空），否则直接抛错。这是防止把未提交改动混进基线。

然后：

1. 取当前分支 `branch = git symbolic-ref --short HEAD`、`currentSha = git rev-parse HEAD`。
2. 算状态文件路径：
   ```
   .sandcastle/logs/batch-<分支名中的非字母数字换成 _>-<featureFilter 或 all>.json
   ```
   例如 `.sandcastle/logs/batch-f-sandcastle-sandcastle-e2e.json`。
3. **尝试读取旧状态文件**：
   - 存在且 `status === "running"` → **续跑模式**。校验：
     - 分支一致
     - `baseSha` 是 40 位十六进制
     - `issueIds`、`specRequirements` 是数组，`specHashes` 存在
     - 每个 issue 文件都还能读到、ID 格式合法
     - `git merge-base --is-ancestor baseSha currentSha`（基线还在当前历史里）
     - `assertSpecsUnchanged()`（`main.mts:72`）：每个 spec 文件当前 sha256 与记录一致
     - 通过 → **直接返回旧 batch，跳过扫描**
   - 不存在或 `status === "complete"` → **新建批次**。
4. 新建批次：
   - `bash .sandcastle/bin/scratch-issues.sh list` 输出 JSON 数组（未 resolved 的 issue）。
     - 该脚本扫描 `.scratch/*/issues/*.md`，取 `Status:` 行，`resolved` 视为关闭。
   - 按 `SANDCASTLE_FEATURE` 前缀过滤。
   - 无 issue → 返回 `undefined`，主流程打印 `No open issues in scope; nothing to review.` 并退出。
   - 收集 spec 路径：`.scratch/<feature>/spec.md`（去重）。
   - 计算每个 spec 的 sha256 → `specHashes`（用于后面检测 spec 是否被偷偷改动）。
   - 解析 `specRequirements`（`main.mts:150`），三种写法：
     - `1. xxx` → `.scratch/x/spec.md#1`
     - `R1: xxx` → `.scratch/x/spec.md#R1`
     - `- [ ] xxx` → `.scratch/x/spec.md#L<行号>`
     - 一条都没解析到 → `.scratch/x/spec.md#document`
   - 生成 `batch.id = <时间戳>-<HEAD 前 8 位>`，写盘（`mode: 0o600`）。

`Batch` 结构（`main.mts:54`）：

```ts
{
  id, branch, baseSha, issueIds, specPaths, specHashes, specRequirements,
  status: "running" | "complete",
  lastOutcome?: "no_progress" | "blocked" | "iteration_limit" | "repair_limit" | "failed",
  blockedIssueIds?: string[]   // 明确 BLOCKED 或审查门禁失败的 issue
}
```

### 3.3 `runBatch()` 外层循环（`main.mts:500`）

```ts
for (let iteration = 1; iteration <= MAX_ITERATIONS; iteration++)  // MAX_ITERATIONS = 15
```

每轮：

1. `pendingIssues(repoRoot, batch.issueIds)`（`main.mts:93`）—— 重新读 `.scratch`，挑出 `Status` 不是 `resolved` 的。
2. 去掉 `blockedIssueIds` 里的。
3. 空 → 退出循环。
4. `readyIssue(...)`（`main.mts:98`）—— 解析 issue 里的 `Blocked by:` 行，阻塞项可以写完整 ID 或两位/三位编号（`05` 会在同 feature 的 `issues/` 里找 `05-*.md`）。所有 blocker 都已 resolved 才算 ready。一个都没有 → 退出循环。
5. 打日志 `=== Issue N/15: <id> ===`
6. `baseSha = host HEAD`
7. `branch = sandcastle/sequential-reviewer/<时间戳>-<随机6位>`（`main.mts:326`）
8. `createSandbox(branch)` —— **开新 sandbox**
9. `preflight(sandbox)`（`main.mts:361`）：`java -version && javac -version && mvn --version && python3.12 --version && test -w "$HOME/.m2"`。失败 → 抛错，整批终止（说明镜像或缓存权限坏了）。

注意 `MAX_ITERATIONS` 是**处理 issue 的个数上限**，不是重试次数。15 个 issue 还没处理完就抛 `Iteration limit reached`。

---

## 4. Phase 1：实现（容器内 Claude 进程 #1）

`main.mts:524`

```ts
const implement = await sandbox.run({
  name: "implementer",
  maxIterations: 1,
  completionSignal: IMPLEMENT_SIGNALS,      // ["<promise>IMPLEMENTED</promise>", "<promise>BLOCKED</promise>"]
  agent: sandcastle.claudeCode(AGENT_MODEL),
  promptFile: join(repoRoot, ".sandcastle", "implement-prompt.md"),
  promptArgs: {
    ISSUE_ID: issue.id,
    SPEC_PATH: batch.specPaths.find((p) => p.includes(`/${issue.id.split("/")[0]}/`)) ?? "",
  },
});
```

| 项 | 值 |
|---|---|
| 提示词文件 | `.sandcastle/implement-prompt.md` |
| Claude CLI 进程 | **新开 1 个** |
| 注入参数 | `ISSUE_ID`、`SPEC_PATH` |
| 完成信号 | `<promise>IMPLEMENTED</promise>` 或 `<promise>BLOCKED</promise>` |

提示词（`implement-prompt.md`）里 agent 被称作 RALPH，任务分 6 步：

1. **Explore** —— 读 issue、spec、相关源码和测试。
2. **Plan** —— 定最小改动。
3. **Execute** —— RGR（Red → Green → Refactor），先写失败测试。
4. **Verify** —— 按变更路径跑对应子项目的检查（表格与 `main.mts` 的 `CHECKS` 一致）。
5. **Commit** —— **只提交一个 commit**，消息以 `RALPH:` 开头，写清完成内容、关键决策、改动文件、阻塞项。
6. **Close** —— `bash .sandcastle/bin/scratch-issues.sh close <ID>`（把 `Status:` 改成 `resolved`），在 issue 里追加 `## Comments`，然后 **amend** 进刚才那个 commit。

提示词开头有三段自动嵌入的内容：

- `` !`bash .sandcastle/bin/scratch-issues.sh view {{ISSUE_ID}}` `` —— 完整 issue 正文
- `` !`cat {{SPEC_PATH}}` `` —— 父 spec
- `` !`git log --oneline --grep="RALPH" -10` `` —— 最近 10 条 RALPH 提交

以及明确的规则：被卡住时 **不许提交、不许关 issue**，输出 `<promise>BLOCKED</promise>`。

### 4.1 实现后的 host 门禁

`main.mts:532-556`，全部在 host 侧判断：

| 检查 | 代码 | 失败后果 |
|---|---|---|
| 信号是 `BLOCKED` | `main.mts:532` | 见下 |
| 无 commit | `main.mts:547` | 抛 `No progress on <id>`，整批终止 |
| issue 仍是未关闭 | `main.mts:551` | 抛 `Implementation did not resolve <id>` |
| 动了别的 issue 文件 | `assertOtherIssuesUnchanged`（`main.mts:368`） | 抛 `ReviewScopeError` |
| 改了 spec | `assertSpecsUnchanged` | 抛 `ReviewScopeError` |
| 测试门禁 | `verify(sandbox, baseSha, "implement")` | 抛 `VerificationError` |

`BLOCKED` 分支（`main.mts:532`）比较严格：

- worktree 必须干净；
- **有 commit → 直接失败**（"Implementer reported BLOCKED with commits"）；
- issue 被关了 → 也失败；
- 都合格 → 加入 `blockedIssueIds`，`saveBatch(lastOutcome: "blocked")`，`continue` 处理下一个 issue。

### 4.2 `verify()`（`main.mts:282`）—— host 独立门禁

这是整个流程最重要的"不信 agent"环节。它不接收 agent 的任何断言，自己跑一遍：

1. `requireClean(worktree)` —— 有未提交改动直接判失败。
2. `sha = git rev-parse HEAD`。
3. `merge-base --is-ancestor baseSha sha` —— 分支必须仍从基线派生。
4. `git diff --name-only --no-renames -z baseSha sha` → 变更文件列表。
5. 按前缀匹配 `CHECKS`（`main.mts:244`），只跑命中子项目的检查：

| 前缀 | label | 命令 |
|---|---|---|
| `main/manager-api/` | manager-api (Java) | `mvn -B -ntp ... clean verify`（排除 3 个需要 Testcontainers 的 PDC NFC 集成测试） |
| `main/manager-web/` | manager-web (Vue2) | `npm run test:unit && npm run check:i18n && npm run check:template` |
| `main/egg-miniprogram/` | egg-miniprogram | `node main/egg-miniprogram/scripts/verify-project.js` |
| `main/xiaozhi-server/` | xiaozhi-server (Python) | `python3.12 -m compileall -q core/ config/ plugins_func/`（仅语法级） |

6. 每个 check 用 `sandbox.exec(cmd)` 跑（**新进程，不是 Claude**），写日志：
   ```
   .sandcastle/logs/<sha>-<phase>-<label首词>-verify.log   (mode 0600)
   ```
   内容含 `commit=`、`command=`、`exitCode=`、输出。输出逐行做敏感信息脱敏（`password|passwd|secret|token|authorization|api[_-]?key|jdbc:|Bearer ...|URL 里的 user:pass|JWT`）。
7. **`sandbox.exec()` 非零 exitCode 不抛异常，只是返回**。所以脚本必须显式判断 `exitCode !== 0` → 抛 `VerificationError`，阻断合并。
8. 再 `requireClean` + 复查 HEAD 未变（防止检查期间被改）。
9. 无命中子项目 → 打印 `verification skipped`，返回空 `checks`。
10. 返回 `{ sha, checks: [{command, label, logPath, status: "passed"}] }`。

---

## 5. Phase 2：单 issue 审查（容器内 Claude 进程 #2）

`reviewIssue()`（`main.mts:377`）

```ts
const result = await sandbox.run({
  name: "reviewer",
  maxIterations: 1,
  completionSignal: REVIEW_SIGNALS,          // ["<promise>COMPLETE</promise>"]
  agent: sandcastle.claudeCode(AGENT_MODEL),
  promptFile: join(repoRoot, ".sandcastle", "issue-review-prompt.md"),
  promptArgs: {
    BASE_SHA: baseSha,                       // 批次基线
    IMPLEMENTED_SHA: implementedSha,         // 实现提交
    ISSUE_ID, ISSUE_PATH, SPEC_PATHS,
    VERIFICATION_RESULTS: JSON.stringify(implemented.checks),  // host 刚跑完的结果
  },
});
```

| 项 | 值 |
|---|---|
| 提示词文件 | `.sandcastle/issue-review-prompt.md` |
| Claude CLI 进程 | **新开 1 个**（与实现进程不共享上下文） |
| 复用 sandbox | 是，同一个 worktree |
| 完成信号 | `<promise>COMPLETE</promise>` |

### 5.1 提示词要求 reviewer 做 6 件事

1. 把 issue 的每条验收标准映射到实现位置和测试证据。
2. 按风险顺序审查：正确性/边界/失败路径 → 安全/权限/数据一致性/并发/幂等 → 契约/兼容/迁移/调用方 → 测试是否覆盖真实行为 → 可读性。
3. 分类：只修"本 issue diff 引入或加重、有具体位置/触发条件/可观察影响、能最小修复、能用可执行证据验证"的 blocking 问题。advisory 只报不改。
4. 修 blocking 时先写暴露它的回归测试，再做最小实现改动。
5. 修完**重新自审完整累积 diff**（`BASE_SHA..final HEAD`），不是只看修复片段。
6. 有修复就提交 **恰好一个** commit，消息 `fix: resolve review findings for {{ISSUE_ID}}`；不 amend/rebase 实现提交；不提交日志/报告/配置/spec/别的 issue 文件。

最后输出**恰好一个** `<review_report>` JSON 块 + `<promise>COMPLETE</promise>`：

```json
{
  "baseSha": "...", "reviewedSha": "...", "issueIds": ["id"],
  "verdict": "pass | changes_required | blocked",
  "fixes": [{ "location": "file:line", "problem": "...", "resolution": "...", "commit": "<40位SHA>", "evidence": "..." }],
  "findings": [{ "severity": "blocking | advisory", "location": "...", "trigger": "...", "impact": "..." }],
  "requirements": [{ "id": "id", "status": "met | missing | unverified", "evidence": "..." }],
  "verification": [{ "command": "...", "status": "passed | failed | skipped", "evidence": "..." }]
}
```

### 5.2 host 侧校验（顺序很重要）

`main.mts:398-438`：

| 步骤 | 函数 | 检查内容 | 失败类型 |
|---|---|---|---|
| 1 | `git rev-parse HEAD` | 记录 `reviewedSha` | — |
| 2 | `parseReviewReport`（`review-contract.mts:46`） | 唯一 `<review_report>`、JSON 合法、字段类型全对、`fixes` 可选并归一化为 `[]` | `ReviewGateError("invalid or missing self-review report")` |
| 3 | `saveReport` | **先落盘**再判断，方便人工检查部分修复 | — |
| 4 | `requireClean` | reviewer 不许留脏工作区 | `ReviewGateError` |
| 5 | `merge-base --is-ancestor` ×2 | `implementedSha` 和 `baseSha` 都要是 `reviewedSha` 的祖先 | `ReviewGateError("repair history must descend from ...")` |
| 6 | `assertIssueReviewFixes`（`review-contract.mts:106`） | 有 `fixes`：`implementedSha..reviewedSha` 恰好 1 个 commit 且等于 HEAD，所有 `fixes[].commit` 都等于它；无 `fixes`：HEAD 必须仍是 `implementedSha` 且没有修复 commit | `ReviewGateError` |
| 7 | `assertOtherIssuesUnchanged` | 没动别的 issue 文件 | `ReviewGateError` |
| 8 | `issueStatus` | 当前 issue 必须 `resolved` | `ReviewGateError` |
| 9 | `assertSpecsUnchanged` | spec 未变 | `ReviewGateError` |
| 10 | `assertReviewPass`（`review-contract.mts:74`） | 见下 | `ReviewGateError("self-review did not pass")` |
| 11 | `verify(sandbox, baseSha, "review")` | **host 独立复验** reviewer 的最终 SHA | `ReviewGateError` |
| 12 | SHA 比对 | 复验 SHA 必须等于 `reviewedSha` | `ReviewGateError` |
| 13 | `saveHostVerification` | 落盘 `issue-N-host-verification.json` | — |

`assertReviewPass` 的具体条件（单 issue 场景 `allowSkippedVerification: true`）：

- `baseSha`、`reviewedSha` 与期望**完全相等**
- `issueIds` 去重排序后等于期望集合（不多不少）
- 每个要求 ID 有 `status: "met"` 且 `evidence` 非空
- 没有 `status !== "met"` 的 requirement
- **没有任何 `blocking` finding**
- 至少 1 条 `passed` 且 evidence 非空
- 没有 `failed`、没有空的 evidence
- `skipped` 仅在 `allowSkippedVerification` 为真时允许（单 issue 允许；最终/修复复检不允许）
- `checkedCommands` 里的每条命令都在报告的 `verification` 里以 `passed` 出现（仅最终阶段用）
- `verdict === "pass"`

### 5.3 审查门禁失败怎么处理

`main.mts:560-575`：

```ts
try {
  const reviewed = await reviewIssue(...);
  verifiedSha = reviewed.reviewedSha;
} catch (error) {
  if (!(error instanceof ReviewGateError)) throw error;
  blockedIssueIds.add(issue.id);
  saveBatch({ ...batch, lastOutcome: "blocked" });
  cleanBlocked = true;
  verifiedSha = undefined;
}
```

- **`ReviewGateError`**（预期内的审查/范围/历史/复验失败）→ 记入 `blockedIssueIds`，跳过这个 issue，继续下一个。sandbox 照样关闭，**不合并**。
- **其他异常**（sandbox 清理、git I/O、manifest 写盘）→ 原样抛出，整批失败。

`reviewGate()`（`main.mts:218`）负责把 `ReviewScopeError` / `VerificationError` 包成 `ReviewGateError`；基础设施异常保持原样。`reviewAssertion()`（`main.mts:232`）把纯内存断言的普通 `Error` 也转成 `ReviewGateError`。

### 5.4 合并（`mergeVerified`，`main.mts:475`）

```ts
async function mergeVerified(baseSha, branch, verifiedSha, hostBranch) {
  await requireClean(repoRoot);
  // host HEAD 和分支必须与开始前一致，否则说明期间有人动过
  if (HEAD !== baseSha || 当前分支 !== hostBranch) throw ...;
  await execFileAsync("git", ["merge", "--no-ff", verifiedSha, "-m", `merge: ${branch}`]);
}
```

冲突时打印处理指引并抛出，不自动解决。

`runBatch` 里 sandbox 一定在 `finally { await sandbox.close(); }` 中关闭（`main.mts:576`），关闭后 worktree 清理。

---

## 6. Phase 4：最终审查与修复循环

只有在所有 issue 都处理完（或全部 blocked 并抛错）之后才进入。`main.mts:585-593` 先做收尾判断：

- 还有未 blocked 的 pending → 抛 `All remaining issues are blocked...`
- 有 blocked → 抛 `Batch ... incomplete: blocked issues: ...`

然后 `main.mts:594`：

```ts
for (let round = 0; round <= MAX_FIX_ROUNDS; round++)   // MAX_FIX_ROUNDS = 2
```

最坏情况：3 次最终审查 + 2 次修复。

### 6.1 final-review sandbox（每轮新建）

`main.mts:595-614`

1. `head = host HEAD`
2. `createSandbox(branchName("final-review"))` —— **新 sandbox、新 worktree**
3. `preflight`
4. `assertSpecsUnchanged`
5. `verify(sandbox, batch.baseSha, "final-N")` —— **对整批累积 diff** 跑 CHECKS，基线是批次开始时的 `baseSha`
6. 调 `review()`，见下
7. `reviewedSha` 必须等于 `head`
8. `requireClean`
9. `finally` 关 sandbox
10. 关完再检查 host HEAD / 分支没变

### 6.2 `review()`（`main.mts:441`）—— 通用只读审查

```ts
await sandbox.run({
  name: "final-reviewer",
  maxIterations: 1,
  completionSignal: REVIEW_SIGNALS,
  agent: claudeCode(AGENT_MODEL),
  promptFile: ".sandcastle/final-review-prompt.md",
  promptArgs: {
    BASE_SHA: batch.baseSha,
    REVIEWED_SHA: 当前 sandbox HEAD,
    ISSUE_IDS: 全部, ISSUE_PATHS: 全部,
    SPEC_PATHS, REQUIREMENT_IDS: 全部,
    VERIFICATION_RESULTS: JSON(finalN.checks),
  },
});
await requireClean(worktree);
if (HEAD !== reviewedSha) throw "final-reviewer changed HEAD; read-only review required";
```

`review()` 是**严格只读**的：前后各 `requireClean` 一次，HEAD 变了直接抛错。

| 项 | 值 |
|---|---|
| 提示词文件 | `.sandcastle/final-review-prompt.md` |
| Claude CLI 进程 | **新开 1 个** |
| 输入 | 整批 `BASE_SHA..REVIEWED_SHA` + 全部 issue + 全部 spec |
| 完成信号 | `<promise>COMPLETE</promise>` |

`final-review-prompt.md` 的额外要求：从 **spec** 出发建需求映射，包括**没有任何 issue 提到**的需求；检查状态流转、访问控制、数据契约、失败路径、兼容性、重复或矛盾实现；`REQUIREMENT_IDS` 里每个 ID 都要有对应 `requirements` 行。

### 6.3 结论分支（`main.mts:619-632`）

| `verdict` | 条件 | 动作 |
|---|---|---|
| `pass` | — | `assertReviewPass`（含 `requirementIds` 和 `checkedCommands`）→ 确认无 pending issue → `saveBatch(status: "complete")` → **返回，批次结束** |
| `changes_required` + 有 blocking | `round < MAX_FIX_ROUNDS` | 进入修复 |
| 其他 | 既不是 pass 也没有 blocking 可修 | 抛 `Final review blocked at <sha>`，人工看 `.sandcastle/logs/<batchId>/` |
| `changes_required` + 有 blocking | `round === MAX_FIX_ROUNDS` | 抛 `Final repair limit reached` |

### 6.4 修复：第二个新 sandbox（`main.mts:634-657`）

1. `branch = sandcastle/final-fix/<ts>-<rand>`
2. `createSandbox(branch)` —— **又一个新 sandbox**，从当前 host HEAD 切
3. `preflight`
4. 跑 fixer：
   ```ts
   fixSandbox.run({
     name: "fixer",
     maxIterations: 1,
     completionSignal: REVIEW_SIGNALS,
     promptFile: ".sandcastle/fix-review-prompt.md",
     promptArgs: {
       REVIEWED_SHA: head,
       FINDINGS: JSON.stringify(report.findings),
       ISSUE_IDS: batch.issueIds.join(","),
     },
   });
   ```
   | 项 | 值 |
   |---|---|
   | 提示词文件 | `.sandcastle/fix-review-prompt.md` |
   | Claude CLI 进程 | **新开 1 个** |
   | 能改代码 | 能 |

   提示词很短：拿到 `findings` JSON，先写失败回归测试复现，再做最小修复；不许削弱/删除/跳过测试；跑对应子项目检查；提交并保持工作区干净；修不了就说明阻塞且不提交；完成输出 `<promise>COMPLETE</promise>`。
5. 无 commit → 抛 `Final fixer made no commits`
6. `assertSpecsUnchanged`
7. `verify(fixSandbox, head, "fix-N")` —— 基线是修复前的 host HEAD，只校验修复 diff
8. 复检：
   ```ts
   review(fixSandbox, batch, {
     name: "fix-reviewer",
     promptFile: "review-prompt.md",
     baseSha: head,
     issueIds: batch.issueIds,
     reportName: `fix-${round}`,
     checks: fixed.checks,
   });
   ```
   | 项 | 值 |
   |---|---|
   | 提示词文件 | `.sandcastle/review-prompt.md` |
   | Claude CLI 进程 | **新开 1 个** |
   | 能改代码 | **不能**，只读 |
   | `REQUIREMENT_IDS` | 未传 → 默认退化为 `issueIds` |

   `review-prompt.md` 与 `final-review-prompt.md` 的差别：前者只审 `BASE_SHA..REVIEWED_SHA`（即修复片段）并要求"不能因为 diff 看起来合理就判通过"，未运行的测试不能标 passed；后者审整批并要覆盖 spec 需求项。
9. `assertReviewPass(report, { baseSha: head, reviewedSha: verifiedSha, issueIds, checkedCommands })`
   - 注意这里**不传** `allowSkippedVerification`，所以任何 `skipped` 都会判失败
   - 这步抛的是**普通 Error**，不是 `ReviewGateError`，不会被降级成 issue blocked，而是整批 `failed`
10. `verify(fixSandbox, head, "fix-review-N")` 再跑一遍 —— 防止 review 期间代码漂移
11. `finally` 关 fixSandbox
12. `mergeVerified(head, branch, verifiedSha, hostBranch)`

下一轮从**新的** host HEAD 重新切 final-review sandbox，所以第二轮审查能看到已合并的修复。

---

## 7. 提示词文件对照表

| 文件 | 用于 | 函数调用处 | 新 Claude 进程 | 可否改代码 | 完成信号 | 注入参数 |
|---|---|---|---|---|---|---|
| `implement-prompt.md` | Phase 1 实现 | `main.mts:524` | 是 | 是 | `IMPLEMENTED` / `BLOCKED` | `ISSUE_ID`, `SPEC_PATH` |
| `issue-review-prompt.md` | Phase 2 单 issue 审查 | `main.mts:388` | 是 | 是（最多 1 个修复 commit） | `COMPLETE` | `BASE_SHA`, `IMPLEMENTED_SHA`, `ISSUE_ID`, `ISSUE_PATH`, `SPEC_PATHS`, `VERIFICATION_RESULTS` |
| `final-review-prompt.md` | Phase 4 批次最终审查 | `main.mts:604` 经 `review()` | 是 | 否（只读） | `COMPLETE` | `BASE_SHA`, `REVIEWED_SHA`, `ISSUE_IDS`, `ISSUE_PATHS`, `SPEC_PATHS`, `REQUIREMENT_IDS`, `VERIFICATION_RESULTS` |
| `fix-review-prompt.md` | Phase 4 按 findings 修复 | `main.mts:639` | 是 | 是 | `COMPLETE` | `REVIEWED_SHA`, `FINDINGS`, `ISSUE_IDS` |
| `review-prompt.md` | Phase 4 修复后复检 | `main.mts:648` 经 `review()` | 是 | 否（只读） | `COMPLETE` | `BASE_SHA`, `REVIEWED_SHA`, `ISSUE_IDS`, `ISSUE_PATHS`, `SPEC_PATHS`, `REQUIREMENT_IDS`, `VERIFICATION_RESULTS` |

共有约定：

- 每个提示词都引用 `@.sandcastle/CODING_STANDARDS.md` 和受影响子项目的 `CLAUDE.md`，由容器内 Claude 自己读。
- 都要求"绝不把没跑过的测试说成 passed"。
- 报告格式统一为单个 `<review_report>` JSON，由 `review-contract.mts` 校验。

---

## 8. 过程文件：存在哪、谁写、谁读

### 8.1 一张表

| 产物 | 路径 | 写入者 | 读取者 | 是否进 git |
|---|---|---|---|---|
| 批次状态 | `.sandcastle/logs/batch-<branch>-<feature>.json` | `main.mts`（`loadBatch` / `saveBatch`） | 下次启动的 `loadBatch`（续跑） | 否（`logs/` 被 gitignore） |
| 审查报告 | `.sandcastle/logs/<batchId>/issue-N.json`、`final-N.json`、`fix-N.json` | `saveReport` → `saveArtifact` | 人 | 否 |
| host 复验记录 | `.sandcastle/logs/<batchId>/issue-N-host-verification.json` | `saveHostVerification` | 人 | 否 |
| 测试门禁日志 | `.sandcastle/logs/<sha>-<phase>-<label>-verify.log` | `verify()` | 人 | 否 |
| Claude 会话日志 | `.sandcastle/logs/sandcastle-<phase>-<ts>-<rand>-<agent>.log`（auto-generated） | Sandcastle 内部（默认 file 模式） | 人 / 调试 | 否 |
| git worktree | `.sandcastle/worktrees/...` | `createSandbox` | 对应 sandbox（bind-mount） | 否（`worktrees/` 被 gitignore） |
| issue 状态 | `.scratch/<feature>/issues/*.md` 的 `Status:` 行 | 容器内 `scratch-issues.sh close`，随实现 commit 进入分支 | host `issueStatus` / `pendingIssues` / `readyIssue` | **是** |
| 业务代码 + 测试 | 仓库常规路径 | 容器内 Claude，commit 后合并 | 下一轮 sandbox | **是** |
| spec | `.scratch/<feature>/spec.md` | 人 | 每个 Claude 进程、host 哈希校验 | **是** |

### 8.2 关键：**进程之间不共享内存，只共享文件**

三个 Claude 进程之间没有任何上下文传递。所谓"传递"只有三条路：

1. **git 提交** —— 实现/修复代码、issue 关闭状态。
2. **`promptArgs` 字符串** —— host 把上一个阶段的 SHA、测试结果 JSON 拼进下一个提示词。
3. **磁盘报告** —— 只给人和 host 的断言函数用，不进下一个 Claude 的上下文。

具体链路：

```
implementer 产出 commit(sha=implemented)
   │  host: verify() 跑测试，产出 checks[] 和日志路径
   ▼
reviewer  promptArgs = { BASE_SHA, IMPLEMENTED_SHA, VERIFICATION_RESULTS=JSON(checks) }
   │  reviewer 可能产出恰好 1 个修复 commit(sha=reviewed)
   │  host: 解析 <review_report>，落盘 issue-N.json，assertIssueReviewFixes，
   │        再独立跑一次 verify() 复验 reviewed
   ▼
host  git merge --no-ff reviewed
   │
   ▼
final-reviewer  promptArgs = { BASE_SHA=batch.baseSha, REVIEWED_SHA=host HEAD, 全部 issue, 全部 spec 需求 }
   │  host: 落盘 final-N.json
   ▼
(若 changes_required) fixer  promptArgs = { REVIEWED_SHA, FINDINGS=JSON(report.findings) }
   │  host: verify(fix 分支)
   ▼
fix-reviewer  promptArgs = { BASE_SHA=修复前 HEAD, REVIEWED_SHA=fix HEAD, VERIFICATION_RESULTS }
   │  host: assertReviewPass + 再 verify
   ▼
host  git merge --no-ff fix
   │
   ▼
下一轮 final-reviewer（看到的是新 HEAD）
```

### 8.3 敏感信息

两处脱敏，正则相同：

- `saveArtifact`（`main.mts:174`）：序列化报告时把命中敏感模式的字符串值替换成 `[redacted sensitive output]`。
- `verify()`（`main.mts:306`）：写测试日志时逐行替换。

模式覆盖 `password|passwd|secret|token|authorization|api[_-]?key|jdbc:|Bearer <token>|URL 中的 user:pass|JWT(eyJ...)`。所有落盘文件都是 `mode: 0o600`。

---

## 9. 失败分类、退出与续跑

### 9.1 三种错误类型

| 类型 | 含义 | 处理 |
|---|---|---|
| `ReviewGateError` | 预期内的门禁失败（报告不合法、范围越界、历史不符、复验失败） | 该 issue 记为 blocked，继续下一个 issue |
| `ReviewScopeError` | spec 被改、issue ID 非法、缺少 Status、动了别的 issue | 由 `reviewGate` 包成 `ReviewGateError`；直接调用处则向上抛 |
| `VerificationError` | 工作区脏、基线不祖先、测试失败、HEAD 漂移 | 由 `reviewGate` 包成 `ReviewGateError`；实现阶段和最终阶段直接抛 |

基础设施异常（Docker、文件 I/O、网络）**不**被转换，一律向上抛，整批终止。

### 9.2 顶层错误归类（`main.mts:662-676`）

```ts
try { await runBatch(batch); }
catch (error) {
  const lastOutcome =
      message 以 "No progress" 开头                        ? "no_progress"
    : message 以 "All remaining issues are blocked" 开头    ? "blocked"
    : message 以 "Implementer reported BLOCKED" 开头        ? "blocked"
    : message 以 "Batch " 开头且含 "incomplete: blocked issues" ? "blocked"
    : message 以 "Iteration limit" 开头                     ? "iteration_limit"
    : message 以 "Final repair limit" 开头                  ? "repair_limit"
    : "failed";
  await saveBatch({ ...batch, lastOutcome });
  throw error;
}
```

### 9.3 续跑

- 上次异常退出，`batch-*.json` 里仍是 `status: "running"` + `lastOutcome`。
- 直接再跑 `npx tsx .sandcastle/main.mts`：
  - `requireClean` 通过（host 干净）
  - 读到旧 batch → 校验通过 → 沿用原 `baseSha`、`issueIds`、`specHashes`、`specRequirements`
  - `pendingIssues` 自动跳过已 `resolved` 的 issue
  - `blockedIssueIds` 里的也会跳过
- 想重试 blocked 的 issue：删掉状态文件里的 `blockedIssueIds`（或删整个状态文件重新组批，注意会重置基线和 spec 哈希）。
- 批次正常结束 → `status: "complete"`，下次启动会新建批次。

---

## 10. 完整时序（单 issue）

```
host main.mts
│
├─ loadBatch()
│   ├─ requireClean(host)
│   ├─ 读/建 batch 状态 json
│   └─ 解析 .scratch issue + spec + specRequirements
│
├─ runBatch() loop
│   ├─ pendingIssues / readyIssue            读 .scratch/*.md
│   ├─ createSandbox(branch)                 → git worktree + docker 容器
│   ├─ preflight()                           → exec: java/mvn/python 检查
│   │
│   ├─ sandbox.run(implementer)              → ★ Claude 进程 #1
│   │      .sandcastle/implement-prompt.md
│   │      产出: 1 个 RALPH commit + issue closed
│   │
│   ├─ host 门禁 (BLOCKED / 无 commit / issue 未关 / 越界 / spec 变)
│   ├─ verify(sandbox, baseSha, "implement") → exec: 子项目测试
│   │      产出: checks[] + logs/<sha>-implement-*.log
│   │
│   ├─ reviewIssue()
│   │   ├─ sandbox.run(reviewer)             → ★ Claude 进程 #2
│   │   │      .sandcastle/issue-review-prompt.md
│   │   │      promptArgs 含 IMPLEMENTED_SHA + VERIFICATION_RESULTS
│   │   │      产出: 0 或 1 个 fix commit + <review_report>
│   │   ├─ parseReviewReport → saveReport()
│   │   ├─ assertIssueReviewFixes / 范围 / issue 状态 / spec
│   │   ├─ assertReviewPass(allowSkippedVerification: true)
│   │   └─ verify(sandbox, baseSha, "review") → 独立复验
│   │
│   ├─ finally sandbox.close()               → 清理 worktree
│   └─ mergeVerified() --no-ff
│
├─ (全部 issue 完成)
│
└─ Final loop (round 0..2)
    ├─ createSandbox(final-review)            → 新 worktree
    ├─ verify(sandbox, batch.baseSha, final-N)
    ├─ review(final-reviewer)                 → ★ Claude 进程
    │      .sandcastle/final-review-prompt.md
    │      saveReport(final-N.json)
    ├─ pass → status=complete, return
    ├─ changes_required + blocking:
    │   ├─ createSandbox(final-fix)           → 又一个新 worktree
    │   ├─ run(fixer)                         → ★ Claude 进程
    │   │      .sandcastle/fix-review-prompt.md
    │   │      promptArgs 含 FINDINGS
    │   ├─ verify(fixSandbox, head, fix-N)
    │   ├─ review(fix-reviewer)               → ★ Claude 进程
    │   │      .sandcastle/review-prompt.md
    │   ├─ assertReviewPass(无 skipped 容忍)
    │   ├─ verify(fixSandbox, head, fix-review-N)
    │   └─ mergeVerified(head, ...)
    └─ 下一轮
```

---

## 11. 新人容易踩的坑

1. **以为 reviewer 记得 implementer 说过什么。** 不会。所有必要信息必须写进 commit、issue 或 `promptArgs`。
2. **以为 `sandbox.exec()` 失败会抛异常。** 不会，它返回 `exitCode`。脚本靠显式判断，自己改代码时也要判断。
3. **在 host 上留了未提交改动再重跑。** `loadBatch` 第一条就是 `requireClean`，直接失败。
4. **改了 spec 却没重启批次。** `assertSpecsUnchanged` 会在多个位置拦截；想改 spec 要先结束或删除批次状态。
5. **以为实现阶段的测试通过就够了。** 不够，还有 review 后的 host 复验和最终阶段的整批复验。
6. **以为 blocked 的 issue 会被自动重试。** 不会，记在 `blockedIssueIds` 里，要手动清。
7. **以为 `MAX_ITERATIONS` 是重试次数。** 是 issue 数量上限，默认 15。
8. **`promptFile` 相对路径按 `process.cwd()` 解析**，`main.mts` 里传的是 `join(repoRoot, ...)` 绝对路径，所以从哪个目录启动都能跑（但 `requireClean`、`git` 都以 `repoRoot` 为 cwd）。
9. **xiaozhi-server 的门禁只是 `compileall`**，不是 pytest。需要运行时的验证必须在 issue 里显式说明留给 host 人工验证。
10. **最终修复的复检失败不是 blocked，是整批 failed。** `assertReviewPass` 那里不捕获，直接冒到顶层。

---

## 12. 相关文件速查

| 文件 | 作用 |
|---|---|
| `.sandcastle/main.mts` | 编排脚本本体 |
| `.sandcastle/review-contract.mts` | 报告解析与断言（`parseReviewReport` / `assertReviewPass` / `assertIssueReviewFixes`） |
| `.sandcastle/implement-prompt.md` | Phase 1 提示词 |
| `.sandcastle/issue-review-prompt.md` | Phase 2 提示词 |
| `.sandcastle/final-review-prompt.md` | Phase 4 最终审查提示词 |
| `.sandcastle/fix-review-prompt.md` | Phase 4 修复提示词 |
| `.sandcastle/review-prompt.md` | Phase 4 修复后复检提示词 |
| `.sandcastle/CODING_STANDARDS.md` | 所有审查提示词引用的代码规范 |
| `.sandcastle/bin/scratch-issues.sh` | `.scratch/` tracker 适配器：`list` / `view` / `close` |
| `.sandcastle/Dockerfile` | 沙箱镜像（JDK 21 / Maven / Node 22 / Python 3.12 / Claude CLI） |
| `.sandcastle/package.json` | `npm run sandcastle` = `tsx main.mts`；`npm test` 跑 `main.test.mts` 和 `review-contract.test.mts` |
| `.sandcastle/sandcastle-guide.md` | Sandcastle 库本身的用法说明 |
| `docs/agents/issue-tracker.md` | `.scratch/` issue 格式约定 |