# PowerMem UPDATE/DELETE SQL生成原理详解

## 概述

在PowerMem智能记忆处理中，当LLM决定对存量记忆进行UPDATE或DELETE操作时，SQL语句的生成遵循严格的分层架构。本文档详细解析从LLM决策到最终SQL执行的完整流程。

---

## 完整调用链路

```
LLM决策 (ADD/UPDATE/DELETE/NONE)
    ↓
Memory._intelligent_add() 执行操作
    ↓
Memory._update_memory() 或 Memory.delete()
    ↓
StorageAdapter.update_memory() 或 StorageAdapter.delete_memory()
    ↓
OceanBaseVectorStore.update() 或 OceanBaseVectorStore.delete()
    ↓
pyobvector.upsert() 或 pyobvector.delete()
    ↓
OceanBase SQL执行
```

---

## 一、UPDATE操作的SQL生成

### 1.1 LLM决策阶段

**输入**：新事实 + 现有相似记忆

**输出示例**：
```json
{
  "memory": [
    {
      "id": "0",
      "text": "2023年5月去了夏威夷",
      "event": "UPDATE",
      "old_memory": "去了夏威夷"
    }
  ]
}
```

### 1.2 Memory._update_memory() 方法

**位置**：`src/powermem/core/memory.py:1121`

**关键步骤**：

```python
def _update_memory(self, memory_id: int, content: str, ...):
    # 步骤1：生成新的向量嵌入
    embedding = embedding_service.embed(content, memory_action="update")

    # 步骤2：生成新的内容哈希
    content_hash = hashlib.md5(content.encode('utf-8')).hexdigest()

    # 步骤3：构建更新数据
    update_data = {
        "content": content,           # 新的记忆内容
        "embedding": embedding,       # 新的向量嵌入
        "hash": content_hash,         # 新的哈希值
        "updated_at": get_current_datetime(),  # 更新时间戳
    }

    # 步骤4：调用存储层更新
    self.storage.update_memory(memory_id, update_data, user_id, agent_id)
```

**数据流**：
```
memory_id: 123456789
content: "2023年5月去了夏威夷"
embedding: [0.1, 0.2, ..., 0.9]  # 1536维向量
hash: "a1b2c3d4..."
updated_at: "2026-05-05 17:14:53"
```

### 1.3 StorageAdapter.update_memory() 方法

**位置**：`src/powermem/storage/adapter.py:340`

**关键步骤**：

```python
def update_memory(self, memory_id: int, update_data: Dict, ...):
    # 步骤1：获取现有记忆（用于合并）
    existing_result = self.vector_store.get(memory_id)
    existing_payload = existing_result.payload

    # 步骤2：处理content字段映射到data
    if "content" in update_data:
        updated_payload["data"] = update_data["content"]
        updated_payload["fulltext_content"] = update_data["content"]

    # 步骤3：合并元数据（保留旧的，添加新的）
    if "metadata" in update_data:
        merged_metadata = {**existing_metadata, **new_metadata}
        updated_payload["metadata"] = merged_metadata

    # 步骤4：更新其他字段
    updated_payload.update(serialized_update_data)

    # 步骤5：调用向量存储层
    target_store.update(
        memory_id,
        vector=update_data.get("embedding"),
        payload=updated_payload
    )
```

**payload字段映射**：

| 应用层字段 | payload字段 | 数据库字段 |
|-----------|------------|----------|
| content | data | text_content |
| embedding | (单独处理) | embedding |
| metadata | metadata | metadata (JSON) |
| user_id | user_id | user_id |
| created_at | created_at | created_at |
| updated_at | updated_at | updated_at |

### 1.4 OceanBaseVectorStore.update() 方法

**位置**：`src/powermem/storage/oceanbase/oceanbase.py:1898`

**关键步骤**：

