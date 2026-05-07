# PowerMem Metadata 字段写入时机详解

## 概述

PowerMem 的 `metadata` 字段包含两类信息：
1. **搜索统计信息**：`search_count`、`last_searched_at`、`_fusion_info`、`_quality_score` 等
2. **智能管理信息**：`access_count`、`importance_score`、`memory_type` 等（通常存储在顶层字段或 `intelligence` 字段中）

**关键区别**：
- **search_count**：记录记忆被检索到的次数（存储在 `metadata` 中）
- **access_count**：记录记忆被访问的次数（存储在顶层，用于艾宾浩斯算法）

---

## 写入时机全景图

```
┌────────────────────────────────────────────────────────────────┐
│                   PowerMem 记忆生命周期                           │
└────────────────────────────────────────────────────────────────┘

1. 添加记忆（add）
   ↓
2. 搜索记忆（search/query）
   ↓
3. 访问记忆（on_get hook）
   ↓
4. 删除记忆（delete）
```

---

## 1. 添加记忆时（add）

### 调用时机

```python
memory.add(messages, user_id="user123")
```

### 写入的字段

**位置**：`powermem/intelligence/plugin.py:on_add()`

```python
def on_add(content: str, metadata: dict) -> dict:
    # 步骤 1: 评估重要性
    importance_score = self._importance.evaluate_importance(content, metadata)
    # 例如: 0.75

    # 步骤 2: 分类记忆类型
    memory_type = self._classify(importance_score)
    # "long_term" / "short_term" / "working"

    # 步骤 3: 生成智能元数据
    intelligence_metadata = self._algo.process_memory_metadata(
        content, importance_score, memory_type
    )

    # 步骤 4: 返回完整元数据
    return {
        "importance_score": 0.75,
        "memory_type": "long_term",
        "access_count": 0,  # 👈 初始访问次数为 0
        "intelligence": {
            "importance_score": 0.75,
            "memory_type": "long_term",
            "initial_retention": 0.75,
            "decay_rate": 0.1,
            "current_retention": 0.75,
            "next_review": "2026-05-07T06:00:00",
            "review_schedule": [...],
            "last_reviewed": "2026-05-07T05:00:00",
            "review_count": 0
        },
        "memory_management": {
            "should_promote": False,
            "should_forget": False,
            "should_archive": False,
            "is_active": True
        }
    }
```

### 数据库存储

**SQL INSERT 示例**：

```sql
INSERT INTO memories (
    content,
    user_id,
    agent_id,
    created_at,
    updated_at,
    access_count,        -- 0
    importance_score,    -- 0.75
    memory_type,         -- 'long_term'
    intelligence,        -- JSON 对象
    memory_management,   -- JSON 对象
    metadata             -- 空对象 {}
) VALUES (
    '用户的名字是张三',
    'user123',
    'agent001',
    NOW(),
    NOW(),
    0,
    0.75,
    'long_term',
    '{...}',  -- intelligence 字段
    '{...}',  -- memory_management 字段
    '{}'      -- metadata 字段初始为空
);
```

---

## 2. 搜索记忆时（search/query）

### 调用时机

```python
# 用户查询记忆
memory.search(query="用户喜欢什么？", user_id="user123")
```

### 写入流程

**完整调用链**：

```
1. search() 方法被调用
   ↓ powermem/core/memory.py:1167
2. storage.search_memories() 执行向量搜索
   ↓ powermem/storage/oceanbase/oceanbase.py
3. 混合搜索（Hybrid Search）融合多种结果
   ↓
4. 返回搜索结果，包含 _fusion_info、_quality_score 等字段
   ↓
5. intelligence_plugin.on_search(搜索结果)
   ↓ powermem/intelligence/plugin.py:167
6. 对每个结果调用 on_get()
   ↓ powermem/intelligence/plugin.py:121
7. 在 on_get() 中更新 access_count += 1
   ↓
8. 调用 _enhance_for_search() 更新 search_count += 1
   ↓ powermem/intelligence/plugin.py:195
9. 返回需要更新的记忆列表
   ↓
10. 批量执行 storage.update_memory() 更新数据库
```

