# PowerMem metadata 字段详解

## 概述

PowerMem 的 `metadata` 字段存储了记忆的搜索和融合相关的元数据信息。这些字段用于调试、优化和理解记忆搜索的质量。

---

## 示例数据

```json
{
  "_fusion_info": {
    "fts_rank": 2,
    "rrf_score": 0.016129032258064516,
    "fts_weight": 0.5,
    "sparse_rank": null,
    "vector_rank": 2,
    "fusion_method": "rrf",
    "sparse_weight": 0,
    "vector_weight": 0.5
  },
  "search_count": 19,
  "_fusion_score": 0.016129032258064516,
  "_quality_score": 0.7190584754663253,
  "last_searched_at": "2026-05-06T09:57:00.296716+00:00",
  "_vector_similarity": 0.7190584754663253
}
```

---

## 字段详细说明

### 1. _fusion_info（融合信息）

**类型**：`object`

**说明**：混合搜索（Hybrid Search）的融合算法详细信息，记录了各个搜索路径的排名和权重配置。

#### 1.1 fts_rank

**类型**：`int` 或 `null`

**说明**：全文搜索（Full-Text Search）的排名

- **含义**：该记忆在全文搜索结果中的排名位置
- **值范围**：1, 2, 3, ...（数字越小排名越靠前）
- **null**：表示未参与全文搜索排名

**示例**：
```json
"fts_rank": 2  // 在全文搜索结果中排第 2 名
```

#### 1.2 vector_rank

**类型**：`int` 或 `null`

**说明**：向量搜索（Vector Search）的排名

- **含义**：该记忆在向量搜索结果中的排名位置
- **值范围**：1, 2, 3, ...（数字越小排名越靠前）
- **null**：表示未参与向量搜索排名

**示例**：
```json
"vector_rank": 2  // 在向量搜索结果中排第 2 名
```

#### 1.3 sparse_rank

**类型**：`int` 或 `null`

**说明**：稀疏向量搜索（Sparse Vector Search）的排名

- **含义**：该记忆在稀疏向量搜索结果中的排名位置
- **值范围**：1, 2, 3, ...（数字越小排名越靠前）
- **null**：表示未启用稀疏向量搜索或未参与排名

**示例**：
```json
"sparse_rank": null  // 稀疏向量搜索未启用
```

#### 1.4 rrf_score

**类型**：`float`

**说明**：RRF（Reciprocal Rank Fusion）融合评分

- **含义**：使用 RRF 算法计算的综合排名分数
- **RRF 公式**：`score = 1 / (k + rank)`，其中 k 通常为 60
- **值范围**：0.0 - 1.0（数值越大排名越靠前）
- **特点**：不依赖于原始分数的绝对值，只依赖排名位置

**示例计算**：
```
假设 fts_rank = 2, vector_rank = 2, k = 60

RRF 计算：
- FTS 贡献: 1 / (60 + 2) = 0.01613
- Vector 贡献: 1 / (60 + 2) = 0.01613
- 总分: 0.01613 + 0.01613 = 0.03226

最终 RRF score = 0.03226
```

#### 1.5 fusion_method

**类型**：`string`

**说明**：融合算法的类型

**可选值**：
- `"rrf"`：Reciprocal Rank Fusion（倒数排名融合）
- `"weighted"`：加权平均融合（Weighted Average）

**示例**：
```json
"fusion_method": "rrf"  // 使用 RRF 算法
```

#### 1.6 vector_weight

**类型**：`float`

**说明**：向量搜索在融合中的权重

- **含义**：向量搜索评分在综合评分中的权重比例
- **值范围**：0.0 - 1.0
- **典型值**：0.5（与 FTS 平权重）

**示例**：
```json
"vector_weight": 0.5  // 向量搜索权重为 50%
```

#### 1.7 fts_weight

**类型**：`float`

**说明**：全文搜索在融合中的权重

- **含义**：全文搜索评分在综合评分中的权重比例
- **值范围**：0.0 - 1.0
- **典型值**：0.5（与 Vector 平权重）

