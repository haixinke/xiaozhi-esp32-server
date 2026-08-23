# 开发流程：mattpocock skills 工作流

本仓库使用 mattpocock-skills 插件驱动开发流程。核心理念：小而可组合的 skill，不接管流程，由人驱动编排。

## 两类 skill

- **User-invoked**：只能人手动 `/xxx` 触发，负责编排流程（grill-with-docs、to-spec、to-tickets、implement、triage、wayfinder 等）
- **Model-invoked**：agent 可自动调用，承载纪律（tdd、diagnosing-bugs、grilling、domain-modeling、code-review 等）

User-invoked skill 可以调 model-invoked skill，但不可调另一个 user-invoked skill — 编排链始终由人驱动。

## 主线：想法到代码

```
想法 → /grill-with-docs → /to-spec → /to-tickets → /implement
       (对齐意图)        (固化spec)   (拆任务)      (执行)
```

### 1. /grill-with-docs — 拷问对齐

每次做改动前使用。agent 反向连续追问设计，把歧义全部挤出；同时沉淀术语到 `CONTEXT.md`、重大决策写 ADR（`docs/adr/`）。

- 回答时使用项目领域术语，不用泛词 — 术语越准，后续 spec/ticket/代码命名越一致
- 出口标准：聊到没有歧义分支为止

### 2. /to-spec — 对话固化成 spec

不再访谈，直接把已有对话合成 spec，发布为 GitHub issue，自动打 `ready-for-agent` 标签。

写之前先确认**测试接缝**：优先已有接缝、越高越好、理想数量为一个。

spec 结构：Problem Statement → Solution → User Stories（长列表）→ Implementation Decisions（模块/接口/架构决策，不写文件路径和代码）→ Testing Decisions（测哪个接缝、只测外部行为）→ Out of Scope。

### 3. /to-tickets — 拆 tracer-bullet 票

大 spec 拆成垂直切片 ticket，发布到 GitHub（本仓库 tracker 配置为 GitHub Issues）：

- 每张票切穿所有层（schema/API/UI/测试），窄但完整，单独可演示
- 每张票声明 **Blocked by** 阻塞边，用 GitHub 原生依赖关系连接，按依赖序发布
- 每张票大小适配单个新会话窗口
- 预重构优先："让改动容易，再做容易的改动"
- 宽幅重构例外：机械性大爆炸改动走 expand–contract（先并存、分批迁移、最后删旧）
- 拆完后交互确认粒度和依赖边，批准后才发布
- 小 spec 可跳过本步，直接进 implement

### 4. /implement — 执行

```
/mattpocock-skills:implement 实现 #5
```

- **建议明确给 ticket/issue 编号** — skill 没有自动找票逻辑，不给编号 agent 需自行猜测
- 在 spec 约定的接缝处跑 TDD（红绿重构）
- 频繁跑类型检查和单测试文件，全套测试只跑最后一次
- 收尾自动 /code-review，然后提交到当前分支
- 多张票时按 frontier 顺序逐张喂：先喂无阻塞的，干完再喂下一张，不一次塞整个票堆

## issue 的关闭

implement **不自动关 issue**。正常闭环靠 commit/PR 关键字：

```
/implement #5 → 提交信息带 Closes #5 → push/合并 → GitHub 自动关
```

也可手动 `gh issue close <n> --comment "..."`。ticket 上的 `ready-for-agent` 标签随 issue 关闭自然失效。

## 支线 skill

### /triage — issue 分诊台

处理进来的 issue：验证 bug 真实性、查冗余实现、必要时 grill 补全，然后落到状态机：

- 类别：`bug` / `enhancement`
- 状态：`needs-triage` → `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`

状态由 GitHub label 承载，流转由 agent 执行，决策权在 maintainer。标签映射见 `docs/agents/triage-labels.md`。

用法：`/triage`（列出待关注）、`/triage 看看 #42`、`/triage 把 #42 移到 ready-for-agent`。

### /prototype — 一次性原型

设计拿不准时使用："这个状态机跑起来感觉对吗？"或"这 UI 该长什么样？"

- 逻辑问题 → 单个可分享 HTML 文件，按钮自由操作 + 分步引导走查
- UI 问题 → 同一路由挂几版不同 UI，URL 参数切换

纪律：即用即弃，不写测试、不做持久化、每次操作后展示完整状态。验证完把结论合进真代码，原型提交到 throwaway 分支并在 issue 留指针，不进 main。

### /wayfinder — 超单会话大工程导航

工作量超单个 agent 会话、且路线模糊时使用。建一张 map issue（`wayfinder:map` 标签），拆成待**决策**的子 ticket（research/prototype/grilling/task 四类），用原生依赖标阻塞。每个会话：读 map → 查 frontier → 认领一张 → 解决 → 关闭并回写决策 gist 到 map。决策清零后交给主线（to-spec → implement）执行。

## 组合关系

```
新 issue 进来 ──► /triage ──► ready-for-agent ──► /implement
                      │
设计拿不准 ──────► /prototype（也可能是 wayfinder 的一张 ticket）
                      │
大块模糊工作 ────► /wayfinder ──► 决策清零 ──► /to-spec → /implement
```

## 操作注意

- gh CLI 默认仓库已设为 fork `haixinke/xiaozhi-esp32-server`；如解析到上游 xinnan-tech，用 `gh repo set-default haixinke/xiaozhi-esp32-server` 修正
- 配置只需跑一次 `/setup-matt-pocock-skills`；之后可直接编辑 `docs/agents/*.md`
- tracker 操作约定见 `docs/agents/issue-tracker.md`
