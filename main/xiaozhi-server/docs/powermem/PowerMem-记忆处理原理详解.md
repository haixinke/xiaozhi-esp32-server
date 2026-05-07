# PowerMem 记忆处理原理详解

## 项目配置概览

### 当前配置

```python
{
    "enable_user_profile": True,  # 启用用户画像功能
    "database_provider": "oceanbase",  # 使用OceanBase数据库
    "llm_provider": "openai",  # 使用OpenAI兼容的LLM
    "embedding_provider": "openai",  # 使用OpenAI兼容的Embedding
    "graph_store": {
        "enabled": True,  # 启用知识图谱存储
        "provider": "oceanbase"
    }
}
```

### 调用参数

```python
memory_client.add(
    messages=messages,  # 对话消息列表
    user_id=self.role_id,  # 用户ID
    native_language="zh",  # 强制输出中文
    profile_type="content",  # 非结构化存储（不使用topics）
    include_roles=["user"],  # 只从用户消息中提取画像
    infer=True  # 启用智能模式
)
```

---

## 记忆处理完整流程

### 第一阶段：消息存储 (UserMemory.add)

```
┌─────────────────────────────────────────────────────────┐
│  1. UserMemory.add() 入口                                │
│     └─> 传入: messages, user_id, native_language,      │
│              profile_type, include_roles, infer         │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│  2. 过滤消息 (_filter_messages_by_roles)                 │
│     - 只保留 include_roles=["user"] 指定的角色           │
│     - 过滤掉 system 和 assistant 消息                    │
└─────────────────────────────────────────────────────────┘
                          │
                          ├──> 存储分支
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│  3. 调用 Memory.add() 存储对话事件                        │
│     └─> 如果 infer=True：进入智能模式                     │
│     └─> 如果 infer=False：进入简单模式                    │
└─────────────────────────────────────────────────────────┘
```

---

### 第二阶段：智能记忆处理 (infer=True)

#### 2.1 事实提取 (Fact Extraction)

```
┌─────────────────────────────────────────────────────────┐
│  步骤1: 从对话中提取关键事实                              │
│                                                          │
│  使用提示词: FACT_RETRIEVAL_PROMPT                      │
│  (详见下文"提示词详解"部分)                               │
│                                                          │
│  提取的信息类型:                                         │
│  - 个人偏好                                               │
│  - 详细信息（姓名、关系、日期）                            │
│  - 计划、意图、需求                                        │
│  - 活动                                                   │
│  - 健康/医疗                                             │
│  - 工作信息                                               │
│  - 其他                                                  │
└─────────────────────────────────────────────────────────┘
```

**关键规则**：
- ⏰ **时间信息**: 必须提取时间（"昨天"、"去年五月"）
- 🔄 **保持原语言**: 不翻译，保持原文（中文→中文，英文→英文）
- 📋 **完整性**: 提取谁/什么/何时/何地信息
- 🎯 **意图提取**: 即使没有时间信息，也要提取意图和需求

**示例**：

```
输入: "昨天我见了John，下午3点。我们讨论了项目。"
输出:
{
  "facts": [
    "昨天下午3点见了John",
    "昨天和John讨论了项目"
  ]
}

输入: "我想预约心脏科医生"
输出:
{
  "facts": [
    "想预约心脏科医生"
  ]
}
```

#### 2.2 相似记忆检索

```
┌─────────────────────────────────────────────────────────┐
│  步骤2: 为每个事实搜索相似的历史记忆                        │
│                                                          │
│  处理流程:                                               │
│  1. 为每个事实生成向量嵌入 (embedding)                    │
│  2. 在OceanBase中进行向量检索                            │
│  3. 使用混合搜索（向量+全文）                              │
│  4. 限制返回最多5条相似记忆                               │
│  5. 去重（保留相似度更高的）                              │
│  6. 限制候选记忆最多10条（避免LLM提示词过长）              │
└─────────────────────────────────────────────────────────┘
```

#### 2.3 记忆决策 (Memory Update Decision)

```
┌─────────────────────────────────────────────────────────┐
│  步骤3: 使用LLM决定记忆操作                               │
│                                                          │
│  使用提示词: DEFAULT_UPDATE_MEMORY_PROMPT               │
│                                                          │
│  LLM决策4种操作:                                         │
│  1. ADD - 新信息，添加为新记忆                            │
│  2. UPDATE - 信息已存在但更详细/更新，更新现有记忆         │
│  3. DELETE - 矛盾信息，删除旧记忆（谨慎使用）             │
│  4. NONE - 重复或无关，不操作                             │
└─────────────────────────────────────────────────────────┘
```

