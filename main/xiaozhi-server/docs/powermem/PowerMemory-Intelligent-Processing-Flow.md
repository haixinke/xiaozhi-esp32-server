# PowerMem 智能记忆处理触发时机分析

## 📋 概述

`intelligent_memory_prompts.py` 中的智能记忆处理在**每次调用 `add()` 方法保存对话时**触发，但需要满足特定条件。

---

## 🔄 完整触发流程

### 1️⃣ **入口：用户对话**

```python
# 在 xiaozhi-server 中的调用
# core/providers/memory/powermem/powermem.py:374
result = self.memory_client.add(
    messages=[
        {"role": "user", "content": "我叫小明，今年25岁"},
        {"role": "assistant", "content": "你好小明！"}
    ],
    user_id="user123",
    infer=True  # ← 关键参数
)
```

---

### 2️⃣ **判断是否启用智能处理**

```python
# core/memory.py:574 - add() 方法
def add(self, messages, ..., infer: bool = True):
    # 检查是否使用智能模式
    use_infer = infer and isinstance(messages, list) and len(messages) > 0

    if not use_infer:
        # ← 简单模式：直接存储，不触发智能处理
        return self._simple_add(...)

    # ← 智能模式：触发 LLM 处理
    return self._intelligent_add(...)
```

**触发条件**：
- ✅ `infer=True`（默认值）
- ✅ `messages` 是非空列表
- ❌ 如果 `infer=False`，跳过所有智能处理

---

### 3️⃣ **智能模式处理流程**

```python
# core/memory.py:787 - _intelligent_add() 方法
def _intelligent_add(self, messages, ...):
    # ========== 第 1 步：提取事实 ==========
    logger.info("Extracting facts from messages...")
    facts = self._extract_facts(messages)  # ← 触发点 1

    if not facts:
        logger.debug("No facts extracted, skip intelligent add")
        return {"results": []}

    # ========== 第 2 步：搜索相似记忆 ==========
    existing_memories = []
    for fact in facts:
        # 为每个事实搜索最相似的前 5 条记忆
        similar = self.storage.search_memories(
            query_embedding=fact_embedding,
            user_id=user_id,
            limit=5
        )
        existing_memories.extend(similar)

    # 去重并限制到最多 10 条
    existing_memories = list(unique_memories.values())[:10]

    # ========== 第 3 步：LLM 决策 ==========
    actions = self._decide_memory_actions(
        facts,
        existing_memories,
        user_id,
        agent_id
    )  # ← 触发点 2

    # ========== 第 4 步：执行操作 ==========
    for action in actions:
        event_type = action.get("event", "NONE")

        if event_type == "ADD":
            # 添加新记忆
            self._create_memory(...)

        elif event_type == "UPDATE":
            # 更新现有记忆
            self._update_memory(...)

        elif event_type == "DELETE":
            # ← 删除记忆（潜在问题）
            self.delete(memory_id, ...)
```

---

## 🎯 两个关键触发点

### **触发点 1：事实提取**（`_extract_facts()`）

```python
# core/memory.py:455
def _extract_facts(self, messages) -> List[str]:
    conversation = parse_messages_for_facts(messages)

    # 使用 FACT_EXTRACTION_PROMPT
    system_prompt = FACT_RETRIEVAL_PROMPT  # ← intelligent_memory_prompts.py
    user_prompt = f"Input:\n{conversation}"

    # 调用 LLM 提取事实
    response = self.llm.generate_response(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ],
        response_format={"type": "json_object"}
    )

    # 解析返回的事实
    facts_data = json.loads(response)
    return facts_data.get("facts", [])
```

**输入示例**：
```python
messages = [
    {"role": "user", "content": "我叫小明，今年25岁，是一名软件工程师"}
]
```

**LLM 输出**：
```json
{
  "facts": [
    "Name is John",
    "Is a software engineer",
    "Is 25 years old"
  ]
}
```

---

### **触发点 2：记忆决策**（`_decide_memory_actions()`）

