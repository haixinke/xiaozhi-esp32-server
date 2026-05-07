# PowerMem 智能记忆控制参数对比

## 核心问题

**禁用智能记忆清理功能**（配置 `plugin.enabled: false`）和 **调用 add() 时传参 `infer=False`** 是**完全不同**的两回事。

---

## 快速对比表

| 对比项 | `plugin.enabled: false` | `infer=False` |
|--------|------------------------|---------------|
| **控制层级** | **智能插件层级**（高层） | **添加方法层级**（底层） |
| **影响范围** | 搜索时的智能管理 | 添加记忆时的智能处理 |
| **主要作用** | 禁用搜索时的自动删除 | 禁用添加时的智能合并 |
| **配置位置** | `data/.config.yaml` | 调用 `add()` 时传参 |
| **生效时机** | 每次搜索记忆时 | 每次添加记忆时 |
| **是否保留记忆查询功能** | ✅ 是 | ✅ 是 |
| **是否保留记忆添加功能** | ✅ 是 | ✅ 是 |
| **是否阻止自动删除** | ✅ **是** | ❌ **否** |

---

## 详细说明

### 1. plugin.enabled: false（禁用智能插件）

#### 配置方式

```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: false  # 👈 禁用智能插件
```

#### 作用范围

**禁用整个智能插件系统**，包括：

1. **on_add 钩子**：
   - ❌ 不再评估记忆重要性
   - ❌ 不再分类记忆类型（working/short_term/long_term）
   - ❌ 不再生成艾宾浩斯元数据

2. **on_search 钩子**（**关键**）：
   - ❌ 不再检查记忆是否应该遗忘
   - ❌ 不再删除旧记忆
   - ❌ 不再更新 `access_count`

3. **on_get 钩子**：
   - ❌ 不再检查记忆是否应该升级

#### 代码逻辑

**位置**：`powermem/core/memory.py:1226`

```python
# Intelligent plugin lifecycle management on search
if self._intelligence_plugin and self._intelligence_plugin.enabled:
    # ✅ 启用了智能插件
    updates, deletes = self._intelligence_plugin.on_search(processed_results)
    # 执行删除操作...
else:
    # ❌ 禁用了智能插件
    # 跳过 on_search 钩子，不执行任何检查和删除
    pass
```

#### 效果

- ✅ **阻止搜索时的自动删除**（主要效果）
- ✅ 记忆永久保留，不会被遗忘曲线删除
- ❌ 失去所有智能记忆管理功能
- ❌ 不再有重要性评分
- ❌ 不再有记忆分类
- ❌ 不再有访问统计

---

### 2. infer=False（禁用智能添加模式）

#### 调用方式

```python
result = memory.add(
    messages=conversation,
    user_id="user123",
    infer=False  # 👈 禁用智能添加模式
)
```

#### 作用范围

**仅禁用 add() 方法的智能添加逻辑**：

**位置**：`powermem/core/memory.py:632-640`

```python
def add(self, messages, ..., infer: bool = True):
    # 检查是否使用智能添加
    use_infer = infer and isinstance(messages, list) and len(messages) > 0

    # 如果不使用智能添加，回退到简单模式
    if not use_infer:  # infer=False 时进入此分支
        return self._simple_add(messages, ...)  # 👈 简单添加

    # 智能添加模式：提取事实、搜索相似记忆、合并
    return self._intelligent_add(messages, ...)  # 智能添加
```

#### 两种模式对比

##### 简单添加模式（infer=False）

**位置**：`powermem/core/memory.py:_simple_add()`

```python
def _simple_add(self, messages, ...):
    # 直接保存消息，不进行智能处理
    for message in messages:
        content = message.get("content")

        # 直接插入数据库
        memory_id = self.storage.add_memory(
            content=content,
            user_id=user_id,
            agent_id=agent_id,
            metadata=metadata
        )

        results.append({
            "id": memory_id,
            "memory": content,
            "event": "ADD"
        })

    return {"results": results}
```

**特点**：
- ✅ 直接保存对话内容
- ✅ 不提取事实（facts）
- ✅ 不搜索相似记忆
- ✅ 不合并重复内容
- ✅ 速度快（无额外 LLM 调用）
- ❌ 可能产生重复记忆
- ❌ 无法自动更新旧记忆

##### 智能添加模式（infer=True，默认）

**位置**：`powermem/core/memory.py:_intelligent_add()`

