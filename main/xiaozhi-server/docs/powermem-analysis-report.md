# PowerMem 记忆框架深度分析报告

> 分析日期：2026-06-05
> 分析范围：用户画像与记忆的抽取存储机制（不含记忆图谱）
> PowerMem 版本：v1.1.0
> 项目配置：`enable_user_profile: true`, `profile_type: content`, `infer: true`

---

## 一、架构总览

### 1.1 双模式架构

PowerMem 在 xiaozhi-server 中支持两种运行模式，由配置项 `enable_user_profile` 控制：

```
                          enable_user_profile
                               │
                ┌──────────────┼──────────────┐
                │ true                        │ false
                ▼                             ▼
         UserMemory                     AsyncMemory
     (用户画像模式)                   (普通记忆模式)
                │                             │
        ┌───────┼───────┐             ┌───────┼───────┐
        │       │       │             │       │       │
   Memory +  Profile  Profile     Memory   (无画像)
   记忆存储   Store    提取
             (独立表)  (LLM)
```

| 特性 | UserMemory (画像模式) | AsyncMemory (普通模式) |
|------|----------------------|----------------------|
| 类位置 | `powermem/user_memory/user_memory.py` | `powermem/core/async_memory.py` |
| 记忆存储 | 有（Memory 实例） | 有 |
| 用户画像 | 有（独立 UserProfileStore） | 无 |
| 搜索方式 | 同步 `memory_client.search()` | 异步 `await memory_client.search()` |
| 存储表 | `memories` + `user_profiles` | `memories` |
| 当前项目 | **启用** | 未启用 |

### 1.2 组件依赖关系

```
UserMemory
├── Memory (core/memory.py)           -- 记忆管理核心
│   ├── VectorStore (OceanBase)       -- 向量存储
│   ├── LLM (qwen)                    -- 大语言模型
│   ├── Embedding (qwen text-embedding-v4)  -- 嵌入模型
│   ├── IntelligenceManager           -- 智能处理管理器
│   └── IntelligentMemoryPlugin       -- 艾宾浩斯遗忘曲线插件（当前禁用）
├── UserProfileStore (OceanBase)      -- 用户画像独立存储
└── QueryRewriter (可选)              -- 查询改写器
```

---

## 二、记忆处理流程

### 2.1 记忆保存（save_memory）

入口：`powermem.py:save_memory()` → `memory_client.add()`

```
对话消息 (msgs)
    │
    ▼
格式化消息 (去除 system 角色，提取 JSON 中的 content)
    │
    ▼
UserMemory.add(messages, user_id, native_language="zh",
               profile_type="content", include_roles=["user"], infer=True)
    │
    ├── Step 1: Memory.add()          -- 记忆存储（4步智能流程）
    │
    └── Step 2: _extract_profile()    -- 用户画像提取
```

### 2.2 智能记忆处理 4 步流程（_intelligent_add）

当 `infer=True` 时启用智能模式，执行以下 4 步：

```
Step 1: 事实抽取 (_extract_facts)
    │   输入：对话消息
    │   提示词：FACT_RETRIEVAL_PROMPT
    │   输出：facts = ["事实1", "事实2", ...]
    │
    ▼
Step 2: 向量搜索 (search_memories)
    │   对每条 fact 生成 embedding
    │   在 OceanBase 中搜索相似记忆 (limit=5)
    │   合并所有已有记忆
    │
    ▼
Step 3: 决策判定 (_decide_memory_actions)
    │   输入：新 facts + 已有记忆
    │   提示词：DEFAULT_UPDATE_MEMORY_PROMPT
    │   输出：[{"id": "x", "text": "...", "event": "ADD|UPDATE|DELETE|NONE"}, ...]
    │
    ▼
Step 4: 执行操作
        ADD    → 生成新 embedding → storage.add_memory()
        UPDATE → 重新生成 embedding → storage.update_memory()
        DELETE → storage.delete_memory()
        NONE   → 不操作
```

当 `infer=False` 或未提取到任何 fact 时，走简单模式（`_simple_add`）：直接存储原始内容，不做 LLM 处理。

### 2.3 用户画像提取流程

