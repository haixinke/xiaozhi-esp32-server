# Sequential Reviewer 单 Issue 审查修复闭环设计

日期：2026-10-26
状态：已确认（2026-10-26）
范围：`.sandcastle/` 的 sequential-reviewer 单 issue 流程

## 1. 背景与目标

原始模板 `.sandcastle/review-prompt-original.md` 允许 reviewer 直接修改、测试并提交代码，但将修改限定为保持功能不变的清晰度改进。当前实现把单 issue review 改为只读结构化审查：reviewer 发现 blocking 问题后，runner 拒绝合并并终止当前处理，无法在单 issue 内完成修复闭环。

本次改造目标：

1. 将单 issue 的 REVIEW PROCESS 改为以需求和正确性为先的风险审查，不再主动进行泛化重构。
2. 允许 reviewer 在同一 review 执行中直接修复符合条件的 blocking 缺陷，补充回归测试并提交修复 commit。
3. 修复后由 host 重新验证；reviewer 自检与 host 复验共同构成通过条件。
4. 当前 issue 修复或复验失败时，不合并该 issue，记录失败并继续处理其他无依赖 issue。
5. 保留 batch 最终 `final-review` 及其独立 fixer/fix-reviewer 闭环。

## 2. 已确定的决策

### 2.1 自动修复边界

仅自动修复同时满足以下条件的问题：

- 属于当前 issue 的本次 diff 引入或加重的问题；
- 已确认且有具体位置、触发条件和可观察影响；
- 级别为 blocking；
- 能在当前 issue 范围内通过最小修改安全修复；
- 能通过回归测试或其他可执行证据验证修复结果。

以下问题只报告，不自动修改：

- advisory 建议；
- 与当前 issue 无关的历史问题；
- 纯风格偏好、泛化重构和无明确收益的清理。

以下问题转人工，不猜测修复：

- 需求、验收条件或业务规则不清；
- 需要修改其他 issue 或冻结的 feature spec；
- 需要破坏性接口变更或缺乏兼容迁移依据；
- 缺少必要外部服务、权限、数据或运行条件；
- 无法在当前 issue 范围内安全完成最小修复。

### 2.2 不做单 issue 独立复审

初始 reviewer 在同一个 agent 执行内完成发现、复现、修复、测试、自检和报告。修复后不启动第二个 reviewer，也不另起单 issue fixer。

这是有意接受的取舍：减少调用和重复上下文，但单 issue 合并前没有第二审查视角。batch 的 final-review 仍提供后续独立审查，但不能把 final-review 之前的合并描述为完全隔离风险。

### 2.3 单 issue 不自动重试

每个 issue 只进行一次 review agent 调用。reviewer 可在该次执行内反复修改和测试，但最终只产生一个修复 commit。

出现以下任一情况，当前 issue 失败并跳过，不由 runner 自动重试：

- 最终仍有未解决 blocking；
- 需求或外部条件导致 blocked；
- reviewer 未按要求提交修复或留下脏工作区；
- 报告缺失、结构不合法、SHA 不真实或范围不匹配；
- reviewer 越权修改 spec、其他 issue 或分支历史；
- 修复后的 host 验证失败；
- 修复后合并前校验失败。

实现阶段原有的失败处理不在本次扩大改造范围内。

### 2.4 失败记录与重启

- 当前 issue 的临时分支不合并到 host；host 上该 issue 保持 open。
- 复用 `Batch.blockedIssueIds` 记录失败 issue，保存 batch manifest。
- 失败报告和已有运行日志保存在 `.sandcastle/logs/<batch-id>/`；manifest 不新增失败原因字段。
- runner 关闭当前 sandbox 后继续选择其他 ready issue。
- 依赖失败 issue 的后续 issue 由现有 `Blocked by` 和 `readyIssue()` 逻辑保持阻塞。
- 所有可处理 issue 结束后，只要仍有 blocked issue，batch 就不得标记 complete。
- 重启时继续跳过 `blockedIssueIds`，避免重复消耗模型调用。
- 人工处理后需要重跑时，从 manifest 的 `blockedIssueIds` 移除对应 ID；本次不新增管理命令。

### 2.5 final-review 保持不变

batch 的最终流程继续使用：

