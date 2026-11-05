# 游戏每日限额统一 + 一起画会话瘦身

Status: resolved

## Problem Statement

后端"游戏管理"（`ai_game` 配置表 + 智控台游戏管理页）支持为每个游戏配置 `daily_limit`（每日次数上限），但目前只有"一起画"（DRAW_TOGETHER）真正读取并强制了这个配置。"听你说"（LISTEN_TO_YOU）用的是独立的 Spring 属性 `listen.daily-group-limit`，按 `DISTINCT group_id` 计数，且代码注释自认存在 count-then-insert 竞态可能超发。结果是：运营在游戏管理后台改 `daily_limit` 只对一起画生效，对听你说无效；两个游戏的限额语义（用户级 vs 设备级、按局次 vs 按 group）也不一致。

同时，"一起画"会话表 `ai_drawing_session` 存了一批属于"星星钱包事实"或"定价快照"的字段（earnStars/consumeStars/awardedStars/balanceAfter/rewardAvailable/petId）。这些字段要么与 `ai_star_daily_reward`/`ai_star_transaction` 冗余，要么对应一个已被产品否定的"开局快照定价"决定（现决定：按完成时配置结算，中途改配置就按新的来）。

## Solution

两件事并行：

1. **统一游戏每日限额**：抽一个 `GamePlayLimitService`，从 `ai_game.daily_limit` 读限额、按用户当日局次计数、超限抛错。听你说从独立属性迁移到这套机制，与一起画对齐（用户级、按局次、行锁防竞态）。
2. **一起画会话瘦身**：`ai_drawing_session` 删掉定价快照与钱包事实字段，只保留"局次归属、幂等键、参考图、计次时间、完成态、发奖幂等标记"。结算（扣费/发奖）全部收敛到 complete 接口、按完成时配置执行。一起画当前不是消费型游戏，开局不扣费也不发奖，结算都在 complete 完成；薅羊毛场景（反复开局不完成）不在本次考虑范围。

## User Stories

1. As 运营, I want 在游戏管理后台为一个游戏设置每日次数上限, so that 该上限对该游戏的所有玩家生效.
2. As 运营, I want 改的每日次数上限对听你说和一起画都生效, so that 我不需要为不同游戏改不同的配置入口.
3. As 运营, I want 把每日次数设为 0 表示不限次数, so that 我可以临时放开某个游戏.
4. As 玩家, I want 达到每日次数上限后开局被拦下并看到明确提示, so that 我知道今天玩完了、明天再来.
5. As 玩家, I want 限额按我（用户）计算而不是按设备/宠物, so that 我换宠物或换入口刷不出额外次数.
6. As 玩家, I want 一起画开局时不被扣星星, so that 我只有在完成时才发生结算.
7. As 玩家, I want 一起画完成时按当时的配置结算星星, so that 我拿到的是最新规则下的奖励.
8. As 玩家, I want 断网或重复点击不会重复扣费或重复发奖, so that 我的星星不会因为重试而出错.
9. As 玩家, I want 一起画开局时不被承诺一个可能不兑现的星数, so that 结算金额以完成时为准、不会产生预期落差.
10. As 后端开发者, I want 一个统一的限额服务入口, so that 新增游戏接入限额时不用各写一套计数逻辑.
11. As 后端开发者, I want 会话表只存局次事实、不存钱包事实, so that 星星余额只以 star_transaction/star_account 为唯一来源、不产生双写不一致.
12. As 后端开发者, I want 听你说的限额并发安全, so that 快速连点不会越过每日上限.
13. As 维护者, I want ADR 0011 反映"按完成时配置结算"的最新决定, so that 后来读代码的人不会被过时的快照结论误导.

## Implementation Decisions

### 模块改动

- **drawing 模块（manager-api）**：会话实体瘦身；`start` 不再读快照、不再扣费、不再返回金额；`complete` 按当前配置统一结算。
- **listen 模块（manager-api）**：删除 `listen.daily-group-limit` 属性读取；限额校验迁移到 `GamePlayLimitService`。
- **game 模块（manager-api）**：新增 `GamePlayLimitService`。`ai_game` 表结构不变。
- **star 模块（manager-api）**：不变。`ai_star_daily_reward` 仍只是"每日奖励资格/已发放"记录，不是计数器。

### 接口契约

- `GamePlayLimitService.checkDailyLimit(Long userId, String gameType)`：从 `ai_game.daily_limit` 读限额（0 = 不限），按用户当日局次计数，超限抛 `RenException`。内部按 `gameType` 分发到对应计数查询（一起画数 `ai_drawing_session`，听你说数 `ai_listen_answer`）。
- `POST /app/drawing/start`：入参删 `expectedConsumeStars`；响应 `DrawingStartVO` 只保留 `sessionId + referenceId`，删 `earnStars/consumeStars/chargedStars/rewardAvailable`。职责只剩：限额校验、选参考图、写一行会话。
- `POST /app/drawing/{sessionId}/complete`：读 `ai_game` 当前 `earnStars/consumeStars`；`consume>0` 则 `stars.consume()`，`earn>0` 则 `dailyRewards.claimWithDecision()` 发奖；记 `reward_status`、写 `completed_at`。响应的 `awardedStars/balance` 从结算返回值与 `stars.balance()` 实时取，不落会话表。