```python
def update(self, vector_id: int, vector=None, payload=None):
    # 步骤1：获取现有记录的所有字段（避免部分更新时丢失数据）
    output_columns = self._get_standard_column_names(include_vector_field=True)
    existing_rows = self._get_records_by_id(vector_id, output_columns)
    existing = self._parse_row_to_dict(existing_rows[0], include_vector=True)

    # 步骤2：重建现有payload字典
    existing_payload = {
        "data": existing.get("text_content", ""),
        "metadata": existing.get("metadata", {}).get("metadata", {}),
        "user_id": existing.get("user_id", ""),
        "agent_id": existing.get("agent_id", ""),
        "run_id": existing.get("run_id", ""),
        "hash": existing.get("hash_val", ""),
        "created_at": existing.get("created_at", ""),
        "updated_at": existing.get("updated_at", ""),
        "category": existing.get("category", ""),
    }

    # 步骤3：合并新旧payload（新值覆盖旧值）
    if payload is not None:
        merged_payload = {**existing_payload, **payload}
    else:
        merged_payload = existing_payload

    # 步骤4：构建完整的插入记录格式
    update_vector = vector if vector is not None else existing_vector
    temp_record = self._build_record_for_insert(update_vector, merged_payload)

    # 步骤5：准备更新数据
    update_data = {self.primary_field: vector_id}  # id: 123456789
    for key, value in temp_record.items():
        if key != self.primary_field:
            update_data[key] = value

    # 步骤6：执行upsert操作
    self.obvector.upsert(
        table_name=self.collection_name,  # "memories"
        data=[update_data]
    )
```

### 1.5 _build_record_for_insert() 方法

**位置**：`src/powermem/storage/oceanbase/oceanbase.py:812`

**关键作用**：将payload转换为数据库记录格式

```python
def _build_record_for_insert(self, vector: List[float], payload: Dict) -> Dict:
    # 序列化元数据（处理datetime对象）
    metadata = payload.get("metadata", {})
    serialized_metadata = serialize_datetime(metadata)

    # 构建记录字典
    record = {
        self.vector_field: vector,        # embedding字段
        self.text_field: payload.get("data") or "",  # text_content字段
        self.metadata_field: serialized_metadata,  # metadata字段
        "user_id": payload.get("user_id", ""),
        "agent_id": payload.get("agent_id", ""),
        "run_id": payload.get("run_id", ""),
        "actor_id": payload.get("actor_id", ""),
        "hash": payload.get("hash", ""),
        "created_at": serialize_datetime(payload.get("created_at", "")),
        "updated_at": serialize_datetime(payload.get("updated_at", "")),
        "category": payload.get("category") or "",
    }

    # 添加全文搜索字段
    record[self.fulltext_field] = (
        payload.get("fulltext_content") or
        payload.get("data") or
        payload.get("content") or ""
    )

    # 添加稀疏向量（如果启用）
    if self.include_sparse and "sparse_embedding" in payload:
        record[self.sparse_vector_field] = payload["sparse_embedding"]

    return record
```

**字段映射表**：

| Python字段 | 数据库字段 | 类型 | 说明 |
|-----------|----------|------|------|
| vector_field | embedding | VECTOR(1536) | OpenAI向量嵌入 |
| text_field | text_content | VARCHAR(65535) | 记忆文本内容 |
| metadata_field | metadata | JSON | 元数据（用户自定义） |
| fulltext_field | fulltext_content | VARCHAR(65535) | 全文搜索内容 |
| sparse_vector_field | sparse_embedding | VECTOR(稀疏) | 稀疏向量（可选）|

### 1.6 pyobvector.upsert() 执行

**底层库**：pyobvector（OceanBase Python客户端）

**SQL生成**（伪代码）：

```sql
-- UPSERT操作（UPDATE + INSERT的原子操作）
INSERT INTO memories (
    id,
    embedding,
    text_content,
    metadata,
    user_id,
    agent_id,
    run_id,
    hash,
    created_at,
    updated_at,
    category,
    fulltext_content
) VALUES (
    123456789,  -- Snowflake ID
    '[0.1, 0.2, ...]',  -- VECTOR(1536)
    '2023年5月去了夏威夷',
    '{"key": "value"}',  -- JSON
    'user_123',
    'agent_456',
    'run_789',
    'a1b2c3d4...',
    '2026-05-04 10:00:00',  -- created_at（保持不变）
    '2026-05-05 17:14:53',  -- updated_at（更新为新时间）
    'travel',
    '2023年5月去了夏威夷'
)
ON DUPLICATE KEY UPDATE
    embedding = VALUES(embedding),
    text_content = VALUES(text_content),
    metadata = VALUES(metadata),
    hash = VALUES(hash),
    updated_at = VALUES(updated_at),
    category = VALUES(category),
    fulltext_content = VALUES(fulltext_content);
```

**关键特性**：
1. ✅ **原子操作**：要么成功，要么失败
2. ✅ **幂等性**：多次执行结果相同
3. ✅ **保留created_at**：原始创建时间不变
4. ✅ **更新updated_at**：记录修改时间

### 1.7 实际SQL示例（OceanBase）

