# PowerMem 集成问题清单

本文档记录 PowerMem v1.1.0 SDK 集成过程中遇到的问题及解决方案。

## 问题 1：SDK Bug - graph_store 初始化错误

**症状**：配置正确但图谱表未创建，日志显示 `enable_graph: False`

**原因**：PowerMem v1.1.0 SDK 存在 Bug
- 位置：`powermem/core/memory.py:234-235` 和 `async_memory.py:157-158`
- 问题：将整个 `MemoryConfig` 对象传递给 `GraphStoreFactory.create()`，而不是只传递 `graph_store` 字典
- 导致：Pydantic 验证失败（额外字段错误）

**解决方案**：应用层 monkey-patch
- 文件：`core/providers/memory/powermem/powermem.py`
- 函数：`_fix_powermem_graph_store_bug()`
- 原理：在 Memory/AsyncMemory 初始化时移除 graph_store 配置，阻止原始 __init__ 执行错误代码，然后手动创建 MemoryGraph

**状态**：✅ 已解决（SDK v1.1.0 仍需此 patch）

---

## 问题 2：缺少 "enabled" 字段

**症状**：配置了 graph_store 但图谱未启用

**原因**：PowerMem 的 `_get_graph_enabled()` 方法检查 `graph_store.enabled` 字段

**解决方案**：在数据库配置中添加 `"enabled": true`
```json
{
  "graph_store": {
    "enabled": true,  // 必需字段
    "provider": "oceanbase",
    "config": { ... }
  }
}
```

**状态**：✅ 已解决

---

## 问题 3：缺少 embedding_model_dims

**症状**：`ValueError: embedding_model_dims is required for OceanBase graph operations`

**原因**：OceanBase 图谱存储需要向量维度参数

**解决方案**：在 `graph_store.config` 中添加 `"embedding_model_dims": 1536`
```json
{
  "graph_store": {
    "enabled": true,
    "provider": "oceanbase",
    "config": {
      "embedding_model_dims": 1536,  // 必需字段
      "host": "localhost",
      "port": 2881,
      ...
    }
  }
}
```

**状态**：✅ 已解决

---

## 问题 4：数据库密码类型错误

**症状**：`Input should be a valid string [type=string_type, input_value=123456, input_type=int]`

**原因**：数据库密码配置为整数而非字符串

**解决方案**：确保数据库密码为字符串类型
```json
{
  "vector_store": {
    "config": {
      "password": "123456"  // 字符串，非整数
    }
  }
}
```

**状态**：✅ 已解决

---

## 问题 5：对话未挂断时旧记忆被自动删除

**发现日期**：2026-05-07

**症状**：
- 用户开启新对话后，在对话未挂断的情况下，昨天的所有记忆被自动删除
- `memories_audit_log` 表显示在对话开始后不久就有大量 DELETE 操作
- 例如：
  - 05:49:26 - 识别到"你好，蛋蛋"
  - 05:49:27 - 14 条 DELETE 操作（删除昨天 17:44 创建的记忆）
  - 06:00:01 - WebSocket 连接超时关闭
  - 06:00:35 - 7 条 INSERT 操作（保存当前对话记忆）

**错误假设**：
- ❌ 认为是 `save_memory()` 方法在连接关闭时触发的删除
- ❌ 认为是延迟的守护线程执行了昨天的删除操作
- ❌ 认为需要挂断对话才会触发记忆清理

**根本原因**：

PowerMem 的**智能记忆管理插件**在**第一次查询记忆时**就会自动触发清理操作，而不是等到对话结束时。

这是 PowerMem SDK 的**设计行为**，基于艾宾浩斯遗忘曲线（Ebbinghaus Forgetting Curve）自动评估和清理"应该遗忘"的记忆。

### 完整调用链