```python
def _intelligent_add(self, messages, ...):
    # 步骤 1: 提取事实
    facts = self._extract_facts(messages)
    # 例如: ["用户喜欢喝咖啡", "用户住在上海"]

    # 步骤 2: 搜索相似记忆
    for fact in facts:
        similar_memories = self.storage.search_memories(
            query_embedding=embedding,
            limit=5
        )

    # 步骤 3: 让 LLM 决定操作（ADD/UPDATE/DELETE）
    actions = self._decide_memory_actions(
        facts,
        existing_memories
    )
    # 例如: [
    #   {"action": "UPDATE", "id": 123, "content": "用户喜欢喝咖啡，不加糖"},
    #   {"action": "ADD", "content": "用户住在上海浦东"},
    #   {"action": "DELETE", "id": 456}
    # ]

    # 步骤 4: 执行操作
    for action in actions:
        if action["event"] == "ADD":
            self.storage.add_memory(...)
        elif action["event"] == "UPDATE":
            self.storage.update_memory(...)
        elif action["event"] == "DELETE":
            self.storage.delete_memory(...)

    return {"results": results}
```

**特点**：
- ✅ 自动提取关键信息
- ✅ 搜索并合并相似记忆
- ✅ 自动更新旧记忆
- ✅ 删除重复或冲突记忆
- ✅ 记忆质量更高
- ❌ 需要多次 LLM 调用（速度慢）
- ❌ 可能删除有用的记忆（通过 DELETE 操作）

#### 效果

- ✅ **禁用添加时的智能处理**（不合并、不提取事实）
- ✅ 保留简单的记忆添加功能
- ✅ 提高性能（减少 LLM 调用）
- ❌ **不影响搜索时的自动删除**
- ⚠️ **无法阻止 on_search 钩子的删除操作**

---

## 关键区别总结

### 区别 1：控制的层级不同

```
┌──────────────────────────────────────────────────────────┐
│                    PowerMem 架构                          │
└──────────────────────────────────────────────────────────┘

高层: 智能插件（IntelligencePlugin）
  ├─ on_add: 评估重要性、分类记忆
  ├─ on_search: 检查是否应该遗忘、删除记忆  👈 受 plugin.enabled 控制
  └─ on_get: 检查是否应该升级

底层: add() 方法
  ├─ infer=True:  _intelligent_add()   👈 受 infer 参数控制
  │                ├─ 提取事实
  │                ├─ 搜索相似
  │                └─ 合并/删除
  └─ infer=False: _simple_add()
                   └─ 直接保存
```

### 区别 2：影响的操作不同

#### plugin.enabled: false 的影响

```python
# 搜索记忆时
memory.search(query="用户喜欢什么？")
  ↓
# ❌ 跳过 on_search 钩子
# ❌ 不检查 should_forget()
# ❌ 不执行任何删除操作
  ↓
返回搜索结果（不会删除任何记忆）
```

#### infer=False 的影响

```python
# 添加记忆时
memory.add(messages, infer=False)
  ↓
# 直接调用 _simple_add()
# ✅ 保存原始对话内容
# ❌ 不提取事实
# ❌ 不搜索相似记忆
# ❌ 不合并重复
  ↓
返回添加结果

# 但搜索时仍然会：
memory.search(query="...")
  ↓
# ✅ 仍然调用 on_search 钩子（如果 plugin.enabled=true）
# ✅ 仍然检查 should_forget()
# ✅ 仍然可能删除记忆
```

### 区别 3：对自动删除的影响

#### 场景 1: plugin.enabled: false

```python
配置:
  plugin.enabled: false

操作:
  memory.add(messages, infer=True)  # 智能添加
  memory.search(query="...")        # 搜索

结果:
  ✅ 添加时会提取事实、合并记忆
  ✅ 搜索时不会删除任何记忆（插件已禁用）
  ✅ 记忆永久保留
```

#### 场景 2: infer=False

```python
配置:
  plugin.enabled: true  # 默认

操作:
  memory.add(messages, infer=False)  # 简单添加
  memory.search(query="...")         # 搜索

结果:
  ✅ 添加时直接保存，不合并
  ⚠️ 搜索时仍然会检查并删除记忆（插件仍启用）
  ⚠️ 昨天的记忆仍可能被删除
```

#### 场景 3: 两者都禁用

```python
配置:
  plugin.enabled: false

操作:
  memory.add(messages, infer=False)  # 简单添加

结果:
  ✅ 添加时直接保存
  ✅ 搜索时不会删除记忆
  ✅ 记忆永久保留，简单高效
```

---

## 实际案例分析

### 案例 1: 昨天的记忆被删除

**配置**：
```yaml
plugin:
  enabled: true  # 启用了智能插件（默认）
```

**操作序列**：
```
昨天 17:44 - memory.add(messages, infer=True)
  - 使用智能添加模式
  - 提取事实、搜索相似记忆、合并

今天 05:49 - memory.search(query="你好")
  - 触发 on_search 钩子
  - 检查 should_forget()
  - 删除了 14 条记忆
```

**问题根源**：`plugin.enabled: true` 导致搜索时自动删除

### 案例 2: 使用 infer=False 但仍然被删除

**配置**：
```yaml
plugin:
  enabled: true  # 插件仍启用
```

**操作序列**：
```
memory.add(messages, infer=False)  # 简单添加
  - 直接保存对话内容
  - 不提取事实、不合并

memory.search(query="...")  # 搜索
  - ❌ 仍然触发 on_search 钩子
  - ❌ 仍然检查 should_forget()
  - ❌ 仍然可能删除记忆
```