假设记忆ID为 `123456789`，内容从 "去了夏威夷" 更新为 "2023年5月去了夏威夷"：

```sql
-- OceanBase实际执行的UPSERT语句
UPDATE memories
SET
    embedding = VECTOR('[0.123, 0.456, ..., 0.789]'),  -- 1536维
    text_content = '2023年5月去了夏威夷',
    metadata = '{}',
    hash = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    updated_at = '2026-05-05 17:14:53',
    category = '',
    fulltext_content = '2023年5月去了夏威夷'
WHERE id = 123456789;
```

**执行流程**：
1. **检查记录是否存在**：`SELECT * FROM memories WHERE id = 123456789`
2. **如果存在**：执行UPDATE语句
3. **如果不存在**：执行INSERT语句
4. **返回**：受影响的行数（0或1）

---

## 二、DELETE操作的SQL生成

### 2.1 LLM决策阶段

**输入**：矛盾的事实

**输出示例**：
```json
{
  "memory": [
    {
      "id": "1",
      "text": "喜欢披萨",
      "event": "DELETE"
    }
  ]
}
```

### 2.2 Memory.delete() 方法

**位置**：`src/powermem/core/memory.py:1514`

```python
def delete(self, memory_id: int, user_id=None, agent_id=None):
    # 步骤1：获取记忆（用于访问控制）
    memory = self.storage.get_memory(memory_id, user_id, agent_id)
    if not memory:
        logger.warning(f"Memory {memory_id} not found")
        return False

    # 步骤2：检查访问权限
    if user_id and memory.get("user_id") != user_id:
        logger.warning(f"Access denied for memory {memory_id}")
        return False

    # 步骤3：删除向量存储中的记录
    self.storage.delete_memory(memory_id, user_id, agent_id)

    # 步骤4：删除知识图谱中的实体（如果启用）
    if self.graph_store:
        deleted_entities = self.graph_store.delete_entity(
            memory_id=memory_id,
            user_id=user_id
        )
        logger.info(f"Deleted {len(deleted_entities)} graph entities")

    return True
```

### 2.3 StorageAdapter.delete_memory() 方法

**位置**：`src/powermem/storage/adapter.py:435`

```python
def delete_memory(self, memory_id: int, user_id=None, agent_id=None):
    # 步骤1：检查记忆是否存在
    existing = self.get_memory(memory_id, user_id, agent_id)
    if not existing:
        return False

    # 步骤2：从向量存储中删除
    try:
        self.vector_store.delete(memory_id)
        return True
    except Exception as e:
        logger.debug(f"Failed to delete from main store: {e}")

    # 步骤3：如果主存储失败，尝试从子存储删除
    if self.sub_stores:
        for sub_config in self.sub_stores.values():
            try:
                sub_config.vector_store.delete(memory_id)
                logger.debug(f"Deleted from sub store {sub_config.name}")
                return True
            except Exception:
                continue

    return False
```

### 2.4 OceanBaseVectorStore.delete() 方法

**位置**：`src/powermem/storage/oceanbase/oceanbase.py:1870`

```python
def delete(self, vector_id: int):
    """根据ID删除向量"""
    try:
        # 直接调用pyobvector的delete方法
        self.obvector.delete(
            table_name=self.collection_name,  # "memories"
            ids=[vector_id]
        )
        logger.debug(f"Successfully deleted vector ID: {vector_id}")
    except Exception as e:
        logger.error(f"Failed to delete vector {vector_id}: {e}")
        raise
```

### 2.5 pyobvector.delete() 执行

**底层库**：pyobvector

**生成的SQL**：

```sql
-- OceanBase实际执行的DELETE语句
DELETE FROM memories
WHERE id = 123456789;
```

**执行流程**：
1. **检查记录是否存在**：内部验证
2. **执行DELETE语句**：根据主键ID删除
3. **返回**：受影响的行数（0或1）
4. **清理索引**：自动更新向量索引和全文索引

---

## 三、完整示例：UPDATE操作追踪

### 场景：用户纠正记忆中的时间信息

#### 步骤1：对话输入

```
用户: "去年五月我去了夏威夷"
```

#### 步骤2：LLM提取事实

```json
{
  "facts": ["去年五月去了夏威夷"]
}
```

#### 步骤3：向量检索相似记忆

```json
{
  "results": [
    {
      "id": 123456789,
      "memory": "去了夏威夷",
      "score": 0.85
    }
  ]
}
```

#### 步骤4：LLM决策

