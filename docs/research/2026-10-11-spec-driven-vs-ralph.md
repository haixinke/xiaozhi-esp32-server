# 研究笔记：人不在时 agent 按 spec/tickets 自动写代码 —— mattpocock skills vs 自建 Ralph 循环

日期：2026-10-11
问题：用户已写好具体 spec / tickets，希望人不在时 agent 自动开工。候选：方案 A = mattpocock/skills 的 implement-spec；方案 B = 自建 Ralph bash 循环。针对本仓库（多子项目：Python xiaozhi-server、Java manager-api、Vue2 manager-web、微信小程序 egg-miniprogram）应选哪个？

## 方案 A：mattpocock/skills 的 spec 流水线

### 机制

来源：repo README + 本地已安装插件 `/Users/minwang/.claude/plugins/cache/mattpocock/mattpocock-skills/1.3.1/skills/`（27 个 skills，分 engineering / productivity）。

核心流水线（README 原文描述）：

- `to-spec` — "Turn the current conversation into a spec and publish it to the issue tracker"
- `to-tickets` — "Break any plan, spec, or conversation into a set of tracer-bullet tickets, each declaring its blocking edges"
- `implement` — 单 ticket / spec 的交互式实现，"driving /tdd at pre-agreed seams and closing out with /code-review"
- `implement-spec` — "Implement a whole spec on one integration branch. Works the tickets as a task graph"，"running implementer subagents across the ready frontier for maximum concurrency"，收尾跑 `/code-review`

`implement-spec/SKILL.md` 全文（本地文件 `.../1.3.1/skills/engineering/implement-spec/SKILL.md`）要点：

- tickets 不是步骤清单，而是带 blocking 关系的 **task graph**，始终有可抓取的 frontier
- 每个 ticket 派一个 **implementer subagent**，独立 worktree + 独立分支，内部调 `tdd` skill
- **merger subagent** 把完成的 ticket 合回 integration branch；frontier 变化后继续派新 subagent
- 全部完成后对 integration branch 跑 `code-review`，问题由单个 implementer subagent 统一修
- 最后清 worktree、关 ticket / 标 PR ready

### 无人值守程度

- README 全文未出现 "unattended" / "autonomous loop" 字样；最接近的是 subagent / task-graph 并发语言
- `implement` 与 `implement-spec` 的 frontmatter 均为 `disable-model-invocation: true` —— 只能由人 `/implement-spec` 触发，但触发一次后整个 spec（多 ticket、多 subagent、merge、review）在一次会话内自动跑完
- 结论：**半自动**。人发起一次命令，之后整条 spec 无人干预跑完；但不会自己轮询 issue tracker 找下一个 spec

### 前提与成本

- 前提：issue tracker 已配置（`/setup-matt-pocock-skills`）；本仓库已配置为 `.scratch/` 本地 markdown（`docs/agents/issue-tracker.md`）
- 成本：单会话内多 subagent 并发，token 消耗集中在一次 spec；无循环重读 spec 的浪费

来源：
- https://github.com/mattpocock/skills/tree/main (README)
- 本地：`/Users/minwang/.claude/plugins/cache/mattpocock/mattpocock-skills/1.3.1/skills/engineering/implement-spec/SKILL.md`
- 本地：`/Users/minwang/.claude/plugins/cache/mattpocock/mattpocock-skills/1.3.1/skills/engineering/implement/SKILL.md`

## 方案 B：自建 Ralph 循环

### 机制

来源：https://ghuntley.com/ralph/ （Geoffrey Huntley 原文）。

"Ralph is a technique. In its purest form, Ralph is a Bash loop." 全部机制：

```bash
while :; do cat PROMPT.md | claude-code ; done
```

- **每轮全新上下文**：每次循环新起 claude-code 进程，上下文为空；所有状态必须落在文件里（`fix_plan.md`、specs、`AGENT.md`），每轮重读
- **一轮一个任务**："One item per loop"，信任模型自己挑优先级
- **任务清单文件**：planning 模式生成优先级列表 `fix_plan.md`，build 模式边做边更新；清单漂移就删掉重新生成（"I have deleted the TODO list multiple times"）
- **backpressure 闸门**：测试、编译、类型检查、静态分析挡住坏输出；成功的循环以 git commit + push + 递增版本号结束
- **subagent 做上下文管理**：主窗口当调度器，"The more you use the context window, the worse the outcomes you'll get"

### 自述优点

- 真无人值守过夜执行；YC hackathon 团队一晚交 6 个 repo；一个 $50k 合同 reportedly 只花 ~$297 模型成本
- "Ralph can replace the majority of outsourcing at most companies for greenfield projects"
- 用 Ralph 从零写出了不在 LLM 训练数据里的编程语言 CURSED

### 自述失败模式与成本（原文承认）

- **占位实现倾向**："Claude has the inherent bias to do minimal and placeholder implementations"，需在 prompt 里吼 "DO NOT IMPLEMENT PLACEHOLDER"
- **误报"未实现"**：ripgrep 漏搜导致重复劳动 —— "This nondeterminism is the Achilles' heel of Ralph"
- **早上起来代码库是坏的**："you'll wake up to a broken codebase that doesn't compile"，要人在 `git reset --hard` 和修复 prompt 之间做判断
- **token 浪费**：spec 每轮重读重计费，Huntley 自认 "this is wasteful"
- **spec 错误级联**：lexer spec 里一个重复 keyword 浪费大量时间（操作者失误）
- **产出脏乱**："The repository is full of garbage, temporary files, and binaries"
- **持续调参负担**："Each time Ralph does something bad, Ralph gets tuned - like a guitar"
- **关键限制**：Huntley 明说只适用于 greenfield —— "There's no way in heck would I use Ralph in an existing code base"，且仍需高级工程师判断力

