# Sequential Reviewer 单 Issue 修复闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 sequential-reviewer 的单 issue reviewer 能审查、最小修复 blocking、提交一个修复 commit，并在 host 复验后合并；失败时跳过当前 issue 并继续无依赖任务。

**Architecture:** 保留现有只读 `review()` 给 final-review/fix-reviewer，新增单 issue 专用 prompt 和可写审查入口。纯报告契约负责 fixes 与提交形态校验，runner 负责真实 Git 拓扑、范围、host 复验和失败隔离；最终合并只相信 runner 实测结果。

**Tech Stack:** Node.js 22、TypeScript ESM、`node:test`、`@ai-hero/sandcastle`、Git worktree、现有 Sandcastle fixture。

**Spec:** `docs/superpowers/specs/2026-10-26-sequential-reviewer-remediation-design.md`

## Global Constraints

- 只改造单 issue review；final-review、fixer、fix-reviewer 和 `MAX_FIX_ROUNDS = 2` 保持原行为。
- 每个 issue 只调用一次 reviewer；同次执行内可反复修改测试，但最终最多一个修复 commit，不由 runner 自动重试。
- 仅自动修复当前 issue 内已确认且可安全最小修复的 blocking；advisory 只报告，不修改。
- 有修复时 `implementedSha..reviewedSha` 恰好一个 commit，所有 `fixes[].commit` 和 `reviewedSha` 都等于该 commit。
- reviewer 自检通过不等于 host 复验通过；最终 `verify()` 成功后才允许 merge。
- 失败复用 `blockedIssueIds` 和现有日志目录，不新增 manifest 失败原因字段或管理命令。
- 不修改 `implement-prompt.md`、`review-prompt.md`、`final-review-prompt.md`、`fix-review-prompt.md`、`CHECKS` 或 `review-prompt-original.md`。
- 不提交用户已有未跟踪文件 `.sandcastle/review-prompt-original.md`，不夹带无关工作区修改。
- 新增关键业务规则使用简洁中文注释；日志和报告沿用现有敏感信息脱敏。
- 每个任务先写失败测试，再写最小实现；完成前运行完整 `.sandcastle` 测试。

## Review Focus

- final/fix-reviewer 旧报告省略 `fixes`：解析后必须归一化为 `fixes: []`，不能破坏 final 流程；由 Task 1 的兼容性测试固定。
- reviewer 提交多个 commit、伪报 fix SHA 或漏报代码变更：即使其他报告字段合法也必须拒绝；由 Task 1 纯校验和 Task 2 Git 集成测试固定。
- 修复路径没有适用 host check：不得虚构 host 通过记录；reviewer 必须有实际自检证据，host 记录明确为 `checks: []`；由 Task 2 的非 Java 修复测试固定。
- 当前 issue 失败后，独立 issue 可继续而依赖 issue 不可提前运行；由 Task 3 的两类依赖测试固定。
- sandbox 清理、manifest 写入或 host HEAD 变化等基础设施异常不得被转换为普通 issue skip；由 Task 3 的异常边界测试固定。

---

### Task 1: 扩展报告契约与修复提交形态校验

**Files:**
- Modify: `.sandcastle/review-contract.mts`
- Test: `.sandcastle/review-contract.test.mts`

**Interfaces:**
- Consumes: 现有 `ReviewReport`、`parseReviewReport(stdout)`、`assertReviewPass(report, expected)`。
- Produces:
  - `ReviewFix = { location: string; problem: string; resolution: string; commit: string; evidence: string }`。
  - `ReviewReport.fixes: ReviewFix[]`；解析省略 fixes 的旧报告时归一化为 `[]`。
  - `assertIssueReviewFixes(report, expected)`，其中 `expected` 为 `{ implementedSha: string; reviewedSha: string; repairCommits: string[] }`，返回 `void`，不合法时抛错。

- [ ] **Step 1: 写 fixes 解析和兼容性的失败测试**

在 `review-contract.test.mts` 增加以下测试，使用现有 `report()` 和 `tagged()`：