### 关键代码详解

#### 2.1 搜索结果生成（OceanBase 层）

**位置**：`powermem/storage/oceanbase/oceanbase.py:_hybrid_search()`

```python
def _hybrid_search(self, ...):
    # 执行向量搜索
    vector_results = self._vector_search(...)
    # 执行全文搜索
    fts_results = self._fulltext_search(...)

    # RRF 融合
    fused_results = self._rrf_fusion(
        vector_results,
        fts_results,
        vector_weight=0.5,
        fts_weight=0.5
    )

    # 为每个结果添加融合信息
    for result in fused_results:
        # 添加到 metadata 中（仅存在于返回结果中，不持久化）
        result.payload['_fusion_info'] = {
            'vector_rank': vector_rank,
            'fts_rank': fts_rank,
            'rrf_score': rrf_score,
            'fusion_method': 'rrf',
            'vector_weight': 0.5,
            'fts_weight': 0.5,
            'sparse_weight': 0
        }

        result.payload['_fusion_score'] = rrf_score
        result.payload['_quality_score'] = quality_score

    return fused_results
```

**⚠️ 重要**：`_fusion_info`、`_fusion_score`、`_quality_score` **仅在搜索结果中临时生成，不会持久化到数据库**！

#### 2.2 智能插件钩子（on_search）

**位置**：`powermem/intelligence/plugin.py:on_search()`

```python
def on_search(self, results: List[Dict]) -> Tuple[updates, deletes]:
    """
    对搜索结果中的每个记忆执行智能管理

    参数:
        results: 搜索返回的记忆列表（包含 _fusion_info 等 metadata）

    返回:
        updates: 需要更新的记忆 [(memory_id, update_dict), ...]
        deletes: 需要删除的记忆 [memory_id, ...]
    """
    updates = []
    deletes = []

    for item in results:
        mem_id = item.get("id")

        # 处理单个记忆
        upd, delete_flag = self.on_get(item)

        if delete_flag:
            deletes.append(mem_id)  # 标记为删除
        elif upd:
            # 添加搜索增强信息
            search_updates = self._enhance_for_search(item, upd)
            updates.append((mem_id, search_updates))

    return updates, deletes
```

#### 2.3 访问计数更新（on_get）

**位置**：`powermem/intelligence/plugin.py:on_get()`

```python
def on_get(self, memory: Dict) -> Tuple[updates, delete_flag]:
    """
    访问单个记忆时的处理

    参数:
        memory: 记忆数据（包含 _fusion_info 等 metadata）

    返回:
        updates: 需要更新的字段
        delete_flag: 是否应该删除
    """
    # 更新访问计数（用于艾宾浩斯算法）
    updates = {
        "access_count": (memory.get("access_count") or 0) + 1,  # 👈 关键！
        "updated_at": get_current_datetime()
    }

    # 检查是否应该遗忘（使用 access_count）
    if self._algo.should_forget(memory):
        return None, True  # 返回 True 表示应该删除

    # 检查是否应该升级（使用 access_count）
    if self._algo.should_promote(memory):
        # ...
        if current == "working":
            updates["memory_type"] = "short_term"
        elif current == "short_term":
            updates["memory_type"] = "long_term"

    return updates, False
```

#### 2.4 搜索统计更新（_enhance_for_search）

**位置**：`powermem/intelligence/plugin.py:_enhance_for_search()`

```python
def _enhance_for_search(self, memory, base_updates):
    """
    为搜索上下文增强记忆元数据

    参数:
        memory: 原始记忆数据
        base_updates: on_get() 返回的基础更新（包含 access_count += 1）

    返回:
        enhanced_updates: 增强后的更新（包含 search_count += 1）
    """
    # 获取或初始化 search_metadata
    search_metadata = memory.get("metadata", {})

    # 👈 更新搜索统计（存储在 metadata 中）
    search_metadata["last_searched_at"] = get_current_datetime()
    search_metadata["search_count"] = search_metadata.get("search_count", 0) + 1

    # 合并基础更新
    enhanced_updates = base_updates.copy()
    enhanced_updates["metadata"] = search_metadata

    return enhanced_updates
```