**示例**：
```json
"fts_weight": 0.5  // 全文搜索权重为 50%
```

#### 1.8 sparse_weight

**类型**：`float`

**说明**：稀疏向量搜索在融合中的权重

- **含义**：稀疏向量搜索评分在综合评分中的权重比例
- **值范围**：0.0 - 1.0
- **0**：表示未启用稀疏向量搜索

**示例**：
```json
"sparse_weight": 0  // 稀疏向量搜索未启用
```

---

### 2. search_count

**类型**：`int`

**说明**：该记忆被检索到的总次数

- **含义**：该记忆在所有搜索操作中出现的次数累计
- **值范围**：0, 1, 2, 3, ...
- **更新时机**：每次该记忆在搜索结果中出现时递增
- **用途**：
  - 评估记忆的热度（popularity）
  - 识别高频访问的记忆
  - 作为重要性评估的参考因素

**示例**：
```json
"search_count": 19  // 该记忆已经被检索到 19 次
```

**更新逻辑**（源码位置：`powermem/intelligence/plugin.py:195-196`）：
```python
def _enhance_for_search(memory, base_updates):
    search_metadata = memory.get("metadata", {})
    search_metadata["last_searched_at"] = get_current_datetime()
    search_metadata["search_count"] = search_metadata.get("search_count", 0) + 1
    return enhanced_updates
```

---

### 3. _fusion_score

**类型**：`float`

**说明**：融合搜索的最终评分

- **含义**：经过融合算法处理后的综合评分
- **值范围**：0.0 - 1.0（数值越大相关性越强）
- **用途**：用于对搜索结果进行最终排序
- **特点**：
  - RRF 方法：等于 `rrf_score`
  - Weighted 方法：等于加权平均分数

**示例**：
```json
"_fusion_score": 0.016129032258064516  // 融合评分为 0.016
```

**与 score 的关系**：
```python
# 在 oceanbase.py 中
result.payload['_fusion_score'] = score  # 保存原始融合分数
result.score = rerank_score              # 可能被重新排序
```

---

### 4. _quality_score

**类型**：`float`

**说明**：质量评分（绝对相似度质量）

- **含义**：所有搜索路径相似度的加权平均值
- **值范围**：0.0 - 1.0（数值越大质量越高）
- **计算方式**：
  ```
  _quality_score = (vector_similarity × vector_weight)
                 + (fts_score × fts_weight)
                 + (sparse_similarity × sparse_weight)
  ```
- **用途**：
  - 用于阈值过滤（threshold filtering）
  - 表示记忆的绝对质量（与排名无关）
  - 评估搜索结果的整体质量

**示例**：
```json
"_quality_score": 0.7190584754663253  // 质量评分为 0.719（较好）
```

**计算示例**：
```
假设：
- vector_similarity = 0.719
- fts_score = 0.5
- vector_weight = 0.5
- fts_weight = 0.5
- sparse_similarity = null
- sparse_weight = 0

计算：
_quality_score = (0.719 × 0.5) + (0.5 × 0.5) + (0 × 0)
              = 0.3595 + 0.25 + 0
              = 0.6095
```

**源码位置**：`powermem/storage/oceanbase/oceanbase.py:_calculate_quality_score()`

---

### 5. last_searched_at

**类型**：`string` (ISO 8601 datetime)

**说明**：最后一次检索到该记忆的时间戳

- **格式**：ISO 8601 格式，包含时区信息
- **示例值**：`"2026-05-06T09:57:00.296716+00:00"`
- **用途**：
  - 追踪记忆的活跃度（recency）
  - 识别冷门记忆（很久没被检索）
  - 作为遗忘曲线算法的输入参数

**示例**：
```json
"last_searched_at": "2026-05-06T09:57:00.296716+00:00"
// 北京时间: 2026-05-06 17:57:00
```

**时区转换**：
```python
# UTC 时间: 2026-05-06T09:57:00+00:00
# 北京时间: 2026-05-06 17:57:00 (UTC+8)
```

---

### 6. _vector_similarity

**类型**：`float`

