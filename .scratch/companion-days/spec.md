# 连续陪伴天数 (Companion Days) — 后端

Status: ready-for-agent

## Problem Statement

用户想看到"自己已经和宠物连续相伴多少天"。产品口径：用户每天打开小纸条即算陪伴一天，连续打开累计，中间一旦断开就从 1 重新计算，断档当天显示 0。该数值当前系统无处可查、无接口下发，小程序主页无法展示。

## Solution

利用已有的「小纸条已打开」记录（`ai_daily_note_opened`，用户级、按完整日期、永久保留），在查询时现算连续陪伴天数，不新增存储。结果随宠物信息接口（PetVO）下发给小程序。星星余额不在本需求重复造通道，前端继续用现有 `GET /star/balance`。

本期只做后端（manager-api），小程序侧消费后续单独立项。

## User Stories

1. As a 小程序用户, I want 宠物信息里带连续陪伴天数, so that 主页能展示我和宠物相伴了多久
2. As a 小程序用户, I want 今天打开小纸条后天数+1, so that 我感受到连续陪伴的积累
3. As a 小程序用户, I want 中断后天数从 1 重新开始, so that 规则诚实反映"连续"语义
4. As a 小程序用户, I want 断档当天（还没打开小纸条时）看到昨天的连续数而不是立即清零, so that 我不会因为白天还没打开就误以为 streak 已断
5. As a 小程序用户, I want 切换当前宠物时连续天数不变, so that 天数是我和"蛋宝宝"整体的陪伴而非单只宠物
6. As a 小程序用户, I want 换设备登录后天数保持, so that 陪伴记录跟人走不跟设备走
7. As a 新用户, I want 从未打开小纸条时天数为 0, so that 起点清晰
8. As a 老用户, I want 上线即按历史打开记录算出连续天数, so that 我过去的陪伴不被清零
9. As a 小程序用户, I want 跨天界（北京时间零点）规则稳定, so that 深夜使用不会出现天数抖动
10. As a 后端服务, I want 不新增表、不改奖励链路, so that 该功能对星星罐结算零风险
11. As a 运营, I want 该字段纯查询计算, so that 不需要运营配置或后台维护
12. As a 开发者, I want 连续陪伴天数与「小纸条连续奖励」的规则解耦, so that 奖励金额规则变更不影响陪伴天数口径

## Implementation Decisions

### 领域定义（已写入 GLOSSARY.md「连续陪伴天数 (Companion Days)」）

- **用户级归属**：按 user_id 计算，与宠物实例无关。多宠物用户（NFC 渠道跨原型领养）切换「当前宠物」时数值不变
- **现算不落库**：不新增表、不新增列。`ai_daily_note_opened(user_id, note_date)` 即全部数据源，查询时算出
- **与「小纸条连续奖励」解耦**：共享数据源，但定义独立。奖励规则（11 起、每天 +1、封顶 17、断回 11）的任何变更不影响本字段；奖励下线本字段仍成立
- **断档语义（streak 锚定"截至昨天"）**：与小纸条奖励判定同构——
  - 今天已打开：锚定今天，从今天往回数连续日期
  - 今天未打开：锚定昨天，从昨天往回数连续日期；昨天无记录则为 0
  - 即"断档"在当天结束才生效，白天未打开仍显示昨天的连续数
- **历史数据全量参与**：无上线锚点，老用户上线即按全部历史记录算出
- **运营漏配内容的风险接受**：`markOpened` 在当日小纸条不存在时不写记录，用户当天打开弹层不会产生记录，连续天数会断。接受现状语义，不由本功能兜底

### 模块改动

