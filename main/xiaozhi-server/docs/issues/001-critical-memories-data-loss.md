# 问题清单

本文档记录 xiaozhi-server 运行过程中发现的问题。

---

## 问题 #001：🔥 严重 - memories 表数据在启动新对话时丢失

**发现时间**：2026-05-05
**报告人**：用户
**严重程度**：🔴 严重 - 数据丢失
**状态**：🔴 未解决 - 需要紧急排查

### 问题描述

隔天启动聊天服务，通过 `test_page.html` 拨号开启**新对话**后，`memories` 表中的数据**全部消失**，但 `user_profiles` 表数据正常保留。

### 触发条件

1. ✅ **服务重启后首次连接**
2. ✅ **隔天启动**（⚠️ **关键线索**）
3. ✅ 开启新对话（新 WebSocket 连接）
4. ✅ 通过 `test_page.html` 拨号连接
5. ❌ **未挂断对话**（还未触发 `save_memory()` 的 `add()` 操作）
6. ❌ 未执行任何删除操作

### ⚠️ 重要补充：时间相关性

**用户观察到的规律**：
- 🔴 **隔天启动** → memories 表数据**丢失**
- ✅ **当天测试** → 开启对话、挂断对话，记忆**正常保留**

**说明**：
- 不是每次连接都删除（排除初始化时必定删除的逻辑）
- 与**服务重启**或**时间间隔**高度相关
- 可能是某种**定期清理机制**或**重启触发的清理逻辑**

### 症状详情

| 表名 | 状态 | 说明 |
|------|------|------|
| `memories` | 🔴 **数据全部丢失** | 历史对话记忆消失 |
| `user_profiles` | ✅ **正常** | 用户画像数据保留 |

### 影响范围

- ⚠️ 历史对话记忆全部丢失，无法恢复
- ⚠️ AI 无法回忆之前的对话内容
- ⚠️ 用户体验严重受损

### 关键线索

1. **时机异常**：
   - 此时应该**还未触发** `memory_client.add()` 操作
   - 问题发生在**新对话建立时**，而非对话结束时
   - 对话还未挂断，记忆就消失了

2. **数据隔离**：
   - `user_profiles` 表数据正常，说明数据库连接正常
   - 只有 `memories` 表受影响

3. **操作路径**：
   ```
   启动服务 → 拨号连接 → hello 握手 → (此时 memories 数据已丢失)
   ```

### 🔬 调查进展

#### ❌ 假设 1 被排除：memories 表不是临时表

**验证时间**：2026-05-05 12:06

**验证结果**：
```sql
SHOW CREATE TABLE memories;
-- 结果：CREATE TABLE `memories` (...)
-- 没有 TEMPORARY 关键字

SELECT TABLE_TYPE, ENGINE FROM information_schema.TABLES;
-- 结果：TABLE_TYPE='BASE TABLE', ENGINE='InnoDB'
```

**结论**：
- ✅ `memories` 是**普通表**（BASE TABLE）
- ✅ 使用 **InnoDB** 引擎（持久化存储）
- ✅ 不是内存表，不是临时表
- ❌ **假设 1 不成立**

---

#### ✅ 当前数据状态

**检查时间**：2026-05-05 12:06

**数据量**：
- `memories` 表：**7 条记录**
- `user_profiles` 表：**1 条记录**

**表创建时间**：
- `memories`：2026-05-03 14:44:27
- `user_profiles`：2026-05-03 14:44:30

**说明**：
- 目前表中有数据（可能是今天测试产生的）
- 需要等到**隔天重启**才能复现问题

---

### 📝 已添加日志追踪（2026-05-05 14:30）

**追踪点**：

1. **PowerMem 初始化** (`powermem.py:290`)
   - 日志：`[CRITICAL] PowerMem __init__ COMPLETE: mode={mode}, enable_user_profile={bool}`

2. **连接初始化** (`connection.py:_initialize_memory`)
   - 日志：`[CRITICAL] _initialize_memory START/COMPLETE: device_id={id}`

3. **查询记忆** (`powermem.py:query_memory`)
   - 入口：`[CRITICAL] query_memory CALLED: user_id={id}, query={query}`
   - 搜索前：`[CRITICAL] query_memory: BEFORE search - found {count} memories`
   - 搜索后：`[CRITICAL] query_memory: AFTER search - found {count} memories`
   - 出口：`[CRITICAL] query_memory: returning result with {parts} parts`

4. **保存记忆** (`powermem.py:save_memory`)
   - 添加前：`[CRITICAL] [SAVE_MEMORY] BEFORE ADD: found {count} existing memories`
   - 添加调用：`[CRITICAL] [SAVE_MEMORY] Calling PowerMem add(), infer=True`
   - 添加后：`[CRITICAL] [SAVE_MEMORY] AFTER ADD: found {count} existing memories`
   - 丢失检测：`[CRITICAL] [SAVE_MEMORY] ⚠️ MEMORY LOSS DETECTED! Before: {x}, After: {y}, Lost: {z}`

5. **连接关闭** (`connection.py:_save_and_close`)
   - 日志：`[CRITICAL] _save_and_close CALLED: device_id={id}, dialogue_len={len}`
   - 线程：`[CRITICAL] save_memory_thread: START/COMPLETE saving memory`

**日志级别**：全部使用 `ERROR` 级别以确保可见性

**下一步**：等到**隔天**重启服务时，通过以下命令监控日志：
```bash
tail -f logs/app.log | grep -E "\[CRITICAL\]"
```

---

### 🔍 修正后的可能原因

#### 🟡 高可能性：PowerMem SDK 有清理逻辑

**推理**：
- SDK 可能在初始化时检查"会话有效性"
- 隔天启动时触发清理过期数据

