# 动态亲密度系统设计

> 状态：设计草案（待评审）
> 日期：2026-07-04
> 范围：`manager-api`（核心算法与调度）+ `miniprogram`（关系等级展示）
> 关联：亲密度已由 xiaozhi-server 每轮通过 `/config/companion-context` 实时注入系统提示词，本设计让其数值动态化。

## 1. 背景与目标

当前 `ai_companion.intimacy`（Float 0.0~1.0）是**静态值**：仅在创建/重塑时由 `relationType` 推导（`deriveIntimacy`），此后无论用户如何与女友互动都不变化。

目标：把亲密度做成**随互动动态变化**的值，使产品：

- **有成就感**：用户能看到关系从"心动"一步步养成到"深爱"。
- **有粘性**：连续陪伴有正反馈，冷落有可感知（但不惩罚过度）的回落，靠失落厌恶驱动每日回访。
- **符合真实恋爱过程**：初期升温快、深处升温慢；久疏则淡，但已建立的感情有韧性、不会归零。

## 2. 关键决策（已确认）

| 决策 | 选择 | 理由 |
|---|---|---|
| 冷落走向 | **会缓慢下降**（宽限期 + 硬下限 + 越亲密越抗跌） | 失落厌恶带来粘性，同时不摧毁已有成就 |
| 起步分 | **新伴侣从「心动」起步（~0.35）**，`relationType` 仅微调；存量伴侣现值不动 | 留出 0.35→1.0 的养成空间，开局又不冷淡 |
| 更新节奏 | **每日批处理**，搭在现有 00:00 定时任务上 | 天然限速防刷、复用现有分页任务、不侵入聊天热路径 |
| 一期信号 | 仅"是否活跃 + 用户消息量 + 会话数" | YAGNI；情感/礼物/纪念日留二期 |

## 3. 等级模型

连续值 `intimacy ∈ [0,1]`，映射为 5 个具名等级：

| 等级 | 区间 | 名称 |
|---|---|---|
| Lv1 | [0.0, 0.2) | 初识 |
| Lv2 | [0.2, 0.4) | 心动 |
| Lv3 | [0.4, 0.6) | 暧昧 |
| Lv4 | [0.6, 0.8) | 恋人 |
| Lv5 | [0.8, 1.0] | 深爱 |

等级同时用于：
- **系统提示词注入**：`CompanionServiceImpl.renderIntimacy` 从现有 4 档改为与上表一致的 5 档，行为描述沿用（初识温柔略带分寸 → 深爱毫无保留）。`buildRealtimeContext` 的"关系亲密度"文本随之变化。
- **小程序关系卡**：等级名 + 进度条。

## 4. 起步分（创建时）

`deriveIntimacy` 改为从「心动」档起步，`relationType` 仅做 ±0.03 微调（均落在 [0.2,0.4) 内）：

| relationType | 值 |
|---|---|
| childhood（青梅竹马） | 0.38 |
| loveAtFirst（一见钟情） | 0.35 |
| bickering（欢喜冤家） | 0.32 |
| 其他/缺省 | 0.35 |

**重要行为修正**：`update()`（重塑）路径当前会在 `relationType` 变化时调用 `deriveIntimacy` **重置** intimacy。动态化后这会**抹掉用户已养成的进度**，因此：

- 创建（`create`）：使用上表起步分。
- 重塑（`update`）：**不再**因 `relationType` 变化而重置 intimacy，保留已养成的数值。

存量伴侣：迁移不回填、不重置，保持现有 intimacy 数值；新逻辑仅对新建生效，历史数值随后续每日批处理自然演进。

## 5. 核心算法（每日批处理，按伴侣）

每天为每个伴侣，基于"昨天"的互动计算一次。

### 5.1 当日投入度（饱和，防刷）

设 `u` = 昨天该伴侣对应 agent 的**用户消息数**（`chat_type=1`）：

```
E = min(1, ln(1 + u) / ln(1 + 15))
```

手感：u=1→0.25，u=3→0.50，u=7→0.75，u≥15→1.0（聊再多封顶）。

> 会话数（`COUNT(DISTINCT session_id)`）一期作为可选微调项预留，默认权重 0（仅采集不参与计算），便于二期无痛接入"分散在多次对话"的奖励。

### 5.2 活跃日 → 涨（渐近增长）

```
streakFactor = 1 + min(streak - 1, 6) × 0.08        // 连续第 7 天起 ×1.48 封顶
gain         = min(0.05, 0.06 × E × (1 - intimacy) × streakFactor)
intimacy     = min(1.0, intimacy + gain)
```

`(1 - intimacy)` 使"初识→暧昧"快、"恋人→深爱"慢，还原真实恋爱曲线；连续陪伴有加成 → 习惯养成 → 粘性；单日硬上限 0.05 防运行时异常放大。

### 5.3 冷落日 → 降（宽限 + 韧性 + 硬下限）

```
若"连续未活跃天数 > 2"（宽限期 2 天）:
    decay    = 0.012 × (1 - 0.4 × intimacy)          // 越亲密掉得越慢
    intimacy = max(0.15, intimacy - decay)           // 硬下限 0.15：相识过就不再跌回陌生
```