```json
{
  "memory": [
    {
      "id": "0",  // 临时ID，映射到真实ID 123456789
      "text": "去年五月去了夏威夷",
      "event": "UPDATE",
      "old_memory": "去了夏威夷"
    }
  ]
}
```

#### 步骤5：Memory._update_memory()

**Python代码**：
```python
memory_id = 123456789
content = "去年五月去了夏威夷"

# 生成新向量
embedding = embedding_service.embed(content)
# -> [0.123, 0.456, ..., 0.789] (1536维)

# 生成新哈希
content_hash = hashlib.md5(content.encode()).hexdigest()
# -> "7d8a9b2c..."

# 构建更新数据
update_data = {
    "content": content,
    "embedding": embedding,
    "hash": content_hash,
    "updated_at": datetime.now()
}
```

#### 步骤6：StorageAdapter.update_memory()

**payload转换**：
```python
updated_payload = {
    "data": "去年五月去了夏威夷",           # 映射到text_content
    "fulltext_content": "去年五月去了夏威夷",  # 映射到fulltext_content
    "embedding": [0.123, 0.456, ...],      # 映射到embedding
    "hash": "7d8a9b2c...",
    "updated_at": "2026-05-05 17:14:53",
    # ... 其他字段保持不变
}
```

#### 步骤7：OceanBaseVectorStore.update()

**记录构建**：
```python
temp_record = {
    "id": 123456789,
    "embedding": [0.123, 0.456, ...],
    "text_content": "去年五月去了夏威夷",
    "metadata": "{}",
    "user_id": "user_123",
    "agent_id": "agent_456",
    "run_id": "run_789",
    "hash": "7d8a9b2c...",
    "created_at": "2026-05-04 10:00:00",  # 保持原值
    "updated_at": "2026-05-05 17:14:53",  # 新值
    "category": "",
    "fulltext_content": "去年五月去了夏威夷"
}
```

#### 步骤8：pyobvector.upsert()

**最终SQL**：
```sql
INSERT INTO memories (
    id, embedding, text_content, metadata,
    user_id, agent_id, run_id, hash,
    created_at, updated_at, category, fulltext_content
) VALUES (
    123456789,
    VECTOR('[0.123, 0.456, ..., 0.789]'),
    '去年五月去了夏威夷',
    '{}',
    'user_123',
    'agent_456',
    'run_789',
    '7d8a9b2c...',
    '2026-05-04 10:00:00',
    '2026-05-05 17:14:53',
    '',
    '去年五月去了夏威夷'
)
ON DUPLICATE KEY UPDATE
    embedding = VALUES(embedding),
    text_content = VALUES(text_content),
    hash = VALUES(hash),
    updated_at = VALUES(updated_at),
    fulltext_content = VALUES(fulltext_content);
```

**执行结果**：
- ✅ 记忆ID 123456789 被成功更新
- ✅ 内容从 "去了夏威夷" 更新为 "去年五月去了夏威夷"
- ✅ 向量嵌入被更新（新内容的向量）
- ✅ `created_at` 保持原值
- ✅ `updated_at` 更新为当前时间

---

## 四、完整示例：DELETE操作追踪

### 场景：用户纠正矛盾信息

#### 步骤1：对话输入

```
用户: "我不喜欢披萨，我讨厌它"
```

#### 步骤2：LLM提取事实

```json
{
  "facts": ["讨厌披萨"]
}
```

#### 步骤3：向量检索相似记忆

```json
{
  "results": [
    {
      "id": 987654321,
      "memory": "喜欢披萨",
      "score": 0.92
    }
  ]
}
```

#### 步骤4：LLM决策

```json
{
  "memory": [
    {
      "id": "0",  // 临时ID，映射到真实ID 987654321
      "text": "讨厌披萨",
      "event": "DELETE"
    }
  ]
}
```

#### 步骤5：Memory.delete()

**Python代码**：
```python
memory_id = 987654321

# 检查访问权限
memory = self.storage.get_memory(memory_id, user_id, agent_id)
if memory.get("user_id") != user_id:
    return False  # 访问被拒绝

# 删除向量存储
self.storage.delete_memory(memory_id, user_id, agent_id)

# 删除知识图谱实体
if self.graph_store:
    deleted_entities = self.graph_store.delete_entity(
        memory_id=memory_id,
        user_id=user_id
    )
```

#### 步骤6：StorageAdapter.delete_memory()

```python
existing = self.get_memory(memory_id, user_id, agent_id)
if existing:
    self.vector_store.delete(memory_id)
    return True
```