```
_filtered_messages = 按 include_roles=["user"] 过滤消息
    │
    ▼
_extract_profile(messages, user_id, native_language="zh")
    │
    ├── 1. 获取已有画像 (profile_store.get_profile_by_user_id)
    │
    ├── 2. 构建提示词 (get_user_profile_extraction_prompt)
    │       传入：对话文本 + 已有画像 + 语言要求(Chinese)
    │
    ├── 3. 调用 LLM 生成画像文本
    │
    └── 4. 保存画像 (profile_store.save_profile)
            存入 user_profiles 表
```

### 2.4 记忆检索流程（query_memory）

```
query_memory(query)
    │
    ├── 1. 提取搜索内容（从 JSON 中提取 content 字段）
    │
    ├── 2. 获取用户画像（缓存优先）
    │       有缓存 → 直接返回 last_profile_content
    │       无缓存 → profile_store.get_profile_by_user_id() → 缓存
    │
    ├── 3. 向量搜索记忆
    │       UserMemory: asyncio.to_thread(memory_client.search)
    │       AsyncMemory: await memory_client.search()
    │       limit=30
    │
    └── 4. 格式化返回
            【用户画像】
            {画像内容}

            【相关记忆】
            - [2026-01-15 10:30:00] 记忆内容1
            - [2026-01-14 08:20:00] 记忆内容2
```

---

## 三、核心提示词详解

### 3.1 FACT_RETRIEVAL_PROMPT（事实抽取提示词）

**文件位置**：`powermem/prompts/intelligent_memory_prompts.py`

**用途**：从对话中提取独立的事实、偏好、意图和需求

**英文原文**：

```
You are a Personal Information Organizer. Extract relevant facts, memories,
preferences, intentions, and needs from conversations into distinct,
manageable facts.

Information Types: Personal preferences, details (names, relationships, dates),
plans, intentions, needs, requests, activities, health/wellness (including
medical appointments, symptoms, treatments), professional, miscellaneous.

CRITICAL Rules:
1. TEMPORAL: ALWAYS extract time info (dates, relative refs like "yesterday",
   "last week"). Include in facts (e.g., "Went to Hawaii in May 2023" or
   "Went to Hawaii last year", not just "Went to Hawaii"). Preserve relative
   time refs for later calculation.
2. COMPLETE: Extract self-contained facts with who/what/when/where when
   available.
3. SEPARATE: Extract distinct facts separately, especially when they have
   different time periods.
4. INTENTIONS & NEEDS: ALWAYS extract user intentions, needs, and requests
   even without time information.
5. LANGUAGE: DO NOT translate. Preserve the original language of the source
   text for each extracted fact.

Examples:
Input: Hi.
Output: {"facts": []}

Input: Yesterday, I met John at 3pm. We discussed the project.
Output: {"facts": ["Met John at 3pm yesterday", "Discussed project with John yesterday"]}

Input: Last May, I went to India. Visited Mumbai and Goa.
Output: {"facts": ["Went to India in May", "Visited Mumbai in May", "Visited Goa in May"]}

Rules:
- Today: {当前日期}
- Return JSON: {"facts": ["fact1", "fact2"]}
- Extract from user/assistant messages only
- Extract intentions, needs, and requests even without time information
- If no relevant facts, return empty list
- Output must preserve the input language (no translation)
```

**中文翻译**：

```
你是一个个人信息整理师。从对话中提取相关的事实、记忆、偏好、意图和需求，
将其拆分为独立、可管理的事实。

信息类型：个人偏好、细节（姓名、关系、日期）、计划、意图、需求、请求、活动、
健康/身体状况（包括医疗预约、症状、治疗）、职业信息、其他杂项。

关键规则：
1. 时间性：始终提取时间信息（日期、"昨天"、"上周"等相对时间引用）。
   在事实中包含时间（例如"2023年5月去了夏威夷"或"去年去了夏威夷"，
   而不是仅仅"去了夏威夷"）。保留相对时间引用以便后续计算。
2. 完整性：提取自包含的事实，包含可用的谁/什么/何时/何地信息。
3. 分离性：将不同的事实分开提取，特别是当它们属于不同时间段时。
4. 意图与需求：始终提取用户的意图、需求和请求，即使没有时间信息。
   例如："想预约医生"、"需要给某人打电话"、"计划去某地"。
5. 语言：不要翻译。保留源文本的原始语言。如果输入是中文，输出中文事实；
   如果是英文，输出英文；如果是混合语言，保持每条事实的原有语言。

示例：
输入：Hi.
输出：{"facts": []}

输入：昨天下午3点，我见到了John。我们讨论了项目。
输出：{"facts": ["昨天下午3点见到了John", "昨天和John讨论了项目"]}

输入：去年5月，我去了印度。去了孟买和果阿。
输出：{"facts": ["5月去了印度", "5月去了孟买", "5月去了果阿"]}

规则：
- 今天：{当前日期}
- 返回 JSON：{"facts": ["事实1", "事实2"]}
- 仅从用户/助手消息中提取
- 即使没有时间信息，也要提取意图、需求和请求
- 如果没有相关事实，返回空列表
- 输出必须保留输入语言（不翻译）
```