```text
final-review（只读）
  -> fixer（存在可修复 blocking 时）
  -> fix-reviewer（独立只读复审）
  -> host 复验
  -> merge / repair_limit
```

现有 `MAX_FIX_ROUNDS = 2` 仅用于 final-review，不引入单 issue 自动重试。

## 3. 目标流程

### 3.1 无 blocking

```text
implementer 提交 SHA-A
  -> host 验证 SHA-A
  -> reviewer 审查 base..SHA-A
  -> 无 blocking，fixes=[]，无新 commit
  -> runner 校验报告、范围、HEAD 和工作区
  -> host 对最终 SHA-A 复验
  -> 合并 SHA-A
```

### 3.2 blocking 修复成功

```text
implementer 提交 SHA-A
  -> host 验证 SHA-A
  -> reviewer 审查 base..SHA-A
  -> 确认 blocking，补回归测试并最小修复
  -> reviewer 自检完整累计 diff
  -> 提交一个 fix commit，得到 SHA-B
  -> 输出 reviewedSha=SHA-B 的最终报告
  -> runner 校验修复 commit、报告、范围和工作区
  -> host 对 SHA-B 执行适用验证
  -> runner 以实际 host 结果完成通过判定
  -> 合并 SHA-B
```

### 3.3 修复或复验失败

```text
review / fix / report / host verification 任一步失败
  -> 保存可解析的报告和失败日志
  -> 当前 issue 加入 blockedIssueIds
  -> 关闭临时 sandbox，不合并临时分支
  -> 继续处理其他无依赖 issue
  -> batch 最终保持未完成并转人工
```

## 4. 新 REVIEW PROCESS

单 issue 专用提示词按以下顺序描述审查和修复。

### 4.1 确定范围

- 读取当前 issue、验收条件、相关 feature spec、根及受影响子项目的 `CLAUDE.md`、`.sandcastle/CODING_STANDARDS.md`。
- 记录 `baseSha`、实现后 HEAD 和 host 已执行验证结果。
- 审查当前 issue 的完整累计 diff，并追踪相关调用方、测试、迁移和接口。
- 不清理无关历史代码，不修改其他 issue 或 spec。

### 4.2 建立需求与实现对应关系

- 将每条 issue 验收条件映射到实现位置和测试证据。
- 检查未实现、实现偏移和无法验证的要求。
- 以 issue/spec 和既有接口契约判断意图，不从实现反推需求。
- 需求不清时停止自动修复并说明缺失信息。

### 4.3 按风险审查

优先级依次为：

1. 正确性、边界条件和失败路径；
2. 安全、认证授权、数据一致性、并发与幂等；
3. API/数据契约、跨端兼容、数据库迁移和调用方影响；
4. 测试是否覆盖实际行为及关键风险；
5. 清晰度、复杂度和编码规范。

只报告有具体位置、触发条件和影响的问题。可维护性问题没有实际影响时不为了“找到改进”而制造修改。

### 4.4 分类处理

- 符合修复边界的 blocking：进入复现和修复。
- 不能安全修复的 blocking：保留在 findings，verdict 为 `changes_required` 或 `blocked`，不提交半成品。
- advisory：保留在 findings，不修改代码，不阻止通过。
- 所有验收条件均有证据、无未解决 blocking 且没有未验证要求时，reviewer 才能给出自检 `pass`。

### 4.5 复现、修复与自检

- 对缺陷先补能在修复前暴露问题的回归测试，再实施最小修复。
- 不删除、弱化或跳过测试来取得通过结果。
- 修复后重新审查 `baseSha..最终HEAD` 的完整累计 diff，而非只看修复片段。
- 检查原验收条件、修复影响、关联调用方和相关测试。
- reviewer 可以运行沙箱内测试辅助自检，但不得把未执行检查写为 passed，也不得把实现阶段的 host 结果描述为修复后结果。
- 修复不完整或无法验证时，不为了保留成果提交半成品。

### 4.6 提交与最终报告