### 数据库更新

**SQL UPDATE 示例**：

```sql
UPDATE memories
SET
    -- on_get() 更新的字段（用于艾宾浩斯算法）
    access_count = access_count + 1,
    updated_at = NOW(),

    -- _enhance_for_search() 更新的字段（存储在 metadata 中）
    metadata = jsonb_set(
        metadata,
        '{last_searched_at}',
        to_jsonb(NOW())
    ),
    metadata = jsonb_set(
        metadata,
        '{search_count}',
        COALESCE((metadata->>'search_count')::int, 0) + 1
    )

WHERE id = 123;
```

---

## 3. 字段与艾宾浩斯算法的关系

### access_count vs search_count

这是两个**不同**但**相关**的计数器：

| 字段 | 存储位置 | 更新时机 | 用途 | 艾宾浩斯算法 |
|------|----------|----------|------|--------------|
| **access_count** | 顶层字段 | 每次记忆在搜索结果中出现时 | 记忆访问热度 | ✅ **直接使用** |
| **search_count** | metadata 中 | 每次记忆在搜索结果中出现时 | 搜索统计信息 | ❌ 不使用 |

### 艾宾浩斯算法如何使用 access_count

**位置**：`powermem/intelligence/ebbinghaus_algorithm.py:should_forget()`

```python
def should_forget(self, memory: Dict) -> bool:
    """
    判断记忆是否应该被遗忘

    返回:
        True: 应该删除
        False: 保留
    """
    # 条件 1: 遗忘因子检查
    created_at = memory.get("created_at")
    if created_at:
        decay_factor = self.calculate_decay(created_at)
        if decay_factor < self.working_threshold:  # 默认 0.3
            return True  # 保留率低于阈值，删除

    # 条件 2: 访问频率检查（使用 access_count）
    access_count = memory.get("access_count", 0)
    if access_count == 0:  # 👈 使用 access_count 而非 search_count
        # 从未被访问过的记忆，检查是否过期
        if created_at:
            time_elapsed = get_current_datetime() - created_at
            if time_elapsed > timedelta(days=7):
                return True  # 7天未访问且未被访问过，删除

    return False  # 默认保留
```

### 为什么使用 access_count 而非 search_count？

**设计原因**：

1. **access_count** 是艾宾浩斯算法的核心指标
   - 反映记忆的"活跃度"
   - 每次"访问"（被检索到）都会递增
   - 用于判断记忆是否被"强化"（reinforcement）

2. **search_count** 是调试和统计信息
   - 存储在 metadata 中
   - 用于分析和优化搜索质量
   - 不参与遗忘判断逻辑

### 更新时机对比

```
┌─────────────────────────────────────────────────────────┐
│              一次 search() 调用的完整流程                 │
└─────────────────────────────────────────────────────────┘

用户查询: "用户喜欢什么？"
    ↓
1. 向量搜索返回 20 条记忆
    ↓
2. 对每条记忆调用 on_get()
    ↓
    更新 access_count: 0 → 1  (顶层字段，用于艾宾浩斯)
    ↓
3. 对每条记忆调用 _enhance_for_search()
    ↓
    更新 search_count: 18 → 19  (metadata 中，用于统计)
    ↓
4. 检查 should_forget()
    ↓
    使用 access_count 判断是否应该删除
    ↓
5. 批量更新数据库
```

**示例数据对比**：

```json
{
  "id": 123,
  "content": "用户喜欢喝咖啡",
  "access_count": 19,           // 顶层字段：艾宾浩斯算法使用
  "importance_score": 0.75,
  "memory_type": "long_term",
  "created_at": "2026-05-06T09:44:00",
  "updated_at": "2026-05-07T05:49:00",
  "intelligence": {
    "current_retention": 0.65,
    "decay_rate": 0.1
  },
  "memory_management": {
    "should_forget": false
  },
  "metadata": {
    "search_count": 19,          // metadata 中：统计信息
    "last_searched_at": "2026-05-07T05:49:00",
    "_fusion_info": {            // 临时字段，不持久化
      "vector_rank": 2,
      "fts_rank": 3,
      "rrf_score": 0.015
    },
    "_quality_score": 0.719,     // 临时字段，不持久化
    "_fusion_score": 0.015       // 临时字段，不持久化
  }
}
```