---

### 3.2 DEFAULT_UPDATE_MEMORY_PROMPT（记忆更新决策提示词）

**文件位置**：`powermem/prompts/intelligent_memory_prompts.py`

**用途**：比较新事实与已有记忆，决定执行 ADD/UPDATE/DELETE/NONE 操作

**英文原文**：

```
You are a memory manager. Compare new facts with existing memory. Decide:
ADD, UPDATE, DELETE, or NONE.

Operations:
1. ADD: New info not in memory -> add with new ID
2. UPDATE: Info exists but different/enhanced -> update (keep same ID).
   Prefer fact with most information.
3. DELETE: Contradictory info -> delete (use sparingly)
4. NONE: Already present or irrelevant -> no change

Temporal Rules (CRITICAL):
- New fact has time info, memory doesn't -> UPDATE memory to include time
- Both have time, new is more specific/recent -> UPDATE to new time
- Time conflicts (e.g., "2022" vs "2023") -> UPDATE to more recent
- Preserve relative time refs (e.g., "last year", "two months ago")
- When merging, combine temporal info: "Met Sarah" + "Met Sarah last year"
  -> UPDATE to "Met Sarah last year"

Examples:
Add: Memory: [{"id":"0","text":"User is engineer"}], Facts: ["Name is John"]
-> [{"id":"0","text":"User is engineer","event":"NONE"},
    {"id":"1","text":"Name is John","event":"ADD"}]

Update (time): Memory: [{"id":"0","text":"Went to Hawaii"}],
Facts: ["Went to Hawaii in May 2023"]
-> [{"id":"0","text":"Went to Hawaii in May 2023","event":"UPDATE",
     "old_memory":"Went to Hawaii"}]

Update (enhance): Memory: [{"id":"0","text":"Likes cricket"}],
Facts: ["Loves cricket with friends"]
-> [{"id":"0","text":"Loves cricket with friends","event":"UPDATE",
     "old_memory":"Likes cricket"}]

Delete: Only clear contradictions (e.g., "Loves pizza" vs "Dislikes pizza").
Prefer UPDATE for time conflicts.

Important: Use existing IDs only. Keep same ID when updating. Always preserve
temporal information.
LANGUAGE (CRITICAL): Do NOT translate memory text. Keep the same language as
the incoming fact(s) and the original memory whenever possible.
```

**中文翻译**：