- 有实际修复时，将代码和回归测试提交为一个独立 `fix` commit。
- 建议提交主题：`fix: resolve review findings for <issue-id>`。
- 无 blocking 或无实际修复时不产生空 commit。
- 不 amend、rebase、reset、合并 host 分支或修改既有实现 commit。
- 只提交当前 issue 的代码和测试；不提交日志、报告、私人配置、spec 或其他 issue 文件。
- 输出一次 `<promise>COMPLETE</promise>` 和恰好一个 `<review_report>`。
- 报告记录已修复问题、剩余 findings、需求证据和实际验证信息。

## 5. 报告契约

### 5.1 新增 fixes

单 issue 审查报告增加 `fixes` 数组；无修复时为空数组。共享报告解析器应兼容 final-review/fix-reviewer 的只读报告：它们可以省略 `fixes`，解析时按空数组处理，不要求修改 final 提示词。

```json
{
  "baseSha": "批次中当前 issue 的基线 SHA",
  "reviewedSha": "修复后的最终 HEAD；无修复时等于实现 HEAD",
  "issueIds": ["当前 issue 的完整 ID"],
  "verdict": "pass | changes_required | blocked",
  "fixes": [
    {
      "location": "file:line",
      "problem": "原 blocking 问题",
      "resolution": "最小修复方式",
      "commit": "40 位修复 commit SHA",
      "evidence": "回归测试位置及实际结果"
    }
  ],
  "findings": [
    {
      "severity": "blocking | advisory",
      "location": "file:line",
      "trigger": "具体触发条件",
      "impact": "可观察影响"
    }
  ],
  "requirements": [
    {
      "id": "当前 issue 的完整 ID",
      "status": "met | missing | unverified",
      "evidence": "实现和测试位置"
    }
  ],
  "verification": [
    {
      "command": "实际执行或 runner 传入的命令",
      "status": "passed | failed | skipped",
      "evidence": "实际结果或日志引用"
    }
  ]
}
```

### 5.2 fixes 与 findings 语义

- `fixes` 只记录已经修复的 blocking，不抵消未解决问题。
- `findings` 保留未解决 blocking 和全部 advisory；已修复且没有残留的问题不再作为未解决 blocking 重复列出。
- `verdict=pass` 时不得存在未解决 blocking，所有 requirement 必须为 met 且有证据。
- `verdict=changes_required` 或 `blocked` 时按失败处理；即使存在 fixes 也不能合并。
- 每条 fix 必须有非空 location、problem、resolution、evidence 和合法的 40 位 commit SHA。

### 5.3 runner 硬校验

在单 issue review 后由 runner 校验：

1. `baseSha`、`issueIds` 与当前任务完全一致。
2. `reviewedSha` 等于 sandbox 实测最终 HEAD。
3. 无 fixes 时，最终 HEAD 必须等于实现验证后的 SHA。
4. 有 fixes 时，`implementedSha..reviewedSha` 必须恰好包含一个 commit；不得提交多个修复 commit 或夹带其他 commit。
5. 每个 `fixes[].commit` 必须等于这个唯一修复 commit，也即最终 `reviewedSha`；多个修复记录可以共同指向该 commit。
6. reviewer 不得改写实现前历史；最终 HEAD 必须仍以 `baseSha` 为祖先。
7. 当前 issue 必须保持 resolved；其他 issue 文件和冻结 spec 必须未变。
8. 工作区必须干净，reviewer 不得留下未提交修改。
9. pass 报告仍须满足原有 requirements、findings 和验证证据校验。

runner 用实际提交数量、fix SHA 与最终 HEAD 的一致性以及最终 diff 范围保证修复可追踪，不仅依赖提示词要求。

## 6. 两段式通过判定

reviewer 输出报告发生在修复后的 host 验证之前，因此 reviewer 无法诚实预知 host 复验结果。通过判定分两段：

### 第一段：reviewer 自检

- 报告绑定实测最终 SHA。
- 所有验收条件有实现和测试证据。
- 无未解决 blocking，无未验证 requirement。
- fixes 与真实 commit 对应。
- reviewer 只陈述实际执行过的检查，不伪造修复后的 host 结果。

### 第二段：runner 复验

- 对 reviewer 最终 HEAD 重新运行现有 `verify()`。
- 以本次实际执行的命令、退出状态和日志为准。
- host 复验通过后才允许 merge；任一检查失败则跳过当前 issue。
- runner 最终落盘的审查记录应同时保留 reviewer 自检报告和最终 host 复验结果，明确二者对应的阶段和 SHA。