来源：https://ghuntley.com/ralph/

### 微信实践案例文章

https://mp.weixin.qq.com/s/B0HQDNKGlw-WP4Rl5JuW6A 被微信反爬拦截（返回"当前环境异常，完成验证后即可继续访问"验证页），Wayback Machine 无快照。本文未能获取其实践细节，结论不依赖该来源。

## Claude Code 原生无人值守能力（可验证部分）

- headless 模式 `claude -p`（print mode）：非交互单次执行，正是 Ralph 循环体内调用的形式（Huntley 文中的 `claude-code` 即此类用法）
- Background agents / 后台 subagent：implement-spec 的 implementer subagents 即依赖此能力（"run in the background where possible"）
- `/loop` skill：本机已安装（skill 列表中存在 `loop`：按固定间隔重复跑一个 prompt 或 slash command），可用来周期性触发 `/implement-spec`，相当于给方案 A 套一层调度器，无需手写 bash 循环
- Workflow / CronCreate：会话内可编排多 agent，但属会话级，不是真正的常驻无人值守

## 本仓库现状（契合度评估）

- 已采用 mattpocock skills 工作流：`docs/agents/workflow.md` 写明主线 `想法 → /grill-with-docs → /to-spec → /to-tickets → /implement`
- issue tracker 已是本地 markdown：`.scratch/<feature-slug>/spec.md` + `issues/NN-<slug>.md`，带 `Status:` / `Blocked by:` 行，frontier/claim/resolve 语义与 implement-spec 的 task graph 完全对齐（`docs/agents/issue-tracker.md`）
- 已装 superpowers（writing-plans、executing-plans、subagent-driven-development）与 mattpocock tdd / code-review，backpressure 纪律齐备
- 本仓库是**既有大型代码库**（4 个活跃子项目，跨 Python/Java/Vue2/小程序），正是 Huntley 明说不要用 Ralph 的场景

## 对比

| 维度 | A: mattpocock implement-spec | B: 自建 Ralph |
|---|---|---|
| 无人值守 | 人触发一次，整 spec 自动跑完；不会自动找下一个 spec（可用 `/loop` 补上） | 真·无限循环，过夜无人 |
| 上下文管理 | 会话内 subagent 分工，context pointer 传递，主上下文不爆 | 每轮全新进程，状态全落文件；简单但每轮重读 spec 浪费 token |
| 失败恢复 | code-review skill 收尾 + 单 subagent 修问题；git worktree 隔离 | 靠测试/编译 backpressure + 人早上 `git reset --hard`；已知会留 broken codebase |
| 成本 | 单 spec 一次性消耗 | spec 每轮重读计费，作者自认 wasteful；需持续调 prompt |
| 既有库适配 | 设计即面向 issue/spec 驱动的既有项目 | 作者明说只限 greenfield |
| 与现有 workflow 契合 | 完全契合：`.scratch/` tracker、tdd、code-review、GLOSSARY/ADR 全部已就位 | 需另建 PROMPT.md / fix_plan.md 体系，与 `.scratch/` 双轨并行 |

## 推荐

**选方案 A（implement-spec），需要"过夜自动开工"时用 `/loop` 或一层薄 bash 包装周期性触发 `/implement-spec`，而不是自建完整 Ralph。**

理由：

1. **仓库类型不匹配 Ralph 的适用边界**：Huntley 本人明说 Ralph 只限 greenfield，"no way in heck" 用在既有代码库；本仓库是 4 子项目的既有大型库， Ralph 的已知失败模式（占位实现、误报未实现、早上 broken codebase）在既有库上会被放大
2. **基础设施已就位**：`.scratch/` issue tracker 的 spec/ticket/blocking/frontier 语义就是 implement-spec 设计的输入格式，零迁移成本；Ralph 需要另起 PROMPT.md + fix_plan.md 平行体系
3. **失败恢复机制更稳**：implement-spec 有 code-review 收尾 + worktree 隔离 + integration branch；Ralph 的恢复靠人早上做 `git reset --hard` 判断
4. **成本更可控**：无每轮重读 spec 的浪费；Ralph 作者自认其循环 wasteful 且需持续人工调参
5. **真正的缺口只是"调度"**：implement-spec 缺的是自动发起，而非执行能力；这个缺口用已安装的 `/loop`（周期触发 slash command）或一个 `while sleep` 外壳即可补上，复杂度远低于维护整套 Ralph 状态文件纪律

落地建议：

1. 保持现有流程：人写 spec/tickets 走 `/grill-with-docs → /to-spec → /to-tickets`（已有）
2. 人离开时：用 `/loop <间隔> /implement-spec <spec 路径>` 或后台 agent 跑 implement-spec；一次只喂一个 spec
3. 保留人工闸门：integration branch 的 PR 由人 review 后合并，不自动合 main —— 对应 Ralph 教训中"早上 broken codebase"的防线
4. 若未来真要做连续多日无人值守（如 greenfield 子项目脚手架期），再评估 Ralph；届时把 `.scratch/` ticket 状态文件直接作为 Ralph 的 fix_plan.md 数据源，避免双轨