```
你是一个记忆管理器。将新事实与已有记忆进行比较，决定执行以下操作之一：
ADD（添加）、UPDATE（更新）、DELETE（删除）或 NONE（不变）。

操作说明：
1. ADD（添加）：新信息不在记忆中 -> 用新 ID 添加
2. UPDATE（更新）：信息已存在但有所不同/更详细 -> 更新（保持相同 ID）。
   优先保留信息量最大的事实。
3. DELETE（删除）：矛盾的信息 -> 删除（谨慎使用）
4. NONE（不变）：已存在或不相关 -> 不做更改

时间规则（关键）：
- 新事实有时间信息，记忆中没有 -> UPDATE 记忆以包含时间
- 两者都有时间，新的更具体/更新 -> UPDATE 为新时间
- 时间冲突（例如"2022" vs "2023"） -> UPDATE 为更新的时间
- 保留相对时间引用（例如"去年"、"两个月前"）
- 合并时组合时间信息："遇到了Sarah" + "去年遇到了Sarah"
  -> UPDATE 为"去年遇到了Sarah"

示例：
添加：记忆：[{"id":"0","text":"用户是工程师"}]，事实：["名字叫John"]
-> [{"id":"0","text":"用户是工程师","event":"NONE"},
    {"id":"1","text":"名字叫John","event":"ADD"}]

更新（时间）：记忆：[{"id":"0","text":"去了夏威夷"}]，
事实：["2023年5月去了夏威夷"]
-> [{"id":"0","text":"2023年5月去了夏威夷","event":"UPDATE",
     "old_memory":"去了夏威夷"}]

更新（增强）：记忆：[{"id":"0","text":"喜欢板球"}]，
事实：["和朋友一起热爱板球"]
-> [{"id":"0","text":"和朋友一起热爱板球","event":"UPDATE",
     "old_memory":"喜欢板球"}]

删除：仅用于明确矛盾的情况（例如"喜欢披萨" vs "不喜欢披萨"）。
时间冲突时优先使用 UPDATE。

重要：只使用已有的 ID。更新时保持相同 ID。始终保留时间信息。
语言（关键）：不要翻译记忆文本。尽可能保持与传入事实和原始记忆相同的语言。
```

---

### 3.3 USER_PROFILE_EXTRACTION_PROMPT（用户画像提取提示词）

**文件位置**：`powermem/prompts/user_profile_prompts.py`

**用途**：从对话中提取并更新用户画像信息（自然语言文本格式）

**英文原文**：

```
You are a user profile extraction specialist. Your task is to analyze
conversations and extract user profile information.

[Reference Topics]:
The following topics are for guidance only. Please selectively extract
information based on the actual content of the conversation, without forcing
all fields to be filled.:

- Basic Information
  - User Name, User Age (integer), Gender, Date of Birth, Nationality,
    Ethnicity, Language
- Contact Information
  - Email, Phone, City, Province
- Education Background
  - School, Degree, Major, Graduation Year
- Demographics
  - Marital Status, Number of Children, Household Income
- Employment
  - Company, Position, Work Location, Projects Involved In, Work Skills
- Interests and Hobbies
  - Books, Movies, Music, Food, Sports
- Lifestyle
  - Dietary Preferences, Exercise Habits, Health Status, Sleep Patterns,
    Smoking, Alcohol Consumption
- Psychological Traits
  - Personality Traits, Values, Beliefs, Motivations, Goals
- Life Events
  - Marriage, Relocation, Retirement

[Instructions]:
1. Review the current user profile if provided below
2. Analyze the new conversation carefully to identify any new or updated
   user-related information
3. Extract only factual information explicitly mentioned in the conversation
4. Update the profile by:
   - Adding new information that is not in the current profile
   - Updating existing information if the conversation provides more recent
     or different details
   - Keeping unchanged information that is still valid
5. Combine all information into a coherent, updated profile description
6. If no relevant profile information is found in the conversation, return
   the current profile as-is
7. Write the profile in natural language, not as structured data
8. Focus on current state and characteristics of the user
9. If no user profile information can be extracted from the conversation
   at all, return an empty string ""
10. The final extracted profile description must not exceed 1,000 characters.
```

**中文翻译**：

```
你是一个用户画像提取专家。你的任务是分析对话并提取用户画像信息。

[参考主题]：
以下主题仅供参考。请根据对话的实际内容选择性提取信息，不要强制填写所有字段：

- 基本信息
  - 用户姓名、用户年龄（整数）、性别、出生日期、国籍、民族、语言
- 联系信息
  - 邮箱、电话、城市、省份
- 教育背景
  - 学校、学位、专业、毕业年份
- 人口统计
  - 婚姻状况、子女数量、家庭收入
- 职业信息
  - 公司、职位、工作地点、参与项目、工作技能
- 兴趣爱好
  - 书籍、电影、音乐、美食、运动
- 生活方式
  - 饮食偏好（如素食、纯素）、运动习惯、健康状况、睡眠模式、吸烟、饮酒
- 心理特征
  - 性格特征、价值观、信仰、动机、目标
- 生活事件
  - 结婚、搬迁、退休

[指令]：
1. 如果下方提供了当前用户画像，请先审阅
2. 仔细分析新对话，识别任何新的或更新的用户相关信息
3. 只提取对话中明确提及的事实信息
4. 通过以下方式更新画像：
   - 添加当前画像中没有的新信息
   - 如果对话提供了更新的或不同的细节，则更新已有信息
   - 保留仍然有效的未更改信息
5. 将所有信息组合成连贯的、更新后的画像描述
6. 如果在对话中没有找到相关的画像信息，原样返回当前画像
7. 用自然语言撰写画像，不使用结构化数据格式
8. 关注用户的当前状态和特征
9. 如果完全无法从对话中提取用户画像信息，返回空字符串 ""
10. 最终提取的画像描述不得超过 1000 个字符。
```

