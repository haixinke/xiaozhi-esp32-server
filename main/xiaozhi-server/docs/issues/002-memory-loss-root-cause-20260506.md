# 问题 #002 根因分析：PowerMem 记忆丢失问题

**分析时间**：2026-05-06 09:40
**状态**：🔴 **确认数据丢失，发现关键线索**

---

## 核心发现

### ✅ 确认：数据确实丢失了！

**证据 1**：今天早上（06:57）的 LLM 输入包含12条记忆
```markdown
【相关记忆】
- [2026-05-05 10:06:52] 助手的生日在昨天，没吃上生日蜡烛...
- [2026-05-05 10:06:51] 用户希望并现在被称呼为浩哥
- [2026-05-05 10:05:53] 用户去北京的行程未能成行
...（共12条）
```

**证据 2**：当前数据库中只有8条记录
```
created_at (UTC)           created_at (北京时间)
2026-05-05T22:59:04        2026-05-06 06:59:04
2026-05-05T22:59:04        2026-05-06 06:59:04
...（共8条，都是今天早上创建的）
```

**结论**：
- 昨天上午的记忆（2026-05-05 10:05-10:06）今天早上06:57还能查到
- 但现在数据库中已经没有了
- 唯一剩下的8条记录是今天早上06:59新创建的

---

## 异常时间线

### 昨天下午（2026-05-05）

| 时间 | 保存前计数 | 操作 | 保存后计数 | 实际查询返回 |
|------|-----------|------|-----------|------------|
| 17:19:24 | 0 | ADD 7条 | 0 | - |
| 17:33:10 | - | - | - | 7 results ✅ |
| 17:34:08 | 0 | ADD 1条 | 0 | - |
| 17:57:33 | - | - | - | 8 results ✅ |
| 18:01:46 | - | - | - | 10 results ✅ |
| 18:02:44 | 0 | UPDATE 1, ADD 1 | 0 | - |
| 18:04:32 | - | - | - | 11 results ✅ |
| 18:05:12 | 0 | UPDATE 1 | 0 | - |

**异常**：
- BEFORE/AFTER ADD 始终显示 0（空查询问题）
- 但实际查询能返回 7-11 条结果
- 说明昨天下午确实有记忆在增加

### 今天早上（2026-05-06）

| 时间 | 计数 | 实际查询返回 | 说明 |
|------|------|------------|------|
| 06:56:50 | 0 | 0 results | ❌ 第一次查询 |
| 06:57:14 | 0 | **12 results** ✅ | ✅ 突然出现12条旧记忆！ |
| 06:57:41 | 0 | 0 results | ❌ 又消失了 |
| 06:59:04 | - | - | 保存8条新记忆 |
| 06:59:14 | 0 | - | AFTER ADD 仍然显示0 |

**异常**：
- 06:56:50 → 06:57:14：从0条变成12条（仅24秒）
- 06:57:14 → 06:57:41：从12条变回0条（仅27秒）
- 这种波动非常不正常！

---

## 关键线索

### 🔍 线索 1：空查询计数不可靠

**代码位置**：`core/providers/memory/powermem/powermem.py`

```python
# 使用空查询统计记忆数量
count_check_before = await asyncio.to_thread(
    self.memory_client.search,
    query="",  # 空查询
    user_id=self.role_id,
    limit=10000
)
count_before = len(count_check_before.get("results", []))
```

**问题**：
- 空查询 `query=""` 在向量搜索中可能返回不可预测的结果
- 导致 BEFORE/AFTER ADD 的计数始终为 0
- 但这不代表数据真的丢失

### 🔍 线索 2：查询结果波动异常

**06:57:14 返回12条记忆**，包含：
- 用户画像：40岁软件工程师，叫"浩哥"
- 昨天生日独自过
- 去北京的行程取消
- 去常州公干
- 吃榴莲
- ...（共12条）

**06:57:41 返回0条**（仅27秒后）

**可能原因**：
1. **缓存问题**：第一次命中缓存，第二次未命中？
2. **数据库连接池问题**：不同连接看到不同的数据？
3. **向量索引重建**：查询期间索引发生变化？
4. **并发删除**：有后台进程在删除记忆？

### 🔍 线索 3：LLM 的 DELETE 决策

**PowerMem SDK 的 `add()` 方法**：
```python
# LLM 可以决定记忆的操作类型
event_type = action.get("event")  # ADD / UPDATE / DELETE / NONE

if event_type == "DELETE":
    real_memory_id = temp_uuid_mapping.get(str(action_id))
    if real_memory_id:
        self.delete(real_memory_id, user_id, agent_id)
```