```
用户发送消息 "你好，蛋蛋"
  ↓
系统调用 query_memory() 检索相关记忆
  ↓ core/providers/memory/powermem/powermem.py:108
  ↓
powermem/core/memory.py:1167 - search() 方法被调用
  ↓
powermem/core/memory.py:1208 - storage.search_memories() 执行向量搜索
  ↓
powermem/core/memory.py:1227 - intelligence_plugin.on_search(搜索结果)
  ↓ 关键步骤：智能插件钩子
powermem/intelligence/plugin.py:167-193 - on_search() 方法
  ↓ 对每个搜索结果调用
powermem/intelligence/plugin.py:121-132 - on_get() 方法
  ↓ 检查艾宾浩斯遗忘条件
powermem/intelligence/ebbinghaus_algorithm.py - should_forget() 方法
  ↓ 判断记忆是否应该被遗忘
返回需要删除的 memory IDs 列表
  ↓
powermem/core/memory.py:1244-1247 - 批量执行删除操作
  ↓
storage.delete_memory(memory_id, user_id, agent_id)
  ↓
OceanBase 触发器自动记录到 memories_audit_log 表
```

### 关键代码位置

**1. 智能插件钩子触发点**：
`powermem/core/memory.py:1225-1247`
```python
# Intelligent plugin lifecycle management on search
if self._intelligence_plugin and self._intelligence_plugin.enabled:
    updates, deletes = self._intelligence_plugin.on_search(processed_results)
    # For embedded SeekDB the engine is single-threaded (NullPool, not
    # thread-safe).  Background threads opening concurrent connections
    # crash the C++ layer.  Run updates/deletes synchronously instead.
    _is_embedded_store = (
        hasattr(self.storage, 'vector_store')
        and hasattr(self.storage.vector_store, 'connection_args')
        and not self.storage.vector_store.connection_args.get("host")
    )
    if updates:
        for mem_id, upd in updates:
            if _is_embedded_store:
                self.storage.update_memory(mem_id, {**upd}, user_id, agent_id)
            else:
                _BACKGROUND_EXECUTOR.submit(self.storage.update_memory, mem_id, {**upd}, user_id, agent_id)
        logger.info(f"Submitted {len(updates)} update operations to background executor")
    if deletes:
        for mem_id in deletes:
            if _is_embedded_store:
                self.storage.delete_memory(mem_id, user_id, agent_id)
            else:
                _BACKGROUND_EXECUTOR.submit(self.storage.delete_memory, mem_id, user_id, agent_id)
        logger.info(f"Submitted {len(deletes)} delete operations to background executor")
```

**2. 智能插件搜索钩子**：
`powermem/intelligence/plugin.py:167-193`
```python
def on_search(self, results: List[Dict[str, Any]]) -> Tuple[List[Tuple[str, Dict[str, Any]]], List[str]]:
    """
    Hook invoked on batch search results.
    Returns (updates, delete_ids).
    updates: list of (memory_id, update_dict)
    delete_ids: list of memory ids to delete
    """
    if not self.enabled or not self._algo:
        return [], []
    updates: List[Tuple[str, Dict[str, Any]]] = []
    deletes: List[str] = []

    for item in results:
        try:
            mem_id = item.get("id") or item.get("memory_id")
            if not mem_id:
                continue

            # Process individual memory
            upd, delete_flag = self.on_get(item)

            if delete_flag:
                deletes.append(mem_id)
            elif upd:
                # Add search-specific enhancements
                search_updates = self._enhance_for_search(item, upd)
                updates.append((mem_id, search_updates))

        except Exception as e:
            logger.warning(f"Failed to process memory {mem_id} in on_search: {e}")
            continue

    return updates, deletes
```

**3. 遗忘判断逻辑**：
`powermem/intelligence/plugin.py:121-132`
```python
def on_get(self, memory: Dict[str, Any]) -> Tuple[Optional[Dict[str, Any]], bool]:
    """
    Hook invoked on single memory access.
    Returns (updates, delete_flag). If delete_flag is True, caller should delete memory.
    """
    if not self.enabled or not self._algo:
        return None, False
    try:
        updates: Dict[str, Any] = {
            "access_count": (memory.get("access_count") or 0) + 1,
            "updated_at": get_current_datetime(),
        }

        # Check if memory should be forgotten
        if self._algo.should_forget(memory):
            return None, True  # 👈 返回 True 表示应该删除

        # ... 其他逻辑
```

### 时间线验证

从数据库审计日志和应用程序日志的对比：

| 时间 (北京时间) | 事件 |
|----------------|------|
| 05:47:xx | 服务器启动 |
| 05:48:48 | WebSocket 连接建立 |
| 05:49:26 | ASR 识别到"你好，蛋蛋" |
| **05:49:27** | **14 条 DELETE 操作**（智能插件在查询时自动清理） |
| 06:00:01 | WebSocket 连接超时关闭 |
| 06:00:35 | 7 条 INSERT 操作（保存当前对话的记忆） |