**动态拼接到提示词中的部分**：

```
[Current User Profile]:
```
{existing_profile}           ← 已有画像内容
```

[Language Requirement]:
You MUST extract and write the profile content in Chinese, regardless of what
languages are used in the conversation.     ← native_language="zh" 触发

[Target]:
Extract and return the user profile information as a text description:

[Conversation]:
{conversation_text}          ← 当前对话内容
```

翻译：

```
[当前用户画像]：
```
{已有画像内容}
```

[语言要求]：
你必须用中文提取和撰写画像内容，无论对话中使用的是什么语言。

[目标]：
提取并以文本描述的形式返回用户画像信息：

[对话内容]：
{当前对话文本}
```

---

### 3.4 用户画像主题提取提示词（profile_type="topics" 时使用）

**文件位置**：`powermem/prompts/user_profile_prompts.py`

**用途**：以结构化 JSON 格式提取用户画像（当前项目未启用，使用的是 "content" 模式）

**英文核心指令**（节选）：

```
You are a user profile topic extraction specialist. Your task is to analyze
conversations and extract user profile information as structured topics.

[Instructions]:
...
5. Structure the output as a JSON object with hierarchical topics
6. All keys must be in snake_case format (lowercase with underscores)
...
8. If no user profile information can be extracted from the conversation
   at all, return an empty JSON object {}

[Output Format]:
Return a valid JSON object with the following structure:
{
  "main_topic_name": {
    "sub_topic_name": "value",
    "another_sub_topic": "value"
  }
}
```

**中文翻译**：

```
你是一个用户画像主题提取专家。你的任务是分析对话，以结构化主题的形式
提取用户画像信息。

[指令]：
...
5. 将输出构建为具有层级主题的 JSON 对象
6. 所有键必须使用 snake_case 格式（小写字母加下划线）
...
8. 如果完全无法从对话中提取用户画像信息，返回空的 JSON 对象 {}

[输出格式]：
返回一个有效的 JSON 对象，结构如下：
{
  "主主题名称": {
    "子主题名称": "值",
    "另一个子主题": "值"
  }
}
```

---

## 四、存储机制

### 4.1 向量存储（memories 表）

每条记忆记录包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Snowflake ID | 雪花算法生成的唯一标识 |
| `content` | TEXT | 记忆文本内容 |
| `embedding` | VECTOR(1024) | text-embedding-v4 生成的向量 |
| `hash` | VARCHAR | 内容的 MD5 哈希（去重用） |
| `user_id` | VARCHAR | 用户标识 |
| `agent_id` | VARCHAR | Agent 标识 |
| `run_id` | VARCHAR | 运行标识 |
| `metadata` | JSON | 元数据 |
| `category` | VARCHAR | 分类 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

### 4.2 用户画像存储（user_profiles 表）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INT | 画像 ID |
| `user_id` | VARCHAR | 用户标识（唯一） |
| `profile_content` | TEXT | 自然语言画像文本（当前启用） |
| `topics` | JSON | 结构化主题 JSON（未启用） |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

### 4.3 搜索机制

OceanBase 向量存储支持混合搜索：

- **向量相似度搜索**：embedding 余弦相似度
- **全文搜索**：文本关键词匹配
- **稀疏向量搜索**（可选，需配置 sparse_embedder）
- **融合方式**：RRF（Reciprocal Rank Fusion）合并多种搜索结果

### 4.4 画像缓存策略

项目适配层（`powermem.py`）实现了缓存优先的画像获取：