**时间冲突处理规则**：

| 情况 | 操作 | 示例 |
|------|------|------|
| 新事实有时间，旧记忆没有 | UPDATE | "去了夏威夷" → "2023年5月去了夏威夷" |
| 都有时间，新的更具体 | UPDATE | "2022年去的" → "2023年5月去的" |
| 时间冲突 | UPDATE到最近的 | "2022年" vs "2023年" → "2023年" |
| 内容矛盾 | DELETE（谨慎） | "喜欢披萨" vs "讨厌披萨" |

**语言规则**：❌ **不翻译记忆文本**，保持原文语言

#### 2.4 执行记忆操作

```
┌─────────────────────────────────────────────────────────┐
│  步骤4: 执行LLM决策的操作                                │
│                                                          │
│  ADD操作:                                               │
│  - 创建新记忆记录                                         │
│  - 生成向量嵌入                                           │
│  - 存储到OceanBase                                       │
│  - 返回新的memory_id                                     │
│                                                          │
│  UPDATE操作:                                            │
│  - 更新现有记忆的content                                  │
│  - 更新向量嵌入                                           │
│  - 更新updated_at时间戳                                   │
│                                                          │
│  DELETE操作:                                            │
│  - 从数据库删除记忆记录                                    │
│  - 从知识图谱删除相关实体                                  │
│                                                          │
│  NONE操作:                                             │
│  - 跳过（重复检测）                                       │
└─────────────────────────────────────────────────────────┘
```

---

### 第三阶段：用户画像提取

```
┌─────────────────────────────────────────────────────────┐
│  步骤5: 提取用户画像信息                                  │
│                                                          │
│  调用: UserMemory._extract_profile()                    │
│                                                          │
│  流程:                                                   │
│  1. 解析对话为文本格式                                    │
│  2. 从user_profiles表获取现有画像                         │
│  3. 构建提示词（包含现有画像+新对话）                       │
│  4. 调用LLM提取新画像                                     │
│  5. 保存到user_profiles表                                │
└─────────────────────────────────────────────────────────┘
```

**画像存储位置**：

```sql
-- OceanBase数据库表
user_profiles (
    id INT PRIMARY KEY,
    user_id VARCHAR,
    profile_content TEXT,        -- 非结构化画像（当前使用）
    topics JSON,                 -- 结构化主题（未使用）
    created_at TIMESTAMP,
    updated_at TIMESTAMP
)
```

---

## 提示词详解

### 1. 事实提取提示词 (FACT_RETRIEVAL_PROMPT)

**中文翻译**：

```
你是一个个人信息整理专家。从对话中提取相关的事实、记忆、偏好、意图和需求，
并将它们组织成独立、易管理的事实。

信息类型：
- 个人偏好
- 详细信息（姓名、关系、日期）
- 计划、意图、需求
- 活动
- 健康/健康（包括医疗预约、症状、治疗）
- 工作
- 其他

关键规则：
1. ⏰ 时间信息：总是提取时间信息（日期、相对引用如"昨天"、"上周"）。
   在事实中包含时间（例如："2023年5月去了夏威夷"或"去年去了夏威夷"，
   而不是仅仅"去了夏威夷"）。保留相对时间引用以便后续计算。

2. 📝 完整性：提取自包含的事实，尽可能包含谁/什么/何时/何地。

3. 🔄 分离：分别提取不同的事实，特别是当它们有不同的时间段时。

4. 🎯 意图和需求：总是提取用户意图、需求和请求，即使没有时间信息。
   例如："想预约医生"、"需要给某人打电话"、"计划去某个地方"。

5. 🌐 语言：不要翻译。保留源文本的原始语言。
   如果输入是中文，输出中文事实；如果英文，输出英文；
   如果混合语言，保持每个事实的语言不变。

示例：
输入: 嗨。
输出: {"facts": []}

输入: 昨天下午3点我见了John。我们讨论了项目。
输出: {"facts": ["昨天下午3点见了John", "昨天和John讨论了项目"]}

输入: 去年五月我去了印度。参观了孟买和果阿。
输出: {"facts": ["五月去了印度", "五月参观了孟买", "五月参观了果阿"]}

输入: 去年我见了Sarah并成为了朋友。上个月我们一起去看了电影。
输出: {"facts": ["去年见了Sarah并成为了朋友", "上个月和Sarah一起看了电影"]}

输入: 我是John，一名软件工程师。
输出: {"facts": ["姓名是John", "是软件工程师"]}

输入: 我想预约心脏科医生。
输出: {"facts": ["想预约心脏科医生"]}

规则：
- 今天的日期: {当前日期}
- 返回JSON: {"facts": ["事实1", "事实2"]}
- 只从用户/助手消息中提取
- 即使没有时间信息也要提取意图、需求和请求
- 如果没有相关事实，返回空列表
- 输出必须保留输入语言（不翻译）

从下面的对话中提取事实：
```

