# PowerMem 艾宾浩斯遗忘曲线算法详解

## 概述

PowerMem 的智能记忆管理基于**艾宾浩斯遗忘曲线（Ebbinghaus Forgetting Curve）**算法，自动评估记忆的重要性、计算遗忘因子，并在适当的时候删除"应该遗忘"的记忆。

**核心思想**：模拟人类大脑的记忆机制，重要信息长期保留，不重要信息逐渐遗忘。

---

## 系统架构

### 三大核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                    1. ImportanceEvaluator                   │
│                    (重要性评估器)                             │
│  - 评估记忆内容的重要性分数 (0.0 - 1.0)                       │
│  - 基于 LLM 或规则引擎                                       │
│  - 考虑 6 个维度：相关性、新颖性、情感影响、可操作性、事实性、个人化 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                  2. EbbinghausAlgorithm                     │
│                  (艾宾浩斯算法核心)                           │
│  - 计算记忆的保留率（retention）                              │
│  - 生成复习计划（review schedule）                            │
│  - 判断是否应该遗忘（should_forget）                         │
│  - 判断是否应该升级（should_promote）                        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│              3. EbbinghausIntelligencePlugin                 │
│              (智能插件钩子)                                   │
│  - on_add: 添加记忆时评估重要性并生成元数据                   │
│  - on_search: 搜索记忆时检查并删除应该遗忘的记忆              │
│  - on_get: 访问单个记忆时更新访问计数和状态                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. 重要性评估（Importance Evaluation）

### 1.1 评估维度

ImportanceEvaluator 从 6 个维度评估记忆的重要性：

| 维度 | 权重 | 评估标准 |
|------|------|----------|
| **相关性（Relevance）** | 30% | 与用户需求和兴趣的相关程度 |
| **新颖性（Novelty）** | 20% | 信息的新颖程度和独特性 |
| **情感影响（Emotional Impact）** | 15% | 情感意义和重要性 |
| **可操作性（Actionable）** | 15% | 信息的可执行性和实用性 |
| **事实性（Factual）** | 10% | 事实的可靠性和准确性 |
| **个人化（Personal）** | 10% | 对用户个人的重要程度 |

### 1.2 LLM 评估流程

当启用 LLM 时，评估流程如下：

```python
# 步骤 1: 构建评估提示词
prompt = f"""
Content to evaluate: "{content}"

Please evaluate the importance of this content based on:
- Relevance: How relevant is this information?
- Novelty: How new or unique is this information?
- Emotional Impact: How emotionally significant?
- Actionability: How actionable or useful?
- Factual Value: How factual and reliable?
- Personal Significance: How personally important?

Respond with JSON:
{{
    "importance_score": <float 0.0-1.0>,
    "reasoning": "<explanation>",
    "criteria_scores": {{
        "relevance": <float>,
        "novelty": <float>,
        "emotional_impact": <float>,
        "actionable": <float>,
        "factual": <float>,
        "personal": <float>
    }}
}}
"""

# 步骤 2: 调用 LLM
response = llm.generate_response(messages)

# 步骤 3: 解析 JSON 响应
result = json.loads(response)
importance_score = result["importance_score"]  # 例如: 0.75
```

### 1.3 规则评估回退

当 LLM 不可用时，使用基于规则的评估：

```python
def _rule_based_evaluation(content, metadata, context) -> float:
    score = 0.0

    # 1. 长度因子
    if len(content) > 100:
        score += 0.1
    elif len(content) > 50:
        score += 0.05

    # 2. 关键词重要性
    important_keywords = [
        "important", "critical", "urgent", "remember",
        "preference", "like", "dislike", "love", "hate",
        "password", "secret", "private"
    ]
    for keyword in important_keywords:
        if keyword in content.lower():
            score += 0.1

    # 3. 问号和感叹号
    if "?" in content:
        score += 0.05
    if "!" in content:
        score += 0.05

    # 4. 元数据因子
    if metadata:
        if metadata.get("priority") == "high":
            score += 0.2
        elif metadata.get("priority") == "medium":
            score += 0.1

    return min(score, 1.0)
```

---

## 2. 艾宾浩斯算法核心（Ebbinghaus Algorithm）

### 2.1 遗忘曲线公式

**核心公式**：