```
query_memory()
    │
    ├── 检查 last_profile_content 缓存
    │   └── 有缓存 → 直接返回（零延迟）
    │
    └── 无缓存
        ├── profile_store.get_profile_by_user_id()
        ├── 优先使用 profile_content（自然语言文本）
        ├── 备选使用 topics（结构化 JSON）
        └── 写入 last_profile_content 缓存
```

---

## 五、完整数据流图

```
┌──────────────────────────────────────────────────────────────────┐
│                        用户与 ESP32 设备对话                       │
└─────────────────┬────────────────────────────────────────────────┘
                  │ WebSocket
                  ▼
┌──────────────────────────────────────────────────────────────────┐
│  ConnectionHandler (core/connection.py)                          │
│  └── 对话结束后调用 save_memory()                                 │
└─────────────────┬────────────────────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────────────────────────────────────┐
│  MemoryProvider.save_memory() (powermem.py)                      │
│  ├── 格式化消息（去 system 角色，提取 JSON content）               │
│  └── 调用 memory_client.add(                                     │
│        messages, user_id, native_language="zh",                  │
│        profile_type="content", include_roles=["user"],           │
│        infer=True)                                               │
└─────────────────┬────────────────────────────────────────────────┘
                  │
                  ▼
┌──────────────────────────────────────────────────────────────────┐
│  UserMemory.add() (user_memory.py)                               │
│  │                                                               │
│  ├── Step 1: Memory.add() ─── 记忆存储 ──────────────────────┐   │
│  │   │                                                        │   │
│  │   └── _intelligent_add() (infer=True)                     │   │
│  │       │                                                    │   │
│  │       ├── 1. _extract_facts()                              │   │
│  │       │   提示词: FACT_RETRIEVAL_PROMPT                    │   │
│  │       │   → ["事实1", "事实2", ...]                        │   │
│  │       │                                                    │   │
│  │       ├── 2. 向量搜索相似记忆 (limit=5)                     │   │
│  │       │   → 已有记忆列表                                    │   │
│  │       │                                                    │   │
│  │       ├── 3. _decide_memory_actions()                      │   │
│  │       │   提示词: DEFAULT_UPDATE_MEMORY_PROMPT             │   │
│  │       │   → [{event: "ADD/UPDATE/DELETE/NONE"}]            │   │
│  │       │                                                    │   │
│  │       └── 4. 执行 CRUD 操作 → OceanBase memories 表        │   │
│  │                                                            │   │
│  └── Step 2: _extract_profile() ─── 用户画像提取 ──────────┐    │
│      │                                                      │    │
│      ├── 过滤消息 (仅保留 role="user")                       │    │
│      │                                                      │    │
│      ├── 获取已有画像 (user_profiles 表)                     │    │
│      │                                                      │    │
│      ├── 构建提示词:                                         │    │
│      │   USER_PROFILE_EXTRACTION_PROMPT                      │    │
│      │   + 已有画像 + 语言要求(Chinese) + 对话文本            │    │
│      │                                                      │    │
│      ├── LLM 生成画像文本 (≤1000字符)                        │    │
│      │                                                      │    │
│      └── 保存到 user_profiles 表                             │    │
│                                                             │    │
│  返回结果合并:                                               │    │
│  { "results": [...], "profile_extracted": true,             │    │
│    "profile_content": "用户画像文本" }                       │    │
└─────────────────────────────────────────────────────────────┘    │
                  │                                                  │
                  │ 缓存 profile_content 到 last_profile_content     │
                  ▼                                                  │
┌──────────────────────────────────────────────────────────────────┐
│  记忆检索: MemoryProvider.query_memory()                         │
│  ├── 获取用户画像（缓存优先）                                     │
│  ├── 向量搜索记忆 (limit=30)                                     │
│  └── 返回格式:                                                   │
│      【用户画像】                                                 │
│      {画像文本}                                                   │
│                                                                   │
│      【相关记忆】                                                 │
│      - [时间戳] 记忆内容                                          │
│      - [时间戳] 记忆内容                                          │
└──────────────────────────────────────────────────────────────────┘
```

---

## 六、项目适配层关键参数

### 6.1 save_memory 调用参数