---

### 2. 记忆更新提示词 (DEFAULT_UPDATE_MEMORY_PROMPT)

**中文翻译**：

```
你是一个记忆管理器。比较新事实与现有记忆，决定：ADD、UPDATE、DELETE或NONE。

操作说明：
1. **ADD（添加）**: 新信息不在记忆中 → 添加新ID
2. **UPDATE（更新）**: 信息存在但不同/更详细 → 更新（保持相同ID）。
   优先选择信息最全面的事实
3. **DELETE（删除）**: 矛盾信息 → 删除（谨慎使用）
4. **NONE（无操作）**: 已存在或无关 → 不改变

时间规则（关键）：
- 新事实有时间信息，记忆没有 → UPDATE记忆以包含时间
- 都有时间，新的更具体/最近 → UPDATE到新的时间
- 时间冲突（例如"2022年"vs"2023年"）→ UPDATE到更近的
- 保留相对时间引用（例如"去年"、"两个月前"）
- 合并时，组合时间信息：
  "见了Sarah" + "去年见了Sarah" → UPDATE为"去年见了Sarah"

示例：
添加:
记忆: [{"id":"0","text":"用户是工程师"}],
事实: ["姓名是John"]
→ [{"id":"0","text":"用户是工程师","event":"NONE"},
   {"id":"1","text":"姓名是John","event":"ADD"}]

更新（时间）:
记忆: [{"id":"0","text":"去了夏威夷"}],
事实: ["2023年5月去了夏威夷"]
→ [{"id":"0","text":"2023年5月去了夏威夷","event":"UPDATE",
    "old_memory":"去了夏威夷"}]

更新（增强）:
记忆: [{"id":"0","text":"喜欢板球"}],
事实: ["喜欢和朋友一起打板球"]
→ [{"id":"0","text":"喜欢和朋友一起打板球","event":"UPDATE",
    "old_memory":"喜欢板球"}]

删除:
仅清除明显矛盾（例如"喜欢披萨"vs"讨厌披萨"）。
对于时间冲突，优先使用UPDATE。

重要提示：只使用现有ID。更新时保持相同ID。
始终保留时间信息。

语言（关键）：不要翻译记忆文本。
尽可能保持与传入事实和原始记忆相同的语言。
```

---

### 3. 用户画像提取提示词 (USER_PROFILE_EXTRACTION_PROMPT)

**中文翻译**：

```
你是一个用户画像提取专家。你的任务是分析对话并提取用户画像信息。

[参考主题]：
以下主题仅供参考。请根据对话的实际内容选择性地提取信息，
不要强制填充所有字段：

- 基本信息
  - 用户姓名
  - 用户年龄（整数）
  - 性别
  - 出生日期
  - 国籍
  - 民族
  - 语言

- 联系信息
  - 邮箱
  - 电话
  - 城市
  - 省份

- 教育背景
  - 学校
  - 学位
  - 专业
  - 毕业年份

- 人口统计
  - 婚姻状况
  - 子女数量
  - 家庭收入

- 工作就业
  - 公司
  - 职位
  - 工作地点
  - 参与的项目
  - 工作技能

- 兴趣爱好
  - 书籍
  - 电影
  - 音乐
  - 食物
  - 体育

- 生活方式
  - 饮食偏好（例如素食、纯素）
  - 运动习惯
  - 健康状况
  - 睡眠模式
  - 吸烟
  - 饮酒

- 心理特征
  - 性格特征
  - 价值观
  - 信仰
  - 动机
  - 目标

- 生活事件
  - 结婚
  - 搬家
  - 退休

[说明]：
1. 如果提供了当前用户画像，请审查
2. 仔细分析新对话以识别任何新的或更新的用户相关信息
3. 仅提取对话中明确提到的事实信息
4. 通过以下方式更新画像：
   - 添加当前画像中没有的新信息
   - 如果对话提供了更新或不同的详细信息，更新现有信息
   - 保持仍然有效的未更改信息
5. 将所有信息组合成一个连贯的、更新的画像描述
6. 如果对话中没有找到相关的画像信息，返回当前画像原样
7. 用自然语言编写画像，而不是结构化数据
8. 关注用户的当前状态和特征
9. 如果完全无法从对话中提取用户画像信息，返回空字符串""
10. 最终提取的画像描述不得超过1000个字符。
    如果超过，请在不丢失基本事实信息的情况下简洁地压缩内容。

[语言要求]：
你必须用中文提取和编写画像内容，无论对话中使用什么语言。
```