1. `a read-only report without fixes remains valid and normalizes to an empty list`：省略 fixes，解析成功且 `fixes` 深等于 `[]`。
2. `malformed fix records fail closed`：分别覆盖 fixes 非数组、缺少任一必填字符串、commit 不是 40 位小写 SHA，均抛出 `Invalid review report schema`。
3. `a declared fix does not hide a blocking finding`：带合法 fixes 和 blocking finding 的报告仍被 `assertReviewPass` 拒绝。

- [ ] **Step 2: 写提交形态的失败测试**

新增 `assertIssueReviewFixes` 测试，固定以下矩阵：

| fixes | implementedSha 与 reviewedSha | repairCommits | 期望 |
|---|---|---|---|
| `[]` | 相同 | `[]` | 通过 |
| `[]` | 不同或有 commit | 任意对应值 | 拒绝 |
| 一条合法 fix | 不同 | 恰好一个，且等于 reviewedSha 和 fixes[].commit | 通过 |
| 多条合法 fix | 不同 | 一个共同 commit，所有 fix.commit 均等于它 | 通过 |
| 一条 fix | 相同 | `[]` | 拒绝 |
| 一条 fix | 不同 | 两个 commit | 拒绝 |
| 一条 fix | 不同 | 一个 commit，但不等于 reviewedSha 或 fix.commit | 拒绝 |

- [ ] **Step 3: 运行契约测试，确认按预期失败**

Run: `cd .sandcastle && node --import tsx --test review-contract.test.mts`
Expected: 新测试因缺少 fixes 解析/校验或 `assertIssueReviewFixes` 未定义而 FAIL；旧测试结果一并记录。

- [ ] **Step 4: 实现最小契约扩展**

在 `review-contract.mts`：

1. 增加 `ReviewFix`，并给解析后的 `ReviewReport` 增加必需的 `fixes`。
2. 解析时验证可选 fixes；省略时返回新对象 `fixes: []`，不修改输入对象。
3. 每条 fix 复用现有 `nonempty()`、`validSha()` 校验所有字段。
4. 实现 `assertIssueReviewFixes()`，只负责报告与已收集提交列表的一致性，不执行 Git。
5. 保持 `assertReviewPass()` 的 requirement、finding、verification 和 `checkedCommands` 语义不变，供 final 路径继续使用。

- [ ] **Step 5: 运行契约测试，确认全部通过**