- **dailynote 模块**：新增「连续陪伴天数」查询能力（服务接口 + 实现），入参 userId，出参 int 天数。日期判定复用现有 `dailyNoteClock`（Asia/Shanghai）时钟 Bean，与小纸条已打开、每日奖励共用同一时钟
- **查询实现**：对 `ai_daily_note_opened` 按 user_id 取最近 N 天（上限参数化，如近 400 天）的 note_date 降序列表，Java 侧循环比对连续性。`DailyNoteOpenedDao` 现为空接口、无 XML mapper，需新增一个降序日期查询方法（注解或 wrapper 均可，按模块惯例）。已有索引 `uk(user_id, note_date)` 直接支撑该查询，无需加索引。**不用窗口函数**——开发库为 OceanBase MySQL 兼容模式，保持 SQL 最保守兼容。单用户单表索引查询，数据量小，无分页批量负担
- **PetVO 下发**：`PetVO` 增加 `companionDays`（Integer）字段。已查明**唯一组装点**：`PetServiceImpl.toVO(PetEntity)`（手工 setter，无 copyProperties/builder），全部用户侧出口（领养/破壳/详情/列表/改昵称/换场景/NFC 链路）都经此一处——在 toVO 内填充即全出口覆盖，无遗漏面
- **星星余额**：复用现有 `GET /star/balance`（StarController），本需求不新增余额通道、不在 PetVO 加余额字段

### 不做的事

- 不新增 Liquibase changeset（无 schema 变更）
- 不动 `markOpened`、不动 `StarDailyRewardService`（奖励链路零改动）
- 不引入缓存层（计算轻量；未来 QPS 有压力再议）

## Testing Decisions

好测试的标准：只测外部行为（给定打开记录集合与时钟，断言返回天数），不断言实现细节（SQL 形态、内部循环）。

### 测试模块

- **dailynote 连续陪伴天数服务**：集成测试，先例 `DailyNoteOpenedServiceTest`——真实 dev 库、专用超大用户 ID（9_999_xxx 段）、可替换 Clock 拨到 2160 年专用日期段、每用例自造数据、AfterEach 物理清理
- **PetVO 填充**：在 PetService 相关测试上补断言（先例 `PetServiceImplAdoptTest` 等），验证 `toVO` 出口带正确 companionDays；不重复 dailynote 侧的口径用例。注意 `listByUserId` 出口会对每只宠物各算一次同一用户值，测试断言一致即可
- **Liquibase 契约**：`DailyNoteLiquibaseContractTest` 显式断言 `ai_daily_note_opened` 无 note_date 独立索引——本需求不加索引，该契约保持绿

### 关键用例

1. 无任何打开记录 → 0
2. 仅今天打开 → 1
3. 昨天+今天连续 → 2
4. 今天未打开、昨天有连续 3 天 → 3（断档当天显示昨天的数）
5. 今天未打开、昨天无记录、前天有记录 → 0（断档已生效）
6. 历史一段连续 + 断一天 + 今天打开 → 1（只看最近连续段）
7. 跨月/跨年连续日期 → 正确累计
8. 时钟拨到日界附近（23:59 / 00:01）→ 按 Asia/Shanghai 日界判定
9. 同一用户多条记录乱序插入 → 结果正确（不依赖物理顺序）
10. 测试数据隔离：不硬编码真实用户数据，AfterEach 清理

## Out of Scope

- 小程序前端消费与 UI 展示（后续单独立项）
- 星星余额下发方式变更（已有 `/star/balance`）
- 智控台展示该字段
- 断档容错（运营漏配内容时补记录）
- 缓存/性能优化
- 按宠物维度的陪伴天数（产品已定为用户级）

## Further Notes

- 「连续陪伴天数」与「小纸条连续奖励」共享数据源但定义独立，已在 GLOSSARY.md 记录，避免未来奖励规则调整时被误绑
- 风险点：运营某日漏配小纸条内容 → 当日不产生打开记录 → 全体用户断档。已知情接受，属运营流程保障范畴

## Comments

- 2026-11-01 实施完成：commit a90cb8f2（业务）+ 8895a527（graphify）。双轴评审 4 项修复落地（NFC 测试 NPE 防护、LOOKBACK 注释、PetVO 全角标点、GLOSSARY 断档语义对齐）。156/156 定向测试绿。预存失败 9 项（MessageUtils.messageSource 反射）与本功能无关，干净树同样挂。