```python
# core/memory.py:510
def _decide_memory_actions(
    self,
    new_facts: List[str],
    existing_memories: List[Dict],
    user_id: Optional[str] = None,
    agent_id: Optional[str] = None,
) -> List[Dict[str, Any]]:
    # 格式化现有记忆
    old_memory = []
    for mem in existing_memories:
        old_memory.append({
            "id": mem.get("id", "unknown"),
            "text": mem.get("memory", "") or mem.get("content", "")
        })

    # 生成提示词
    update_prompt = get_memory_update_prompt(
        old_memory,      # 现有记忆
        new_facts,       # 新提取的事实
        custom_prompt    # 可选的自定义提示词
    )  # ← intelligent_memory_prompts.py

    # 调用 LLM 决策
    response = self.llm.generate_response(
        messages=[{"role": "user", "content": update_prompt}],
        response_format={"type": "json_object"}
    )

    # 解析操作
    actions_data = json.loads(response)
    return actions_data.get("memory", [])
```

**输入示例**：
```python
existing_memories = [
    {"id": "123", "text": "User is engineer"}
]

new_facts = [
    "Name is John",
    "Is a software engineer"
]
```

**LLM 输出**：
```json
{
  "memory": [
    {
      "id": "123",
      "text": "User is engineer",
      "event": "NONE"  # 保持不变
    },
    {
      "id": "1",  # 新 ID
      "text": "Name is John",
      "event": "ADD"  # 添加新记忆
    },
    {
      "id": "2",  # 新 ID
      "text": "Is a software engineer",
      "event": "NONE"  # 重复，跳过
    }
  ]
}
```

---

## 🕒 触发时机总结

### ✅ **会触发智能处理的情况**

| 触发场景 | 条件 | 处理内容 |
|----------|------|----------|
| **每次对话** | `add(..., infer=True)` | 1. 提取事实<br>2. 搜索相似记忆<br>3. LLM 决策<br>4. 执行操作 |
| **多轮对话** | 每次调用 `add()` | 每次都会重新处理所有相关记忆 |
| **空事实** | 提取不到任何事实 | 跳过智能处理，返回空结果 |

### ❌ **不会触发智能处理的情况**

| 场景 | 原因 |
|------|------|
| `infer=False` | 直接调用 `_simple_add()`，跳过所有 LLM 处理 |
| 空消息列表 | `messages = []` 或 `None` |
| 单条非列表消息 | 虽然会转换，但不触发智能处理 |

---

## 🔍 在您的代码中的实际触发

```python
# core/providers/memory/powermem/powermem.py:300-456
async def save_memory(self, msgs, session_id=None):
    # ...

    # 每次保存对话时都会触发
    result = self.memory_client.add(
        messages=messages,  # ← 格式化后的对话
        user_id=self.role_id,
        native_language="zh",
        profile_type="content",
        include_roles=["user"],
        infer=True  # ← 启用智能处理
    )

    # ...
```

**触发频率**：
- 每次 ESP32 设备发送语音对话
- 每次调用 `save_memory()` 方法
- **每次都会调用 2 次 LLM**：
  1. 提取事实（FACT_EXTRACTION_PROMPT）
  2. 决策记忆操作（DEFAULT_UPDATE_MEMORY_PROMPT）

---

## 💡 性能影响

### LLM 调用次数

假设每天有 N 次对话：

```
总 LLM 调用次数 = N × 2

示例：
- 10 次对话/天 → 20 次 LLM 调用
- 100 次对话/天 → 200 次 LLM 调用
```

### 每次调用的开销

```
事实提取：~500 tokens（输入）
记忆决策：~800 tokens（输入）+ ~200 tokens（输出）
```

---

## 🎛️ 如何控制触发

### **方法 1：全局禁用**（推荐）

```python
# 修改配置文件
# data/.config.yaml 或代码中
infer: false  # 全局禁用智能处理
```

### **方法 2：代码中控制**

```python
# 在调用时动态设置
result = self.memory_client.add(
    messages=messages,
    user_id=user_id,
    infer=False  # ← 这次不触发智能处理
)
```

### **方法 3：条件触发**

```python
# 只在特定条件下启用
should_use_intelligent = len(messages) > 5  # 例如：长对话才启用

result = self.memory_client.add(
    messages=messages,
    user_id=user_id,
    infer=should_use_intelligent
)
```

---

## 📝 关键要点

1. **每次对话都会触发**（如果 `infer=True`）
2. **需要 2 次 LLM 调用**（提取事实 + 决策）
3. **会搜索相似记忆**（每个事实搜索前 5 条）
4. **LLM 决定删除**（虽然提示词要求谨慎）
5. **可以通过 `infer=False` 完全禁用**

---

**分析日期**：2026-05-04
**PowerMem 版本**：1.1.0