**验证方法**：
```bash
cd ~/codes/github/powermem-1.1.0
grep -rn "DELETE\|TRUNCATE\|DROP\|clean\|purge" powermem/ --include="*.py"
```

---

#### 🟡 中可能性：UserMemory 模式的特殊行为

**推理**：
- `user_profiles` 正常，`memories` 丢失
- 可能是 UserMemory 模式下，memories 表被视为"临时缓存"

**验证方法**：
- 检查 PowerMem SDK 中 UserMemory 的实现
- 搜索 `UserMemory` 类中对 memories 表的操作

---

#### 🟢 低可能性：数据库连接池或会话问题

**推理**：
- 隔天启动可能使用了不同的数据库连接
- 某种会话级操作导致数据丢失

**验证方法**：
- 启用数据库查询日志
- 监控所有对 memories 表的操作

### 调试步骤

#### 🔍 优先级 1：验证 memories 表是否为临时表（高优先级）

**⚠️ 这是最可能的原因，优先验证！**

```bash
# 1. 检查 memories 表的类型和创建语句
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -e "
SHOW CREATE TABLE powermem.memories;
"

# 2. 检查表属性
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -e "
SELECT TABLE_NAME, TABLE_TYPE, TEMPORARY, ENGINE, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'powermem' AND TABLE_NAME = 'memories';
"

# 3. 检查 user_profiles 表的创建语句（对比）
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -e "
SHOW CREATE TABLE powermem.user_profiles;
"
```

**如果是临时表（TEMPORARY）的迹象**：
- `CREATE TEMPORARY TABLE` 或 `TEMPORARY=Y`
- `ENGINE=MEMORY` 或 `TEMPORARY` 关键字
- `TABLE_ROWS=0` 在某些查询中

---

#### 🔍 优先级 2：检查 PowerMem SDK 的表创建逻辑

```bash
# 在 PowerMem SDK 源码中搜索表创建逻辑
cd ~/codes/github/powermem-1.1.0

# 搜索 CREATE TABLE 和 TEMPORARY 关键字
grep -rn "CREATE TABLE\|TEMPORARY" --include="*.py" powermem/storage/oceanbase/

# 搜索 memories 表的创建代码
grep -rn "memories" --include="*.py" powermem/storage/oceanbase/ | grep -i "create\|table"

# 查看 OceanBase 向量存储的实现
ls -la powermem/storage/oceanbase/
cat powermem/storage/oceanbase/oceanbase_vector.py
```

---

#### 🔍 优先级 3：添加日志追踪

在 `core/providers/memory/powermem/powermem.py` 中添加：

```python
# 在 __init__ 方法最后添加
logger.bind(tag=TAG).info(f"[DEBUG] PowerMem initialized: user_profile={self.enable_user_profile}, role_id={self.role_id}")

# 在 query_memory 方法开头添加
logger.bind(tag=TAG).info(f"[DEBUG] query_memory START: user_id={self.role_id}, query={query[:50]}")

# 查询前后检查数据量
existing_before = await asyncio.to_thread(
    self.memory_client.search,
    query="test",
    user_id=self.role_id,
    limit=10000
)
count_before = len(existing_before.get('results', []))
logger.bind(tag=TAG).error(f"[DEBUG] ⚠️ Before query: {count_before} memories found")

# ... (查询逻辑)

existing_after = await asyncio.to_thread(
    self.memory_client.search,
    query="test",
    user_id=self.role_id,
    limit=10000
)
count_after = len(existing_after.get('results', []))
logger.bind(tag=TAG).error(f"[DEBUG] ⚠️ After query: {count_after} memories found")
```

---

#### 🔍 优先级 4：完整的复现测试流程

```bash
# 1. 准备环境：确保 memories 表有数据
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 powermem -e "
SELECT COUNT(*) as count FROM memories;
SELECT COUNT(*) as count FROM user_profiles;
"

# 2. 记录当前数据量（用于对比）
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 powermem -e "
SELECT 'memories' as table_name, COUNT(*) as row_count FROM memories
UNION ALL
SELECT 'user_profiles', COUNT(*) FROM user_profiles;
"

# 3. 启动服务
cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/xiaozhi-server
python app.py

# 4. 在另一个终端监控日志
tail -f logs/app.log | grep -E "PowerMem|memories|query_memory|⚠️"

# 5. 通过 test_page.html 拨号连接（**不要挂断**）

# 6. 连接成功后立即检查数据库
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 powermem -e "
SELECT COUNT(*) as memories_count FROM memories;
SELECT COUNT(*) as profiles_count FROM user_profiles;
"

# 7. 如果 memories_count = 0，确认问题复现
```

### 相关文件

- `core/providers/memory/powermem/powermem.py` - PowerMem 集成代码
- `core/connection.py` - 连接处理和记忆初始化
- `test/test_page.html` - 测试前端页面
- `test/js/core/network/websocket.js` - WebSocket 连接逻辑

### 备注

- 这是一个**数据丢失**的严重问题，需要**紧急排查**
- 目前只知道症状，**不知道根本原因**
- 需要通过日志追踪和数据库审计来定位问题

---

## 问题模板

（后续发现新问题时，按此格式添加）

```markdown
## 问题 #XXX：[问题标题]

**发现时间**：YYYY-MM-DD
**严重程度**：🔴 严重 / 🟡 中等 / 🟢 轻微
**状态**：未解决 / 调查中 / 已解决

### 问题描述
[详细描述问题]

### 触发条件
1. 条件1
2. 条件2

### 症状详情
[症状描述]

### 影响范围
[影响描述]

### 解决方案
[方案描述]

### 相关文件
- 文件1
- 文件2
```