单 issue 不再要求 reviewer 的报告预先逐字包含修复后 host 命令；这些命令尚未执行，强制包含会诱导编造结果。现有 `checkedCommands` 精确匹配继续用于只读 final/fix-review 路径，单 issue 路径改用以下规则：

- 无修复时，报告沿用实现阶段传入的 host 结果，runner 再对同一 SHA 复验。
- 有修复时，旧 host 结果只属于实现 SHA；reviewer 的 verification 记录自己实际运行的修复后检查，未运行项写 skipped 并说明原因。
- reviewer 自检 verdict=pass 只代表代码审查和已有自检证据通过，不代表 host 复验已通过。
- runner 以最终 `verify()` 的真实结果作为强制合并门禁，不再把 reviewer 文本当作最终 host 通过证明。

报告中的 `verification` 不得被解释为单独证明修复后 host 已通过。实现阶段传入的 host 结果只能标记为对应实现 SHA 的历史证据；最终通过依据是 runner 在报告之后执行的新结果。

## 7. Runner 改造

### 7.1 分离单 issue 与 final 审查提示词

当前 `.sandcastle/review-prompt.md` 同时用于单 issue reviewer 和 final 阶段的 fix-reviewer。若直接把它改为可写，会意外改变已决定保持不变的 final 闭环。

因此采用以下文件分工（实现层必要调整）：

- 新增 `.sandcastle/issue-review-prompt.md`：单 issue 可写审查修复提示词。
- 保留 `.sandcastle/review-prompt.md`：只读报告提示词，供 final 的 fix-reviewer 使用。
- 保留 `.sandcastle/final-review-prompt.md` 和 `.sandcastle/fix-review-prompt.md` 不变。

通用 review helper 可继续复用，但单 issue 路径需要独立的可写后处理；final/fix-review 路径继续强制 HEAD 不变。

### 7.2 单 issue 后处理

将当前“reviewer 必须保持 HEAD 不变”的逻辑替换为：

1. 记录实现验证后的 `implementedSha`。
2. 运行单 issue reviewer。
3. 读取最终 HEAD 和工作区状态。
4. 解析并保存报告。
5. 校验 fixes、commit 拓扑、issue/spec 范围和报告契约。
6. 若 reviewer 自检不通过，抛出可识别的 issue review 失败。
7. 对最终 HEAD 执行 `verify(sandbox, baseSha, "review")`。
8. 以最终验证结果完成通过判定并返回最终 SHA。
9. host 未变化时由现有 `mergeVerified()` 合并。

final-review 和 fix-reviewer 仍调用只读 helper，不获得修复权限。

### 7.3 失败隔离

单 issue review 至 merge 前的预期失败需要被当前 iteration 捕获并转换为：

- 保存可解析报告及必要失败日志；
- 将当前 issue ID 加入 `blockedIssueIds`；
- 保存 manifest，`lastOutcome` 使用现有失败语义；
- 在 `finally` 中关闭 sandbox；
- 不调用 merge；
- 继续下一次 issue 选择。

不能用一个吞掉所有异常的宽泛 `catch` 隐藏编程错误、sandbox 清理失败或 manifest 写入失败。应为“当前 issue 审查/修复/验证不通过”定义可识别的失败类型或等价窄范围处理；基础设施异常仍可终止 runner。

`readyIssue()` 已根据 host issue 状态处理依赖，无需新增依赖图。由于失败分支不合并，host 上该 issue 保持 open，依赖它的 issue 不会 ready。

### 7.4 权限边界

reviewer 可以编辑、运行测试和 commit，但 runner 必须继续约束：

- spec 哈希不变；
- 其他目标 issue 文件不变；
- 当前 issue 不从 resolved 被重开；
- HEAD 是合法前进且仍是 base 后代；
- 工作区干净；
- host 分支和 HEAD 在合并前未变化；
- 验证期间 HEAD 不变且验证后工作区干净。

## 8. 文件改造范围

预计修改：

- `.sandcastle/main.mts`
  - 单 issue 使用新提示词；
  - 支持修复 commit 和最终 SHA；
  - 增加修复后校验、host 复验和失败跳过；
  - final 流程保持原行为。