### Schema 变更（一个 Liquibase changeset）

`ai_drawing_session` 由 14 列瘦到 8 列。删除：`pet_id`（用户级行为，幂等只看 requestId）、`earn_stars`、`consume_stars`（无存快照，complete 按当前配置结算）、`reward_available`（可由 ai_star_daily_reward 实时查）、`awarded_stars`、`balance_after`（钱包事实，以 star_transaction 为唯一来源）。保留：`id, user_id, request_id, reference_id, started_day, started_at, completed_at, reward_status`。

唯一键 `uk_drawing_user_request (user_id, request_id)` 保留（幂等防重放）；`idx_drawing_user_day (user_id, started_day)` 保留（每日计次）。

### 具体行为

- **幂等**：`start` 按 `requestId` 幂等，同 requestId 直接回放已有 session（不再比对 petId）。`complete` 靠 `reward_status` + `claimWithDecision` 的每日首发资格双重保证不重复发奖。
- **听你说计数口径**：从 `DISTINCT group_id` 改为按用户当日起的局次，与一起画对齐。
- **并发**：一起画已用 `lockUser`（SELECT FOR UPDATE）；听你说迁移后同样走该模式，消除注释里自认的 count-then-insert 超发竞态。
- **ADR 0011 改写**：把"局次持久化开局费用/奖励快照、运营改配置不影响在飞局次"改为"扣费/发奖均在 complete 按当前配置结算；会话不存定价快照与钱包余额"。

## Testing Decisions

好测试只测外部行为，不测实现细节：给定用户与游戏，断言开局/完成接口的返回与星星余额、计次、限额的可见效果，不断言内部 SQL 或私有方法。

- **GamePlayLimitService**：单测覆盖 限额为 0 不限、未超限放行、达上限抛错、按 gameType 正确分发计数。
- **一起画**：定向测 start 幂等（同 requestId 不重复开局）、complete 按当前配置结算、重试 complete 不重复发奖、瘦身后实体不再读写已删字段。
- **听你说**：定向测限额从 ai_game.daily_limit 读取并生效、达上限拦截、并发请求不越限。

测试数据不得硬编码真实业务字典（如 DRAW_TOGETHER）；用专用测试字典项或 mock，避免撞 dev DB 唯一键。先跑定向测试，再跑全量确认无回归。

## Out of Scope

- MEET_YOU / TRAVEL / WORK 等尚无实现的游戏接入限额服务（等有实现时按同模式接入）。
- 运营报表/活跃趋势分析（计数表天然可聚合，但本次不建分析结构）。
- 一起画薅羊毛场景（反复开局不完成）的额外防护。
- 听你说 reward 侧逻辑（`GameStarRewardServiceImpl` 不变，仅限额校验迁移）。
- 参考图机制（后端随机选、前端按 id 找本地图）不变。

## Further Notes

- 关键边界：`ai_star_daily_reward` 唯一键 `(user_id, source_type, source_key, reward_date)` 决定它每用户每游戏每天最多一行、仅首次发奖写入，因此**不能**充当"玩了几次"的计数器；次数的事实来源是各游戏自己的局次表。这是"直接存进 ai_star_daily_reward 就行"这一直觉被否定的根本原因。
- 领域词汇遵循根 `GLOSSARY.md` 与 ADR 0008/0009（星星钱包/流水）、ADR 0011（一起画用户级局次）。

## Comments

- 2026-11-05 实施确认：听你说一次整组提交计 1 局，重试不计次。现有答案表每题一行，且同组不可重复游玩，因此保留 `COUNT(DISTINCT group_id)` 作为局次数查询；不将题目行数当成局次数。本次统一配置来源、用户归属、日界和并发锁，不新增听你说局次表。
- 2026-11-05 测试边界已确认：`GamePlayLimitService` 公共接口、一起画 `start/complete`、听你说 `submitAnswers`，配合现有小程序测试。代码审查基点为 `541f4b63917a5a72830d5812619f2ff5da2cfdd5`，聚焦本规格相关变更。
- 2026-11-05 已完成：后端全量 1,288 项测试与小程序 51 个测试文件全部通过；三个关键服务行覆盖率 95.65%–100%、分支覆盖率 85.94%–90.91%。隔离 MySQL 验证迁移、并发限次、结算回滚和重试幂等；规范、规格及前端审查无遗留问题。未运行微信真机交互测试。