Run: `cd .sandcastle && node --import tsx --test review-contract.test.mts`
Expected: 旧测试和新增测试全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add .sandcastle/review-contract.mts .sandcastle/review-contract.test.mts docs/superpowers/specs/2026-10-26-sequential-reviewer-remediation-design.md docs/superpowers/plans/2026-10-26-sequential-reviewer-remediation.md
git commit -m "feat: add issue review fix report contract"
```

该提交同时将设计状态改为“已确认（2026-10-26）”，并纳入本实施计划；不修改已确认技术决策。

### Task 2: 接入单 issue 可写审查、修复提交和 host 复验

**Files:**
- Create: `.sandcastle/issue-review-prompt.md`
- Modify: `.sandcastle/main.mts`
- Modify: `.sandcastle/test-support/sandcastle.mjs`
- Test: `.sandcastle/main.test.mts`
- Uses: `.sandcastle/review-contract.mts` 的 `assertIssueReviewFixes()`

**Interfaces:**
- Consumes: Task 1 的 `ReviewReport.fixes`、`assertIssueReviewFixes()`；现有 `verify()`、`review()`、`mergeVerified()`。
- Produces:
  - `ReviewGateError extends Error`：仅表示可预期的审查/验证门禁失败。
  - `reviewIssue(sandbox, batch, options)`；options 含 `{ baseSha, implementedSha, issueId, reportName, checks }`，返回 `{ report, reviewedSha }`。
  - `saveHostVerification(batch, reportName, record)`；record 含 `{ phase: "issue-review", baseSha, reviewedSha, issueIds, checks }`，落盘为 `<reportName>-host-verification.json`。
  - 单 issue prompt 参数：`BASE_SHA`、`IMPLEMENTED_SHA`、`ISSUE_ID`、`ISSUE_PATH`、`SPEC_PATHS`、`VERIFICATION_RESULTS`。

- [ ] **Step 1: 扩展 fixture，先写可写 review 的失败集成测试**

在 `test-support/sandcastle.mjs` 增加明确的测试开关，默认行为不变：

- `TEST_ISSUE_REVIEW_FIX=1`：reviewer 修改实现和回归测试，只 commit 一次，主题为 `fix: resolve review findings for <issue-id>`；报告 fixes[].commit/reviewedSha 等于新 HEAD，verdict=pass，verification 含实际模拟的 reviewer 自检证据。
- `TEST_ISSUE_REVIEW_ADVISORY=1`：不改文件、不 commit，报告仅含 advisory，verdict=pass。
- `TEST_REVIEW_UNDECLARED_FIX=1`：reviewer commit 代码但 fixes=[]。
- `TEST_REVIEW_TWO_COMMITS=1`：reviewer 创建两个 commit，fixes 指向最终 HEAD。
- `TEST_REVIEW_WRONG_FIX_SHA=1`：一个修复 commit，但 fixes[].commit 指向 implementedSha。
- `TEST_REVIEW_MISSING_FIX=1`：无新 commit，却声明一个 40 位 SHA 的 fix。
- `TEST_FIX_REVIEW_JAVA=1`：仅 final 的 fix-reviewer 留下未提交修改，用于证明只读边界未放松。

在 `main.test.mts` 添加：

1. `an issue reviewer can commit one declared fix and merge after host reverification`：status=0；host log 含修复主题；self report 的 fix commit 等于 reviewedSha；发生 implement/review 两轮 verify，没有单 issue 第二个 reviewer。
2. `an issue review without findings adds no fix commit`：status=0，无修复主题，fixes=[]。
3. `an advisory-only issue review merges without a fix commit`：status=0，advisory 保留，fixes=[]。
4. `undeclared, multiple, missing, or mismatched review commits cannot merge`：四种 fixture 均 status!=0、merges 为空。
5. `issue review persists self review and final host verification for the same SHA`：`issue-N.json` 与 `issue-N-host-verification.json` 的 reviewedSha 相同，host record.phase=`issue-review`，checks 来自 runner。
6. `a fixed non-Java change does not invent host checks`：`TEST_NON_JAVA=1` 加修复时，只有 reviewer 自检证据非空才允许通过，host record.checks=[]，不得出现伪造命令。
7. `final fix-review remains read-only`：final retry 场景叠加 `TEST_FIX_REVIEW_JAVA=1` 时修复分支不能作为通过结果完成 batch。

把原 `a reviewer mutation is rejected before merge` 改名为 `an uncommitted reviewer mutation is rejected before merge`，避免与合法修复冲突；fixture 复制列表和 COMPLETE 静态测试加入 `issue-review-prompt.md`。

- [ ] **Step 2: 运行新增集成测试，确认旧只读门禁使其失败**

Run: `cd .sandcastle && node --import tsx --test --test-name-pattern='issue reviewer|issue review|review commits|final fix-review|reviewer mutation' main.test.mts`
Expected: 合法修复测试在现有 `reviewer changed HEAD; read-only review required` 或等价门禁处 FAIL；提交形态测试在实现前不能通过。

- [ ] **Step 3: 新增单 issue 专用 prompt**

创建 `.sandcastle/issue-review-prompt.md`，包含：

1. 范围参数、issue/spec/子项目规则读取要求和完整累计 diff。
2. 按“范围、需求映射、风险审查、分类、复现修复、自检、提交报告”排列的 REVIEW PROCESS。
3. 只修已确认 blocking，advisory 只报告；需求不清、跨 issue、破坏性接口变更或缺外部条件时停止修复。
4. 先回归测试后最小修复；不得删测、弱化断言、改 spec、改其他 issue、重开当前 issue。
5. 最多一个 `fix: resolve review findings for {{ISSUE_ID}}` commit；禁止 amend/rebase/reset/merge；无修复不得空 commit。
6. 最终报告 fixes/findings/requirements/verification 语义及 `<promise>COMPLETE</promise>`。
7. 明确 `VERIFICATION_RESULTS` 属于 `IMPLEMENTED_SHA`；修复后只能记录 reviewer 实际运行的自检，runner 随后独立复验。

不改动现有 `review-prompt.md`，它继续供 final 的 fix-reviewer 使用。

- [ ] **Step 4: 实现单 issue 可写审查入口和真实 Git 校验**

在 `main.mts`：

1. 定义 `ReviewGateError`；其 message 不包含测试输出、报告正文或环境值，只含阶段、issue ID 和本地日志路径。新增关键门禁和两阶段通过逻辑使用简洁中文注释。
2. 新增 `reviewIssue()`，与只读 `review()` 分离。`sandbox.run()` 使用 `issue-review-prompt.md`、`maxIterations: 1`、现有 REVIEW_SIGNALS 和 Step 3 的参数。
3. reviewer 返回后读取实际 HEAD，要求工作区干净；解析并立即保存 self report，使 changes_required/blocked 的可解析报告也留痕。
4. 用 `git merge-base --is-ancestor implementedSha reviewedSha` 确认历史只前进；用 `git rev-list --reverse implementedSha..reviewedSha` 得到 `repairCommits`。
5. 无论 verdict 是否 pass，都先校验报告身份、修复提交形态、spec/issue 范围和工作区，确保 changes_required/blocked 的部分修复也不能绕过提交真实性和权限检查。
6. 调用 Task 1 的 `assertIssueReviewFixes()`；再调用 `assertReviewPass()` 校验 baseSha、实际 reviewedSha、单 issue scope、requirements、findings 和自检证据，但单 issue 不传 `checkedCommands`。
7. 复用/提取当前“其他 issue 未变”检查，在 reviewer 后再次执行；确认当前 issue 仍 resolved、spec 哈希未变。
8. 将解析、报告断言和明确 Git/范围门禁转换为 `ReviewGateError`；文件 I/O、sandbox 执行和未知异常不得包装成门禁失败。
9. 保留现有 `review()` 的 HEAD 不变检查，final/fix-reviewer 继续调用它。

- [ ] **Step 5: 接入最终 host 复验与独立验证记录**

在单 issue 路径：

1. `reviewIssue()` 成功后，对其最终 HEAD 运行现有 `verify(sandbox, baseSha, "review")`。
2. 以该次实际结果作为合并门禁；不要求 reviewer 预知或逐字填入尚未执行的 host 命令。
3. 抽取现有报告落盘的脱敏写入逻辑供通用 artifact 使用，保留 `saveReport()` 行为；新增 `saveHostVerification()`，保存 self report 之外的 `<reportName>-host-verification.json`。
4. host record 记录实际 baseSha、reviewedSha、issueIds、phase 和 checks；无适用检查时保留空数组，不写入“passed”。
5. 只有复验成功才把其 SHA 交给现有 `mergeVerified()`；final 路径不传新 prompt、不启用修复权限。

- [ ] **Step 6: 运行新增集成测试和完整回归**

Run: `cd .sandcastle && node --import tsx --test --test-name-pattern='issue reviewer|issue review|review commits|final fix-review|reviewer mutation' main.test.mts`
Expected: 新增及改名测试全部 PASS。

Run: `npm --prefix .sandcastle test`
Expected: 全部测试 PASS；若旧测试仍假设所有 reviewer 修改都非法，只更新该过时假设，不放宽 final 只读断言。

- [ ] **Step 7: Commit**

```bash
git add .sandcastle/issue-review-prompt.md .sandcastle/main.mts .sandcastle/main.test.mts .sandcastle/test-support/sandcastle.mjs
git commit -m "feat: allow issue reviewers to commit verified fixes"
```

### Task 3: 失败 issue 隔离、继续调度、重启语义与文档

**Files:**
- Modify: `.sandcastle/main.mts`
- Modify: `.sandcastle/main.test.mts`
- Modify: `.sandcastle/test-support/sandcastle.mjs`
- Modify: `.sandcastle/sandcastle-guide.md`
- Uses: Task 2 的 `ReviewGateError`、`reviewIssue()` 和 host 复验记录

**Interfaces:**
- Consumes: 现有 `blockedIssueIds`、`saveBatch()`、`readyIssue()`、`pendingIssues()`；Task 2 的门禁错误类型。
- Produces: 单 issue review/修复/复验的 `ReviewGateError` 被当前 iteration 捕获、记录并跳过；其他异常继续向上抛出，不改变实现阶段和 final 阶段错误语义。

- [ ] **Step 1: 扩展 fixture，写失败继续和重启的失败测试**

新增最小测试开关：

- `TEST_REVIEW_REJECT_FIRST=1`：仅第一个独立 issue 返回未解决 blocking。
- `TEST_FIX_REJECT=1`：reviewer 提交一个修复，但最终报告仍有 blocking。
- `TEST_FAIL_REVIEW_ISSUE=01-example`：仅指定 issue 的 review 后 host check 返回非零；实现阶段及其他 issue 不受影响。
- `TEST_SECOND_DEPENDS_FIRST=1`：issue 02 的 `Blocked by` 指向 01。
- `TEST_REVIEW_MUTATE_SPEC=1`、`TEST_REVIEW_CLOSE_OTHER_ISSUE=1`、`TEST_REVIEW_REOPEN_ISSUE=1`：分别模拟 reviewer 越权。
- `TEST_SECURITY_FIX=1`：fixes 文本包含敏感样例，验证落盘脱敏；不要在测试输出打印样例值。

为 `runFixture()` 增加同目录第二次 spawn 的测试能力，并在返回中提供 `restartStatus`、`restartEvents` 和必要的 restart manifest/log 快照：

- `TEST_RESTART_BLOCKED=1`：保留 manifest 原样重启。
- `TEST_MANUAL_UNBLOCK=1`：第一次失败后，仅将 manifest 的 blockedIssueIds 清空，并关闭导致 review reject 的 fixture 开关，再重启。

新增测试：

1. `a rejected issue is blocked and an independent issue continues`：01 不合并且进入 blockedIssueIds；02 正常 implement/review/merge；batch 最终不 complete。
2. `a fix with unresolved blocking is discarded and skipped`：修复 commit 不进入 host，当前 issue 被记录 blocked。
3. `host verification failure after a fix skips only that issue`：01 复验失败不合并，02 成功合并。
4. `a dependent issue stays pending when its blocker fails review`：只有 01 被尝试，02 没有 implementer 事件，host 上两者均未 resolved。
5. `malformed reports and reviewer scope mutations block without merging`：缺报告、错误 reviewedSha、改 spec、改其他 issue、重开当前 issue、脏工作区和 rebase 均不合并，并记录当前 issue blocked。
6. `restart skips blocked issues until the manifest entry is removed`：原样重启没有新 implementer/reviewer；人工清空 blockedIssueIds 后重启会重新处理该 issue，并可完成。
7. `cleanup and host movement failures are not converted into issue skips`：现有 TEST_CLOSE_FAIL/TEST_HOST_MOVED 场景仍向上失败，不能错误地继续其他 issue 或写成普通 review blocked。
8. `sensitive fix evidence is redacted in saved reports`：保存的 fixes 敏感字段为 `[redacted sensitive output]`，JSON 仍可解析。
9. 保留并运行现有 final retry、repair limit、fix-reviewer 测试，确认 final 行为未变。

- [ ] **Step 2: 运行失败隔离测试，确认当前 runner 会中止而非继续**

Run: `cd .sandcastle && node --import tsx --test --test-name-pattern='independent issue continues|dependent issue stays|skips only|restart skips|scope mutations|unresolved blocking|not converted|sensitive fix' main.test.mts`
Expected: 继续调度、依赖和重启测试 FAIL；现有异常边界测试不得为了制造 RED 而被改弱。

- [ ] **Step 3: 实现窄范围失败捕获和 blocked 记录**

在 `main.mts`：

1. 将明确的预期门禁失败统一为 `ReviewGateError`：报告解析/断言失败、reviewer 范围或历史不合法、工作区脏、host check 非零、验证期间 HEAD 改变。未知 Git/I/O、sandbox.exec 抛出、preflight、sandbox.close、manifest 写入异常保持普通错误。
2. 在实现验证完成之后，仅以窄作用域包裹 `reviewIssue()` 和随后的 review `verify()`；catch 只接受 `ReviewGateError`，其他错误原样抛出。
3. 保持实现阶段验证在该 catch 之外，避免把实现失败意外改为 review skip。
4. sandbox 仍在 `finally` 关闭；close 失败不得被吞掉。
5. 捕获门禁失败后：不 merge，加入 `blockedIssueIds`，保存 manifest（lastOutcome 使用现有 blocked 语义），输出 `ReviewGateError` 提供的简短安全原因，不打印原始 stdout/stderr，然后 continue。
6. 若 sandbox 清理或 saveBatch 失败，终止 runner，不继续调度。
7. `mergeVerified()` 保持在 catch 之外；host HEAD/branch 变化仍终止，而不是跳过 issue。
8. 不新增依赖图；依靠 host issue 保持 open 和现有 `readyIssue()` 阻止依赖任务。
9. 实现验证失败继续使用外层失败分类并保存 no_progress/failed，但不得改写为 review skip。
10. 批次末尾沿用现有 blocked 检查：剩余全部 blocked 时抛出 incomplete 错误；即使部分 issue 已合并，blockedIssueIds 非空时也不进入 final-review 或标记 complete。

- [ ] **Step 4: 运行失败隔离和完整回归测试**

Run: `cd .sandcastle && node --import tsx --test --test-name-pattern='independent issue continues|dependent issue stays|skips only|restart skips|scope mutations|unresolved blocking|not converted|sensitive fix' main.test.mts`
Expected: 新测试全部 PASS。

Run: `npm --prefix .sandcastle test`
Expected: 全部测试 PASS，包括 final-review 既有修复闭环。

- [ ] **Step 5: 更新使用文档**

在 `.sandcastle/sandcastle-guide.md` 增加本仓 sequential-reviewer 小节，说明：

- 单 issue 流程及最多一个修复 commit；无独立单 issue 复审。
- reviewer 自检与 host 复验的两段门禁。
- self report 与 `<reportName>-host-verification.json` 的位置和用途。
- 失败后 blockedIssueIds、日志位置、依赖阻塞和部分完成语义。
- 人工处理后从 manifest 移除对应 blocked ID 再重启；不新增命令。
- final-review 仍只读，fixer/fix-reviewer 与最多 2 轮修复不变。

- [ ] **Step 6: 更新代码知识图谱并检查最终差异**

Run: `graphify update .`
Expected: 命令成功，图谱反映本次 `.sandcastle` TypeScript 修改；无 API 调用。

Run: `git diff --check && git status --short`
Expected: 无空白错误；仅包含本计划、已确认设计和 graphify 自动更新等预期文件，不包含 `.env`、日志或 `review-prompt-original.md`。

- [ ] **Step 7: 最终完整验证**

Run: `npm --prefix .sandcastle test`
Expected: 全部测试 PASS，fail=0。

Run: `git diff --check`
Expected: 无输出，退出码 0。

- [ ] **Step 8: Commit**

```bash
git add .sandcastle/main.mts .sandcastle/main.test.mts .sandcastle/test-support/sandcastle.mjs .sandcastle/sandcastle-guide.md
# 仅当 graphify update 产生对应变更时，再明确暂存其生成文件。
git commit -m "feat: continue independent issues after review failures"
```

## 完成标准

- 单 issue reviewer 能在一次执行内修复 blocking、提交恰好一个修复 commit，并在 host 复验后合并。
- 无修复或 advisory-only 不产生修复 commit；final/fix-reviewer 仍只读。
- 报告 fixes、实际 HEAD 和唯一修复 commit 严格一致；无法声明不存在或未报告的修复。
- 当前 issue 失败不合并，独立 issue 继续，依赖 issue 保持阻塞，重启不自动重复 blocked 项。
- reviewer 自检记录与 runner 最终 host 验证分别留痕，敏感内容继续脱敏。
- `.sandcastle` 全部测试通过，final-review 原有闭环和修复上限不变。