- `.sandcastle/review-contract.mts`
  - 增加可选 fixes 类型、解析和校验；
  - 增加单 issue 修复报告与真实提交历史的断言入口。
- `.sandcastle/main.test.mts`
  - 更新只读 reviewer 假设；
  - 增加成功修复、失败跳过和继续处理测试。
- `.sandcastle/review-contract.test.mts`
  - 增加 fixes schema、verdict 一致性和 SHA/拓扑校验测试。
- `.sandcastle/test-support/sandcastle.mjs`
  - mock reviewer 支持修复 commit、修复报告和失败场景。
- `.sandcastle/sandcastle-guide.md`
  - 更新单 issue 流程、失败记录和人工重跑说明。

新增：

- `.sandcastle/issue-review-prompt.md`
  - 承载本方案第 4 节的完整 REVIEW PROCESS 和修复权限。

保持不变：

- `.sandcastle/implement-prompt.md`
- `.sandcastle/review-prompt.md`（继续作为 final fix-reviewer 的只读提示词）
- `.sandcastle/final-review-prompt.md`
- `.sandcastle/fix-review-prompt.md`
- host `CHECKS` 验证矩阵
- `.sandcastle/review-prompt-original.md`（仅作原始参考）
- final-review 的最大修复轮数与独立复审结构

若测试 fixture 需要复制提示词文件，只补充新文件到现有复制列表，不改变其他 fixture 行为。

## 9. 验收测试

至少覆盖以下场景：

1. **无 blocking**：reviewer 不新增 commit，host 复验后合并。
2. **advisory-only**：不修改代码、不产生修复 commit，报告保留 advisory，可以合并。
3. **blocking 修复成功**：reviewer 补测试、提交 fix commit、报告 fixes，host 复验后合并。
4. **修复后仍有 blocking**：当前 issue 不合并，加入 blockedIssueIds。
5. **修复后 host 验证失败**：当前 issue 不合并，后续无依赖 issue 继续执行。
6. **报告缺失或不合法**：当前 issue 不合并，不凭 agent 文本通过。
7. **reviewedSha 不等于最终 HEAD**：拒绝合并。
8. **fixes commit 非法或不存在**：拒绝合并。
9. **修复提交数量或 SHA 不匹配**：`implementedSha..reviewedSha` 不是恰好一个 commit，或 fixes[].commit 不等于最终 HEAD 时拒绝合并。
10. **有代码变更但 fixes 为空**：拒绝合并。
11. **声明 fixes 但没有新 commit**：拒绝合并。
12. **reviewer 修改 spec**：当前 issue 不合并并记录失败。
13. **reviewer 修改其他 issue 或重开当前 issue**：当前 issue 不合并并记录失败。
14. **reviewer 留下脏工作区或改写历史**：当前 issue 不合并。
15. **失败 issue 的依赖项**：不会提前执行。
16. **部分成功**：成功 issue 正常合并；失败 issue 不合并；后续无依赖 issue 可继续。
17. **重启行为**：blockedIssueIds 中 issue 不自动重试；人工移除后可重新选择。
18. **batch 结束状态**：仍有 blocked issue 时不标 complete。
19. **final-review 回归**：现有 final fixer/fix-reviewer、最大修复轮数和只读限制测试继续通过。
20. **host checks 证据**：不能用 reviewer 文本替代 runner 最终复验；修复后 host 失败必须拦截合并。
21. **敏感信息**：新增 fixes 和失败日志沿用现有脱敏逻辑，不写入密钥或凭据。

同时运行 `.sandcastle` 现有完整测试，不以新增场景通过代替旧回归。

## 10. 非目标

- 不增加单 issue 独立 reviewer。
- 不自动修复 advisory。
- 不自动处理需求不清、跨 issue 或破坏性变更。
- 不新增失败 issue 管理命令或新的 manifest 失败原因结构。
- 不重构整个 Sandcastle 调度器。
- 不改变 final-review 的只读审查和独立修复闭环。
- 不扩大或重写各子项目验证矩阵。
- 不保证 reviewer 自检等于无缺陷；host 复验和 final-review 仍是必要门禁。