（`连续未活跃天数 = 今天 - last_active_date`，无需额外字段。streak 的归零由 §5.4 统一处理：任何未活跃日都断连。）

手感示例：恋人（0.70）失踪 2 周（宽限后约 12 个衰减日）→ 约 -0.11 → 跌到 ~0.59；恢复稳定聊天约两周可回补。既有失落感又不摧毁成就。

### 5.4 连续天数（streak）维护

- 活跃且 `last_active_date == 昨天 - 1`：`streak += 1`
- 活跃但断档：`streak = 1`
- 未活跃：`streak = 0`

## 6. 数据与调度

### 6.1 表结构变更（Liquibase 新 changeset，`ai_companion`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `last_active_date` | DATE | 最近活跃日 |
| `active_streak` | INT default 0 | 连续活跃天数 |
| `intimacy_updated_date` | DATE | 防同日重复处理 |

> 遵循项目铁律：新增 changeset + 带日期 SQL 文件，不改历史 changeset。

### 6.2 批处理流程

并入每日 00:00 任务（`CompanionMoodRefreshTask` → `refreshAllMoods` 之后，新增 `refreshAllIntimacy` 或在同一分页循环内处理）：

1. 计算"昨天"日窗（Asia/Shanghai），构造与存库同格式的 ISO 边界串：
   `>= '2026-07-03T00:00:00.000+08:00'` 且 `< '2026-07-04T00:00:00.000+08:00'`。
   （`created_at` 存储格式为 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`，偏移固定 `+08:00`、定宽，字典序即时序，字符串范围查询成立。）
2. 一条聚合查询拿到活跃 agent 统计：
   ```sql
   SELECT agent_id,
          COUNT(*)                     AS user_msgs,
          COUNT(DISTINCT session_id)   AS sessions
   FROM ai_agent_chat_history
   WHERE chat_type = 1
     AND created_at >= ? AND created_at < ?
   GROUP BY agent_id
   ```
   → `Map<agentId, {userMsgs, sessions}>`。未出现的 agent 即"昨天无用户消息"。
3. 复用现有分页遍历伴侣（单页 ≤ 500）。对每个伴侣：
   - 幂等保护：`intimacy_updated_date == 今天` 则跳过。
   - 经 `deviceService.getAgentIdByDeviceId` 取 agentId，在 Map 中查昨日统计。
   - 活跃（userMsgs>0）：更新 streak、按 5.1/5.2 涨、`last_active_date = 昨天`。
   - 不活跃：按 5.3（过宽限期才）降、`streak = 0`。
   - 写回 `intimacy / last_active_date / active_streak / intimacy_updated_date`。
   - 单条失败 try/catch 隔离，不影响同页其他记录；日志记录总数/成功/失败。
4. **无需重同步提示词**：亲密度已走实时注入，改完库值下一句话女友语气即随之变化。

## 7. 产品出口（成就感 & 粘性）

### 7.1 只读接口

`GET /companion/intimacy?deviceId=`（用户鉴权 + 归属校验），返回：

```json
{
  "intimacy": 0.42,
  "level": 3,
  "levelName": "暧昧",
  "progressToNext": 0.10,
  "nextLevelName": "恋人",
  "streak": 5,
  "lastActiveDate": "2026-07-03"
}
```

`progressToNext = (intimacy - 档下限) / (档上限 - 档下限)`。

### 7.2 小程序展示

- 关系等级卡 + 进度条 + "已连续陪伴 N 天"。
- **升级庆祝**：小程序缓存上次看到的 level，发现变高即弹"关系升级"动画（服务端主动推送 ⑤ 已搁置，此处用客户端对比实现，零后端依赖）。

## 8. 一期不做（YAGNI，留二期）

作为"事件奖励层"叠加，不进一期：送礼加分（已有 ItemService/支付可接）、纪念日/生日陪伴、深夜安慰、经期关怀响应、消息情感倾向分析。

## 9. 测试计划

**纯函数单测**（无需 DB）：
- 投入度曲线 `E`：u=0/1/3/7/15/30 的取值与封顶。
- 增长 `gain`：低亲密快、高亲密慢；单日上限 0.05；streakFactor 边界（第 1/7/10 天）。
- 衰减：宽限期内不降；过期后按韧性衰减；硬下限 0.15 生效。
- 等级映射 + `progressToNext` 边界。
- 起步分（各 relationType）与"重塑不重置 intimacy"。

**批处理集成测**（mock 聚合结果 + 分页）：
- 活跃升、连续 streak 累加。
- 断档 streak 归 1、跨宽限衰减、streak 归 0。
- 同日幂等跳过。
- 单条异常隔离。

## 10. 实现风险与缓解

| 风险 | 缓解 |
|---|---|
| `created_at` 若非预期 ISO 格式/偏移 | 实现首步用真实数据核验一条边界查询；必要时改用范围过滤或补索引 |
| `ai_agent_chat_history` 无 `created_at` 索引导致日窗查询慢 | 评估加 `(chat_type, created_at)` 索引（独立 changeset） |
| 存量伴侣缺少新字段初值 | 新列可空/默认 0；首次批处理按"无 last_active"路径安全处理 |
| 起步分下调影响现有用户观感 | 仅对新建生效，存量不动 |
