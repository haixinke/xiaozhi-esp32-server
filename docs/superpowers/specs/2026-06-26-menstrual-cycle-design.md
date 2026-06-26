# 完美女友生理期数据设计文档

**日期**：2026-06-26  
**功能**：为 AI 女友（gf 类型）增加自动模拟的生理周期，使其日常交流更具真实感。  
**范围**：`main/manager-api/`（Java 后端）+ `main/miniprogram/`（微信小程序设置页）

---

## 1. 背景与目标

为了增加“完美女友”产品的真实感，希望为女友类型伴侣引入生理周期数据。生理期会影响她的情绪波动和互动方式：经期时更容易疲惫、焦虑、撒娇求关心；非经期时表现与现有逻辑一致。

### 设计约束（已通过头脑风暴确认）

- **数据来源**：系统自动模拟生成，不需要用户手动录入。
- **适用类型**：仅女友类型（`type = 'gf'`），男友类型不受影响。
- **对话影响**：经期时 AI 会主动撒娇、求关心、表达身体不适。
- **UI 可见性**：小程序设置页展示状态；非经期时经期 pill 隐藏，只显示心情 pill。
- **周期参数**：根据女友角色、星座、创建时间等特征生成个性化周期（26~32 天不等）。

---

## 2. 设计决策

采用**数据库轻量扩展方案**：在 `ai_companion` 表中新增三列存储周期参数，复用现有每日心情刷新任务和提示词同步机制，改动最小，真实感足够。

未选择独立模块方案（过度设计）和纯提示词方案（真实感不足）。

---

## 3. 架构设计

### 3.1 数据层

在 `ai_companion` 表中新增以下可空字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `menstrual_cycle_start` | DATE | 本次/最近一次周期开始日期（经期第一天） |
| `menstrual_cycle_length` | INT | 周期长度（天），如 28 |
| `menstrual_period_length` | INT | 经期长度（天），如 5 |

仅对 `type = 'gf'` 的伴侣生成和使用；男友类型这三列保持 NULL。

### 3.2 领域层

新增 `MenstrualCycle` 相关工具：

- `MenstrualPhase` 枚举：`MENSTRUATION`（经期）、`FOLLICULAR`（卵泡期）、`OVULATION`（排卵期）、`LUTEAL`（黄体期）
- `MenstrualCycleUtil.computePhase(startDate, cycleLength, periodLength, today)`：返回当前阶段和周期第几天
- `MenstrualCycleUtil.daysUntilNextPeriod(...)`：返回距离下次经期天数，用于详情展示

### 3.3 业务层

1. **伴侣创建时初始化周期**
   - 仅当 `type = 'gf'` 时生成
   - 根据角色编码、星座、创建时间做哈希/随机，得到 26~32 天的周期长度和 4~6 天的经期长度
   - `menstrual_cycle_start` 设为 `[今天 - cycleLength + 1, 今天]` 范围内的随机日期，让创建时可能处于周期任意阶段

2. **每日心情刷新任务增强**
   - `CompanionMoodRefreshTask` 刷新心情前，计算伴侣当前经期阶段
   - 经期期间：基于默认权重，将 `EXCITEMENT`/`CURIOSITY` 各下调 5，将 `FATIGUE`/`ANXIETY`/`CARE` 各上调 5，总权重保持 100
   - 非经期：使用默认权重

3. **系统提示词同步增强**
   - `CompanionLabels.SYSTEM_PROMPT_TEMPLATE` 新增 `# Menstrual State` 段落
   - 仅当 `type = 'gf'` 且处于经期时，注入详细描述，例如：“你正在经期第 2 天，小腹有点不舒服，容易累，可能会想向用户撒娇求关心”
   - 非 gf 类型或不在经期时，`{{menstrualState}}` 替换为空字符串，不额外强调经期状态

4. **查询接口**
   - 在现有伴侣详情接口的 `CompanionVO` 中新增 `menstrualStatus` 字段
   - 包含：阶段编码、阶段中文、周期第几天、距离下次经期天数、当前心情

### 3.4 表现层

- 小程序设置页羁绊面板下方展示状态 pill：
  - **经期**：左侧 pill 显示经期阶段（如“经期第 2 天”），右侧 pill 显示今日心情（如“心情：疲惫”）
  - **非经期**：左侧经期 pill 隐藏，只显示心情 pill

---

## 4. 数据流

```text
1. 创建女友 (gf)
   └── CompanionServiceImpl.create()
       └── 生成 menstrual_cycle_start / length / period_length
           └── 写入 ai_companion

2. 每日 00:00 刷新
   └── CompanionMoodRefreshTask.refreshMoods()
       └── 分页查询所有伴侣
           └── MenstrualCycleUtil.computePhase()
               ├── 经期 → 提高疲惫/焦虑/关怀权重，降低兴奋/好奇
               └── 非经期 → 使用默认权重
           └── CompanionMood.random(adjustedWeights)
           └── 更新 companion.mood
           └── 同步系统提示词（包含经期状态）

3. 用户打开设置页
   └── GET /companion/{deviceId}
       └── 返回 companion + menstrualStatus + mood
           └── 小程序渲染羁绊面板和 pill

4. 用户聊天
   └── xiaozhi-server 拉取 agent 配置
       └── 系统提示词已包含经期状态描述
           └── LLM 据此调整语气和互动方式
```