**可能情况**：
- LLM 在处理新对话时，可能认为某些旧记忆不再相关
- 自动生成 DELETE 事件删除旧记忆
- 但这无法解释为什么06:57:14还能查到12条

---

## 待验证假设

### 假设 1：PowerMem SDK 有自动清理机制

**验证方法**：
```bash
# 检查是否有定时任务或后台清理
grep -rn "cleanup\|purge\|expire\|retention" ~/codes/github/powermem-1.1.0/src/powermem/ --include="*.py"
```

**已有发现**：
- `retention_days` 配置（默认90天）
- `cleanup_forgotten_memories()` 方法（但只是接口，未实现）
- Ebbinghaus 遗忘曲线算法（可能影响记忆保留）

### 假设 2：UserMemory 模式的特殊行为

**推理**：
- UserMemory 使用用户画像模式
- 可能在更新用户画像时，清理旧记忆
- 或者有记忆数量限制（如最多保留N条）

**验证方法**：
- 查看 `~/codes/github/powermem-1.1.0/src/powermem/user_memory/user_memory.py`
- 搜索记忆数量限制或自动清理逻辑

### 假设 3：数据库索引或表结构问题

**推理**：
- 向量索引可能在查询期间重建
- 不同查询可能使用不同的索引
- 导致查询结果不一致

**验证方法**：
```sql
-- 检查向量索引状态
SHOW INDEX FROM memories WHERE Key_name = 'vidx';

-- 检查表是否有分区
SELECT PARTITION_NAME, PARTITION_METHOD
FROM information_schema.PARTITIONS
WHERE TABLE_NAME = 'memories';
```

---

## 紧急排查步骤

### 优先级 1：监控内存删除操作

在 PowerMem SDK 中添加日志：

```python
# 在 memory.py 的 delete() 方法中添加
def delete(self, memory_id, user_id=None, agent_id=None):
    logger.error(f"[MEMORY_DELETE] Deleting memory: id={memory_id}, user_id={user_id}, agent_id={agent_id}")
    result = self.storage.delete_memory(memory_id, user_id, agent_id)
    logger.error(f"[MEMORY_DELETE] Delete result: {result}")
    return result
```

### 优先级 2：检查数据库事务隔离级别

```bash
# 检查 OceanBase 的事务隔离级别
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@sys -p'123456' -e "
SELECT @@tx_isolation, @@session.tx_isolation, @@global.tx_isolation;
"
```

### 优先级 3：启用数据库查询日志

```bash
# 在 OceanBase 中启用慢查询日志
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@sys -p'123456' -e "
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0;
"
```

---

## 临时解决方案

### 方案 1：定期备份记忆数据

```python
# 添加定期备份任务
import asyncio

async def backup_memories():
    while True:
        await asyncio.sleep(3600)  # 每小时备份
        memories = await query_all_memories()
        save_to_backup_file(memories)
```

### 方案 2：禁用 DELETE 操作

在 PowerMem SDK 中临时禁用 DELETE：

```python
# 修改 memory.py 的 add() 方法
elif event_type == "DELETE":
    logger.warning(f"[MEMORY_DELETE] DELETE operation blocked for safety: id={action_id}")
    # 不执行删除操作
    action_counts["DELETE"] += 1
```

---

## 下一步行动

1. **立即行动**：添加 DELETE 操作日志
   - 在 `memory.py` 的 `delete()` 方法中添加日志
   - 监控是否有自动删除操作

2. **短期验证**：检查是否有清理机制
   - 搜索 `cleanup`、`purge`、`expire` 等关键词
   - 查看配置中的 `retention_days` 设置

3. **长期解决**：修复查询计数问题
   - 使用 SQL COUNT 查询代替空查询
   - 或者使用专门的 count() 方法

---

## 相关文件

- `core/providers/memory/powermem/powermem.py` - 项目集成代码
- `~/codes/github/powermem-1.1.0/src/powermem/core/memory.py` - PowerMem SDK 核心
- `~/codes/github/powermem-1.1.0/src/powermem/user_memory/user_memory.py` - UserMemory 实现
- `logs/app.log` - 应用日志（包含详细的时间线）

---

## 备注

- 这是一个**数据丢失**的严重问题，需要**紧急修复**
- 记忆在不同查询间出现和消失，非常不正常
- 可能与 PowerMem SDK 的内部逻辑有关
- 需要深入分析 SDK 的删除和清理机制