**说明**：向量搜索的相似度分数

- **含义**：查询向量与记忆向量之间的余弦相似度
- **值范围**：0.0 - 1.0（数值越大越相似）
- **计算方式**：
  - 余弦相似度（Cosine Similarity）：`cos(θ) = (A · B) / (||A|| × ||B||)`
  - 值为 1.0 表示完全相同方向
  - 值为 0.0 表示正交（无关）
- **特点**：不受向量长度影响，只关注方向

**示例**：
```json
"_vector_similarity": 0.7190584754663253  // 相似度为 71.9%（较高）
```

**质量判断**：
```
0.9 - 1.0: 非常相似（几乎相同）
0.7 - 0.9: 高度相似（相关性强）
0.5 - 0.7: 中等相似（有一定相关性）
0.3 - 0.5: 低相似度（弱相关）
0.0 - 0.3: 几乎不相似（基本无关）
```

---

## 字段关系图

```
┌─────────────────────────────────────────────────────────────┐
│                    Metadata 字段关系图                        │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  搜索请求         │
│  "用户喜欢什么？"  │
└────────┬─────────┘
         │
         ▼
┌───────────────────────────────────────────────────────────┐
│              混合搜索（Hybrid Search）                     │
├───────────────────────┬───────────────────┬───────────────┤
│   向量搜索             │   全文搜索(FTS)    │  稀疏向量搜索  │
│   (Vector Search)     │   (Full-Text)     │  (Sparse)     │
├───────────────────────┼───────────────────┼───────────────┤
│ similarity: 0.719     │ score: 0.85       │ score: null   │
│ rank: 2               │ rank: 2           │ rank: null    │
└───────────┬───────────┴────────┬──────────┴───────┬───────┘
            │                      │                  │
            │ weight=0.5           │ weight=0.5        │ weight=0
            ▼                      ▼                  ▼
    ┌──────────────────────────────────────────────────────┐
    │         融合算法（Fusion Algorithm）                  │
    │                                                      │
    │  方法: RRF (Reciprocal Rank Fusion)                 │
    │  _fusion_info = {                                   │
    │    "fts_rank": 2,                                   │
    │    "vector_rank": 2,                                │
    │    "sparse_rank": null,                             │
    │    "rrf_score": 0.016,                              │
    │    "fusion_method": "rrf",                          │
    │    "vector_weight": 0.5,                            │
    │    "fts_weight": 0.5,                               │
    │    "sparse_weight": 0                               │
    │  }                                                  │
    │                                                      │
    │  _fusion_score = 0.016  (用于最终排名)              │
    │  _quality_score = 0.609  (加权平均质量分)            │
    └──────────────────────────┬──────────────────────────┘
                               │
                               ▼
            ┌──────────────────────────────────────┐
            │      更新记忆统计信息                  │
            ├──────────────────────────────────────┤
            │ search_count: 19                      │
            │ last_searched_at: "2026-05-06..."    │
            └──────────────────────────────────────┘
```

---

## 使用场景

### 1. 调试搜索质量

**问题**：为什么这条记忆排在第 2 位？

**查询**：
```sql
SELECT
    id,
    memory,
    metadata->>'._quality_score' AS quality,
    metadata->>'._vector_similarity' AS vector_sim,
    metadata->>'._fusion_score' AS fusion_score,
    metadata->'#>{_fusion_info,fts_rank}' AS fts_rank,
    metadata->'#>{_fusion_info,vector_rank}' AS vector_rank
FROM memories
WHERE id = 123;
```

**结果分析**：
```
quality_score: 0.719 (质量较好)
vector_sim: 0.719 (向量相似度高)
fts_rank: 2 (全文搜索排第2)
vector_rank: 2 (向量搜索排第2)
```

**结论**：该记忆在两种搜索方法中都排第 2，融合后仍然排第 2，排名合理。

### 2. 识别热门记忆

**查询**：找出检索次数最多的前 10 条记忆