```python
self.memory_client.add(
    messages=messages,           # 对话消息列表
    user_id=self.role_id,        # 用户标识（设备绑定）
    native_language="zh",        # 强制中文提取画像
    profile_type="content",      # 自然语言画像（非结构化 topics）
    include_roles=["user"],      # 仅从用户消息提取画像
    infer=True                   # 启用智能记忆处理
)
```

### 6.2 配置来源

```
config.yaml
  └── Memory.Memory_powermem
        ├── type: "powermem"
        ├── enable_user_profile: true
        ├── llm_model: "qwen3.6-flash"
        ├── embedding_model: "text-embedding-v4"
        ├── embedding_dims: 1024
        ├── vector_store.provider: "oceanbase"
        ├── vector_store.config: { host, port, user, password, db_name }
        └── intelligent_memory.plugin.enabled: false  (艾宾浩斯遗忘曲线已禁用)
```

---

## 七、已知特性与注意事项

| 项目 | 说明 |
|------|------|
| 画像长度限制 | 自然语言画像不超过 1000 字符 |
| 画像缓存 | 每次对话保存后更新 `last_profile_content` 内存缓存 |
| 消息过滤 | 画像提取仅使用 `role="user"` 的消息 |
| 语言保持 | 事实抽取和记忆更新保留原始语言，不翻译 |
| 时间保留 | 事实抽取时保留所有相对时间引用（"昨天"、"上周"） |
| 去重机制 | 记忆内容通过 MD5 哈希去重 |
| 艾宾浩斯遗忘 | 已禁用（`plugin.enabled: false`），记忆不会随时间衰减 |
| 画像更新策略 | 每次对话保存时都会重新调用 LLM 生成完整画像（含已有信息） |
| 搜索返回数量 | 记忆检索最多返回 30 条，相似度搜索每条事实匹配 5 条 |
| 最低消息数 | 至少需要 2 条消息才会触发保存 |

---

## 八、艾宾浩斯遗忘曲线算法（已禁用）

> 当前配置 `intelligent_memory.plugin.enabled: false`，以下算法未生效，仅供参考。

### 8.1 核心公式

```
R = e^(-t/S)
S = 24 * decay_rate * memory_type_multiplier
```

- **R** = 记忆保留率
- **t** = 距上次访问的小时数
- **S** = 记忆稳定性
- **decay_rate**：衰减速率（配置值）
- **memory_type_multiplier**：按重要性分类的乘数

### 8.2 记忆分类阈值

| 类型 | 重要性范围 | 说明 |
|------|-----------|------|
| 工作记忆 | 0 - 0.3 | 极易遗忘 |
| 短期记忆 | 0.3 - 0.8 | 中等保留 |
| 长期记忆 | 0.8 - 1.0 | 高保留 |

### 8.3 重要性评估（6 维度加权）

| 维度 | 权重 | 说明 |
|------|------|------|
| 相关性 | 30% | 与用户需求的相关程度 |
| 新颖性 | 20% | 信息的新鲜程度 |
| 情感性 | 15% | 情感关联强度 |
| 可操作性 | 15% | 可采取行动的程度 |
| 事实性 | 10% | 客观事实含量 |
| 个人性 | 10% | 个人特征相关度 |

### 8.4 遗忘判定条件

```
should_forget():
  decay_factor < working_threshold (0.3)
  OR (access_count == 0 AND age > 7 days)
```

---

## 九、混合搜索机制

### 9.1 三路搜索融合

1. **向量搜索**：基于语义相似度（embedding cosine similarity）
2. **全文搜索（FTS）**：基于关键词匹配
3. **稀疏向量搜索**（可选）：需配置 sparse_embedder

### 9.2 RRF 融合算法

```
rrf_score = sum(1 / (k + rank_i))
```

- **k** 为平滑常数
- 将多种搜索的排名结果融合为最终分数

### 9.3 搜索元数据字段

| 字段 | 持久化 | 用途 |
|------|--------|------|
| `access_count` | 是 | 艾宾浩斯算法使用 |
| `search_count` | 是 | 统计信息 |
| `_fusion_info` | 否（临时） | 搜索排名详情 |
| `_quality_score` | 否（临时） | 质量评分 |

### 9.4 数据库字段映射

```
应用层 content → 适配器层 data → 数据库层 text_content
```