```
保留率 R = e^(-t/S)

其中：
- R (Retention): 记忆保留率 (0.0 - 1.0)
- t (Time): 经过的时间（小时）
- S (Strength): 记忆强度参数
- e: 自然对数底数 (2.71828...)
```

**记忆强度 S 的计算**：

```python
S = 24 * decay_rate * memory_type_multiplier

其中：
- decay_rate: 遗忘率（默认 0.1）
- memory_type_multiplier: 记忆类型倍数
  - working memory（工作记忆）: 2.0（遗忘最快）
  - short_term memory（短期记忆）: 1.5
  - long_term memory（长期记忆）: 1.0（遗忘最慢）
```

### 2.2 记忆分类阈值

根据重要性分数，记忆被分为三类：

| 重要性分数范围 | 记忆类型 | 特征 | 遗忘速度 |
|---------------|----------|------|----------|
| 0.0 - 0.3 | Working Memory（工作记忆） | 临时信息，不重要 | 最快（2x decay_rate） |
| 0.3 - 0.8 | Short-term Memory（短期记忆） | 中等重要，可能有用 | 中等（1.5x decay_rate） |
| 0.8 - 1.0 | Long-term Memory（长期记忆） | 非常重要，长期保留 | 最慢（1x decay_rate） |

**默认阈值**（可配置）：

```yaml
intelligent_memory:
  plugin:
    ebbinghaus:
      working_threshold: 0.3    # 工作记忆阈值
      short_term_threshold: 0.6  # 短期记忆阈值
      long_term_threshold: 0.8   # 长期记忆阈值
```

### 2.3 遗忘计算示例

假设：
- 遗忘率 `decay_rate = 0.1`（默认）
- 记忆类型 = `working_memory`（临时信息）
- 重要性分数 = `0.2`（不重要）

**时间线**：

```
创建时 (t=0):
  保留率 R = e^0 = 1.0 (100%)
  记忆类型 = working_memory

1 小时后 (t=1):
  S = 24 * 0.1 * 2.0 = 4.8
  R = e^(-1/4.8) = e^(-0.208) = 0.812 (81.2%)

6 小时后 (t=6):
  R = e^(-6/4.8) = e^(-1.25) = 0.287 (28.7%)
  ⚠️ 低于 working_threshold (0.3) -> 标记为应该遗忘
```

### 2.4 复习计划（Review Schedule）

基于艾宾浩斯曲线，生成记忆复习时间点：

```python
# 默认复习间隔（小时）
review_intervals = [1, 6, 24, 72, 168]  # 1小时、6小时、1天、3天、7天

# 根据重要性调整间隔
# 重要性越高，复习间隔越短（复习越频繁）
adjusted_intervals = []
for interval in review_intervals:
    adjusted_interval = interval * (1 - importance_score * 0.3)
    adjusted_intervals.append(max(adjusted_interval, 0.5))  # 最小 0.5 小时

# 示例：
# 重要性 0.9 (非常重要): [0.73, 4.38, 17.5, 52.5, 122.5] 小时
# 重要性 0.5 (中等): [0.85, 5.1, 20.4, 61.2, 142.8] 小时
# 重要性 0.1 (不重要): [0.97, 5.82, 23.3, 69.8, 162.6] 小时
```

---

## 3. 智能插件钩子（Intelligence Plugin Hooks）

### 3.1 on_add 钩子 - 添加记忆时

**调用时机**：当新记忆被添加时

**处理流程**：

```python
def on_add(content: str, metadata: dict) -> dict:
    # 步骤 1: 评估重要性
    importance_score = self._importance.evaluate_importance(content, metadata)
    # 例如: 0.65

    # 步骤 2: 分类记忆类型
    if importance_score >= long_term_threshold:
        memory_type = "long_term"
    elif importance_score >= short_term_threshold:
        memory_type = "short_term"
    else:
        memory_type = "working"

    # 步骤 3: 计算遗忘率
    decay_rate = self._get_decay_rate_for_type(memory_type)
    # working: 0.2, short_term: 0.15, long_term: 0.1

    # 步骤 4: 生成复习计划
    review_schedule = self._generate_review_schedule(importance_score, created_at)
    # [1小时后, 6小时后, 1天后, 3天后, 7天后]

    # 步骤 5: 返回智能元数据
    return {
        "intelligence": {
            "importance_score": 0.65,
            "memory_type": "short_term",
            "initial_retention": 0.65,  # 初始保留率
            "decay_rate": 0.15,
            "current_retention": 0.65,
            "next_review": "2026-05-07T06:49:00",  # 下次复习时间
            "review_schedule": [...],
            "last_reviewed": "2026-05-07T05:49:00",
            "review_count": 0,
            "access_count": 0
        },
        "memory_management": {
            "should_promote": False,
            "should_forget": False,
            "should_archive": False,
            "is_active": True
        }
    }
```