**关键发现**：
- DELETE 操作发生在对话进行中（05:49:27），**不是**在连接关闭时（06:00:01）
- DELETE 操作发生在 `query_memory()` → `search()` 调用期间，**不是**在 `save_memory()` 调用期间
- 应用程序日志**没有**记录 `save_memory()` 或 `add()` 调用，因为这是通过 `search()` → `intelligence_plugin.on_search()` 触发的

### 艾宾浩斯遗忘曲线算法

PowerMem 使用艾宾浩斯遗忘曲线来评估记忆是否应该被遗忘：

- **working memory**（工作记忆）：短期，容易遗忘
- **short_term memory**（短期记忆）：中等期限
- **long_term memory**（长期记忆）：重要记忆，不易遗忘

遗忘判断因素：
- 记忆的重要性评分（importance_score）
- 记忆类型（memory_type）
- 访问次数（access_count）
- 最后访问时间（last_accessed_at）
- 创建时间（created_at）
- 配置的遗忘曲线参数（decay_rate、review_interval 等）

### 解决方案

如果想**禁用自动记忆清理**功能，可以在配置中禁用智能插件：

```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: false  # 禁用智能插件（包括自动清理）
```

或者，只调整遗忘曲线参数，使记忆更不容易被删除：

```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: true
        ebbinghaus:
          decay_rate: 0.1  # 降低遗忘率（默认可能是 0.5）
          short_term_threshold: 0.3  # 调整阈值
          long_term_threshold: 0.7
```

**注意**：
- `infer=False` 参数只影响 `add()` 方法的智能模式，**不影响** `search()` 时的智能清理
- 智能插件的 `on_search()` 钩子是独立于 `add()` 方法的，无法通过参数禁用
- 只能通过配置 `plugin.enabled: false` 完全禁用智能插件

### 相关文档

- [PowerMem-记忆处理原理详解.md](./PowerMem-记忆处理原理详解.md) - PowerMem 记忆管理原理
- [PowerMemory-Intelligent-Processing-Flow.md](./PowerMemory-Intelligent-Processing-Flow.md) - 智能处理流程
- [PowerMem-1.1.0-Analysis.md](./PowerMem-1.1.0-Analysis.md) - SDK 源码分析

### SDK 源码位置

- 智能插件接口：`~/.venv/lib/python3.12/site-packages/powermem/intelligence/plugin.py`
- 艾宾浩斯算法：`~/.venv/lib/python3.12/site-packages/powermem/intelligence/ebbinghaus_algorithm.py`
- Memory 类：`~/.venv/lib/python3.12/site-packages/powermem/core/memory.py`
- PowerMem SDK 完整源码：`~/codes/github/powermem-1.1.0/`

**状态**：✅ 已理解（这是 PowerMem SDK 的设计行为，不是 Bug）

---

## 正确的 PowerMem 配置示例

```yaml
memory:
  enable_user_profile: true  # UserMemory 模式（用户画像）
  vector_store:
    provider: oceanbase
    config:
      host: localhost
      port: 2881
      user: root@test
      password: "123456"  # 字符串类型
      db_name: powermem
      collection_name: memories
      embedding_model_dims: 1536
  llm:
    provider: qwen
    config:
      api_key: sk-xxx
      model: qwen-turbo
  embedder:
    provider: openai
    config:
      api_key: sk-xxx
      model: embedding-3
  graph_store:
    enabled: true  # 必需
    provider: oceanbase
    config:
      host: localhost
      port: 2881
      user: root@test
      password: "123456"
      db_name: powermem
      max_hops: 3
      embedding_model_dims: 1536  # 必需
```

## 验证清单

启动服务后，确认以下表已创建：

- [ ] `memories` - 向量记忆表
- [ ] `user_profiles` - 用户画像表
- [ ] `graph_entities` - 图谱实体表
- [ ] `graph_relationships` - 图谱关系表

验证命令：
```bash
python check_tables.py
```

## 相关文件

- `core/providers/memory/powermem/powermem.py` - PowerMem 集成代码
- `check_tables.py` - 表验证脚本
- `test_graph_fix.py` - Bug 修复验证脚本