#### 步骤7：OceanBaseVectorStore.delete()

```python
self.obvector.delete(
    table_name="memories",
    ids=[987654321]
)
```

#### 步骤8：pyobvector.delete()

**最终SQL**：
```sql
DELETE FROM memories
WHERE id = 987654321;
```

**知识图谱清理**（同时执行）：
```sql
-- 删除相关的图谱实体
DELETE FROM graph_entities
WHERE memory_id = 987654321;

-- 删除相关的图谱关系
DELETE FROM graph_relations
WHERE from_entity_id IN (
    SELECT id FROM graph_entities WHERE memory_id = 987654321
) OR to_entity_id IN (
    SELECT id FROM graph_entities WHERE memory_id = 987654321
);
```

**执行结果**：
- ✅ 记忆ID 987654321 被删除
- ✅ 相关的知识图谱实体被删除
- ✅ 相关的知识图谱关系被删除
- ✅ 向量索引被自动更新

---

## 五、关键设计模式

### 5.1 Payload合并模式

**目的**：避免部分更新时丢失数据

```python
# ❌ 错误做法：直接覆盖
payload["data"] = new_content  # 丢失其他字段！

# ✅ 正确做法：先合并后更新
existing = get_existing_payload()
merged = {**existing, **new_payload}  # 保留旧字段，覆盖新字段
update(merged)
```

### 5.2 字段映射模式

**目的**：统一不同层次的字段命名

| 层次 | content字段名 | 嵌套位置 |
|------|-------------|---------|
| 应用层 | content | update_data["content"] |
| 适配层 | data | payload["data"] |
| 数据库层 | text_content | memories.text_content |

**转换代码**：
```python
# 应用层 → 适配层
if "content" in update_data:
    payload["data"] = update_data["content"]
    payload["fulltext_content"] = update_data["content"]

# 适配层 → 数据库层
self.text_field: payload.get("data") or ""
```

### 5.3 向量更新策略

**决策**：何时更新向量嵌入？

| 情况 | 更新向量？ | 原因 |
|------|----------|------|
| 内容改变 | ✅ 是 | 新内容需要新向量 |
| 元数据改变 | ❌ 否 | 元数据不影响语义 |
| 时间信息改变 | ✅ 是 | 时间改变语义（语义增强）|

**代码实现**：
```python
if "content" in update_data:
    # 内容改变，重新生成向量
    new_embedding = embedding_service.embed(new_content)
    update_data["embedding"] = new_embedding
```

### 5.4 UPSERT vs INSERT+UPDATE

**UPSERT优势**：
- ✅ **原子性**：单条语句，无需事务
- ✅ **幂等性**：多次执行结果相同
- ✅ **性能**：减少数据库往返次数
- ✅ **简洁**：代码更简单

**传统INSERT+UPDATE**：
```sql
-- 需要两条语句 + 事务
BEGIN;
SELECT id FROM memories WHERE id = ?;
IF EXISTS THEN
    UPDATE memories SET ... WHERE id = ?;
ELSE
    INSERT INTO memories (...) VALUES (...);
END IF;
COMMIT;
```

**UPSERT**：
```sql
-- 一条语句搞定
INSERT INTO memories (...) VALUES (...)
ON DUPLICATE KEY UPDATE ...;
```

---

## 六、性能优化

### 6.1 批量操作

**场景**：同时更新多条记忆

```python
# ❌ 低效：逐条更新
for memory_id in memory_ids:
    store.update(memory_id, new_data)

# ✅ 高效：批量upsert
batch_data = []
for memory_id in memory_ids:
    batch_data.append(build_update_record(memory_id))
store.upsert(batch_data)  # 一次性更新
```

### 6.2 索引优化

**OceanBase自动维护的索引**：

```sql
-- 主键索引（自动）
CREATE UNIQUE INDEX pk_id ON memories(id);

-- 向量索引（自动）
CREATE VECTOR INDEX idx_embedding ON memories(embedding);

-- 全文索引（自动）
CREATE FULLTEXT INDEX idx_fulltext ON memories(fulltext_content);

-- 用户ID索引（推荐）
CREATE INDEX idx_user_id ON memories(user_id);

-- 复合索引（推荐）
CREATE INDEX idx_user_updated ON memories(user_id, updated_at);
```

### 6.3 向量嵌入缓存

**优化**：避免重复生成向量