---

## 5. 关键组件

### 5.1 后端（manager-api）

| 组件 | 改动 |
|---|---|
| `CompanionEntity` | 新增 `menstrualCycleStart`, `menstrualCycleLength`, `menstrualPeriodLength` |
| `MenstrualPhase` | 新增枚举 |
| `MenstrualCycleUtil` | 新增周期计算工具 |
| `CompanionMood` | 修改：支持按阶段调整权重后随机生成 |
| `CompanionMoodRefreshTask` | 修改：刷新心情前计算经期阶段 |
| `CompanionLabels` | 修改：`SYSTEM_PROMPT_TEMPLATE` 新增 `# Menstrual State` |
| `CompanionServiceImpl` | 修改：创建时初始化周期；同步提示词时注入经期状态 |
| `CompanionVO` | 新增 `menstrualStatus` 字段 |
| Liquibase changeset | 新增 `ai_companion` 三列 |

### 5.2 前端（miniprogram）

| 组件 | 改动 |
|---|---|
| `pages/settings/settings.js` | 加载伴侣详情时读取 `menstrualStatus` |
| `pages/settings/settings.wxml` | 羁绊面板下方条件渲染两个 pill |
| `pages/settings/settings.wxss` | 新增 pill 样式 |

---

## 6. 数据库变更

新增迁移文件（示例）：

```sql
ALTER TABLE ai_companion
    ADD COLUMN menstrual_cycle_start DATE NULL COMMENT '经期开始日期',
    ADD COLUMN menstrual_cycle_length INT NULL COMMENT '周期长度（天）',
    ADD COLUMN menstrual_period_length INT NULL COMMENT '经期长度（天）';
```

所有列均为可空，存量数据无需回填；男友类型列保持 NULL。

---

## 7. 系统提示词示例

在 `SYSTEM_PROMPT_TEMPLATE` 中新增段落：

```text
# Menstrual State
{{menstrualState}}
```

`{{menstrualState}}` 替换规则：

- `type = 'gf'` 且处于经期：替换为详细描述，例如“你正在经期第 N 天，小腹有点不舒服，容易累，可能会想向用户撒娇求关心。可以自然地说‘今天肚子好难受，你哄哄我嘛’。”
- 其他情况（非 gf、或 gf 但不在经期）：替换为空字符串，提示词中不保留任何经期相关内容

---

## 8. 错误处理

- **周期参数缺失**：若三列任一为空，计算返回空值，心情刷新和提示词同步走现有默认逻辑。
- **计算异常**：捕获并记录日志，不影响同页其他伴侣的刷新。
- **类型错误**：所有新逻辑加 `"gf".equals(type)` 守卫，男友类型完全不受影响。
- **时区一致**：全部使用 `Asia/Shanghai`，与现有生日、心情刷新任务保持一致。
- **提示词同步失败**：现有逻辑已捕获异常并记录 warn，经期状态注入不会破坏该行为。

---

## 9. 测试策略

### 9.1 后端单元测试

- `MenstrualCycleUtilTest`
  - 经期第 1 天、经期最后一天、经期后一天
  - 周期长度 26/28/32 天的边界
  - 跨月、跨年场景
- `CompanionMoodTest`
  - 经期阶段权重调整后，疲惫/焦虑出现概率高于默认
  - 非经期阶段权重与默认一致

### 9.2 后端集成测试

- `CompanionServiceImplTest`
  - 创建 gf 伴侣时自动生成周期参数
  - 创建 bf 伴侣时周期参数为空
  - 同步提示词后，系统提示词包含经期状态文本

### 9.3 前端验证

- 经期时显示两个 pill
- 非经期时只显示心情 pill
- 深色模式样式正确

---

## 10. 后续扩展

- 增加痛经程度、情绪标签等更细粒度状态
- 允许用户在设置页校准周期参数（从“自动模拟”升级为“用户可调整”）
- 为男友类型设计其他周期性状态（如精力周期）
- 在聊天页顶部增加 subtle 状态提示

---

## 11. 附录：状态 pill 展示规则

| 场景 | 左侧 pill | 右侧 pill |
|---|---|---|
| 经期 | 显示“经期第 N 天” | 显示“心情：xxx” |
| 非经期 | 隐藏 | 显示“心情：xxx” |

---

*文档由 /superpowers:brainstorming 流程生成，待用户审阅后进入 writing-plans 阶段。*