UPSERT 操作使用 `INSERT ... ON DUPLICATE KEY UPDATE` 保证原子性更新。

---

## 十、已知问题

### 问题 1：搜索时记忆自动删除（严重）

| 项目 | 说明 |
|------|------|
| **现象** | 查询记忆时触发大量 DELETE 操作 |
| **根因** | intelligent_memory 插件的 `on_search()` 钩子根据艾宾浩斯遗忘曲线删除"过期"记忆 |
| **时间线证据** | 05:49:27 - 14 次 DELETE 操作由 search 触发，非 save |
| **解决方案** | 设置 `intelligent_memory.plugin.enabled: false` |
| **状态** | 已定位根因，当前配置已禁用 |

### 问题 2：SDK graph_store 初始化错误

| 项目 | 说明 |
|------|------|
| **现象** | graph_store 配置解析异常 |
| **解决方案** | monkey-patch 修复（代码中已有） |
| **状态** | 已通过代码绕过 |

### 问题 3：缺少 embedding_model_dims 参数

| 项目 | 说明 |
|------|------|
| **现象** | SDK 初始化报错 |
| **解决方案** | 配置中显式设置 `embedding_model_dims: 1024` |
| **状态** | 已修复 |

### 问题 4：数据库密码类型错误

| 项目 | 说明 |
|------|------|
| **现象** | password 字段需为 string 类型 |
| **解决方案** | 配置中确保 password 为字符串 |
| **状态** | 配置层面已解决 |

### 问题 5：query_memory 未使用 add_profile=True

| 项目 | 说明 |
|------|------|
| **现象** | `query_memory()` 中 `search()` 调用未传 `add_profile=True`，可能缺少画像信息 |
| **影响** | 当前通过独立的 `get_user_profile()` 方法补偿，功能正常但未走标准路径 |
| **状态** | 功能已补偿，建议后续优化 |

### 问题 6：SDK 版本风险

| 项目 | 说明 |
|------|------|
| **现象** | requirements.txt 声明 `powermem>=0.3.1`，实际安装 1.1.0 |
| **风险** | SDK API 可能在小版本升级中发生破坏性变更 |
| **建议** | 固定版本为 `powermem==1.1.0` |

---

## 十一、配置选项对比

### plugin.enabled vs infer 参数的关键区别

| 维度 | `plugin.enabled=false` | `infer=False` |
|------|----------------------|---------------|
| 控制范围 | 搜索时行为 | 存储时行为 |
| 防止自动删除 | **能** | **不能** |
| 影响 on_search() | 禁用 | 不影响 |
| 影响 on_add() | 禁用 | 不影响 |
| 生产建议 | 防止数据丢失推荐使用 | 降低 LLM 调用成本 |

---

## 十二、设计决策总结

1. **Provider 模式**：记忆作为可插拔组件，支持多种后端（PowerMem、Redis 等）
2. **双 LLM 调用**：每次 save 触发两次 LLM（事实提取 + 决策），换取记忆质量，代价是延迟和 token 成本（约 1300 tokens/次）
3. **仅提取用户角色**：`include_roles=["user"]` 只从用户消息提取记忆，避免将 AI 回复存为"用户记忆"
4. **Cache-Aside 画像缓存**：约 95% 命中率减少 DB 查询，但需要处理缓存失效
5. **RRF 混合搜索**：融合语义搜索和关键词搜索的优势
6. **艾宾浩斯遗忘曲线**：模拟人类记忆衰减的设计理念，但实际效果存疑（导致数据丢失问题）

---

## 十三、提示词注入机制

系统提示词模板中包含 `<memory>...</memory>` 占位符，运行时被替换为：

```
【用户画像】
{user_profile_content}

【相关记忆】
- [2026-01-15 10:30:00] 记忆内容1
- [2026-01-14 08:20:00] 记忆内容2
```

每次对话的交互流程：
1. 先查询记忆 → 注入系统提示词
2. LLM 生成回复
3. 对话结束后保存记忆

---

> **核心结论**：系统设计上功能完善（向量记忆 + 用户画像 + 知识图谱），但在生产环境中需特别注意 intelligent_memory 插件的自动删除行为，当前已通过 `intelligent_memory.plugin.enabled: false` 禁用以防止记忆数据意外丢失。