### 3.2 on_search 钩子 - 搜索记忆时（自动删除触发点）

**调用时机**：每次调用 `query_memory()` 或 `search()` 时

**这是自动记忆删除的核心触发点！**

```python
def on_search(results: List[Dict]) -> Tuple[updates, deletes]:
    """
    对搜索结果中的每个记忆执行遗忘检查

    参数:
        results: 搜索返回的记忆列表

    返回:
        updates: 需要更新的记忆 [(memory_id, update_dict), ...]
        deletes: 需要删除的记忆 [memory_id, ...]
    """
    updates = []
    deletes = []

    for memory in results:
        mem_id = memory.get("id")

        # 检查单个记忆
        upd, delete_flag = self.on_get(memory)

        if delete_flag:
            deletes.append(mem_id)  # 👈 标记为删除
        elif upd:
            updates.append((mem_id, upd))

    return updates, deletes
```

### 3.3 on_get 钩子 - 访问记忆时

**调用时机**：访问单个记忆时（由 `on_search` 对每个结果调用）

```python
def on_get(memory: Dict) -> Tuple[updates, delete_flag]:
    """
    检查记忆是否应该被遗忘、升级或归档

    返回:
        updates: 需要更新的字段
        delete_flag: 是否应该删除
    """
    # 更新访问计数
    updates = {
        "access_count": memory.get("access_count", 0) + 1,
        "updated_at": get_current_datetime()
    }

    # ========== 遗忘检查（关键逻辑）==========
    if self._algo.should_forget(memory):
        return None, True  # 返回 True 表示应该删除

    # ========== 升级检查 ==========
    if self._algo.should_promote(memory):
        current_type = memory.get("memory_type")
        if current_type == "working":
            updates["memory_type"] = "short_term"
        elif current_type == "short_term":
            updates["memory_type"] = "long_term"

    # ========== 归档检查 ==========
    if self._algo.should_archive(memory):
        metadata = memory.get("metadata") or {}
        metadata["archived"] = True
        updates["metadata"] = metadata

    return updates, False
```

---

## 4. should_forget 判断逻辑（核心）

这是决定是否删除记忆的核心函数：

```python
def should_forget(memory: Dict) -> bool:
    """
    判断记忆是否应该被遗忘

    返回:
        True: 应该删除
        False: 保留
    """
    # ========== 条件 1: 遗忘因子检查 ==========
    created_at = memory.get("created_at")
    if created_at:
        # 计算当前保留率
        decay_factor = self.calculate_decay(created_at)

        # 如果保留率低于工作记忆阈值，删除
        if decay_factor < self.working_threshold:  # 默认 0.3
            return True  # 👈 删除

    # ========== 条件 2: 访问频率检查 ==========
    access_count = memory.get("access_count", 0)
    if access_count == 0:
        # 从未被访问过的记忆，检查是否过期
        if created_at:
            time_elapsed = get_current_datetime() - created_at
            if time_elapsed > timedelta(days=7):
                return True  # 👈 7天未访问且未被访问过，删除

    # 默认保留
    return False
```

### 遗忘因子计算

```python
def calculate_decay(created_at) -> float:
    """
    计算记忆的当前保留率

    公式: R = e^(-t/S)

    返回:
        保留率 (0.0 - 1.0)
    """
    # 计算经过的时间（小时）
    time_elapsed = get_current_datetime() - created_at
    hours_elapsed = time_elapsed.total_seconds() / 3600

    # 获取记忆类型的遗忘率
    memory_type = memory.get("memory_type", "working")
    if memory_type == "working":
        decay_rate = self.decay_rate * 2.0  # 0.2
    elif memory_type == "short_term":
        decay_rate = self.decay_rate * 1.5  # 0.15
    else:  # long_term
        decay_rate = self.decay_rate        # 0.1

    # 计算保留率
    S = 24 * decay_rate  # 记忆强度
    retention = math.exp(-hours_elapsed / S)

    return max(retention, 0.0)
```