**问题**：`infer=False` 无法阻止搜索时的删除

### 案例 3: 正确禁用自动删除

**配置**：
```yaml
plugin:
  enabled: false  # 禁用智能插件
```

**操作序列**：
```
memory.add(messages, infer=False)  # 简单添加
  - 直接保存对话内容

memory.search(query="...")  # 搜索
  - ✅ 不触发 on_search 钩子
  - ✅ 不检查 should_forget()
  - ✅ 不删除任何记忆
  - ✅ 记忆永久保留
```

**成功**：`plugin.enabled: false` 成功阻止删除

---

## 如何选择

### 需求 1: 完全禁用自动删除，保留记忆

**推荐配置**：
```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: false  # 禁用智能插件
```

**调用方式**：
```python
memory.add(messages, infer=False)  # 可选，简单添加
```

**效果**：
- ✅ 记忆永久保留
- ✅ 不被自动删除
- ✅ 性能更好（无智能处理开销）

### 需求 2: 保留智能管理，但提高性能

**推荐配置**：
```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: true  # 启用智能插件
```

**调用方式**：
```python
memory.add(messages, infer=False)  # 禁用智能添加
```

**效果**：
- ✅ 添加时性能更好（不提取事实、不合并）
- ⚠️ 搜索时仍会自动删除记忆
- ❌ 可能产生重复记忆

### 需求 3: 完整的智能记忆管理

**推荐配置**：
```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: true  # 启用智能插件
```

**调用方式**：
```python
memory.add(messages, infer=True)  # 智能添加（默认）
```

**效果**：
- ✅ 自动提取事实
- ✅ 自动合并相似记忆
- ✅ 自动删除重复/冲突记忆
- ✅ 自动删除应该遗忘的记忆
- ❌ 性能开销较大

---

## 常见误区

### 误区 1: infer=False 可以禁用自动删除

❌ **错误**：`infer=False` 只影响 `add()` 方法的行为，不影响 `search()` 时的删除。

✅ **正确**：要禁用自动删除，需要设置 `plugin.enabled: false`

### 误区 2: plugin.enabled: false 会禁用所有功能

❌ **错误**：禁用插件只是禁用智能管理，记忆的添加和查询功能仍然可用。

✅ **正确**：禁用插件后，仍可以：
- 添加记忆（使用简单模式）
- 查询记忆
- 更新记忆
- 删除记忆（手动）

### 误区 3: 两者效果相同

❌ **错误**：它们控制的层次和功能完全不同。

✅ **正确**：
- `plugin.enabled: false` 控制**搜索时的智能管理**（包括删除）
- `infer=False` 控制**添加时的智能处理**（包括合并）

---

## 代码示例

### 示例 1: 禁用自动删除（推荐）

```python
# 配置文件
# data/.config.yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: false  # 禁用智能插件

# 应用代码
from core.providers.memory.powermem.powermem import PowerMem

memory = PowerMem(config)

# 添加记忆（简单模式）
result = memory.add(
    messages=conversation,
    user_id="user123"
    # 注意：这里不需要传 infer=False
    # 因为 plugin.enabled=false 会禁用所有智能功能
)

# 搜索记忆（不会自动删除）
results = memory.search(
    query="用户喜欢什么？",
    user_id="user123"
)
# ✅ 不会删除任何记忆
```

### 示例 2: 保留智能管理，但提高添加性能

```python
# 配置文件
# data/.config.yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: true  # 启用智能插件

# 应用代码
memory = PowerMem(config)

# 添加记忆（简单模式，提高性能）
result = memory.add(
    messages=conversation,
    user_id="user123",
    infer=False  # 👈 禁用智能添加
)

# 搜索记忆（仍然会自动删除）
results = memory.search(
    query="用户喜欢什么？",
    user_id="user123"
)
# ⚠️ 仍然可能删除旧记忆（plugin.enabled=true）
```

---

## 总结

| 特性 | `plugin.enabled: false` | `infer=False` |
|------|------------------------|---------------|
| **主要作用** | 禁用搜索时的智能管理 | 禁用添加时的智能处理 |
| **阻止自动删除** | ✅ **是** | ❌ **否** |
| **禁用事实提取** | ✅ 是 | ✅ 是 |
| **禁用记忆合并** | ✅ 是 | ✅ 是 |
| **保留查询功能** | ✅ 是 | ✅ 是 |
| **保留添加功能** | ✅ 是 | ✅ 是 |
| **推荐场景** | 想要记忆永久保留 | 想要提高添加性能 |

**最佳实践**：

如果目标是**防止记忆被自动删除**（你的情况），**必须使用**：

```yaml
memory:
  powermem:
    intelligent_memory:
      plugin:
        enabled: false  # 这是唯一有效的方法
```

`infer=False` **无法阻止**搜索时的自动删除！