---

## 数据存储结构

### OceanBase数据库表

#### 1. memories（记忆表）

```sql
CREATE TABLE memories (
    id INT PRIMARY KEY,           -- Snowflake ID
    content VARCHAR(65535),       -- 记忆内容
    embedding VECTOR(1536),       -- 向量嵌入
    user_id VARCHAR,              -- 用户ID
    agent_id VARCHAR,             -- 代理ID
    run_id VARCHAR,               -- 运行ID
    hash VARCHAR(32),             -- 内容哈希（去重）
    category VARCHAR,             -- 分类
    metadata JSON,                -- 元数据
    filters JSON,                 -- 过滤器
    created_at TIMESTAMP,         -- 创建时间
    updated_at TIMESTAMP          -- 更新时间
);
```

#### 2. user_profiles（用户画像表）

```sql
CREATE TABLE user_profiles (
    id INT PRIMARY KEY,
    user_id VARCHAR UNIQUE,       -- 每个用户一条记录
    profile_content TEXT,         -- 非结构化画像（自然语言）
    topics JSON,                  -- 结构化主题（未使用）
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

#### 3. graph_entities（知识图谱实体表）

```sql
CREATE TABLE graph_entities (
    id INT PRIMARY KEY,
    name VARCHAR,
    type VARCHAR,
    description TEXT,
    user_id VARCHAR,
    metadata JSON,
    created_at TIMESTAMP
);
```

#### 4. graph_relations（知识图谱关系表）

```sql
CREATE TABLE graph_relations (
    id INT PRIMARY KEY,
    from_entity_id INT,
    to_entity_id INT,
    relation_type VARCHAR,
    user_id VARCHAR,
    metadata JSON,
    created_at TIMESTAMP
);
```

---

## 记忆检索流程

### 查询记忆 (query_memory)

```
┌─────────────────────────────────────────────────────────┐
│  1. 检查用户画像模式是否启用                              │
│     └─> 如果启用，先获取用户画像                          │
│                                                          │
│  2. 查询重写（Query Rewrite）                            │
│     - 使用用户画像增强查询                                │
│     - 例如："他" → "用户John"                            │
│                                                          │
│  3. 向量检索                                             │
│     - 为查询生成向量嵌入                                  │
│     - 在memories表中搜索                                  │
│     - 使用混合搜索（向量+全文）                           │
│     - 限制返回30条                                        │
│                                                          │
│  4. 格式化结果                                           │
│     - 按时间倒序排列（最新的在前）                         │
│     - 格式: "[时间] 记忆内容"                             │
│                                                          │
│  5. 返回格式                                             │
│     【用户画像】                                          │
│     {画像内容}                                            │
│                                                          │
│     【相关记忆】                                          │
│     - [2026-05-05 17:14:53] 昨天见了John                 │
│     - [2026-05-04 10:30:00] 想预约心脏科医生              │
└─────────────────────────────────────────────────────────┘
```

---

## 性能优化策略

### 1. 缓存机制

```python
# 用户画像缓存
self.last_profile_content = ""  # 内存缓存

# 快速路径：如果缓存存在，直接返回
if self.last_profile_content:
    return self.last_profile_content
```

### 2. 向量嵌入复用

```python
# 在_intelligent_add中，复用事实的向量嵌入
fact_embeddings = {}
for fact in facts:
    fact_embedding = embedding_service.embed(fact, memory_action="add")
    fact_embeddings[fact] = fact_embedding

