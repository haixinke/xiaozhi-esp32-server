# DELETE 操作监控使用指南

**启用时间**：2026-05-06
**位置**：`core/providers/memory/powermem/powermem.py`
**状态**：✅ 已启用

---

## 功能说明

此监控功能通过 monkey-patch PowerMem SDK，记录所有记忆删除操作的详细信息，帮助追踪记忆丢失的根本原因。

---

## 监控内容

### 1. 单次删除监控

监控 `Memory.delete()` 方法，记录：

**删除前日志**：
```
╔══════════════════════════════════════════════════════════════════════╗
║ 🔴 [MEMORY DELETE] Memory deletion DETECTED                            ║
╠══════════════════════════════════════════════════════════════════════╣
║ Timestamp: 2026-05-06 09:45:23
║ Memory ID: 706797077039939584
║ User ID: 62:68:0B:F7:03:8C
║ Agent ID: None
║ Caller: /path/to/code.py:123 in some_function
╚══════════════════════════════════════════════════════════════════════╝
```

**删除的内存内容**：
```
[MEMORY DELETE] Content being deleted: '用户昵称是敏哥'
```

**删除后确认**：
```
[MEMORY DELETE] Deletion completed: result=True
```

### 2. 批量删除监控（在 add() 期间）

监控 `Memory.add()` 方法中的 DELETE 事件，记录：

```
╔══════════════════════════════════════════════════════════════════════╗
║ ⚠️  [MEMORY DELETE] Batch deletion during add() detected                  ║
╠══════════════════════════════════════════════════════════════════════╣
║ Timestamp: 2026-05-06 09:45:23
║ User ID: 62:68:0B:F7:03:8C
║ Agent ID: None
║ Deleted Count: 3
╚══════════════════════════════════════════════════════════════════════╝
```

**删除详情**：
```
[MEMORY DELETE] [1/3] ID=706797077039939584, Content='用户昵称是敏哥'
[MEMORY DELETE] [2/3] ID=706797078948347904, Content='用户称呼assistant为蛋蛋'
[MEMORY DELETE] [3/3] ID=706797079648796672, Content='用户去北京的行程未能成行'
```

---

## 日志级别

所有 DELETE 监控日志使用 **ERROR** 级别，确保：
- 始终可见（不受日志级别设置影响）
- 容易通过 `grep` 过滤

---

## 查看监控日志

### 实时监控

```bash
# 实时查看所有 DELETE 操作
tail -f logs/app.log | grep "MEMORY DELETE"

# 只看批量删除
tail -f logs/app.log | grep "Batch deletion"

# 查看删除时间戳
tail -f logs/app.log | grep "Timestamp:"
```

### 历史查询

```bash
# 查看所有删除操作
grep "MEMORY DELETE" logs/app.log

# 查看特定日期的删除
grep "2026-05-06.*MEMORY DELETE" logs/app.log

# 统计删除次数
grep -c "MEMORY DELETE" logs/app.log

# 查看删除的内容
grep "Content being deleted" logs/app.log
```

---

## 调用堆栈追踪

监控功能会记录调用堆栈，帮助定位删除操作的来源：

```
Caller: /path/to/powermem/core/memory.py:960 in add
```

**可能的调用来源**：

1. **LLM 智能决策**（最常见）
   - `memory.py:960` - LLM 决定删除旧记忆
   - 原因：LLM 认为某些记忆过时或不相关

2. **用户直接调用**
   - `powermem.py:XXX` - 项目代码直接调用 `delete()`
   - 原因：用户触发清理操作

3. **自动清理机制**
   - 如果 SDK 有自动清理功能
   - 会显示具体的清理函数

---

## 预期输出示例

### 场景 1：LLM 删除旧记忆

```
2026-05-06 09:45:23 [ERROR] ╔══════════════════════════════════════════════════════════════════════╗
2026-05-06 09:45:23 [ERROR] ║ 🔴 [MEMORY DELETE] Memory deletion DETECTED                            ║
2026-05-06 09:45:23 [ERROR] ╠══════════════════════════════════════════════════════════════════════╣
2026-05-06 09:45:23 [ERROR] ║ Timestamp: 2026-05-06 09:45:23
2026-05-06 09:45:23 [ERROR] ║ Memory ID: 706797077039939584
2026-05-06 09:45:23 [ERROR] ║ User ID: 62:68:0B:F7:03:8C
2026-05-06 09:45:23 [ERROR] ║ Agent ID: None
2026-05-06 09:45:23 [ERROR] ║ Caller: /path/to/powermem/core/memory.py:960 in add
2026-05-06 09:45:23 [ERROR] ╚══════════════════════════════════════════════════════════════════════╝
2026-05-06 09:45:23 [ERROR] [MEMORY DELETE] Content being deleted: '用户昨天过生日，只有一个人过'
2026-05-06 09:45:23 [ERROR] [MEMORY DELETE] Deletion completed: result=True
```