```python
# 在_intelligent_add中
fact_embeddings = {}
for fact in facts:
    # 为每个事实生成一次向量
    fact_embeddings[fact] = embedding_service.embed(fact)

# 后续ADD/UPDATE操作时复用
if fact in existing_embeddings:
    embedding = existing_embeddings[fact]  # 复用
else:
    embedding = generate_new_embedding(fact)  # 生成新的
```

---

## 七、错误处理

### 7.1 记忆不存在

**场景**：UPDATE时记忆已被删除

```python
existing_rows = self._get_records_by_id(vector_id, columns)
if not existing_rows:
    logger.warning(f"Memory {vector_id} not found")
    return  # 优雅降级，不抛异常
```

### 7.2 访问权限拒绝

**场景**：用户尝试删除其他用户的记忆

```python
memory = self.storage.get_memory(memory_id, user_id, agent_id)
if user_id and memory.get("user_id") != user_id:
    logger.warning(f"Access denied for memory {memory_id}")
    return False  # 拒绝访问
```

### 7.3 向量嵌入失败

**场景**：Embedding服务不可用

```python
try:
    vector = embedding_service.embed(content)
except Exception as e:
    logger.warning(f"Failed to generate embedding: {e}")
    vector = [0.1] * 1536  # 降级到mock向量
```

### 7.4 数据库连接失败

**场景**：OceanBase连接中断

```python
try:
    self.obvector.upsert(table_name, data)
except OperationalError as e:
    logger.error(f"Database connection failed: {e}")
    raise RetryableError("Temporary database error")  # 可重试错误
```

---

## 八、调试建议

### 8.1 启用SQL日志

**配置OceanBase日志**：

```python
import logging
logging.getLogger('pyobvector').setLevel(logging.DEBUG)
logging.getLogger('sqlalchemy.engine').setLevel(logging.INFO)
```

**输出示例**：
```
[DEBUG] pyobvector: Executing SQL:
INSERT INTO memories (...) VALUES (...) ON DUPLICATE KEY UPDATE ...
[INFO] sqlalchemy.engine: Col ('memories'), 1 rows affected
```

### 8.2 监控UPDATE/DELETE操作

**添加审计日志**：

```python
# 在Memory类中
self.audit.log_event("memory.update", {
    "memory_id": memory_id,
    "old_content": old_memory,
    "new_content": new_content,
    "user_id": user_id,
    "agent_id": agent_id
}, user_id=user_id)
```

### 8.3 性能监控

**记录操作耗时**：

```python
import time

start = time.time()
self.storage.update_memory(memory_id, update_data, user_id, agent_id)
duration = time.time() - start

logger.info(f"UPDATE memory {memory_id} took {duration:.3f}s")
```

---

## 九、总结

### UPDATE操作SQL生成流程

```
LLM决策 (event="UPDATE")
    ↓
Memory._update_memory()
    ├─ 生成新向量嵌入
    ├─ 生成新哈希
    └─ 构建update_data
    ↓
StorageAdapter.update_memory()
    ├─ 获取现有payload
    ├─ 合并新旧payload
    └─ 映射字段 (content → data)
    ↓
OceanBaseVectorStore.update()
    ├─ 获取现有记录（所有字段）
    ├─ 合并payload（避免数据丢失）
    └─ 调用_build_record_for_insert()
    ↓
_build_record_for_insert()
    ├─ 序列化元数据
    ├─ 映射到数据库字段名
    └─ 添加全文搜索字段
    ↓
pyobvector.upsert()
    └─ 生成UPSERT SQL语句
    ↓
OceanBase执行SQL
    INSERT ... ON DUPLICATE KEY UPDATE ...
```

### DELETE操作SQL生成流程

```
LLM决策 (event="DELETE")
    ↓
Memory.delete()
    ├─ 检查访问权限
    └─ 删除知识图谱实体
    ↓
StorageAdapter.delete_memory()
    ├─ 检查记忆是否存在
    └─ 尝试删除
    ↓
OceanBaseVectorStore.delete()
    └─ 调用pyobvector.delete()
    ↓
pyobvector.delete()
    └─ 生成DELETE SQL语句
    ↓
OceanBase执行SQL
    DELETE FROM memories WHERE id = ?
```

### 关键要点

1. **分层架构**：Memory → StorageAdapter → VectorStore → pyobvector
2. **字段映射**：content → data → text_content
3. **payload合并**：避免部分更新时丢失数据
4. **UPSERT模式**：原子操作，简化代码
5. **向量更新**：内容改变时必须重新生成向量
6. **错误处理**：优雅降级，记录日志
7. **性能优化**：批量操作、向量缓存、索引优化