---

## 5. 实际案例分析

### 案例 1：昨天的记忆被删除

**背景**：
- 昨天 17:44 创建了 14 条记忆
- 今天 05:49:27 开启新对话时被删除

**分析**：

```
记忆 A: "用户问了天气情况"
- 创建时间: 2026-05-06 17:44:00 (北京时间)
- 重要性评分: 0.2 (不重要)
- 记忆类型: working_memory
- 访问次数: 0

时间计算:
- 经过时间: 12 小时 5 分钟 = 12.08 小时
- 记忆强度: S = 24 * 0.1 * 2.0 = 4.8
- 保留率: R = e^(-12.08/4.8) = e^(-2.52) = 0.08

判断:
- 保留率 0.08 < working_threshold 0.3
- 结果: ✅ 应该删除
```

**为什么会删除**：
1. 记忆被评定为不重要（0.2）
2. 归类为工作记忆（遗忘最快）
3. 经过 12 小时后保留率降至 8%
4. 远低于阈值 30%，触发删除

### 案例 2：重要记忆保留

```
记忆 B: "用户的名字叫张三，生日是 5月 1 日"
- 创建时间: 2026-05-06 17:44:00
- 重要性评分: 0.9 (非常重要)
- 记忆类型: long_term_memory
- 访问次数: 5

时间计算:
- 经过时间: 12.08 小时
- 记忆强度: S = 24 * 0.1 * 1.0 = 2.4
- 保留率: R = e^(-12.08/2.4) = e^(-5.03) = 0.0065

判断:
- 保留率 0.0065 < working_threshold 0.3
- 但是！！！访问次数 5 > 0
- 结果: ❌ 不删除（访问次数保护）
```

**为什么会保留**：
虽然保留率很低，但访问次数 > 0，跳过了"7天未访问且未被访问过"的删除条件。

---

## 6. 配置参数说明

### 6.1 默认配置

```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: true  # 启用智能插件
        # ========== 遗忘曲线参数 ==========
        ebbinghaus:
          # 遗忘率（越小遗忘越慢）
          decay_rate: 0.1

          # 重要性阈值
          working_threshold: 0.3      # 工作记忆阈值
          short_term_threshold: 0.6   # 短期记忆阈值
          long_term_threshold: 0.8    # 长期记忆阈值

          # 复习间隔（小时）
          review_intervals: [1, 6, 24, 72, 168]  # 1h, 6h, 1d, 3d, 7d

          # 初始保留率
          initial_retention: 1.0

          # 强化因子（每次访问增加保留率）
          reinforcement_factor: 0.3

      # ========== 重要性评估配置 ==========
      importance:
        # 自定义评估提示词（可选）
        # custom_importance_evaluation_prompt: "..."

        # 各维度权重（默认）
        # relevance: 0.3
        # novelty: 0.2
        # emotional_impact: 0.15
        # actionable: 0.15
        # factual: 0.1
        # personal: 0.1
```

### 6.2 调整遗忘速度

```yaml
# 方案 1: 降低遗忘率，记忆保留更久
decay_rate: 0.05  # 从 0.1 降到 0.05，遗忘速度减半

# 方案 2: 提高阈值，减少删除
working_threshold: 0.2  # 从 0.3 降到 0.2，更宽容
short_term_threshold: 0.5  # 从 0.6 降到 0.5
long_term_threshold: 0.7  # 从 0.8 降到 0.7

# 方案 3: 延长删除期限
# 修改源码中的 timedelta(days=7) 改为 timedelta(days=30)
```

---

## 7. 完整调用时序图

```
用户说 "你好，蛋蛋"
    ↓
query_memory() 被调用
    ↓
search() 执行向量搜索
    ↓
返回 20 条相关记忆（包括昨天的 14 条）
    ↓
intelligence_plugin.on_search(20 条记忆)  👈 关键点
    ↓
对每条记忆调用 on_get()
    ↓
    ├─ 更新 access_count += 1
    ├─ 计算当前保留率 R = e^(-t/S)
    ├─ 判断: R < working_threshold?
    │   ├─ 是 → 返回 delete_flag=True
    │   └─ 否 → 判断: access_count == 0 且超过 7 天?
    │       ├─ 是 → 返回 delete_flag=True
    │       └─ 否 → 保留记忆
    ↓
收集所有 delete_flag=True 的记忆 IDs
    ↓
批量执行 storage.delete_memory(memory_ids)  👈 删除操作
    ↓
OceanBase 触发器记录到 memories_audit_log
```