# 后续ADD/UPDATE操作时复用，避免重复生成
```

### 3. 限制提示词长度

```python
# 限制候选记忆数量（避免LLM提示词过长）
existing_memories = list(unique_memories.values())[:10]  # 最多10条
similar = self.storage.search_memories(..., limit=5)  # 每个事实最多5条
```

---

## 智能模式 vs 简单模式

| 特性 | 智能模式 (infer=True) | 简单模式 (infer=False) |
|------|---------------------|---------------------|
| **事实提取** | ✅ 使用LLM提取关键事实 | ❌ 直接存储对话 |
| **去重** | ✅ LLM智能去重 | ⚠️ 仅基于MD5哈希 |
| **记忆更新** | ✅ UPDATE增强现有记忆 | ❌ 不更新 |
| **记忆删除** | ✅ DELETE矛盾记忆 | ❌ 不删除 |
| **时间处理** | ✅ 智能合并时间信息 | ❌ 不处理 |
| **Token消耗** | 高（3次LLM调用） | 低（0次LLM调用） |
| **准确性** | 高 | 低 |
| **延迟** | 高（~3-5秒） | 低（~0.5秒） |

**推荐使用场景**：

- **智能模式**：重要的长期记忆（个人信息、偏好、计划）
- **简单模式**：临时对话、日志记录、性能敏感场景

---

## 调试建议

### 1. 查看日志

```bash
# 查看事实提取日志
grep "Extracted.*facts" logs/app.log

# 查看记忆决策日志
grep "LLM decided on.*memory actions" logs/app.log

# 查看用户画像提取日志
grep "Profile.*saved for user_id" logs/app.log

# 查看记忆数量变化
grep "MEMORY.*DETECTED" logs/app.log
```

### 2. 数据库查询

```sql
-- 查看用户所有记忆
SELECT id, content, created_at, updated_at
FROM memories
WHERE user_id = 'your_user_id'
ORDER BY updated_at DESC;

-- 查看用户画像
SELECT * FROM user_profiles
WHERE user_id = 'your_user_id';

-- 查看知识图谱实体
SELECT * FROM graph_entities
WHERE user_id = 'your_user_id';

-- 查看记忆数量统计
SELECT
    COUNT(*) as total,
    SUM(CASE WHEN DATE(created_at) = CURDATE() THEN 1 ELSE 0 END) as today,
    SUM(CASE WHEN DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) as last_7_days
FROM memories
WHERE user_id = 'your_user_id';
```

---

## 常见问题

### Q1: 为什么记忆会消失？

**可能原因**：
1. **LLM决策为DELETE**：新事实与旧记忆矛盾，LLM决定删除
2. **记忆合并**：UPDATE操作合并了多个记忆
3. **去重机制**：被识别为重复，返回NONE

**解决方法**：
- 查看日志中的`action_counts`统计
- 检查是否有DELETE操作
- 调整`DEFAULT_UPDATE_MEMORY_PROMPT`提示词

### Q2: 如何禁用智能模式？

```python
# 方法1：调用时设置infer=False
memory_client.add(messages, user_id="xxx", infer=False)

# 方法2：配置fallback_to_simple_add
intelligent_config = {
    "fallback_to_simple_add": True  # 失败时自动降级到简单模式
}
```

### Q3: 用户画像多久更新一次？

每次调用`add()`方法都会尝试更新画像。但LLM可能返回空字符串（如果对话中没有新的画像信息）。

### Q4: 如何切换到结构化画像（topics）？

```python
memory_client.add(
    messages=messages,
    user_id=user_id,
    profile_type="topics",  # 改为topics模式
    native_language="zh"
)
```

---

## 总结

PowerMem记忆处理的核心思想：

1. **事实优先**：先提取关键事实，再存储
2. **智能合并**：通过LLM决策ADD/UPDATE/DELETE/NONE
3. **时间感知**：特别关注时间信息的处理
4. **多语言支持**：保持原文语言，不翻译
5. **用户画像**：单独提取并缓存用户信息
6. **知识图谱**：自动构建实体和关系

通过智能模式（infer=True），系统能够：
- ✅ 自动去重
- ✅ 增强现有记忆
- ✅ 处理时间冲突
- ✅ 识别并删除矛盾信息
- ✅ 提取并维护用户画像

代价是更高的延迟和Token消耗，但对于长期记忆系统来说，这是值得的。