**分析**：
- LLM 在 `add()` 方法中决定删除这条记忆
- 可能原因：认为这条记忆不再相关

### 场景 2：批量删除

```
2026-05-06 09:50:15 [ERROR] ╔══════════════════════════════════════════════════════════════════════╗
2026-05-06 09:50:15 [ERROR] ║ ⚠️  [MEMORY DELETE] Batch deletion during add() detected                  ║
2026-05-06 09:50:15 [ERROR] ╠══════════════════════════════════════════════════════════════════════╣
2026-05-06 09:50:15 [ERROR] ║ Timestamp: 2026-05-06 09:50:15
2026-05-06 09:50:15 [ERROR] ║ User ID: 62:68:0B:F7:03:8C
2026-05-06 09:50:15 [ERROR] ║ Agent ID: None
2026-05-06 09:50:15 [ERROR] ║ Deleted Count: 5
2026-05-06 09:50:15 [ERROR] ╚══════════════════════════════════════════════════════════════════════╝
2026-05-06 09:50:15 [ERROR] [MEMORY DELETE] [1/5] ID=706797077039939584, Content='用户昵称是敏哥'
2026-05-06 09:50:15 [ERROR] [MEMORY DELETE] [2/5] ID=706797078948347904, Content='用户称呼assistant为蛋蛋'
2026-05-06 09:50:15 [ERROR] [MEMORY DELETE] [3/5] ID=706797079648796672, Content='用户去北京的行程未能成行'
2026-05-06 09:50:15 [ERROR] [MEMORY DELETE] [4/5] ID=706797079678156800, Content='用户计划前往常州'
2026-05-06 09:50:15 [ERROR] [MEMORY DELETE] [5/5] ID=706797079720099840, Content='用户前往常州是去公干'
```

**分析**：
- LLM 在处理新对话时，批量删除了5条旧记忆
- 需要检查 LLM 的删除逻辑是否合理

---

## 分析建议

### 1. 收集数据

运行服务至少 24 小时，收集足够的删除日志：

```bash
# 启动服务
python app.py

# 在另一个终端监控删除操作
tail -f logs/app.log | grep "MEMORY DELETE" > delete_monitor.log
```

### 2. 分析模式

```bash
# 统计删除频率
grep -c "MEMORY DELETE" delete_monitor.log

# 查看删除最多的时间段
grep "Timestamp:" delete_monitor.log | cut -d' ' -f2 | cut -d':' -f1-2 | sort | uniq -c

# 查看哪些记忆最常被删除
grep "Content being deleted" delete_monitor.log | sort | uniq -c | sort -rn
```

### 3. 定位问题

**如果发现频繁删除**：
- 检查 `Caller` 字段，确认删除来源
- 如果是 LLM 删除，考虑调整 LLM 提示词
- 如果是代码调用，检查业务逻辑

**如果发现特定记忆被删除**：
- 分析记忆内容的特点
- 检查是否与某些关键词相关
- 考虑是否需要保护某些类型的记忆

---

## 禁用监控

如果需要禁用监控（不推荐）：

```python
# 在 core/providers/memory/powermem/powermem.py 中注释掉
# _monitor_delete_operations()
```

---

## 相关文件

- `core/providers/memory/powermem/powermem.py` - 监控实现
- `logs/app.log` - 监控日志输出
- `docs/issues/002-memory-loss-root-cause-20260506.md` - 问题分析报告
- `docs/DELETE-MONITOR-GUIDE.md` - 本文档

---

## 下一步

1. ✅ 监控已启用
2. ⏳ 等待下次记忆丢失事件
3. ⏳ 分析删除日志，找到根本原因
4. ⏳ 实施修复方案

---

## 备注

- 此监控是**非侵入式**的，不会影响 PowerMem 的正常功能
- 所有日志都是**只读**的，不修改任何删除行为
- 使用 ERROR 级别确保日志始终可见
- 如需调试信息，请联系开发者