```sql
SELECT
    id,
    memory,
    CAST(metadata->>'search_count' AS INTEGER) AS search_count,
    metadata->>'last_searched_at' AS last_searched
FROM memories
WHERE metadata->>'search_count' IS NOT NULL
ORDER BY search_count DESC
LIMIT 10;
```

**用途**：
- 发现用户最关心的内容
- 优化这些记忆的保存策略
- 作为重要性评估的参考

### 3. 发现冷门记忆

**查询**：找出 30 天未被检索的记忆

```sql
SELECT
    id,
    memory,
    metadata->>'last_searched_at' AS last_searched,
    metadata->>'search_count' AS search_count
FROM memories
WHERE metadata->>'last_searched_at' < NOW() - INTERVAL '30 days'
   OR metadata->>'last_searched_at' IS NULL;
```

**用途**：
- 识别可能应该遗忘的记忆
- 清理不再相关的旧记忆
- 优化存储空间

### 4. 优化搜索权重

**问题**：向量搜索和全文搜索的权重分配是否合理？

**分析**：
```sql
SELECT
    AVG(CAST(metadata->>'._quality_score' AS FLOAT)) AS avg_quality,
    AVG(CAST(metadata->'#>{_fusion_info,vector_rank}' AS FLOAT)) AS avg_vector_rank,
    AVG(CAST(metadata->'#>{_fusion_info,fts_rank}' AS FLOAT)) AS avg_fts_rank
FROM memories
WHERE metadata->>'search_count' IS NOT NULL;
```

**调优**：
```yaml
# 如果向量搜索质量更高，提高其权重
memory:
  powermem:
    vector_store:
      hybrid_search:
        vector_weight: 0.7  # 从 0.5 提高到 0.7
        fts_weight: 0.3     # 相应降低 FTS 权重
```

---

## 性能优化建议

### 1. 定期清理 metadata

如果不需要调试信息，可以定期清理：

```sql
-- 保留核心字段，清理调试字段
UPDATE memories
SET metadata = jsonb_build_object(
    'search_count', (metadata->>'search_count')::int,
    'last_searched_at', metadata->>'last_searched_at'
)
WHERE metadata ? 'search_count';
```

### 2. 创建索引加速查询

```sql
-- 为 search_count 创建索引
CREATE INDEX idx_memories_search_count ON memories(
    CAST(metadata->>'search_count' AS INTEGER)
);

-- 为 last_searched_at 创建索引
CREATE INDEX idx_memories_last_searched ON memories(
    (metadata->>'last_searched_at')::timestamp
);
```

---

## 相关文档

- [PowerMem-Issues.md](./PowerMem-Issues.md) - PowerMem 集成问题清单
- [PowerMem-Ebbinghaus-Algorithm.md](./PowerMem-Ebbinghaus-Algorithm.md) - 艾宾浩斯遗忘曲线算法详解
- [PowerMem-记忆处理原理详解.md](./PowerMem-记忆处理原理详解.md) - 记忆管理原理

---

## SDK 源码位置

- OceanBase 存储层：`~/.venv/lib/python3.12/site-packages/powermem/storage/oceanbase/oceanbase.py`
- 智能插件：`~/.venv/lib/python3.12/site-packages/powermem/intelligence/plugin.py`
- Memory 核心：`~/.venv/lib/python3.12/site-packages/powermem/core/memory.py`

---

## 总结

PowerMem 的 `metadata` 字段存储了丰富的搜索和融合信息：

**融合信息（_fusion_info）**：
- 记录了向量搜索、全文搜索、稀疏向量搜索的排名
- 记录了融合算法的权重配置
- 用于调试和优化搜索质量

**质量评分（_quality_score）**：
- 加权平均的绝对相似度质量
- 用于阈值过滤
- 与排名无关的质量指标

**融合评分（_fusion_score）**：
- 融合算法的最终评分
- 用于搜索结果排序

**统计信息（search_count, last_searched_at）**：
- 追踪记忆的热度和活跃度
- 作为重要性评估和遗忘判断的参考

**向量相似度（_vector_similarity）**：
- 查询与记忆的余弦相似度
- 衡量语义相关性的核心指标