---

## 4. 临时字段 vs 持久化字段

### 临时字段（仅在搜索结果中存在）

这些字段在搜索时动态生成，**不会持久化到数据库**：

| 字段 | 生成时机 | 存储位置 | 持久化 |
|------|----------|----------|--------|
| `_fusion_info` | 混合搜索融合时 | 搜索结果的 payload 中 | ❌ 否 |
| `_fusion_score` | 混合搜索融合时 | 搜索结果的 payload 中 | ❌ 否 |
| `_quality_score` | 混合搜索融合时 | 搜索结果的 payload 中 | ❌ 否 |
| `_vector_similarity` | 向量搜索时 | 搜索结果的 payload 中 | ❌ 否 |

**代码证据**：

```python
# powermem/storage/oceanbase/oceanbase.py
result.payload['_vector_similarity'] = similarity  # 仅存储在 payload
result.payload['_quality_score'] = similarity      # 仅存储在 payload
result.payload['_fusion_info'] = {...}             # 仅存储在 payload
```

### 持久化字段（存储在数据库中）

这些字段会永久存储在 `memories` 表中：

| 字段 | 更新时机 | 存储位置 | 持久化 |
|------|----------|----------|--------|
| `access_count` | 每次搜索时 | 顶层字段 | ✅ 是 |
| `search_count` | 每次搜索时 | metadata 中 | ✅ 是 |
| `last_searched_at` | 每次搜索时 | metadata 中 | ✅ 是 |
| `importance_score` | 添加记忆时 | intelligence 中 | ✅ 是 |
| `memory_type` | 添加/升级时 | intelligence 中 | ✅ 是 |

---

## 5. 完整时间线示例

### 场景：用户查询"用户喜欢什么？"

```
时间: 2026-05-07 05:49:26

1️⃣ 用户发送消息: "用户喜欢什么？"
    ↓
2️⃣ 调用 memory.search(query="用户喜欢什么？")
    ↓
3️⃣ OceanBase 执行混合搜索
    ├─ 向量搜索: 返回 20 条结果
    ├─ 全文搜索: 返回 15 条结果
    └─ RRF 融合: 合并并排序，返回 20 条结果
    ↓
4️⃣ 为每条结果生成临时 metadata
    ├─ _vector_similarity: 0.719
    ├─ _quality_score: 0.719
    ├─ _fusion_score: 0.016
    └─ _fusion_info: {...}  (包含排名信息)
    ↓
5️⃣ intelligence_plugin.on_search(20 条结果)
    ↓
6️⃣ 对每条记忆执行 on_get()
    ├─ 检查 should_forget(memory)
    │   ├─ 计算 decay_factor = 0.08
    │   ├─ 判断: 0.08 < 0.3 → 应该删除？❌
    │   └─ 检查 access_count = 18 > 0 → 跳过删除
    │
    ├─ 更新 access_count: 18 → 19
    └─ 返回 updates = {"access_count": 19, "updated_at": "2026-05-07T05:49:26"}
    ↓
7️⃣ 对每条记忆执行 _enhance_for_search()
    ├─ 更新 search_count: 18 → 19
    ├─ 更新 last_searched_at: "2026-05-07T05:49:26"
    └─ 返回 enhanced_updates
    ↓
8️⃣ 批量更新数据库
    UPDATE memories
    SET access_count = 19,
        updated_at = NOW(),
        metadata = {
            "search_count": 19,
            "last_searched_at": "2026-05-07T05:49:26"
        }
    WHERE id IN (123, 456, 789, ...)
    ↓
9️⃣ 返回搜索结果给用户（包含 _fusion_info 等临时字段）
```

---

## 6. 常见问题

### Q1: _fusion_info 为什么会出现在数据库中？

**A**: 这可能是因为：
1. 某些版本的 PowerMem 会将搜索结果直接保存（不推荐）
2. 应用层代码可能将整个搜索结果写入数据库