---

## 8. 常见问题

### Q1: 为什么对话进行中就会删除记忆？

**A**: 这是 PowerMem 的设计行为。每次调用 `query_memory()` 检索记忆时，智能插件的 `on_search()` 钩子都会自动检查所有搜索结果的记忆是否应该遗忘。这不依赖对话是否结束。

**触发时机**：
- ✅ 每次用户发送消息时
- ✅ 每次系统查询相关记忆时
- ✅ 对话进行中任何需要检索记忆的场景

### Q2: 如何完全禁用自动清理？

**A**: 在配置中禁用智能插件：

```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: false  # 完全禁用
```

**影响**：
- ✅ 记忆不会被自动删除
- ✅ 保留记忆的查询和添加功能
- ⚠️ 失去智能记忆管理（重要性评分、自动分类、遗忘曲线）

### Q3: 为什么重要记忆也会被删除？

**A**: 可能的原因：
1. **重要性评分不准确**：LLM 可能评估过低
2. **访问次数为 0**：即使重要，如果从未被访问过，7天后会被删除
3. **遗忘率过高**：`decay_rate` 配置过大
4. **阈值过低**：`working_threshold` 配置过低

**解决方案**：
```yaml
# 方案 1: 提高阈值
working_threshold: 0.2  # 更宽容

# 方案 2: 降低遗忘率
decay_rate: 0.05  # 更慢遗忘

# 方案 3: 自定义重要性评估提示词
importance:
  custom_importance_evaluation_prompt: |
    提高用户偏好、个人信息、重要事件的重要性评分...
```

### Q4: 如何查看记忆的重要性评分？

**A**: 查询 `memories` 表的 `intelligence` 字段：

```sql
SELECT
    id,
    content,
    JSON_EXTRACT(intelligence, '$.importance_score') AS importance,
    JSON_EXTRACT(intelligence, '$.memory_type') AS type,
    JSON_EXTRACT(intelligence, '$.current_retention') AS retention,
    created_at
FROM memories
ORDER BY created_at DESC
LIMIT 10;
```

---

## 9. 相关文档

- [PowerMem-Issues.md](./PowerMem-Issues.md) - PowerMem 集成问题清单
- [PowerMem-记忆处理原理详解.md](./PowerMem-记忆处理原理详解.md) - 记忆管理原理
- [PowerMemory-Intelligent-Processing-Flow.md](./PowerMemory-Intelligent-Processing-Flow.md) - 智能处理流程

---

## 10. SDK 源码位置

- 艾宾浩斯算法：`~/.venv/lib/python3.12/site-packages/powermem/intelligence/ebbinghaus_algorithm.py`
- 重要性评估器：`~/.venv/lib/python3.12/site-packages/powermem/intelligence/importance_evaluator.py`
- 智能插件：`~/.venv/lib/python3.12/site-packages/powermem/intelligence/plugin.py`
- 重要性评估提示词：`~/.venv/lib/python3.12/site-packages/powermem/prompts/importance_evaluation.py`
- Memory 核心：`~/.venv/lib/python3.12/site-packages/powermem/core/memory.py`

---

## 总结

PowerMem 的艾宾浩斯遗忘曲线算法是一个复杂的智能记忆管理系统：

1. **重要性评估**：使用 LLM 或规则评估记忆的重要性（0.0-1.0）
2. **记忆分类**：根据重要性分为工作、短期、长期三类
3. **遗忘计算**：使用公式 R = e^(-t/S) 计算保留率
4. **自动删除**：每次查询记忆时检查并删除应该遗忘的记忆
5. **访问保护**：访问次数 > 0 的记忆有额外保护

**核心特点**：
- 模拟人类大脑记忆机制
- 重要信息长期保留，不重要信息逐渐遗忘
- 自动管理，无需人工干预
- 基于艾宾浩斯遗忘曲线的科学算法
