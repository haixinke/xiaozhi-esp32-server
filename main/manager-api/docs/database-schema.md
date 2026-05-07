# 小智ESP32服务器 - 数据库表结构文档

> 本文档详细描述了 `manager-api` (Java Spring Boot) 后端服务的所有数据库表结构、字段说明以及表之间的关系。

## 目录

- [数据库概述](#数据库概述)
- [系统管理模块](#系统管理模块)
- [AI模型配置模块](#ai模型配置模块)
- [智能体管理模块](#智能体管理模块)
- [设备管理模块](#设备管理模块)
- [对话管理模块](#对话管理模块)
- [声纹与声音克隆模块](#声纹与声音克隆模块)
- [知识库管理模块](#知识库管理模块)
- [表关系图](#表关系图)

---

## 数据库概述

- **数据库类型**: MySQL 8.0+
- **字符集**: utf8mb4
- **迁移工具**: Liquibase
- **迁移文件位置**: `src/main/resources/db/changelog/`
- **主变更日志**: `db.changelog-master.yaml`

### 表统计

| 模块 | 表数量 | 说明 |
|------|--------|------|
| 系统管理 | 5 | 用户、认证、参数、字典 |
| AI模型配置 | 3 | 模型供应器、配置、音色 |
| 智能体管理 | 12 | 智能体、插件、标签、上下文等 |
| 设备管理 | 2 | 设备信息、固件升级 |
| 对话管理 | 3 | 聊天记录、消息、音频 |
| 声纹与声音克隆 | 2 | 声纹识别、声音克隆 |
| 知识库管理 | 2 | RAG知识库、文档 |
| **总计** | **29** | |

---

## 系统管理模块

### 1. sys_user - 系统用户表

存储系统管理员和Web用户的基本信息和认证数据。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | bigint | - | NO | - | PK | - | 用户唯一标识 |
| username | varchar | 50 | NO | - | - | UK | 用户名（唯一） |
| password | varchar | 100 | YES | NULL | - | - | 密码（加密存储） |
| super_admin | tinyint | unsigned | YES | NULL | - | - | 超级管理员标识（0否/1是） |
| status | tinyint | - | YES | NULL | - | - | 状态（0停用/1正常） |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `uk_username`: 用户名唯一索引，确保用户名不重复

### 2. sys_user_token - 用户Token表

存储用户的登录令牌，用于API认证。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | bigint | - | NO | - | PK | - | Token唯一标识 |
| user_id | bigint | - | NO | - | - | UK | 关联用户ID |
| token | varchar | 100 | NO | - | - | UK | 访问令牌 |
| expire_date | datetime | - | YES | NULL | - | - | 过期时间 |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |

**索引说明**:
- `user_id`: 用户ID唯一索引，一个用户只能有一个有效Token
- `token`: Token唯一索引，确保Token全局唯一

**表关系**:
- `user_id` → `sys_user.id` (多对一)

### 3. sys_params - 系统参数配置表

存储系统的全局配置参数，包括服务器配置、日志配置、插件配置等。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | bigint | - | NO | - | PK | - | 参数唯一标识 |
| param_code | varchar | 100 | YES | NULL | - | UK | 参数编码（唯一） |
| param_value | varchar | 2000 | YES | NULL | - | - | 参数值 |
| value_type | varchar | 20 | YES | 'string' | - | - | 值类型（string/number/boolean/array/json） |
| param_type | tinyint | unsigned | YES | 1 | - | - | 参数类型（0系统参数/1非系统参数） |
| remark | varchar | 200 | YES | NULL | - | - | 备注说明 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `uk_param_code`: 参数编码唯一索引

**常用参数编码示例**:
- `server.ip`: 服务器监听IP地址
- `server.port`: 服务器监听端口
- `server.secret`: 服务器密钥
- `server.websocket`: WebSocket地址
- `server.ota`: OTA升级地址
- `log.log_level`: 日志级别
- `device_max_output_size`: 单台设备每天最多输出字数

### 4. sys_dict_type - 字典类型表

存储系统字典的分类信息。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | bigint | - | NO | - | PK | - | 字典类型唯一标识 |
| dict_type | varchar | 100 | NO | - | - | UK | 字典类型编码（唯一） |
| dict_name | varchar | 255 | NO | - | - | - | 字典类型名称 |
| remark | varchar | 255 | YES | NULL | - | - | 备注说明 |
| sort | int | unsigned | YES | NULL | - | - | 排序号 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `dict_type`: 字典类型唯一索引

### 5. sys_dict_data - 字典数据表

存储系统字典的具体数据项。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | bigint | - | NO | - | PK | - | 字典数据唯一标识 |
| dict_type_id | bigint | - | NO | - | - | - | 关联字典类型ID |
| dict_label | varchar | 255 | NO | - | - | - | 字典标签（显示名称） |
| dict_value | varchar | 255 | YES | NULL | - | - | 字典值（实际值） |
| remark | varchar | 255 | YES | NULL | - | - | 备注说明 |
| sort | int | unsigned | YES | NULL | - | IDX | 排序号 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `uk_dict_type_value`: 字典类型ID和字典值的联合唯一索引
- `idx_sort`: 排序字段索引

**表关系**:
- `dict_type_id` → `sys_dict_type.id` (多对一)

---

## AI模型配置模块

### 6. ai_model_provider - 模型供应器表

定义各种AI模型的服务提供者和配置模板，包括VAD、ASR、LLM、TTS、Memory、Intent和Plugin等类型。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 供应器唯一标识 |
| model_type | varchar | 20 | YES | NULL | - | IDX | 模型类型（VAD/ASR/LLM/TTS/Memory/Intent/Plugin） |
| provider_code | varchar | 50 | YES | NULL | - | - | 供应器类型代码 |
| name | varchar | 50 | YES | NULL | - | - | 供应器名称 |
| fields | json | - | YES | NULL | - | - | 配置字段列表（JSON格式） |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_ai_model_provider_model_type`: 模型类型索引，用于快速查找特定类型的所有供应器

**模型类型说明**:
- **VAD** (Voice Activity Detection): 语音活动检测，如 SileroVAD
- **ASR** (Automatic Speech Recognition): 语音识别，如 FunASR、SherpaASR、DoubaoASR、TencentASR
- **LLM** (Large Language Model): 大语言模型，如 ChatGLM、Ollama、通义千问、DeepSeek
- **TTS** (Text To Speech): 语音合成，如 EdgeTTS、DoubaoTTS、OpenAITTS
- **Memory**: 记忆模型，如 Mem0AI、本地短期记忆
- **Intent**: 意图识别，如 LLM意图识别、函数调用
- **Plugin**: 功能插件，如天气查询、新闻订阅、音乐播放、HomeAssistant控制

### 7. ai_model_config - 模型配置表

存储具体的AI模型配置实例，每个配置对应一个供应器。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 配置唯一标识 |
| model_type | varchar | 20 | YES | NULL | - | IDX | 模型类型 |
| model_code | varchar | 50 | YES | NULL | - | - | 模型编码（如AliLLM、DoubaoTTS） |
| model_name | varchar | 50 | YES | NULL | - | - | 模型名称 |
| is_default | tinyint | 1 | YES | 0 | - | - | 是否默认配置（0否/1是） |
| is_enabled | tinyint | 1 | YES | 0 | - | - | 是否启用（0否/1是） |
| config_json | json | - | YES | NULL | - | - | 模型配置参数（JSON格式） |
| doc_link | varchar | 200 | YES | NULL | - | - | 官方文档链接 |
| remark | varchar | 255 | YES | NULL | - | - | 备注说明 |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_ai_model_config_model_type`: 模型类型索引

### 8. ai_tts_voice - TTS音色表

存储各种TTS模型的音色配置。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 音色唯一标识 |
| tts_model_id | varchar | 32 | YES | NULL | - | IDX | 关联的TTS模型ID |
| name | varchar | 20 | YES | NULL | - | - | 音色名称 |
| tts_voice | varchar | 50 | YES | NULL | - | - | 音色编码 |
| languages | varchar | 50 | YES | NULL | - | - | 支持的语言 |
| voice_demo | varchar | 500 | YES | NULL | - | - | 音色Demo音频URL |
| remark | varchar | 255 | YES | NULL | - | - | 备注说明 |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_ai_tts_voice_tts_model_id`: TTS模型ID索引

**表关系**:
- `tts_model_id` → `ai_model_config.id` (多对一)

---

## 智能体管理模块

### 9. ai_agent_template - 智能体配置模板表

存储预定义的智能体模板，用户可以基于模板快速创建智能体。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 模板唯一标识 |
| agent_code | varchar | 36 | YES | NULL | - | - | 智能体编码 |
| agent_name | varchar | 64 | YES | NULL | - | - | 智能体名称 |
| asr_model_id | varchar | 32 | YES | NULL | - | - | 语音识别模型ID |
| vad_model_id | varchar | 64 | YES | NULL | - | - | 语音活动检测模型ID |
| llm_model_id | varchar | 32 | YES | NULL | - | - | 大语言模型ID |
| tts_model_id | varchar | 32 | YES | NULL | - | - | 语音合成模型ID |
| tts_voice_id | varchar | 32 | YES | NULL | - | - | 音色ID |
| mem_model_id | varchar | 32 | YES | NULL | - | - | 记忆模型ID |
| intent_model_id | varchar | 32 | YES | NULL | - | - | 意图识别模型ID |
| system_prompt | text | - | YES | NULL | - | - | 角色设定Prompt |
| lang_code | varchar | 10 | YES | NULL | - | - | 语言编码 |
| language | varchar | 10 | YES | NULL | - | - | 交互语种 |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |

### 10. ai_agent - 智能体配置表

存储用户创建的具体智能体实例。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 智能体唯一标识 |
| user_id | bigint | - | YES | NULL | - | IDX | 所属用户ID |
| agent_code | varchar | 36 | YES | NULL | - | - | 智能体编码 |
| agent_name | varchar | 64 | YES | NULL | - | - | 智能体名称 |
| asr_model_id | varchar | 32 | YES | NULL | - | - | 语音识别模型ID |
| vad_model_id | varchar | 64 | YES | NULL | - | - | 语音活动检测模型ID |
| llm_model_id | varchar | 32 | YES | NULL | - | - | 大语言模型ID |
| slm_model_id | varchar | 255 | YES | NULL | - | - | 小模型ID |
| tts_model_id | varchar | 32 | YES | NULL | - | - | 语音合成模型ID |
| tts_voice_id | varchar | 32 | YES | NULL | - | - | 音色ID |
| mem_model_id | varchar | 32 | YES | NULL | - | - | 记忆模型ID |
| intent_model_id | varchar | 32 | YES | NULL | - | - | 意图识别模型ID |
| system_prompt | text | - | YES | NULL | - | - | 角色设定Prompt |
| lang_code | varchar | 10 | YES | NULL | - | - | 语言编码 |
| language | varchar | 10 | YES | NULL | - | - | 交互语种 |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_ai_agent_user_id`: 用户ID索引

**表关系**:
- `user_id` → `sys_user.id` (多对一)
- `asr_model_id` → `ai_model_config.id` (多对一)
- `vad_model_id` → `ai_model_config.id` (多对一)
- `llm_model_id` → `ai_model_config.id` (多对一)
- `slm_model_id` → `ai_model_config.id` (多对一)
- `tts_model_id` → `ai_model_config.id` (多对一)
- `tts_voice_id` → `ai_tts_voice.id` (多对一)
- `mem_model_id` → `ai_model_config.id` (多对一)
- `intent_model_id` → `ai_model_config.id` (多对一)

### 11. ai_agent_plugin_mapping - 智能体插件映射表

存储智能体与插件的关联关系及插件参数配置。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | bigint | - | NO | - | PK | AUTO | 主键ID（自增） |
| agent_id | varchar | 32 | NO | - | - | UK | 智能体ID |
| plugin_id | varchar | 32 | NO | - | - | UK | 插件ID（关联ai_model_provider） |
| param_info | json | - | NO | - | - | - | 插件参数信息（JSON格式） |

**索引说明**:
- `uk_agent_provider`: 智能体ID和插件ID的联合唯一索引

**表关系**:
- `agent_id` → `ai_agent.id` (多对一)
- `plugin_id` → `ai_model_provider.id` (多对一)

### 12. ai_agent_tag - 智能体标签表

存储智能体的分类标签。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 标签唯一标识 |
| tag_name | varchar | 64 | NO | - | - | UK | 标签名称 |
| sort | int | unsigned | YES | 0 | - | IDX | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |
| deleted | tinyint | - | YES | 0 | - | - | 删除标记（0正常/1删除） |

**索引说明**:
- `uk_tag_name`: 标签名称唯一索引
- `idx_sort`: 排序索引

### 13. ai_agent_tag_relation - 智能体标签关联表

存储智能体与标签的多对多关联关系。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 关联记录唯一标识 |
| agent_id | varchar | 32 | NO | - | - | IDX | 智能体ID |
| tag_id | varchar | 32 | NO | - | - | IDX | 标签ID |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `uk_agent_tag`: 智能体ID和标签ID的联合唯一索引
- `idx_agent_id`: 智能体ID索引
- `idx_tag_id`: 标签ID索引

**表关系**:
- `agent_id` → `ai_agent.id` (多对一)
- `tag_id` → `ai_agent_tag.id` (多对一)

### 14. ai_agent_context_provider - 智能体上下文源配置表

存储智能体的上下文数据源配置（如RAG知识库等）。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 配置唯一标识 |
| agent_id | varchar | 32 | NO | - | - | IDX | 智能体ID |
| context_providers | json | - | YES | NULL | - | - | 上下文源配置（JSON格式） |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_agent_id`: 智能体ID索引

**表关系**:
- `agent_id` → `ai_agent.id` (一对一或一对多)

### 15. ai_agent_correct_word_file - 替换词文件表

存储智能体的替换词配置文件，用于ASR后的文本纠错。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 文件唯一标识 |
| file_name | varchar | 256 | NO | - | - | - | 原始文件名 |
| word_count | int | - | NO | 0 | - | - | 替换词数量 |
| content | text | - | YES | NULL | - | - | 文件原始内容 |
| creator | bigint | - | YES | NULL | - | IDX | 创建者ID |
| created_at | datetime | - | YES | CURRENT_TIMESTAMP | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | CURRENT_TIMESTAMP | - | - | 更新时间 |

**索引说明**:
- `idx_creator`: 创建者ID索引

### 16. ai_agent_correct_word_item - 替换词词条表

存储替换词文件中的具体替换规则。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 词条唯一标识 |
| file_id | varchar | 32 | NO | - | - | IDX | 所属文件ID |
| source_word | varchar | 128 | NO | - | - | - | 原词（需要替换的词） |
| target_word | varchar | 128 | NO | - | - | - | 替换词（替换成的词） |

**索引说明**:
- `idx_file_id`: 文件ID索引

**表关系**:
- `file_id` → `ai_agent_correct_word_file.id` (多对一)

### 17. ai_agent_correct_word_mapping - 替换词文件关联表

存储智能体与替换词文件的关联关系。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 关联记录唯一标识 |
| agent_id | varchar | 32 | NO | - | - | IDX | 智能体ID |
| file_id | varchar | 32 | NO | - | - | IDX | 替换词文件ID |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | CURRENT_TIMESTAMP | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | CURRENT_TIMESTAMP | - | - | 更新时间 |

**索引说明**:
- `uk_agent_file`: 智能体ID和文件ID的联合唯一索引
- `idx_agent_id`: 智能体ID索引
- `idx_file_id`: 文件ID索引

**表关系**:
- `agent_id` → `ai_agent.id` (多对一)
- `file_id` → `ai_agent_correct_word_file.id` (多对一)

---

## 设备管理模块

### 18. ai_device - 设备信息表

存储ESP32设备的注册信息和配置。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 设备唯一标识 |
| user_id | bigint | - | YES | NULL | - | - | 关联用户ID |
| mac_address | varchar | 50 | YES | NULL | - | IDX | MAC地址 |
| last_connected_at | datetime | - | YES | NULL | - | - | 最后连接时间 |
| auto_update | tinyint | unsigned | YES | 0 | - | - | 自动更新开关（0关闭/1开启） |
| board | varchar | 50 | YES | NULL | - | - | 设备硬件型号 |
| alias | varchar | 64 | YES | NULL | - | - | 设备别名 |
| agent_id | varchar | 32 | YES | NULL | - | - | 关联的智能体ID |
| app_version | varchar | 20 | YES | NULL | - | - | 固件版本号 |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_ai_device_created_at`: MAC地址索引

**表关系**:
- `user_id` → `sys_user.id` (多对一)
- `agent_id` → `ai_agent.id` (多对一)

### 19. ai_ota - 固件信息表

存储设备的固件版本信息，用于OTA升级。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 固件唯一标识 |
| firmware_name | varchar | 100 | YES | NULL | - | - | 固件名称 |
| type | varchar | 50 | YES | NULL | - | - | 固件类型 |
| version | varchar | 50 | YES | NULL | - | - | 版本号 |
| size | bigint | - | YES | NULL | - | - | 文件大小（字节） |
| remark | varchar | 500 | YES | NULL | - | - | 备注说明 |
| firmware_path | varchar | 255 | YES | NULL | - | - | 固件文件路径 |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |

---

## 对话管理模块

### 20. ai_chat_history - 对话历史表（已废弃）

存储对话会话的基本信息（已被ai_agent_chat_history替代）。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 对话编号 |
| user_id | bigint | - | YES | NULL | - | - | 用户编号 |
| agent_id | varchar | 32 | YES | NULL | - | - | 聊天角色（智能体ID） |
| device_id | varchar | 32 | YES | NULL | - | - | 设备编号 |
| message_count | int | - | YES | NULL | - | - | 消息汇总数量 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

### 21. ai_chat_message - 对话信息表（已废弃）

存储具体的对话消息内容（已被ai_agent_chat_history替代）。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 对话记录唯一标识 |
| user_id | bigint | - | NO | - | - | IDX | 用户唯一标识 |
| chat_id | varchar | 64 | YES | NULL | - | IDX | 对话历史ID |
| role | enum | - | YES | NULL | - | - | 角色（user/assistant） |
| content | text | - | YES | NULL | - | - | 对话内容 |
| prompt_tokens | int | unsigned | YES | 0 | - | - | 提示Token数 |
| total_tokens | int | unsigned | YES | 0 | - | - | 总Token数 |
| completion_tokens | int | unsigned | YES | 0 | - | - | 完成Token数 |
| prompt_ms | int | unsigned | YES | 0 | - | - | 提示耗时（毫秒） |
| total_ms | int | unsigned | YES | 0 | - | - | 总耗时（毫秒） |
| completion_ms | int | unsigned | YES | 0 | - | - | 完成耗时（毫秒） |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | IDX | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| update_date | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_ai_chat_message_user_id_chat_id_role`: 用户ID、聊天会话ID和角色的联合索引
- `idx_ai_chat_message_created_at`: 创建时间索引

### 22. ai_agent_chat_history - 智能体聊天记录表

存储智能体的对话历史记录（当前使用的表）。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | bigint | - | NO | - | PK | AUTO | 主键ID（自增） |
| mac_address | varchar | 50 | YES | NULL | - | IDX | 设备MAC地址 |
| agent_id | varchar | 32 | YES | NULL | - | IDX | 智能体ID |
| session_id | varchar | 50 | YES | NULL | - | IDX | 会话ID |
| chat_type | tinyint | 3 | YES | NULL | - | - | 消息类型（1用户/2智能体） |
| content | varchar | 1024 | YES | NULL | - | - | 聊天内容 |
| audio_id | varchar | 32 | YES | NULL | - | - | 音频ID |
| created_at | datetime | 3 | NO | CURRENT_TIMESTAMP(3) | - | - | 创建时间 |
| updated_at | datetime | 3 | NO | CURRENT_TIMESTAMP(3) | - | - | 更新时间 |

**索引说明**:
- `idx_ai_agent_chat_history_mac`: MAC地址索引
- `idx_ai_agent_chat_history_session_id`: 会话ID索引
- `idx_ai_agent_chat_history_agent_id`: 智能体ID索引
- `idx_ai_agent_chat_history_agent_session_created`: 智能体ID、会话ID和创建时间的联合索引

**表关系**:
- `agent_id` → `ai_agent.id` (多对一)
- `audio_id` → `ai_agent_chat_audio.id` (多对一)

### 23. ai_agent_chat_audio - 智能体聊天音频数据表

存储对话过程中的音频数据。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 主键ID |
| audio | longblob | - | YES | NULL | - | - | 音频opus数据 |

### 24. ai_agent_chat_title - 智能体聊天标题表

存储对话会话的自动生成标题。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 主键ID |
| session_id | varchar | 255 | NO | - | - | IDX | 会话ID |
| title | varchar | 255 | YES | NULL | - | - | 聊天标题 |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `idx_session_id`: 会话ID索引

---

## 声纹与声音克隆模块

### 25. ai_voiceprint - 声纹识别表

存储用户的声音指纹数据，用于声纹识别。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 声纹唯一标识 |
| name | varchar | 64 | YES | NULL | - | - | 声纹名称 |
| user_id | bigint | - | YES | NULL | - | - | 用户ID |
| agent_id | varchar | 32 | YES | NULL | - | - | 关联智能体ID |
| agent_code | varchar | 36 | YES | NULL | - | - | 关联智能体编码 |
| agent_name | varchar | 36 | YES | NULL | - | - | 关联智能体名称 |
| description | varchar | 255 | YES | NULL | - | - | 声纹描述 |
| embedding | longtext | YES | NULL | - | - | 声纹特征向量（JSON数组格式） |
| memory | text | - | YES | NULL | - | - | 关联记忆数据 |
| sort | int | unsigned | YES | 0 | - | - | 排序权重 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |

### 26. ai_voice_clone - 声音克隆表

存储声音克隆的训练数据和状态。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 唯一标识 |
| name | varchar | 64 | YES | NULL | - | - | 声音名称 |
| model_id | varchar | 32 | YES | NULL | - | IDX | 模型ID |
| voice_id | varchar | 32 | YES | NULL | - | IDX | 声音ID |
| user_id | bigint | - | YES | NULL | - | IDX | 用户ID |
| voice | longblob | - | YES | NULL | - | - | 声音数据 |
| train_status | tinyint | 1 | YES | 0 | - | - | 训练状态（0待训练/1训练中/2训练成功/3训练失败） |
| train_error | varchar | 255 | YES | NULL | - | - | 训练错误原因 |
| creator | bigint | - | YES | NULL | - | - | 创建者ID |
| create_date | datetime | - | YES | NULL | - | - | 创建时间 |

**索引说明**:
- `idx_ai_voice_clone_user_id_model_id_train_status`: 模型ID、用户ID和训练状态的联合索引
- `idx_ai_voice_clone_voice_id`: 声音ID索引
- `idx_ai_voice_clone_user_id`: 用户ID索引
- `idx_ai_voice_clone_model_id_voice_id`: 模型ID和声音ID的联合索引

**表关系**:
- `user_id` → `sys_user.id` (多对一)

---

## 知识库管理模块

### 27. ai_rag_dataset - 知识库表

存储RAG（检索增强生成）知识库的配置信息。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 32 | NO | - | PK | - | 唯一标识 |
| dataset_id | varchar | 64 | NO | - | - | UK | 知识库ID（RAGFlow远程ID） |
| rag_model_id | varchar | 64 | YES | NULL | - | - | RAG模型配置ID |
| tenant_id | varchar | 32 | YES | NULL | - | - | 租户ID |
| name | varchar | 100 | NO | - | - | - | 知识库名称 |
| description | text | - | YES | NULL | - | - | 知识库描述 |
| avatar | text | - | YES | NULL | - | - | 知识库头像（Base64） |
| embedding_model | varchar | 50 | YES | NULL | - | - | 嵌入模型名称 |
| permission | varchar | 20 | YES | 'me' | - | - | 权限设置（me/team） |
| chunk_method | varchar | 50 | YES | NULL | - | - | 分块方法 |
| parser_config | text | - | YES | NULL | - | - | 解析器配置（JSON） |
| chunk_count | bigint | 20 | YES | 0 | - | - | 分块总数 |
| document_count | bigint | 20 | YES | 0 | - | - | 文档总数 |
| token_num | bigint | 20 | YES | 0 | - | - | 总Token数 |
| status | tinyint | 1 | YES | 1 | IDX | - | 状态（0停用/1启用） |
| creator | bigint | - | YES | NULL | - | IDX | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updater | bigint | - | YES | NULL | - | - | 更新者ID |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |

**索引说明**:
- `uk_dataset_id`: 知识库ID唯一索引
- `idx_ai_rag_dataset_status`: 状态索引
- `idx_ai_rag_dataset_creator`: 创建者索引
- `idx_ai_rag_dataset_created_at`: 创建时间索引

### 28. ai_rag_knowledge_document - 知识库文档表

存储知识库中的文档元信息（实际文档存储在RAGFlow）。

| 字段名 | 类型 | 长度 | 允许NULL | 默认值 | 键 | 索引 | 说明 |
|--------|------|------|----------|--------|-----|------|------|
| id | varchar | 36 | NO | - | PK | - | 本地唯一ID |
| dataset_id | varchar | 36 | NO | - | - | IDX | 知识库ID（关联ai_rag_dataset） |
| document_id | varchar | 64 | NO | - | - | UK | RAGFlow文档ID（远程ID） |
| name | varchar | 255 | YES | NULL | - | - | 文档名称 |
| size | bigint | 20 | YES | NULL | - | - | 文件大小（字节） |
| type | varchar | 20 | YES | NULL | - | - | 文件类型 |
| chunk_method | varchar | 50 | YES | NULL | - | - | 分块方法 |
| parser_config | text | - | YES | NULL | - | - | 解析配置（JSON） |
| status | varchar | 10 | YES | '1' | IDX | - | 可用状态（1启用/0禁用） |
| run | varchar | 32 | YES | 'UNSTART' | - | - | 运行状态（UNSTART/RUNNING/CANCEL/DONE/FAIL） |
| progress | double | - | YES | 0 | - | - | 解析进度（0.0~1.0） |
| thumbnail | mediumtext | - | YES | NULL | - | - | 缩略图（Base64或URL） |
| process_duration | double | - | YES | 0 | - | - | 解析耗时（秒） |
| meta_fields | text | - | YES | NULL | - | - | 自定义元数据（JSON） |
| source_type | varchar | 32 | YES | 'local' | - | - | 来源类型（local/s3/url等） |
| error | text | - | YES | NULL | - | - | 错误信息 |
| chunk_count | int | 11 | YES | 0 | - | - | 分块数量 |
| token_count | bigint | 20 | YES | 0 | - | - | Token数量 |
| enabled | tinyint | 1 | YES | 1 | - | - | 启用状态 |
| creator | bigint | 20 | YES | NULL | - | - | 创建者ID |
| created_at | datetime | - | YES | NULL | - | - | 创建时间 |
| updated_at | datetime | - | YES | NULL | - | - | 更新时间 |
| last_sync_at | datetime | - | YES | NULL | - | - | 最后同步时间 |

**索引说明**:
- `uk_doc_id`: RAGFlow文档ID唯一索引
- `idx_dataset_id`: 知识库ID索引
- `idx_status`: 状态索引

**表关系**:
- `dataset_id` → `ai_rag_dataset.id` (多对一)

---

## 表关系图

### 核心关系

```
系统用户 (sys_user)
    ├── 1:N → 用户Token (sys_user_token)
    ├── 1:N → 设备 (ai_device)
    ├── 1:N → 智能体 (ai_agent)
    ├── 1:N → 声纹 (ai_voiceprint)
    ├── 1:N → 声音克隆 (ai_voice_clone)
    └── 1:N → 知识库 (ai_rag_dataset)

模型供应器 (ai_model_provider)
    ├── 1:N → 模型配置 (ai_model_config)
    └── 1:N → 插件映射 (ai_agent_plugin_mapping)

模型配置 (ai_model_config)
    ├── 1:N → 智能体 (ai_agent) [通过多个模型ID字段]
    └── 1:N → TTS音色 (ai_tts_voice)

智能体 (ai_agent)
    ├── 1:N → 设备 (ai_device)
    ├── 1:N → 插件映射 (ai_agent_plugin_mapping)
    ├── 1:N → 标签关联 (ai_agent_tag_relation)
    ├── 1:1 → 上下文源 (ai_agent_context_provider)
    ├── 1:N → 替换词关联 (ai_agent_correct_word_mapping)
    └── 1:N → 聊天记录 (ai_agent_chat_history)

智能体标签 (ai_agent_tag)
    └── 1:N → 标签关联 (ai_agent_tag_relation)

替换词文件 (ai_agent_correct_word_file)
    ├── 1:N → 替换词词条 (ai_agent_correct_word_item)
    └── 1:N → 替换词关联 (ai_agent_correct_word_mapping)

知识库 (ai_rag_dataset)
    └── 1:N → 文档 (ai_rag_knowledge_document)
```

### 主要业务流程

#### 1. 设备注册与配置流程
```
sys_user → ai_device → ai_agent
                ↓
           ai_ota (固件升级)
```

#### 2. 语音对话流程
```
ai_device → ai_agent → ai_agent_chat_history
                    ↓
              ai_agent_chat_audio
                    ↓
              ai_agent_chat_title
```

#### 3. 智能体配置流程
```
ai_agent_template (模板)
        ↓
ai_agent (实例化)
        ↓
├── ai_agent_plugin_mapping (插件配置)
├── ai_agent_tag_relation (标签分类)
├── ai_agent_context_provider (上下文源)
└── ai_agent_correct_word_mapping (文本纠错)
```

#### 4. 知识库RAG流程
```
ai_rag_dataset (知识库)
        ↓
ai_rag_knowledge_document (文档)
        ↓
ai_agent_context_provider (配置到智能体)
```

#### 5. 声纹与声音克隆流程
```
sys_user → ai_voiceprint (声纹识别)
        ↓
ai_voice_clone (声音克隆)
```

---

## 数据字典

### 字段类型说明

| 类型 | 说明 | 示例 |
|------|------|------|
| varchar | 变长字符串 | 用户名、设备ID |
| text | 长文本 | Prompt、配置JSON |
| longtext | 超长文本 | 声纹特征向量 |
| bigint | 大整数 | 用户ID、主键ID |
| int | 整数 | 排序、数量 |
| tinyint | 小整数 | 状态、开关 |
| datetime | 日期时间 | 创建时间、更新时间 |
| json | JSON数据 | 配置参数、字段列表 |
| longblob | 二进制大对象 | 音频数据、声音数据 |
| enum | 枚举 | 角色（user/assistant） |

### 常用字段命名规范

| 前缀 | 说明 | 示例 |
|------|------|------|
| id | 主键ID | id |
| *_id | 外键ID | user_id, agent_id |
| *_code | 编码 | agent_code, model_code |
| *_name | 名称 | agent_name, model_name |
| *_type | 类型 | model_type, dict_type |
| *_status | 状态 | status, train_status |
| *_at | 时间戳 | created_at, updated_at |
| *_date | 日期 | create_date, update_date |
| is_* | 布尔标识 | is_default, is_enabled |

### 状态值说明

#### 通用状态 (status)
- 0: 停用/禁用
- 1: 启用/正常

#### 训练状态 (train_status)
- 0: 待训练
- 1: 训练中
- 2: 训练成功
- 3: 训练失败

#### 消息类型 (chat_type)
- 1: 用户消息
- 2: 智能体消息

#### 对话角色 (role)
- user: 用户
- assistant: 智能体

#### 运行状态 (run)
- UNSTART: 未开始
- RUNNING: 运行中
- CANCEL: 已取消
- DONE: 已完成
- FAIL: 失败

---

## 附录

### 迁移文件命名规范

格式: `YYYYMMDDHHMM.sql`

示例:
- `202503141335.sql` - 2025年3月14日13:35创建的迁移
- `202604211700.sql` - 2026年4月21日17:00创建的迁移

### 最佳实践

1. **表设计原则**:
   - 所有表都有主键
   - 关联字段建立索引
   - 经常查询的字段建立索引
   - 使用外键约束保持数据完整性

2. **字段类型选择**:
   - ID使用varchar(32)或bigint
   - 状态使用tinyint
   - 时间使用datetime
   - JSON配置使用json类型
   - 二进制数据使用longblob

3. **索引策略**:
   - 主键自动创建索引
   - 外键字段创建索引
   - 唯一字段创建唯一索引
   - 经常查询的字段创建普通索引
   - 联合查询创建联合索引

4. **数据迁移**:
   - 每次schema变更都创建新的changeset
   - 不要修改已有的changeset
   - 使用Liquibase的rollback功能

---

**文档版本**: 1.0
**最后更新**: 2026-04-29
**维护者**: 小智ESP32服务器开发团队