**正确做法**：
- `_fusion_info` 只存在于搜索结果中
- 不应该持久化到数据库
- 如果需要保留调试信息，可以手动记录

### Q2: search_count 和 access_count 为什么不同步？

**A**: 它们是**独立**的计数器：
- `access_count`：每次 `on_get()` 都会递增
- `search_count`：每次 `_enhance_for_search()` 都会递增

理论上它们应该相同，但如果：
- 代码被修改，跳过了某个钩子
- 使用了不同的 API 调用
- 发生了异常导致部分更新失败

它们可能会不同步。

### Q3: 如何禁用 metadata 的自动更新？

**A**: 如果不想记录 `search_count` 和 `last_searched_at`，可以：

**方法 1**：禁用智能插件
```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: false
```

**方法 2**：修改源码（不推荐）
```python
# 注释掉 _enhance_for_search 中的更新逻辑
# search_metadata["search_count"] = ...
# search_metadata["last_searched_at"] = ...
```

### Q4: 艾宾浩斯算法会删除 search_count 高的记忆吗？

**A**: **不会直接删除**。艾宾浩斯算法使用的是 `access_count`，不是 `search_count`。

但理论上：
- 如果 `access_count == search_count`（正常情况）
- 高 `search_count` 意味着高 `access_count`
- 高 `access_count` 会保护记忆不被删除（见 `should_forget()` 逻辑）

---

## 7. 性能影响

### 每次搜索的数据库更新开销

```python
# 假设搜索返回 20 条结果

1. 向量搜索: ~50ms
2. 全文搜索: ~30ms
3. RRF 融合: ~10ms
4. 智能插件处理: ~20ms
5. 批量更新数据库: ~100ms (20 条 × 5ms/条)

总计: ~210ms
```

### 优化建议

如果不需要实时更新统计信息，可以：
1. 使用异步队列批量更新
2. 降低更新频率（例如每 10 次搜索更新一次）
3. 禁用智能插件（但会失去遗忘曲线功能）

---

## 相关文档

- [PowerMem-Metadata-Fields.md](./PowerMem-Metadata-Fields.md) - Metadata 字段详解
- [PowerMem-Ebbinghaus-Algorithm.md](./PowerMem-Ebbinghaus-Algorithm.md) - 艾宾浩斯遗忘曲线算法详解
- [PowerMem-Issues.md](./PowerMem-Issues.md) - PowerMem 集成问题清单

---

## SDK 源码位置

- 智能插件：`powermem/intelligence/plugin.py`
  - `on_add()`: 添加记忆时初始化字段
  - `on_get()`: 访问记忆时更新 access_count
  - `on_search()`: 搜索记忆时调用 on_get
  - `_enhance_for_search()`: 更新 search_count

- 艾宾浩斯算法：`powermem/intelligence/ebbinghaus_algorithm.py`
  - `should_forget()`: 使用 access_count 判断是否删除
  - `should_promote()`: 使用 access_count 判断是否升级

- OceanBase 存储：`powermem/storage/oceanbase/oceanbase.py`
  - `_hybrid_search()`: 生成 _fusion_info 等临时字段
  - `_calculate_quality_score()`: 计算质量评分

---

## 总结

**写入时机**：

1. **添加记忆时**：
   - 初始化 `access_count = 0`
   - 评估 `importance_score` 和 `memory_type`
   - 生成 `intelligence` 和 `memory_management` 元数据

2. **搜索记忆时**：
   - 生成临时的 `_fusion_info`、`_quality_score`（不持久化）
   - 更新 `access_count += 1`（用于艾宾浩斯算法）
   - 更新 `search_count += 1`（统计信息）
   - 更新 `last_searched_at = NOW()`（统计信息）

**与艾宾浩斯算法的关系**：

- ✅ **access_count**：艾宾浩斯算法**直接使用**，判断记忆是否应该遗忘
- ❌ **search_count**：仅用于统计，**不参与**遗忘判断
- ⚠️ **_fusion_info、_quality_score**：临时生成，与艾宾浩斯算法**无关**
