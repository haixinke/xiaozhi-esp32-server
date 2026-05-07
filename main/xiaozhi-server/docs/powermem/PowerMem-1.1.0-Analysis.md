# PowerMem 1.1.0 源代码分析报告

## 📋 分析目的

分析 PowerMem 1.1.0 是否还会出现"删除所有昨天的记忆"的问题。

---

## 🔍 核心发现

### 1. **记忆删除机制**

PowerMem 1.1.0 在 **`infer=True`** 模式下，**确实存在删除记忆的可能性**。

**删除流程**：

```python
# core/memory.py:574 - add() 方法
def add(self, messages, ..., infer: bool = True):
    if infer:
        return self._intelligent_add(...)  # 智能模式
    else:
        return self._simple_add(...)       # 简单模式（不删除）
```

**智能模式**（`_intelligent_add()`）的流程：

1. **提取事实**：从对话中提取关键信息（facts）
2. **搜索相似记忆**：为每个事实搜索最相似的前 10 条记忆
3. **LLM 决策**：让 LLM 决定对每条记忆执行什么操作（ADD/UPDATE/DELETE/NONE）
4. **执行操作**：根据 LLM 的决定执行操作

**关键代码**（`core/memory.py:956-968`）：

```python
elif event_type == "DELETE":
    real_memory_id = temp_uuid_mapping.get(str(action_id))
    if real_memory_id:
        self.delete(real_memory_id, user_id, agent_id)  # ← 删除记忆
        results.append({
            "id": real_memory_id,
            "memory": action_text,
            "event": event_type
        })
        action_counts["DELETE"] += 1
```

---

### 2. **LLM 删除决策的依据**

LLM 根据以下提示词决定是否删除记忆（`prompts/intelligent_memory_prompts.py:63-92`）：

**删除规则**：
```
3. **DELETE**: Contradictory info -> delete (use sparingly)
```

**示例**：
```
Delete: Only clear contradictions (e.g., "Loves pizza" vs "Dislikes pizza").
Prefer UPDATE for time conflicts.
```

**重要约束**：
- 只在**明确的矛盾**情况下删除
- 时间冲突优先使用 UPDATE 而不是 DELETE
- 要谨慎使用（use sparingly）

---

### 3. **可能导致记忆删除的场景**

虽然提示词要求"谨慎删除"，但以下情况可能导致 LLM 误判：

#### ⚠️ 场景 1：时间信息冲突

**旧记忆**：`"昨天天气很好，去了公园"`
**新对话**：`"今天下雨了，没出门"`

**风险**：LLM 可能认为这是矛盾信息，删除旧记忆。

#### ⚠️ 场景 2：状态变化

**旧记忆**：`"正在找工作"`
**新对话**：`"已经找到工作了"`

**风险**：LLM 可能认为状态矛盾，删除旧记忆（应该用 UPDATE）。

#### ⚠️ 场景 3：偏好改变

**旧记忆**：`"喜欢吃披萨"`
**新对话**：`"最近在减肥，不吃披萨了"`

**风险**：LLM 可能认为这是矛盾，删除旧记忆（应该用 UPDATE）。

---

### 4. **您的配置分析**

在 `core/providers/memory/powermem/powermem.py:374-382`：

```python
result = self.memory_client.add(
    messages=messages,
    user_id=self.role_id,
    native_language="zh",
    profile_type="content",
    include_roles=["user"],
    infer=True  # ← 智能模式已启用
)
```

**关键参数**：
- ✅ `infer=True`：启用了智能记忆模式
- ✅ `profile_type="content"`：用户画像模式
- ✅ `include_roles=["user"]`：只分析用户消息

**调试代码已注释**：
```python
# TODO: 调试结束后恢复为 True（启用智能模式）
```

这表明之前在调试时已经遇到过记忆删除问题，临时禁用了智能模式。

---

## 🎯 结论与建议

### ❓ 是否还会删除记忆？

**答案：是的，仍然可能删除记忆。**

**原因**：
1. `infer=True` 启用了智能记忆模式
2. LLM 会根据提示词决定删除"矛盾"的记忆
3. 虽然提示词要求"谨慎使用"，但 LLM 可能误判

### ✅ 解决方案

#### **方案 1：禁用智能模式**（推荐）

```python
# core/providers/memory/powermem/powermem.py:381
infer=False  # 禁用智能模式，防止 LLM 删除记忆
```

**优点**：
- ✅ 完全避免 LLM 误删记忆
- ✅ 记忆只会添加，不会删除
- ✅ 性能更好（不需要 LLM 调用）

**缺点**：
- ❌ 可能会有重复记忆
- ❌ 不会自动更新旧记忆

---

#### **方案 2：优化删除规则**

修改 PowerMem SDK 的提示词，添加更严格的删除条件：

```python
# 提示词修改
DELETE: Only delete when EXPLICIT contradiction (e.g., "A is true" vs "A is false").
- NEVER delete based on temporal changes (use UPDATE instead)
- NEVER delete based on status changes (use UPDATE instead)
- NEVER delete based on preference changes (use UPDATE instead)
- DELETE only for clear logical impossibilities
```

但这需要修改 SDK 源代码，不推荐。

---

#### **方案 3：监控和回滚**

在服务器代码中添加删除监控：

```python
# 在 _intelligent_add() 后检查
if action_counts["DELETE"] > 0:
    logger.warning(f"⚠️ LLM deleted {action_counts['DELETE']} memories!")
    # 可以选择记录或回滚
```

---

### 📌 最终建议

**对于生产环境，强烈建议使用 `infer=False`**：

```python
# core/providers/memory/powermem/powermem.py
result = self.memory_client.add(
    messages=messages,
    user_id=self.role_id,
    native_language="zh",
    profile_type="content",
    include_roles=["user"],
    infer=False  # ← 禁用智能模式，保护记忆不被删除
)
```

**原因**：
1. 记忆的持久性比智能合并更重要
2. 重复记忆比丢失记忆好
3. 用户体验更可预测

---

## 🔧 验证步骤

1. **修改配置**：设置 `infer=False`
2. **测试对话**：进行多轮对话
3. **检查记忆**：验证记忆是否完整保留
4. **监控日志**：查看是否还有 DELETE 操作

---

## 📚 相关文件

- **SDK 源码**：`~/codes/github/powermem-1.1.0/`
- **核心逻辑**：`src/powermem/core/memory.py`
- **提示词**：`src/powermem/prompts/intelligent_memory_prompts.py`
- **用户画像**：`src/powermem/user_memory/user_memory.py`
- **服务器代码**：`core/providers/memory/powermem/powermem.py`

---

**报告日期**：2026-05-04
**PowerMem 版本**：1.1.0
**分析者**：Claude Code
