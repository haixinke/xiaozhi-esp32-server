# 蛋宝宝后端服务 - 数据库表结构文档

> 本文档详细描述了 `manager-api` (Java Spring Boot) 后端服务的所有数据库表结构、字段说明以及表之间的关系。
> 内容由开发库 `egg_database` 的 `information_schema` 元数据导出，字段注释直接取自库表定义。

## 目录

- [数据库概述](#数据库概述)
- [系统管理模块](#系统管理模块)
- [AI 模型配置模块](#ai-模型配置模块)
- [智能体管理模块](#智能体管理模块)
- [设备管理模块](#设备管理模块)
- [对话管理模块](#对话管理模块)
- [声音克隆模块](#声音克隆模块)
- [知识库管理模块](#知识库管理模块)
- [微信用户与用户资料模块](#微信用户与用户资料模块)
- [宠物与伙伴模块](#宠物与伙伴模块)
- [故事引擎模块](#故事引擎模块)
- [星星罐 / 道具 / 订阅模块](#星星罐--道具--订阅模块)
- [游戏与听你说模块](#游戏与听你说模块)
- [小纸条模块](#小纸条模块)
- [邀请码模块](#邀请码模块)
- [支付模块](#支付模块)
- [AI 生图与反馈模块](#ai-生图与反馈模块)
- [记忆模块](#记忆模块)
- [NFC 模块](#nfc-模块)
- [表关系图](#表关系图)
- [数据字典](#数据字典)

---

## 数据库概述

- **数据库类型**: MySQL 8.0+（开发/测试环境实际为 OceanBase MySQL 兼容模式，端口 2881）
- **数据库名**: `egg_database`
- **字符集**: utf8mb4
- **迁移工具**: Liquibase
- **迁移文件位置**: `src/main/resources/db/changelog/`
- **主变更日志**: `db.changelog-master.yaml`

### 表统计

| 模块 | 表数量 | 说明 |
|------|--------|------|
| 系统管理 | 6 | 用户、Token、参数、字典、操作日志 |
| AI 模型配置 | 3 | 模型供应器、模型配置、TTS 音色 |
| 智能体管理 | 11 | 智能体、模板、快照、插件、标签、上下文、替换词、声纹 |
| 设备管理 | 3 | 设备信息、通讯录、固件升级 |
| 对话管理 | 3 | 聊天记录、音频、标题 |
| 声音克隆 | 1 | 声音克隆训练 |
| 知识库管理 | 2 | RAG 知识库、文档 |
| 微信用户与用户资料 | 2 | 微信绑定、用户资料 |
| 宠物与伙伴 | 8 | 宠物、孵化、收藏卡、明信片、故事状态、伴侣、小物件 |
| 故事引擎 | 4 | 大/小场景、动作、动作图片 |
| 星星罐 / 道具 / 订阅 | 9 | 星星账户与流水、道具 SKU 与库存、订阅 |
| 游戏与听你说 | 4 | 游戏配置、题库、答题记录 |
| 小纸条 | 2 | 每日内容、已打开记录 |
| 邀请码 | 2 | 邀请码主表、使用记录 |
| 支付 | 3 | 订单、退款、回调日志 |
| AI 生图与反馈 | 2 | 生图任务、用户反馈 |
| 记忆 | 1 | PowerMem 记忆表 |
| NFC | 11 | 产品类型、批次、Scheme 生成、写卡、领取、日志 |
| **总计** | **77** | 不含 Liquibase 自带的 `databasechangelog` / `databasechangeloglock` |

### 与旧版文档的差异

- 已删除：`ai_chat_history`、`ai_chat_message`（废弃表已从库中移除）。
- 重命名：`ai_voiceprint` → `ai_agent_voice_print`（智能体声纹表）。
- 新增：宠物、故事引擎、星星罐、道具、订阅、游戏、听你说、小纸条、邀请码、支付、生图、反馈、记忆、NFC 等模块，详见下文。

---## 系统管理模块

### 1. sys_user - 系统用户表

> 系统用户

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | id |
| username | varchar(50) | NO |  | UK | 用户名 |
| password | varchar(100) | YES |  |  | 密码 |
| super_admin | tinyint(3) unsigned | YES |  |  | 超级管理员   0：否   1：是 |
| status | tinyint(4) | YES |  |  | 状态  0：停用   1：正常 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| creator | bigint(20) | YES |  |  | 创建者 |
| update_date | datetime | YES |  |  | 更新时间 |
| role | varchar(20) | NO | admin |  | 角色：admin=管理员 operator=运营者 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_username`（唯一）: username

### 2. sys_user_token - 系统用户 Token 表

> 系统用户Token

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | id |
| user_id | bigint(20) | NO |  | UK | 用户id |
| token | varchar(100) | NO |  | UK | 用户token |
| expire_date | datetime | YES |  |  | 过期时间 |
| update_date | datetime | YES |  |  | 更新时间 |
| create_date | datetime | YES |  |  | 创建时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `token`（唯一）: token
- `user_id`（唯一）: user_id

### 3. sys_params - 系统参数配置表

> 参数管理

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | id |
| param_code | varchar(100) | YES |  | UK | 参数编码 |
| param_value | varchar(2000) | YES |  |  | 参数值 |
| value_type | varchar(20) | YES | string |  | 值类型：string-字符串，number-数字，boolean-布尔，array-数组 |
| param_type | tinyint(3) unsigned | YES | 1 |  | 类型   0：系统参数   1：非系统参数 |
| remark | varchar(200) | YES |  |  | 备注 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_param_code`（唯一）: param_code

### 4. sys_dict_type - 字典类型表

> 字典类型

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | id |
| dict_type | varchar(100) | NO |  | UK | 字典类型 |
| dict_name | varchar(255) | NO |  |  | 字典名称 |
| remark | varchar(255) | YES |  |  | 备注 |
| sort | int(10) unsigned | YES |  |  | 排序 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `dict_type`（唯一）: dict_type
- `PRIMARY`（唯一）: id

### 5. sys_dict_data - 字典数据表

> 字典数据

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | id |
| dict_type_id | bigint(20) | NO |  | IDX | 字典类型ID |
| dict_label | varchar(255) | NO |  |  | 字典标签 |
| dict_value | varchar(255) | YES |  |  | 字典值 |
| remark | varchar(255) | YES |  |  | 备注 |
| sort | int(10) unsigned | YES |  | IDX | 排序 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_sort`（普通）: sort
- `PRIMARY`（唯一）: id
- `uk_dict_type_value`（唯一）: dict_type_id,dict_value

### 6. sys_operation_log - 通用操作日志表

> 通用操作日志表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 |
| user_id | bigint(20) | YES |  | IDX | 操作人用户ID（系统/匿名操作可为空） |
| username | varchar(64) | YES |  |  | 操作人用户名（冗余，便于查询展示） |
| operation_type | varchar(64) | NO |  | IDX | 操作类型，如 CHAT_HISTORY_EXPORT |
| operation_desc | varchar(255) | YES |  |  | 操作描述（人类可读） |
| request_uri | varchar(255) | YES |  |  | 请求路径（注解方式自动填充） |
| request_method | varchar(8) | YES |  |  | 请求方法 GET/POST（注解方式自动填充） |
| ip | varchar(64) | YES |  |  | 操作IP |
| detail | text | YES |  |  | 业务上下文JSON（不含敏感信息） |
| status | tinyint(4) | NO | 1 |  | 状态：1成功 0失败 |
| error_msg | varchar(500) | YES |  |  | 失败原因 |
| create_date | datetime | NO |  |  | 操作时间 |

**索引**:
- `idx_type_create`（普通）: operation_type,create_date
- `idx_user_create`（普通）: user_id,create_date
- `PRIMARY`（唯一）: id


## AI 模型配置模块

### 7. ai_model_provider - 模型供应器表

> 模型配置表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键 |
| model_type | varchar(20) | YES |  | IDX | 模型类型(Memory/ASR/VAD/LLM/TTS) |
| provider_code | varchar(50) | YES |  |  | 供应器类型 |
| name | varchar(50) | YES |  |  | 供应器名称 |
| fields | json | YES |  |  | 供应器字段列表(JSON格式) |
| sort | int(10) unsigned | YES | 0 |  | 排序 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_model_provider_model_type`（普通）: model_type
- `PRIMARY`（唯一）: id

### 8. ai_model_config - 模型配置表

> 模型配置表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键 |
| model_type | varchar(20) | YES |  | IDX | 模型类型(Memory/ASR/VAD/LLM/TTS) |
| model_code | varchar(50) | YES |  |  | 模型编码(如AliLLM、DoubaoTTS) |
| model_name | varchar(50) | YES |  |  | 模型名称 |
| is_default | tinyint(1) | YES | 0 |  | 是否默认配置(0否 1是) |
| is_enabled | tinyint(1) | YES | 0 |  | 是否启用 |
| config_json | json | YES |  |  | 模型配置(JSON格式) |
| doc_link | varchar(200) | YES |  |  | 官方文档链接 |
| remark | text | YES |  |  | 备注 |
| sort | int(10) unsigned | YES | 0 |  | 排序 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_model_config_model_type`（普通）: model_type
- `PRIMARY`（唯一）: id

### 9. ai_tts_voice - TTS 音色表

> TTS 音色表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键 |
| tts_model_id | varchar(32) | YES |  | IDX | 对应 TTS 模型主键 |
| name | varchar(20) | YES |  |  | 音色名称 |
| tts_voice | varchar(50) | YES |  |  | 音色编码 |
| languages | varchar(50) | YES |  |  | 语言 |
| voice_demo | varchar(500) | YES |  |  | 音色 Demo |
| remark | varchar(255) | YES |  |  | 备注 |
| reference_audio | varchar(500) | YES |  |  | 参考音频路径 |
| reference_text | varchar(500) | YES |  |  | 参考文本 |
| sort | int(10) unsigned | YES | 0 |  | 排序 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_tts_voice_tts_model_id`（普通）: tts_model_id
- `PRIMARY`（唯一）: id


## 智能体管理模块

### 10. ai_agent_template - 智能体配置模板表

> 智能体配置模板表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 智能体唯一标识 |
| agent_code | varchar(36) | YES |  |  | 智能体编码 |
| agent_name | varchar(64) | YES |  |  | 智能体名称 |
| asr_model_id | varchar(32) | YES |  |  | 语音识别模型标识 |
| vad_model_id | varchar(64) | YES |  |  | 语音活动检测标识 |
| llm_model_id | varchar(32) | YES |  |  | 大语言模型标识 |
| vllm_model_id | varchar(32) | YES | VLLM_ChatGLMVLLM |  | 视觉模型标识 |
| tts_model_id | varchar(32) | YES |  |  | 语音合成模型标识 |
| tts_voice_id | varchar(32) | YES |  |  | 音色标识 |
| tts_language | varchar(50) | YES |  |  | 音色语言 |
| tts_volume | int(11) | YES |  |  | TTS音量 |
| tts_rate | int(11) | YES |  |  | TTS语速 |
| tts_pitch | int(11) | YES |  |  | TTS音调 |
| mem_model_id | varchar(32) | YES |  |  | 记忆模型标识 |
| intent_model_id | varchar(32) | YES |  |  | 意图模型标识 |
| system_prompt | text | YES |  |  | 角色设定参数 |
| summary_memory | text | YES |  |  | 总结记忆 |
| chat_history_conf | tinyint(4) | NO | 0 |  | 聊天记录配置（0不记录 1仅记录文本 2记录文本和语音） |
| lang_code | varchar(10) | YES |  |  | 语言编码 |
| language | varchar(10) | YES |  |  | 交互语种 |
| sort | int(10) unsigned | YES | 0 |  | 排序权重 |
| creator | bigint(20) | YES |  |  | 创建者 ID |
| created_at | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 ID |
| updated_at | datetime | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id

### 11. ai_agent - 智能体配置表

> 智能体配置表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 智能体唯一标识 |
| user_id | bigint(20) | YES |  | IDX | 所属用户 ID |
| agent_code | varchar(36) | YES |  |  | 智能体编码 |
| agent_name | varchar(64) | YES |  |  | 智能体名称 |
| asr_model_id | varchar(32) | YES |  |  | 语音识别模型标识 |
| vad_model_id | varchar(64) | YES |  |  | 语音活动检测标识 |
| llm_model_id | varchar(32) | YES |  |  | 大语言模型标识 |
| slm_model_id | varchar(255) | YES |  |  | 小模型ID |
| vllm_model_id | varchar(32) | YES | VLLM_ChatGLMVLLM |  | 视觉模型标识 |
| tts_model_id | varchar(32) | YES |  |  | 语音合成模型标识 |
| tts_voice_id | varchar(32) | YES |  |  | 音色标识 |
| tts_language | varchar(50) | YES |  |  | 音色语言 |
| tts_volume | int(11) | YES |  |  | TTS音量 |
| tts_rate | int(11) | YES |  |  | TTS语速 |
| tts_pitch | int(11) | YES |  |  | TTS音调 |
| mem_model_id | varchar(32) | YES |  |  | 记忆模型标识 |
| intent_model_id | varchar(32) | YES |  |  | 意图模型标识 |
| system_prompt | text | YES |  |  | 角色设定参数 |
| summary_memory | text | YES |  |  | 总结记忆 |
| chat_history_conf | tinyint(4) | NO | 0 |  | 聊天记录配置（0不记录 1仅记录文本 2记录文本和语音） |
| lang_code | varchar(10) | YES |  |  | 语言编码 |
| language | varchar(10) | YES |  |  | 交互语种 |
| sort | int(10) unsigned | YES | 0 |  | 排序权重 |
| creator | bigint(20) | YES |  |  | 创建者 ID |
| created_at | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 ID |
| updated_at | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_agent_user_id`（普通）: user_id
- `PRIMARY`（唯一）: id

### 12. ai_agent_snapshot - 智能体配置快照表

> 智能体配置快照表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 快照ID |
| agent_id | varchar(32) | NO |  | IDX | 智能体ID |
| user_id | bigint(20) | YES |  | IDX | 所属用户ID |
| version_no | int(10) unsigned | NO |  |  | 版本号 |
| snapshot_data | json | NO |  |  | 快照数据 |
| changed_fields | json | YES |  |  | 变更字段 |
| source | varchar(32) | YES | config |  | 快照来源 |
| restore_from_snapshot_id | varchar(32) | YES |  |  | 恢复来源快照ID |
| restore_from_version_no | int(10) unsigned | YES |  |  | 恢复来源版本号 |
| creator | bigint(20) | YES |  |  | 创建者 |
| created_at | datetime | YES | CURRENT_TIMESTAMP |  | 创建时间 |
| redaction_version | tinyint(3) unsigned | NO | 0 | IDX | 快照脱敏规则版本 |

**索引**:
- `idx_agent_created_at`（普通）: agent_id,created_at
- `idx_snapshot_redaction_version_id`（普通）: redaction_version,id
- `idx_snapshot_user_created_at`（普通）: user_id,created_at
- `PRIMARY`（唯一）: id
- `uk_agent_version`（唯一）: agent_id,version_no

### 13. ai_agent_plugin_mapping - 智能体插件映射表

> Agent与插件的唯一映射表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 |
| agent_id | varchar(32) | NO |  | IDX | 智能体ID |
| plugin_id | varchar(32) | NO |  |  | 插件ID |
| param_info | json | NO |  |  | 参数信息 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_agent_provider`（唯一）: agent_id,plugin_id

### 14. ai_agent_tag - 智能体标签表

> 智能体标签表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键 |
| tag_name | varchar(64) | NO |  | UK | 标签名称 |
| sort | int(10) unsigned | YES | 0 | IDX | 排序 |
| creator | bigint(20) | YES |  |  | 创建者 |
| created_at | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| updated_at | datetime | YES |  |  | 更新时间 |
| deleted | tinyint(4) | YES | 0 |  | 删除标记 |

**索引**:
- `idx_sort`（普通）: sort
- `PRIMARY`（唯一）: id
- `uk_tag_name`（唯一）: tag_name

### 15. ai_agent_tag_relation - 智能体标签关联表

> 智能体标签关联表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键 |
| agent_id | varchar(32) | NO |  | IDX | 智能体ID |
| tag_id | varchar(32) | NO |  | IDX | 标签ID |
| creator | bigint(20) | YES |  |  | 创建者 |
| created_at | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| updated_at | datetime | YES |  |  | 更新时间 |
| sort | int(10) unsigned | YES | 0 |  | 排序 |

**索引**:
- `idx_agent_id`（普通）: agent_id
- `idx_tag_id`（普通）: tag_id
- `PRIMARY`（唯一）: id
- `uk_agent_tag`（唯一）: agent_id,tag_id

### 16. ai_agent_context_provider - 智能体上下文源配置表

> 智能体上下文源配置表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键 |
| agent_id | varchar(32) | NO |  | IDX | 智能体ID |
| context_providers | json | YES |  |  | 上下文源配置 |
| creator | bigint(20) | YES |  |  | 创建者 |
| created_at | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| updated_at | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_agent_id`（普通）: agent_id
- `PRIMARY`（唯一）: id

### 17. ai_agent_correct_word_file - 替换词文件表

> 替换词文件

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 替换词文件ID |
| file_name | varchar(256) | NO |  |  | 原始文件名 |
| word_count | int(11) | NO | 0 |  | 替换词数量 |
| content | text | YES |  |  | 文件原始内容 |
| creator | bigint(20) | YES |  | IDX | 创建者 |
| created_at | datetime | YES | CURRENT_TIMESTAMP |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| updated_at | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

**索引**:
- `idx_creator`（普通）: creator
- `PRIMARY`（唯一）: id

### 18. ai_agent_correct_word_item - 替换词词条表

> 替换词词条

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 词条ID |
| file_id | varchar(32) | NO |  | IDX | 所属文件ID |
| source_word | varchar(128) | NO |  |  | 原词 |
| target_word | varchar(128) | NO |  |  | 替换词 |

**索引**:
- `idx_file_id`（普通）: file_id
- `PRIMARY`（唯一）: id

### 19. ai_agent_correct_word_mapping - 替换词文件关联表

> 智能体替换词文件关联

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键 |
| agent_id | varchar(32) | NO |  | IDX | 智能体ID |
| file_id | varchar(32) | NO |  | IDX | 替换词文件ID |
| creator | bigint(20) | YES |  |  | 创建者 |
| created_at | datetime | YES | CURRENT_TIMESTAMP |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| updated_at | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

**索引**:
- `idx_agent_id`（普通）: agent_id
- `idx_file_id`（普通）: file_id
- `PRIMARY`（唯一）: id
- `uk_agent_file`（唯一）: agent_id,file_id

### 20. ai_agent_voice_print - 智能体声纹表

> 智能体声纹表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 声纹ID |
| agent_id | varchar(32) | NO |  |  | 关联的智能体ID |
| source_name | varchar(50) | NO |  |  | 声纹来源的人的姓名 |
| introduce | varchar(200) | YES |  |  | 描述声纹来源的这个人 |
| create_date | datetime | YES |  |  | 创建时间 |
| creator | bigint(20) | YES |  |  | 创建者 |
| update_date | datetime | YES |  |  | 修改时间 |
| updater | bigint(20) | YES |  |  | 修改者 |
| audio_id | varchar(32) | NO |  |  | 音频ID |

**索引**:
- `PRIMARY`（唯一）: id


## 设备管理模块

### 21. ai_device - 设备信息表

> 设备信息表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 设备唯一标识 |
| user_id | bigint(20) | YES |  |  | 关联用户 ID |
| mac_address | varchar(50) | YES |  | IDX | MAC 地址 |
| last_connected_at | datetime | YES |  |  | 最后连接时间 |
| auto_update | tinyint(3) unsigned | YES | 0 |  | 自动更新开关(0 关闭/1 开启) |
| board | varchar(50) | YES |  |  | 设备硬件型号 |
| alias | varchar(64) | YES |  |  | 设备别名 |
| agent_id | varchar(32) | YES |  |  | 智能体 ID |
| app_version | varchar(20) | YES |  |  | 固件版本号 |
| sort | int(10) unsigned | YES | 0 |  | 排序 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_device_created_at`（普通）: mac_address
- `PRIMARY`（唯一）: id

### 22. ai_device_address_book - 设备通讯录表

> 设备通讯录表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| mac_address | varchar(64) | NO |  | PK | 本设备MAC地址 |
| target_mac | varchar(64) | NO |  | PK | 对方设备MAC地址 |
| alias | varchar(64) | YES |  |  | 别名 |
| has_permission | tinyint(1) | YES | 1 |  | 是否有权限呼叫 |
| creator | bigint(20) | YES |  |  | 创建人 |
| create_date | datetime | YES | CURRENT_TIMESTAMP |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新人 |
| update_date | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: mac_address,target_mac

### 23. ai_ota - 固件信息表

> 固件信息表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | ID |
| firmware_name | varchar(100) | YES |  |  | 固件名称 |
| type | varchar(50) | YES |  |  | 固件类型 |
| version | varchar(50) | YES |  |  | 版本号 |
| size | bigint(20) | YES |  |  | 文件大小(字节) |
| remark | varchar(500) | YES |  |  | 备注/说明 |
| firmware_path | varchar(255) | YES |  |  | 固件路径 |
| sort | int(10) unsigned | YES | 0 |  | 排序 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |

**索引**:
- `PRIMARY`（唯一）: id


## 对话管理模块

### 24. ai_agent_chat_history - 智能体聊天记录表

> 智能体聊天记录表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID |
| mac_address | varchar(50) | YES |  | IDX | MAC地址 |
| agent_id | varchar(32) | YES |  | IDX | 智能体id |
| session_id | varchar(50) | YES |  | IDX | 会话ID |
| chat_type | tinyint(3) | YES |  | IDX | 消息类型: 1-用户, 2-智能体 |
| content | text | YES |  |  | 聊天内容 |
| audio_id | varchar(32) | YES |  | IDX | 音频ID |
| created_at | datetime(3) | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime(3) | NO | CURRENT_TIMESTAMP |  | 更新时间 |

**索引**:
- `idx_ai_agent_chat_history_agent_id`（普通）: agent_id
- `idx_ai_agent_chat_history_agent_session_created`（普通）: agent_id,session_id,created_at
- `idx_ai_agent_chat_history_audio_id`（普通）: audio_id
- `idx_ai_agent_chat_history_mac`（普通）: mac_address
- `idx_ai_agent_chat_history_session_id`（普通）: session_id
- `idx_ai_agent_chat_history_type_created`（普通）: chat_type,created_at
- `PRIMARY`（唯一）: id

### 25. ai_agent_chat_audio - 智能体聊天音频数据表

> 智能体聊天音频数据表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键ID |
| audio | longblob | YES |  |  | 音频opus数据 |
| oss_key | varchar(256) | YES |  |  | OSS对象存储路径 |

**索引**:
- `PRIMARY`（唯一）: id

### 26. ai_agent_chat_title - 智能体聊天标题表

> 智能体聊天标题表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键ID |
| session_id | varchar(255) | NO |  | IDX | 会话ID |
| title | varchar(255) | YES |  |  | 聊天标题 |
| created_at | datetime | YES |  |  | 创建时间 |
| updated_at | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_session_id`（普通）: session_id
- `PRIMARY`（唯一）: id


## 声音克隆模块

### 27. ai_voice_clone - 声音克隆表

> 声音克隆表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 唯一标识 |
| name | varchar(64) | YES |  |  | 声音名称 |
| model_id | varchar(32) | YES |  | IDX | 模型id |
| voice_id | varchar(32) | YES |  | IDX | 声音id |
| languages | varchar(50) | YES |  |  | 语言 |
| user_id | bigint(20) | YES |  | IDX | 用户 ID（关联用户表） |
| voice | longblob | YES |  |  | 声音 |
| train_status | tinyint(1) | YES | 0 |  | 训练状态：0待训练 1训练中 2训练成功 3训练失败 |
| train_error | varchar(255) | YES |  |  | 训练错误原因 |
| creator | bigint(20) | YES |  |  | 创建者 ID |
| create_date | datetime | YES |  |  | 创建时间 |

**索引**:
- `idx_ai_voice_clone_model_id_voice_id`（普通）: model_id,voice_id
- `idx_ai_voice_clone_user_id`（普通）: user_id
- `idx_ai_voice_clone_user_id_model_id_train_status`（普通）: model_id,user_id,train_status
- `idx_ai_voice_clone_voice_id`（普通）: voice_id
- `PRIMARY`（唯一）: id


## 知识库管理模块

### 28. ai_rag_dataset - 知识库表

> 知识库表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 唯一标识 |
| dataset_id | varchar(64) | NO |  | UK | 知识库ID |
| rag_model_id | varchar(64) | YES |  |  | RAG模型配置ID |
| name | varchar(100) | NO |  |  | 知识库名称 |
| description | text | YES |  |  | 知识库描述 |
| status | tinyint(1) | YES | 1 | IDX | 状态：0停用 1启用 |
| creator | bigint(20) | YES |  | IDX | 创建者 |
| created_at | datetime | YES |  | IDX | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| updated_at | datetime | YES |  |  | 更新时间 |
| tenant_id | varchar(32) | YES |  |  | 租户ID |
| avatar | text | YES |  |  | 知识库头像 (Base64) |
| embedding_model | varchar(50) | YES |  |  | 嵌入模型名称 |
| permission | varchar(20) | YES | me |  | 权限设置：me/team |
| chunk_method | varchar(50) | YES |  |  | 分块方法 |
| parser_config | text | YES |  |  | 解析器配置 (JSON) |
| chunk_count | bigint(20) | YES | 0 |  | 分块总数 |
| document_count | bigint(20) | YES | 0 |  | 文档总数 |
| token_num | bigint(20) | YES | 0 |  | 总 Token 数 |

**索引**:
- `idx_ai_rag_dataset_created_at`（普通）: created_at
- `idx_ai_rag_dataset_creator`（普通）: creator
- `idx_ai_rag_dataset_status`（普通）: status
- `PRIMARY`（唯一）: id
- `uk_dataset_id`（唯一）: dataset_id

### 29. ai_rag_knowledge_document - 知识库文档表

> 知识库文档表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(36) | NO |  | PK | 本地唯一ID |
| dataset_id | varchar(36) | NO |  | IDX | 知识库ID (关联 ai_rag_dataset) |
| document_id | varchar(64) | NO |  | UK | RAGFlow文档ID (远程ID) |
| name | varchar(255) | YES |  |  | 文档名称 |
| size | bigint(20) | YES |  |  | 文件大小(Bytes) |
| type | varchar(20) | YES |  |  | 文件类型 |
| chunk_method | varchar(50) | YES |  |  | 分块方法 |
| parser_config | text | YES |  |  | 解析配置(JSON) |
| status | varchar(10) | YES | 1 | IDX | 可用状态 (1:启用 0:禁用) |
| run | varchar(32) | YES | UNSTART |  | 运行状态 (UNSTART/RUNNING/CANCEL/DONE/FAIL) |
| progress | double | YES | 0 |  | 解析进度 (0.0 ~ 1.0) |
| thumbnail | mediumtext | YES |  |  | 缩略图 (Base64 或 URL) |
| process_duration | double | YES | 0 |  | 解析耗时 (单位: 秒) |
| meta_fields | text | YES |  |  | 自定义元数据 (JSON) |
| source_type | varchar(32) | YES | local |  | 来源类型 (local, s3, url 等) |
| error | text | YES |  |  | 错误信息 |
| chunk_count | int(11) | YES | 0 |  | 分块数量 |
| token_count | bigint(20) | YES | 0 |  | Token数量 |
| enabled | tinyint(1) | YES | 1 |  | 启用状态 |
| creator | bigint(20) | YES |  |  | 创建者 |
| created_at | datetime | YES |  |  | 创建时间 |
| updated_at | datetime | YES |  |  | 更新时间 |
| last_sync_at | datetime | YES |  |  | 最后同步时间 |

**索引**:
- `idx_dataset_id`（普通）: dataset_id
- `idx_status`（普通）: status
- `PRIMARY`（唯一）: id
- `uk_doc_id`（唯一）: document_id


## 微信用户与用户资料模块

### 30. ai_wechat_user - 微信小程序用户绑定表

> 微信小程序用户绑定表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID |
| openid | varchar(64) | NO |  | UK | 微信openid |
| user_id | bigint(20) | NO |  | IDX | 关联sys_user.id |
| session_key | varchar(128) | YES |  |  | 微信会话密钥 |
| nickname | varchar(64) | YES |  |  | 微信昵称 |
| avatar_url | varchar(512) | YES |  |  | 微信头像URL |
| phone | varchar(20) | YES |  | IDX | 用户授权手机号 |
| gender | varchar(8) | YES |  |  | 性别: MALE/FEMALE/OTHER |
| birthday | date | YES |  |  | 生日 |
| city | varchar(32) | YES |  |  | 常驻城市 |
| mbti | varchar(4) | YES |  |  | MBTI类型 |
| age_range | varchar(50) | YES |  |  | 年龄区间（字典 EGG_AGE_RANGE 的 dict_value） |
| create_date | datetime | YES | CURRENT_TIMESTAMP |  | 创建时间 |
| update_date | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

**索引**:
- `idx_openid`（普通）: openid
- `idx_phone`（普通）: phone
- `idx_user_id`（普通）: user_id
- `openid`（唯一）: openid
- `PRIMARY`（唯一）: id

### 31. user_profiles - 用户资料表

> 用户资料表，按 user_id 存画像字段（性别、生日、城市、MBTI 等）。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID |
| user_id | varchar(128) | YES |  | IDX | 用户ID |
| profile_content | longtext | YES |  |  | 画像内容 |
| topics | json | YES |  |  | 主题标签（JSON格式） |
| created_at | varchar(128) | YES |  |  | 创建时间（ISO 8601格式字符串） |
| updated_at | varchar(128) | YES |  |  | 更新时间（ISO 8601格式字符串） |

**索引**:
- `idx_user_id`（普通）: user_id
- `PRIMARY`（唯一）: id


## 宠物与伙伴模块

### 32. ai_pet - AI 宠物表

> AI宠物表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 宠物唯一标识 |
| user_id | bigint(20) | NO |  | IDX | 归属用户ID |
| device_id | varchar(32) | YES |  | UK | 关联设备ID(领养阶段为空,破壳时回填) |
| nickname | varchar(50) | YES |  |  | 昵称 |
| birth_date | datetime | YES |  |  | 出生日期时间(领养阶段为空,破壳时回填) |
| bazi | json | YES |  |  | 八字 |
| wuxing | json | YES |  |  | 五行 |
| zodiac | varchar(20) | YES |  |  | 星座英文编码 |
| mbti | varchar(4) | YES |  |  | MBTI人格 |
| personality | varchar(500) | YES |  |  | 性格描述 |
| today_mood | varchar(20) | YES |  |  | 今日心情 |
| hatch_status | varchar(20) | NO | HATCHED |  | 孵化状态: EGG-孵化中, HATCHED-已破壳 |
| hatch_start_time | datetime | YES |  |  | 孵化开始时间(完成首个修炼任务时刻,7天倒计时起点) |
| expected_hatch_time | datetime | YES |  |  | 预计破壳时间(=孵化开始时间+周期-已加速时长) |
| hatched_at | datetime | YES |  |  | 实际破壳时间(分享卡片生日) |
| accelerated_minutes | int(11) | NO | 0 |  | 累计已加速孵化时长(分钟) |
| avatar_url | varchar(500) | YES |  |  | IP形象照片/头像URL(破壳时AI生成) |
| scene_url | varchar(500) | YES |  |  | 场景图URL |
| prototype | varchar(50) | YES |  |  | IP形象原型(锦鲤/玉兔等) |
| gender | varchar(10) | YES |  |  | 性别: MALE/FEMALE/OTHER |
| blood_type | varchar(10) | YES |  |  | 血型(随机分配) |
| personality_brief | varchar(50) | YES |  |  | 性格简介(20字以内,卡片展示用),与personality(系统提示词)区分 |
| today_mood_date | date | YES |  |  | 今日心情对应日期(跨天重算判断) |
| today_mood_sentence | varchar(200) | YES |  |  | 今日心情一句话 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |
| deleted_at | bigint(20) | NO | 0 |  | 逻辑删除时间戳(epoch毫秒): 0=未删除 |
| source | varchar(32) | NO |  |  | 创建来源: INVITE_CODE-邀请码(激活码)领养, NFC-NFC触碰领取, DEVICE_BIRTH-设备出生 |

**索引**:
- `idx_ai_pet_user_id`（普通）: user_id
- `PRIMARY`（唯一）: id
- `uk_ai_pet_device_id`（唯一）: device_id
- `uk_ai_pet_user_prototype_deleted`（唯一）: user_id,prototype,deleted_at

### 33. ai_pet_hatch_action - 蛋宝宝孵化修炼动作明细表

> 蛋宝宝孵化修炼动作明细

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| pet_id | varchar(32) | NO |  | IDX | 宠物ID |
| action_type | varchar(20) | NO |  |  | NICKNAME/CUDDLE/WISH/LESSON/DOODLE |
| payload | text | YES |  |  | 动作载荷JSON |
| action_date | date | NO |  |  | 动作日期(Asia/Shanghai,幂等用) |
| accelerated_minutes | int(11) | NO | 0 |  | 本次加速分钟数 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |

**索引**:
- `idx_pet_id`（普通）: pet_id
- `PRIMARY`（唯一）: id
- `uk_pet_action_date`（唯一）: pet_id,action_type,action_date

### 34. ai_pet_collection_card - 宠物收藏卡表

> 宠物收藏卡表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 收藏卡唯一标识 |
| pet_id | varchar(32) | NO |  | IDX | 关联宠物ID |
| image_url | varchar(1024) | NO |  |  | 收藏卡图片URL |
| brief | varchar(100) | YES |  |  | 一句话简介 |
| source | varchar(50) | NO | HATCH |  | 来源类型: HATCH-破壳首卡 |
| sort_order | int(11) | NO | 0 |  | 排序序号(0=最先获取) |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_pet_card_sort`（普通）: pet_id,sort_order
- `PRIMARY`（唯一）: id
- `uk_pet_card_image`（唯一）: pet_id,image_url

### 35. ai_pet_postcard - 宠物明信片表

> 宠物明信片表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 明信片ID(内部UUID) |
| share_id | varchar(32) | NO |  | UK | 对外分享随机串(不可枚举) |
| pet_id | varchar(32) | NO |  | IDX | 关联宠物ID |
| user_id | bigint(20) | NO |  | IDX | 收件用户ID |
| image_url | varchar(512) | NO |  |  | 明信片照片OSS URL(已过审) |
| caption | varchar(255) | NO |  |  | 明信片文字(LLM生成) |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_postcard_pet`（普通）: pet_id
- `idx_postcard_user_create`（普通）: user_id,create_date
- `PRIMARY`（唯一）: id
- `uk_postcard_share_id`（唯一）: share_id

### 36. ai_pet_story_state - 宠物原型共享故事当前状态表

> 宠物原型共享故事当前状态

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键UUID |
| pet_prototype | varchar(20) | NO |  | UK | 宠物原型：锦鲤/玉兔 |
| runtime_status | varchar(20) | NO | UNINITIALIZED |  | UNINITIALIZED/ACTIVE |
| big_scene_id | varchar(32) | YES |  |  | 大场景ID |
| big_scene_name | varchar(64) | YES |  |  | 大场景名称快照 |
| small_scene_id | varchar(32) | YES |  |  | 小场景ID |
| small_scene_name | varchar(100) | YES |  |  | 小场景名称快照 |
| action_id | varchar(32) | YES |  |  | 动作ID |
| action_name | varchar(100) | YES |  |  | 动作名称快照 |
| action_image_id | varchar(32) | YES |  |  | 选中的动作图片ID |
| weight_period | varchar(20) | YES |  |  | NIGHT/MORNING/AFTERNOON/EVENING |
| image_time_of_day | varchar(20) | YES |  |  | DAY/SUNSET/NIGHT |
| image_url | varchar(512) | YES |  |  | 动作图片URL快照 |
| tag_image_url | varchar(512) | YES |  |  | 窗户标签图URL快照(选中动作中tag=窗户的图片,取当前时段首张) |
| tag_image_caption | varchar(1000) | YES |  |  | 窗户标签图配文快照(选中动作中tag=窗户的图片captions,\|分隔) |
| caption | varchar(1000) | YES |  |  | 配文快照（多条用\|分隔，客户端拆分随机展示），可空 |
| duration_hours | int(11) | YES |  |  | 本次随机生成的整数持续小时数 |
| started_at | datetime | YES |  |  | 实际开始时间 |
| expected_end_at | datetime | YES |  |  | 最早允许下次切换的时间 |
| last_evaluated_hour | datetime | YES |  |  | Asia/Shanghai整点时槽 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_pet_story_state_prototype`（唯一）: pet_prototype

### 37. ai_pet_story_history - 宠物原型共享故事历史快照表

> 宠物原型共享故事历史快照

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 历史主键UUID |
| pet_prototype | varchar(20) | NO |  | IDX | 宠物原型(如:锦鲤、玉兔) |
| big_scene_id | varchar(32) | NO |  |  | 大场景ID |
| big_scene_name | varchar(64) | NO |  |  | 大场景名称快照 |
| small_scene_id | varchar(32) | NO |  |  | 小场景ID |
| small_scene_name | varchar(100) | NO |  |  | 小场景名称快照 |
| action_id | varchar(32) | NO |  |  | 动作ID |
| action_name | varchar(100) | NO |  |  | 动作名称快照 |
| action_image_id | varchar(32) | NO |  |  | 选中的动作图片ID |
| weight_period | varchar(20) | NO |  |  | 权重时段: NIGHT/MORNING/AFTERNOON/EVENING |
| image_time_of_day | varchar(20) | NO |  |  | 图片时段: 白天/落日/黑夜 |
| image_url | varchar(512) | NO |  |  | 动作图片URL快照 |
| caption | varchar(1000) | YES |  |  | 选中的单条配文快照，可空 |
| duration_hours | int(11) | NO |  |  | 本次随机生成的整数持续小时数 |
| started_at | datetime | NO |  |  | 实际开始时间 |
| expected_end_at | datetime | NO |  |  | 预计结束时间 |
| archived_at | datetime | NO |  |  | 被下一状态实际替换的时间 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |

**索引**:
- `idx_pet_story_history_prototype_started`（普通）: pet_prototype,started_at,id
- `PRIMARY`（唯一）: id

### 38. ai_companion - AI 伴侣配置表

> AI伴侣配置表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| user_id | bigint(20) | NO |  |  | 关联用户表 |
| device_id | varchar(32) | NO |  | UK | 设备ID |
| type | varchar(8) | NO |  |  | 伴侣类型: gf=女友, bf=男友 |
| avatar | varchar(256) | NO |  |  | 伴侣头像URL |
| default_image | varchar(256) | NO |  |  | 默认图片URL |
| birthday | datetime | NO |  |  | 出生日期 |
| zodiac | varchar(16) | NO |  |  | 星座: aries/taurus/.../pisces |
| chinese_zodiac | varchar(16) | NO |  |  | 属相: rat/ox/tiger/.../pig |
| bazi | json | NO |  |  | 八字 |
| wuxing | json | NO |  |  | 五行 |
| character | varchar(32) | NO |  |  | 角色 |
| occupation | varchar(32) | NO |  |  | 职业 |
| voice | varchar(32) | NO |  |  | 音色 |
| quirks_text | varchar(256) | YES |  |  | 职业病描述 |
| soul_traits | varchar(32) | NO |  |  | 灵魂特质,逗号分隔 |
| soul_quirk | varchar(32) | NO |  |  | 小任性 |
| relation_type | varchar(32) | NO |  |  | 关系类型 |
| intimacy | float | YES |  |  | 亲密程度: 0.0~1.0 |
| pet_type | varchar(16) | NO |  |  | 宠物类型: cat/dog |
| pet_name | varchar(32) | NO |  |  | 宠物名 |
| mood | varchar(16) | NO |  |  | 今日心情 |
| past_life_secret | varchar(256) | YES |  |  | 前世秘密 |
| created_by | bigint(20) | NO |  |  | 创建人ID |
| updated_by | bigint(20) | YES |  |  | 修改人ID |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime | YES | CURRENT_TIMESTAMP |  | 修改时间 |
| menstrual_cycle_start | date | YES |  |  | 经期开始日期 |
| menstrual_cycle_length | int(11) | YES |  |  | 周期长度（天） |
| menstrual_period_length | int(11) | YES |  |  | 经期长度（天） |
| last_active_date | date | YES |  |  | 最近活跃日（有用户消息的最后一天） |
| active_streak | int(11) | NO | 0 |  | 连续活跃天数 |
| intimacy_updated_date | date | YES |  |  | 亲密度最近处理日期（防同日重复处理） |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_device_id`（唯一）: device_id

### 39. ai_small_object - 小物件表

> 小物件

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID(雪花) |
| object_type | varchar(64) | NO |  | IDX | 物件类型(字典SMALL_OBJECT_TYPE的dict_value) |
| image_url | varchar(512) | NO |  |  | 物件图片完整OSS URL |
| story | varchar(2000) | NO |  |  | 物件背后的故事(纯文本) |
| status | int(11) | NO | 1 | IDX | 状态:1启用 0停用 |
| deleted | tinyint(4) | NO | 0 |  | 删除标记:0未删除 1已删除 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_small_object_status`（普通）: status
- `idx_ai_small_object_type`（普通）: object_type
- `PRIMARY`（唯一）: id


## 故事引擎模块

### 40. ai_story_big_scene - 故事引擎-大场景表

> 故事引擎-大场景

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键UUID |
| name | varchar(64) | NO |  |  | 大场景名称（如：在家、旅行、上学、打工） |
| sort_order | int(11) | NO | 0 |  | 排序序号，越小越靠前 |
| status | tinyint(4) | NO | 1 | IDX | 状态：1=启用 0=禁用 |
| creator | bigint(20) | YES |  |  | 创建者ID |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者ID |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_status_sort`（普通）: status,sort_order
- `PRIMARY`（唯一）: id

### 41. ai_story_small_scene - 故事引擎-小场景表

> 故事引擎-小场景

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键UUID |
| big_scene_id | varchar(32) | NO |  | IDX | 所属大场景ID |
| name | varchar(100) | NO |  |  | 小场景名称（如：卧室、北京-故宫、快餐厅） |
| weight_night | int(11) | NO | 0 |  | 深夜时段(19:00~06:59)权重百分比 |
| weight_morning | int(11) | NO | 0 |  | 上午时段(07:00~11:59)权重百分比 |
| weight_afternoon | int(11) | NO | 0 |  | 下午时段(12:00~16:59)权重百分比 |
| weight_evening | int(11) | NO | 0 |  | 傍晚时段(17:00~18:59)权重百分比 |
| sort_order | int(11) | NO | 0 |  | 排序序号 |
| status | tinyint(4) | NO | 1 |  | 状态：1=启用 0=禁用 |
| creator | bigint(20) | YES |  |  | 创建者ID |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者ID |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_big_scene_status`（普通）: big_scene_id,status,sort_order
- `PRIMARY`（唯一）: id

### 42. ai_story_action - 故事引擎-蛋宝宝动作表

> 故事引擎-蛋宝宝动作

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键UUID |
| small_scene_id | varchar(32) | NO |  | IDX | 所属小场景ID |
| name | varchar(100) | NO |  |  | 动作名称（如：小憩、看书、故宫红墙前散步） |
| duration_min | int(11) | NO | 1 |  | 最短时长（小时） |
| duration_max | int(11) | NO | 2 |  | 最长时长（小时） |
| sort_order | int(11) | NO | 0 |  | 排序序号 |
| status | tinyint(4) | NO | 1 |  | 状态：1=启用 0=禁用 |
| creator | bigint(20) | YES |  |  | 创建者ID |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者ID |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_small_scene_status`（普通）: small_scene_id,status,sort_order
- `PRIMARY`（唯一）: id

### 43. ai_story_action_image - 故事引擎-动作图片表

> 故事引擎-动作图片

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | varchar(32) | NO |  | PK | 主键UUID |
| action_id | varchar(32) | NO |  | IDX | 所属动作ID |
| pet_prototype | varchar(20) | NO |  |  | 宠物原型：锦鲤 / 玉兔 |
| time_of_day | varchar(10) | NO |  |  | 时段类型：白天 / 落日 / 黑夜 |
| image_url | varchar(512) | NO |  |  | 图片OSS完整URL |
| captions | varchar(1000) | YES |  |  | 图片配文，多句用\|分隔，前端随机展示一句 |
| tag | varchar(64) | YES |  |  | 图片标签（管理端分类标注，最长64字符） |
| sort_order | int(11) | NO | 0 |  | 排序序号（同组多图排序） |
| creator | bigint(20) | YES |  |  | 创建者ID |
| create_date | datetime | YES |  |  | 创建时间 |

**索引**:
- `idx_action_proto_time`（普通）: action_id,pet_prototype,time_of_day,sort_order
- `PRIMARY`（唯一）: id


## 星星罐 / 道具 / 订阅模块

### 44. ai_star_account - 星星罐账户表

> 星星罐账户

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID |
| user_id | bigint(20) | NO |  | UK | 用户ID(sys_user.id) |
| balance | bigint(20) | NO | 0 |  | 星星余额(整数,永不过期,不允许负值) |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_ai_star_account_user`（唯一）: user_id

### 45. ai_star_transaction - 星星罐流水表

> 星星罐流水

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID |
| user_id | bigint(20) | NO |  | IDX | 用户ID(sys_user.id) |
| type | varchar(16) | NO |  |  | 类型: earn-赚取, consume-消费 |
| biz_type | varchar(32) | NO |  |  | 业务类型: earn[sign_in/ad_reward/invite/activity/admin_grant], consume[exchange/lottery] |
| ref_id | varchar(64) | NO |  |  | 业务单号(幂等键): 签到=日期, 兑换=订单号等，格式由调用方自定 |
| amount | bigint(20) | NO |  |  | 变动数量(earn为正,consume为负) |
| balance_after | bigint(20) | NO |  |  | 操作后余额快照 |
| remark | varchar(255) | YES |  |  | 备注 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |

**索引**:
- `idx_ai_star_txn_user_id`（普通）: user_id,id
- `PRIMARY`（唯一）: id
- `uk_ai_star_txn_user_biz_ref`（唯一）: user_id,biz_type,ref_id

### 46. ai_star_daily_reward - 每日星星奖励记录表

> 每日星星奖励记录

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID(雪花) |
| user_id | bigint(20) | NO |  | IDX | 用户ID |
| source_type | varchar(16) | NO |  |  | 奖励来源类型: DAILY_NOTE/GAME |
| source_key | varchar(64) | NO |  |  | DAILY_NOTE固定为DAILY_NOTE; GAME为游戏类型code |
| reward_date | date | NO |  |  | 北京时间领取自然日 |
| amount | bigint(20) | NO |  |  | 本次实际奖励数量，必须大于0 |
| star_transaction_id | bigint(20) | NO |  | UK | 关联星星入账流水ID |
| create_date | datetime | NO |  |  | 领取记录创建时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_star_daily_reward_transaction`（唯一）: star_transaction_id
- `uk_star_daily_reward_user_source_date`（唯一）: user_id,source_type,source_key,reward_date

### 47. ai_item_sku - 道具 SKU 表

> 道具SKU

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| sku_code | varchar(64) | NO |  | UK | 道具编码 |
| sku_name | varchar(64) | NO |  |  | 道具名称 |
| category | varchar(32) | NO |  |  | 类型: consumable_change/outfit/voice_quota/intimacy |
| price_fen | bigint(20) | NO |  |  | 原价(分) |
| promo_price_fen | bigint(20) | YES |  |  | 促销价(分) |
| attributes | text | YES |  |  | 道具属性JSON |
| icon_url | varchar(256) | YES |  |  | 图标 |
| description | varchar(500) | YES |  |  | 描述 |
| status | tinyint(4) | NO | 1 |  | 0下架 1上架 |
| sort | int(11) | NO | 0 |  | 排序 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime | YES |  |  | 修改时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_sku_code`（唯一）: sku_code

### 48. ai_user_item - 用户道具库存表

> 用户道具库存

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| user_id | bigint(20) | NO |  | IDX | 用户ID |
| sku_code | varchar(64) | NO |  |  | 道具编码 |
| total_count | int(11) | NO | 0 |  | 累计获得 |
| used_count | int(11) | NO | 0 |  | 累计消耗 |
| remain_count | int(11) | NO | 0 |  | 剩余 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime | YES |  |  | 修改时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_user_sku`（唯一）: user_id,sku_code

### 49. ai_item_grant_log - 道具发放流水表

> 道具发放流水

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| user_id | bigint(20) | NO |  | IDX | 用户ID |
| sku_code | varchar(64) | NO |  |  | 道具编码 |
| count | int(11) | NO |  |  | 发放数量 |
| source | varchar(32) | NO |  |  | 来源: purchase/subscription_bonus/admin_grant |
| source_ref | varchar(64) | YES |  | IDX | 关联订单号或运营记录 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |

**索引**:
- `idx_user`（普通）: user_id,created_at
- `PRIMARY`（唯一）: id
- `uk_source_ref_sku`（唯一）: source_ref,sku_code

### 50. ai_item_consume_log - 道具消耗流水表

> 道具消耗流水

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| user_id | bigint(20) | NO |  | IDX | 用户ID |
| sku_code | varchar(64) | NO |  |  | 道具编码 |
| count | int(11) | NO |  |  | 消耗数量 |
| biz_type | varchar(32) | NO |  | IDX | 业务类型 |
| biz_ref_id | varchar(64) | YES |  |  | 业务ID |
| remark | varchar(200) | YES |  |  | 备注 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |

**索引**:
- `idx_biz`（普通）: biz_type,biz_ref_id
- `idx_user`（普通）: user_id,created_at
- `PRIMARY`（唯一）: id

### 51. ai_subscription_plan - 订阅套餐档位表

> 订阅套餐档位

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| plan_code | varchar(32) | NO |  | IDX | 档位编码: bronze/silver/gold |
| plan_name | varchar(64) | NO |  |  | 档位名称 |
| duration_days | int(11) | NO |  |  | 周期天数 |
| price_fen | bigint(20) | NO |  |  | 原价(分) |
| promo_price_fen | bigint(20) | YES |  |  | 促销价(分) |
| features | text | NO |  |  | 权益JSON数组: ["long_term_memory","voice_input","superpower","social_moments"] |
| bonus_items | text | YES |  |  | 附赠道具JSON: [{"skuCode":"rose","count":10}] |
| description | varchar(500) | YES |  |  | 描述 |
| status | tinyint(4) | NO | 1 |  | 0下架 1上架 |
| sort | int(11) | NO | 0 |  | 排序 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime | YES |  |  | 修改时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_plan_duration`（唯一）: plan_code,duration_days

### 52. ai_user_subscription - 用户订阅记录表

> 用户订阅记录

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| user_id | bigint(20) | NO |  | IDX | 用户ID |
| plan_id | bigint(20) | NO |  |  | 档位ID |
| plan_code | varchar(32) | NO |  |  | 档位冗余 |
| order_id | bigint(20) | NO |  | UK | 订单ID |
| features_snapshot | text | NO |  |  | 权益JSON快照 |
| start_at | datetime | NO |  |  | 生效时间 |
| end_at | datetime | NO |  |  | 到期时间 |
| status | tinyint(4) | NO |  |  | 0未生效 1生效中 2已过期 3已退款 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime | YES |  |  | 修改时间 |

**索引**:
- `idx_user_status`（普通）: user_id,status,end_at
- `PRIMARY`（唯一）: id
- `uk_order`（唯一）: order_id


## 游戏与听你说模块

### 53. ai_game - 游戏配置表

> 游戏配置

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID |
| game_type | varchar(64) | NO |  | UK | 游戏类型(字典GAME_TYPE的code) |
| earn_stars | int(11) | NO | 0 |  | 赚取星星数(非负) |
| consume_stars | int(11) | NO | 0 |  | 消耗星星数(非负) |
| daily_limit | int(11) | NO | 0 |  | 每日每设备可玩次数上限(0=不限) |
| icon | varchar(512) | YES |  |  | 游戏图标OSS URL |
| description | varchar(500) | YES |  |  | 游戏说明 |
| entrances | varchar(128) | NO |  |  | 游戏入口(逗号分隔枚举code,如NOTE,ACTIVITY) |
| status | tinyint(4) | NO | 1 |  | 状态:1启用 0停用 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_ai_game_game_type`（唯一）: game_type

### 54. ai_listen_group - 听你说题库-题目分组表

> 听你说题库-题目分组

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID(雪花) |
| category_code | varchar(64) | NO |  | IDX | 题目大类(字典LISTEN_CATEGORY的dict_value) |
| name | varchar(255) | NO |  |  | 题目分组名称 |
| sort_order | int(11) | NO | 0 |  | 排序序号，越小越靠前(同一大类内递增追加) |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_ai_listen_group_category_name`（唯一）: category_code,name

### 55. ai_listen_question - 听你说题库-题目表

> 听你说题库-题目

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID(雪花) |
| group_id | bigint(20) | NO |  | IDX | 所属题目分组ID(ai_listen_group.id) |
| text | varchar(500) | NO |  |  | 题干 |
| type | varchar(16) | NO | SINGLE |  | 题目类型:当前仅SINGLE(单选) |
| options_json | json | NO |  |  | 选项列表JSON,格式[{"key":"A","text":"..."}] |
| sort_order | int(11) | NO | 0 |  | 组内排序序号，越小越靠前(组内递增追加) |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_listen_question_group_sort`（普通）: group_id,sort_order
- `PRIMARY`（唯一）: id
- `uk_ai_listen_question_group_text`（唯一）: group_id,text

### 56. ai_listen_answer - 听你说-答题记录表

> 听你说-答题记录

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID(雪花) |
| user_id | bigint(20) | NO |  | IDX | 答题用户(sys_user.id) |
| group_id | bigint(20) | NO |  |  | 所属题目分组ID(ai_listen_group.id，冗余列) |
| question_id | bigint(20) | NO |  |  | 题目ID(ai_listen_question.id) |
| option_key | char(1) | NO |  |  | 所选选项(A-E) |
| custom_text | varchar(200) | YES |  |  | 自定义文本答案(选E"都不是"时可填，非强制，最长200字) |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_ai_listen_answer_user_create_date`（普通）: user_id,create_date
- `idx_ai_listen_answer_user_group`（普通）: user_id,group_id
- `PRIMARY`（唯一）: id
- `uk_ai_listen_answer_user_question`（唯一）: user_id,question_id


## 小纸条模块

### 57. ai_daily_note - 小纸条-每日内容表

> 小纸条-每日内容

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID(雪花) |
| note_date | date | NO |  | UK | 完整日期(yyyy-MM-dd)，全局唯一，不按年循环 |
| title | varchar(50) | NO |  |  | 标题，去首尾空白后1-50个Unicode字符 |
| content | varchar(200) | NO |  |  | 正文，去首尾空白后1-200个Unicode字符，保留内部换行，按纯文本处理 |
| solar_term | varchar(50) | YES |  |  | 节气（可选，任意文案，最多50个Unicode字符） |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_ai_daily_note_date`（唯一）: note_date

### 58. ai_daily_note_opened - 小纸条-用户已打开记录表

> 小纸条-用户已打开记录

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID(雪花) |
| user_id | bigint(20) | NO |  | IDX | 用户ID(后台用户与微信用户主键均为BIGINT) |
| note_date | date | NO |  |  | 小纸条完整日期(yyyy-MM-dd)，不跨年复用 |
| opened_at | datetime | NO |  |  | 首次打开时间，重复上报不更新 |
| create_date | datetime | YES |  |  | 记录创建时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_ai_daily_note_opened_user_date`（唯一）: user_id,note_date


## 邀请码模块

### 59. ai_invite_code - 邀请码主表

> 邀请码主表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| code | varchar(32) | NO |  | UK | 邀请码字符串 |
| type | tinyint(4) | NO |  |  | 1=个人邀请码 2=企业邀请码 |
| owner_user_id | bigint(20) | YES |  | IDX | 个人码=归属用户id;企业码=NULL |
| quota | int(11) | NO |  |  | 总配额数量 |
| used_count | int(11) | NO | 0 |  | 已使用数量 |
| remaining | int(11) | NO |  |  | 剩余数量 |
| status | tinyint(4) | NO | 1 | IDX | 0=失效 1=有效 |
| expire_time | datetime | YES |  |  | 过期时间,NULL=不过期 |
| remark | varchar(255) | YES |  |  | 备注 |
| creator | bigint(20) | YES |  |  | 创建人 |
| create_date | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新人 |
| update_date | datetime | YES | CURRENT_TIMESTAMP |  | 更新时间 |

**索引**:
- `idx_status`（普通）: status
- `PRIMARY`（唯一）: id
- `uk_code`（唯一）: code
- `uk_owner_type`（唯一）: owner_user_id,type

### 60. ai_invite_usage - 邀请码使用记录表

> 邀请码使用记录表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| code_id | bigint(20) | NO |  | IDX | 关联ai_invite_code.id |
| invitee_user_id | bigint(20) | NO |  | IDX | 被邀请人user_id |
| create_date | datetime | NO | CURRENT_TIMESTAMP |  | 消耗时间 |

**索引**:
- `idx_invitee`（普通）: invitee_user_id
- `PRIMARY`（唯一）: id
- `uk_code_invitee`（唯一）: code_id,invitee_user_id


## 支付模块

### 61. ai_payment_order - 统一支付订单表

> 统一支付订单

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| out_trade_no | varchar(40) | NO |  | UK | 商户订单号 |
| user_id | bigint(20) | NO |  | IDX | 用户ID |
| product_type | varchar(16) | NO |  |  | SUBSCRIPTION/ITEM |
| product_ref_id | bigint(20) | NO |  |  | plan_id 或 sku_id |
| product_snapshot | text | NO |  |  | 产品快照JSON |
| quantity | int(11) | NO | 1 |  | 数量 |
| amount_fen | bigint(20) | NO |  |  | 金额(分) |
| pay_channel | varchar(16) | NO | WECHAT_JSAPI |  | 支付渠道 |
| status | tinyint(4) | NO |  |  | 0待支付 1已支付 2已发货 3已取消 4已退款 5已超时 |
| prepay_id | varchar(64) | YES |  |  | 微信预支付ID |
| transaction_id | varchar(40) | YES |  | IDX | 微信支付订单号 |
| paid_at | datetime | YES |  |  | 支付时间 |
| fulfilled_at | datetime | YES |  |  | 发货时间 |
| expire_at | datetime | NO |  |  | 订单超时时间 |
| refund_amount_fen | bigint(20) | NO | 0 |  | 已退款金额(分) |
| client_ip | varchar(64) | YES |  |  | 下单IP |
| fail_reason | varchar(500) | YES |  |  | 失败原因 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime | YES |  |  | 修改时间 |

**索引**:
- `idx_transaction`（普通）: transaction_id
- `idx_user_status`（普通）: user_id,status,created_at
- `PRIMARY`（唯一）: id
- `uk_out_trade_no`（唯一）: out_trade_no

### 62. ai_payment_refund - 退款记录表

> 退款记录

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| out_refund_no | varchar(40) | NO |  | UK | 商户退款单号 |
| order_id | bigint(20) | NO |  | IDX | 订单ID |
| refund_fen | bigint(20) | NO |  |  | 退款金额(分) |
| reason | varchar(200) | YES |  |  | 原因 |
| status | tinyint(4) | NO |  |  | 0处理中 1成功 2失败 |
| refund_id | varchar(64) | YES |  |  | 微信退款单号 |
| refunded_at | datetime | YES |  |  | 退款完成时间 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |
| updated_at | datetime | YES |  |  | 修改时间 |

**索引**:
- `idx_order`（普通）: order_id
- `PRIMARY`（唯一）: id
- `uk_out_refund_no`（唯一）: out_refund_no

### 63. ai_payment_callback_log - 支付回调原始日志表

> 支付回调原始日志

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | ID |
| channel | varchar(16) | NO |  |  | 渠道 |
| out_trade_no | varchar(40) | YES |  | IDX | 商户订单号 |
| transaction_id | varchar(40) | YES |  | IDX | 微信订单号 |
| raw_headers | text | YES |  |  | 请求头 |
| raw_body | text | NO |  |  | 请求体 |
| signature_valid | tinyint(4) | NO |  |  | 签名是否有效 |
| process_result | varchar(32) | NO |  |  | SUCCESS/DUPLICATE/SIGN_FAIL/PROCESS_FAIL |
| remark | varchar(500) | YES |  |  | 备注 |
| created_at | datetime | NO | CURRENT_TIMESTAMP |  | 创建时间 |

**索引**:
- `idx_out_trade`（普通）: out_trade_no
- `idx_trans`（普通）: transaction_id
- `PRIMARY`（唯一）: id


## AI 生图与反馈模块

### 64. ai_image_task - AI 生图任务表

> AI生图任务表

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 任务ID(雪花) |
| user_id | bigint(20) | NO |  | IDX | 归属用户ID |
| pet_id | varchar(64) | NO |  |  | 宠物ID |
| photo_url | varchar(512) | NO |  |  | 用户照片OSS URL |
| ip_image_url | varchar(512) | NO |  |  | IP参考图URL快照 |
| caption | varchar(64) | NO |  |  | 抽中的预置文案快照 |
| status | varchar(16) | NO | PENDING | IDX | PENDING照片审核/RUNNING生成中/REVIEWING结果审核/SUCCEEDED成功/FAILED失败 |
| result_url | varchar(512) | YES |  |  | 结果图OSS URL |
| fail_reason | varchar(255) | YES |  |  | 用户可读失败原因 |
| photo_trace_id | varchar(128) | YES |  | IDX | 照片mediaCheckAsync的trace_id |
| result_trace_id | varchar(128) | YES |  | IDX | 结果图mediaCheckAsync的trace_id |
| counted | tinyint(4) | NO | 0 |  | 是否计入每日配额: 0否 1是(成功才计次) |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_photo_trace`（普通）: photo_trace_id
- `idx_result_trace`（普通）: result_trace_id
- `idx_status_create`（普通）: status,create_date
- `idx_user_create`（普通）: user_id,create_date
- `PRIMARY`（唯一）: id

### 65. ai_feedback - 用户反馈表

> 用户反馈

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 |
| user_id | bigint(20) | NO |  | IDX | 提交用户ID |
| type | varchar(50) | NO |  |  | 诉求类型（字典 EGG_FEEDBACK_TYPE 的 dict_value） |
| content | varchar(500) | NO |  |  | 反馈内容 |
| status | int(11) | NO | 0 | IDX | 处理状态：0-未处理 1-已处理 |
| remark | varchar(500) | YES |  |  | 运营处理备注 |
| creator | bigint(20) | YES |  |  | 创建者 |
| create_date | datetime | YES |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新者 |
| update_date | datetime | YES |  |  | 更新时间 |

**索引**:
- `idx_feedback_status_created`（普通）: status,create_date
- `idx_feedback_user`（普通）: user_id
- `PRIMARY`（唯一）: id


## 记忆模块

### 66. memories - 记忆表

> PowerMem 记忆表，存智能体的长期记忆（向量/图谱）数据。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键ID |
| embedding | VECTOR(1024) | YES |  |  | 记忆内容向量（VECTOR(1024)），用于语义检索 |
| document | longtext | YES |  |  | 记忆文档内容 |
| metadata | json | YES |  |  | 用户自定义元数据（JSON） |
| user_id | varchar(128) | YES |  |  | 用户ID（MAC地址字符串） |
| agent_id | varchar(128) | YES |  |  | 智能体ID |
| run_id | varchar(128) | YES |  |  | 产生该记忆的运行批次 ID，用于按会话归并 |
| actor_id | varchar(128) | YES |  |  | 记忆主体 ID（说话人/角色维度） |
| hash | varchar(32) | YES |  |  | 记忆内容哈希，用于去重与变更比对 |
| created_at | varchar(128) | YES |  |  | 创建时间（ISO 8601格式字符串） |
| updated_at | varchar(128) | YES |  |  | 更新时间（ISO 8601格式字符串） |
| category | varchar(64) | YES |  |  | 记忆分类 |
| fulltext_content | longtext | YES |  |  | 全文检索内容副本，供关键词检索使用 |

**索引**:
- `PRIMARY`（唯一）: id


## NFC 模块

### 67. pdc_nfc_product_type - NFC 产品类型表

> NFC 产品类型表，定义可发放的产品类别、领取页路径与能力模式。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| type_code | varchar(32) | NO |  | UK | 类型编码，全局唯一（如 EGG_STANDARD） |
| type_name | varchar(64) | NO |  |  | 类型名称（如“蛋宝宝标准版”） |
| claim_page_path | varchar(128) | NO |  |  | 微信小程序领取页路径（如 /pages/nfc-claim/nfc-claim） |
| capability_mode | varchar(32) | NO |  |  | 能力模式（如 BASIC / PREMIUM） |
| status | varchar(16) | NO |  |  | 状态（ACTIVE / INACTIVE） |
| creator | bigint(20) | YES |  |  | 创建人 ID |
| create_date | datetime(3) | NO |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新人 ID |
| update_date | datetime(3) | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_product_type_code`（唯一）: type_code

### 68. pdc_nfc_batch - NFC 生产批次表

> NFC 生产批次表，按产品类型登记计划产量与生产状态。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| batch_no | varchar(64) | NO |  | UK | 批次编号，全局唯一 |
| product_type_id | bigint(20) | NO |  | IDX | 关联商品类型 ID（对应 pdc_nfc_product_type.id） |
| sku_code | varchar(64) | NO |  |  | SKU 编码，标识产品型号 |
| prototype | varchar(16) | NO |  |  | 原型标识，区分同一 SKU 下的不同硬件版本 |
| planned_quantity | int(11) | NO |  |  | 计划生产数量 |
| status | varchar(32) | NO |  | IDX | 批次状态（CREATED / SCHEME_GENERATING / READY_FOR_WRITE / STOCKED / COMPLETED / CANCELLED） |
| remark | varchar(512) | YES |  |  | 备注信息 |
| creator | bigint(20) | YES |  |  | 创建人 ID |
| create_date | datetime(3) | NO |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新人 ID |
| update_date | datetime(3) | YES |  |  | 更新时间 |

**索引**:
- `idx_pdc_nfc_batch_product`（普通）: product_type_id
- `idx_pdc_nfc_batch_status`（普通）: status
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_batch_no`（唯一）: batch_no

### 69. pdc_nfc_scheme_job - NFC Scheme 生成任务表

> NFC Scheme 生成任务表，驱动调用微信 generatenfcscheme 的批处理与租约。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| job_no | varchar(64) | NO |  | UK | 任务编号，全局唯一 |
| batch_id | bigint(20) | NO |  | IDX | 关联批次 ID（对应 pdc_nfc_batch.id） |
| status | varchar(32) | NO |  | IDX | 任务状态（CREATED / RUNNING / PAUSED / COMPLETED / FAILED / CANCELLED） |
| requested_by | bigint(20) | NO |  |  | 请求人用户 ID |
| total_count | int(11) | NO |  |  | 待处理资产总数 |
| success_count | int(11) | NO | 0 |  | 成功处理数 |
| failure_count | int(11) | NO | 0 |  | 失败处理数 |
| cursor_asset_id | bigint(20) | YES |  |  | 游标资产 ID（用于断点续传，下次处理从此 ID 开始） |
| lease_owner | varchar(128) | YES |  |  | 租约持有者标识（多实例部署时用于任务抢占） |
| lease_until | datetime(3) | YES |  |  | 租约到期时间（过期后其他实例可抢占） |
| heartbeat_at | datetime(3) | YES |  |  | 最近心跳时间 |
| next_retry_at | datetime(3) | YES |  |  | 下次重试时间（失败后延迟重试） |
| cancelled_at | datetime(3) | YES |  |  | 取消时间 |
| create_date | datetime(3) | NO |  |  | 创建时间 |
| update_date | datetime(3) | YES |  |  | 更新时间 |

**索引**:
- `idx_pdc_nfc_scheme_job_batch`（普通）: batch_id
- `idx_pdc_nfc_scheme_job_lease`（普通）: status,next_retry_at,lease_until
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_scheme_job_no`（唯一）: job_no

### 70. pdc_nfc_scheme_attempt - NFC Scheme 生成尝试表

> NFC Scheme 生成尝试表，记录单件资产的每次调用与错误。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| job_id | bigint(20) | NO |  | IDX | 所属 Scheme 任务 ID（对应 pdc_nfc_scheme_job.id） |
| asset_id | bigint(20) | NO |  | IDX | 目标资产 ID（对应 pdc_nfc_asset.id） |
| attempt_no | int(11) | NO |  |  | 尝试序号，从 1 递增，用于重试计数 |
| request_fingerprint | char(64) | NO |  |  | 请求指纹（微信 API 请求的规范化哈希） |
| action | varchar(32) | NO |  |  | 操作动作（GENERATE / RETRY） |
| wechat_error_code | int(11) | YES |  |  | 微信 API 返回的错误码（0 表示成功） |
| error_message | varchar(512) | YES |  |  | 错误消息（失败时记录，已脱敏） |
| started_at | datetime(3) | NO |  |  | 尝试开始时间 |
| finished_at | datetime(3) | YES |  |  | 尝试完成时间 |

**索引**:
- `idx_pdc_nfc_scheme_attempt_asset`（普通）: asset_id
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_scheme_attempt`（唯一）: job_id,asset_id,attempt_no

### 71. pdc_nfc_asset - NFC 资产表

> NFC 资产表，一物一码：Scheme 密文、标签 UID 与 生产/入库/激活/领取 状态机。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| asset_no | varchar(64) | NO |  | UK | 资产编号，全局唯一，格式如 NFC-YYYYMMDD-NNNNN |
| batch_id | bigint(20) | NO |  | IDX | 所属批次 ID（对应 pdc_nfc_batch.id） |
| item_no | varchar(16) | NO |  |  | 批次内序号，从 1 递增 |
| sku_code | varchar(64) | NO |  |  | SKU 编码，标识产品型号 |
| prototype | varchar(16) | NO |  |  | 原型标识，区分同一 SKU 下的不同硬件版本 |
| wechat_sn | varchar(64) | NO |  | UK | 微信序列号，由微信 NFC 服务分配 |
| claim_ref_hash | char(64) | NO |  | UK | claimRef 的 HMAC-SHA-256 查找哈希（用于通过 claimRef 快速定位资产） |
| claim_ref_hash_version | varchar(16) | NO |  |  | claimRef 查找哈希的版本号（对应 HMAC 密钥版本） |
| claim_ref_key_version | varchar(16) | NO |  |  | claimRef 加密密钥版本号 |
| claim_ref_nonce | varbinary(12) | NO |  |  | claimRef AES-256-GCM 加密随机 Nonce（12 字节） |
| claim_ref_ciphertext | varbinary(128) | NO |  |  | claimRef AES-256-GCM 加密密文（含认证标签） |
| scheme_key_version | varchar(16) | YES |  |  | scheme 加密密钥版本号 |
| scheme_nonce | varbinary(12) | YES |  |  | scheme AES-256-GCM 加密随机 Nonce（12 字节） |
| scheme_ciphertext | mediumblob | YES |  |  | scheme AES-256-GCM 加密密文（微信小程序 Scheme 明文加密存储） |
| scheme_sha256 | char(64) | YES |  |  | scheme 明文的 SHA-256 哈希（用于写卡前完整性校验） |
| tag_uid | varchar(128) | YES |  |  | NFC 标签 UID（写卡后读取确认） |
| status | varchar(32) | NO |  | IDX | 资产状态（CREATED / SCHEME_GENERATED / WRITTEN / VERIFIED / IN_STOCK / ACTIVE / CLAIMED / DISABLED / SCRAPPED） |
| version | int(11) | NO | 0 |  | 乐观锁版本号 |
| active_scheme_job_id | bigint(20) | YES |  | IDX | 当前关联的 Scheme 生成任务 ID（对应 pdc_nfc_scheme_job.id） |
| active_write_job_id | bigint(20) | YES |  | IDX | 当前关联的写卡任务 ID（对应 pdc_nfc_write_job.id） |
| scheme_generated_at | datetime(3) | YES |  |  | Scheme 生成完成时间 |
| written_at | datetime(3) | YES |  |  | 写卡完成时间 |
| verified_at | datetime(3) | YES |  |  | 校验通过时间 |
| verify_source | varchar(16) | YES |  |  | 验证来源：TOUCH 触碰自验证，MANUAL 人工验证；工厂CSV模式为空 |
| locked_at | datetime | YES |  |  | 锁卡确认时间（手动模式），为空表示未锁卡 |
| lock_verified_at | datetime | YES |  |  | 锁后触碰复验时间（手动模式） |
| stocked_at | datetime(3) | YES |  |  | 入库时间 |
| activated_at | datetime(3) | YES |  |  | 激活时间 |
| claimed_at | datetime(3) | YES |  |  | 用户领取时间 |
| disabled_at | datetime(3) | YES |  |  | 停用时间 |
| scrapped_at | datetime(3) | YES |  |  | 报废时间 |
| claimed_user_id | bigint(20) | YES |  |  | 领取用户 ID（对应 sys_user.id） |
| pet_id | varchar(64) | YES |  |  | 关联宠物 ID（领取时绑定） |
| stock_business_no | varchar(64) | YES |  |  | 入库业务单号（用于幂等和审计追踪） |
| activation_business_no | varchar(64) | YES |  |  | 激活业务单号（用于幂等和审计追踪） |
| creator | bigint(20) | YES |  |  | 创建人 ID |
| create_date | datetime(3) | NO |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新人 ID |
| update_date | datetime(3) | YES |  |  | 更新时间 |

**索引**:
- `idx_pdc_nfc_asset_scheme_lease`（普通）: active_scheme_job_id
- `idx_pdc_nfc_asset_status`（普通）: status
- `idx_pdc_nfc_asset_write_lease`（普通）: active_write_job_id
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_asset_batch_item`（唯一）: batch_id,item_no
- `uk_pdc_nfc_asset_claim_hash`（唯一）: claim_ref_hash
- `uk_pdc_nfc_asset_no`（唯一）: asset_no
- `uk_pdc_nfc_asset_wechat_sn`（唯一）: wechat_sn

### 72. pdc_nfc_write_job - NFC 写卡任务表

> NFC 写卡任务表，工厂 CSV 导出与写卡结果回传。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| job_no | varchar(64) | NO |  | UK | 任务编号，全局唯一 |
| batch_id | bigint(20) | NO |  | IDX | 关联批次 ID（对应 pdc_nfc_batch.id） |
| format_version | varchar(32) | NO |  |  | CSV 格式版本号（如 "1.0"） |
| mode | varchar(16) | NO | FACTORY_CSV |  | 写卡模式：FACTORY_CSV 工厂CSV模式，MANUAL 手动模式 |
| status | varchar(32) | NO |  | IDX | 任务状态（CREATED / EXPORTED / RESULT_IMPORTED / COMPLETED / CANCELLED） |
| total_count | int(11) | NO |  |  | 待处理资产总数 |
| success_count | int(11) | NO | 0 |  | 写卡成功数 |
| failure_count | int(11) | NO | 0 |  | 写卡失败数 |
| file_sha256 | char(64) | YES |  |  | 导出 CSV 文件的 SHA-256 哈希 |
| row_count | int(11) | YES |  |  | CSV 数据行数（不含表头） |
| export_user_id | bigint(20) | YES |  |  | 导出操作人用户 ID |
| exported_at | datetime(3) | YES |  |  | 导出时间 |
| result_file_sha256 | char(64) | YES |  |  | 工厂回传结果文件的 SHA-256 哈希 |
| import_request_id | char(36) | YES |  |  | 导入幂等请求 ID（UUID 字符串） |
| result_response_json | json | YES |  |  | 导入响应 JSON（完整的导入结果序列化） |
| import_user_id | bigint(20) | YES |  |  | 导入操作人用户 ID |
| imported_at | datetime(3) | YES |  |  | 导入时间 |
| completed_at | datetime(3) | YES |  |  | 任务完成时间 |
| cancelled_at | datetime(3) | YES |  |  | 任务取消时间 |
| creator | bigint(20) | YES |  |  | 创建人 ID |
| create_date | datetime(3) | NO |  |  | 创建时间 |
| updater | bigint(20) | YES |  |  | 更新人 ID |
| update_date | datetime(3) | YES |  |  | 更新时间 |

**索引**:
- `idx_pdc_nfc_write_job_batch`（普通）: batch_id
- `idx_pdc_nfc_write_job_status`（普通）: status
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_write_job_no`（唯一）: job_no

### 73. pdc_nfc_write_job_item - NFC 写卡任务明细表

> NFC 写卡任务明细表，逐件记录待写入的 NDEF URI/AAR 记录。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| job_id | bigint(20) | NO |  | IDX | 所属写卡任务 ID（对应 pdc_nfc_write_job.id） |
| asset_id | bigint(20) | NO |  | IDX | 对应资产 ID（对应 pdc_nfc_asset.id） |
| sequence_no | int(11) | NO |  |  | CSV 行序号，从 1 递增 |
| asset_no | varchar(64) | NO |  |  | 资产编号（快照） |
| batch_no | varchar(64) | NO |  |  | 批次编号（快照） |
| wechat_sn | varchar(64) | NO |  |  | 微信序列号（快照） |
| sku_code | varchar(64) | NO |  |  | SKU 编码（快照） |
| prototype | varchar(16) | NO |  |  | 原型标识（快照） |
| uri_sha256 | char(64) | NO |  |  | URI 记录的 SHA-256 哈希（用于写卡后完整性校验） |
| uri_tnf | varchar(8) | NO |  |  | URI NDEF 记录 TNF（Type Name Format） |
| uri_type | varchar(8) | NO |  |  | URI NDEF 记录类型（如 "U"） |
| aar_tnf | varchar(8) | NO |  |  | AAR NDEF 记录 TNF |
| aar_type | varchar(32) | NO |  |  | AAR NDEF 记录类型（如 "android.com:pkg"） |
| aar_payload | varchar(128) | NO |  |  | AAR NDEF 记录载荷（Android 包名） |
| create_date | datetime(3) | NO |  |  | 创建时间 |

**索引**:
- `idx_pdc_nfc_write_item_asset`（普通）: asset_id
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_write_item_asset`（唯一）: job_id,asset_id
- `uk_pdc_nfc_write_item_seq`（唯一）: job_id,sequence_no

### 74. pdc_nfc_write_record - NFC 写卡记录表

> NFC 写卡记录表，记录实际写卡与校验结果。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| job_id | bigint(20) | NO |  | IDX | 所属写卡任务 ID（对应 pdc_nfc_write_job.id） |
| asset_id | bigint(20) | NO |  | IDX | 对应资产 ID（对应 pdc_nfc_asset.id） |
| attempt_no | int(11) | NO |  |  | 写卡尝试序号 |
| write_result | varchar(16) | NO |  |  | 写卡结果（SUCCESS / FAILED） |
| verify_result | varchar(16) | NO |  |  | 校验结果（PASS / FAIL / SKIPPED） |
| tag_uid | varchar(128) | YES |  |  | NFC 标签 UID（写卡后读取） |
| ndef_record_count | int(11) | YES |  |  | NDEF 记录数 |
| uri_sha256 | char(64) | YES |  |  | URI 记录的 SHA-256 哈希（完整性校验） |
| aar_package | varchar(128) | YES |  |  | Android AAR 包名 |
| is_read_only | tinyint(1) | YES |  |  | 标签是否已设为只读 |
| error_code | varchar(64) | YES |  |  | 错误码（失败时记录） |
| error_message | varchar(512) | YES |  |  | 错误消息（失败时记录） |
| written_at | datetime(3) | YES |  |  | 工厂写卡完成时间 |
| imported_at | datetime(3) | NO |  |  | 结果导入时间 |
| import_user_id | bigint(20) | NO |  |  | 导入操作人用户 ID |

**索引**:
- `idx_pdc_nfc_write_record_asset`（普通）: asset_id
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_write_record_attempt`（唯一）: job_id,asset_id,attempt_no

### 75. pdc_nfc_claim_record - NFC 领取记录表

> NFC 领取记录表，用户触碰领取的幂等记录与结果。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| asset_id | bigint(20) | NO |  | UK | 领取的资产 ID（对应 pdc_nfc_asset.id） |
| user_id | bigint(20) | NO |  | IDX | 领取用户 ID（对应 sys_user.id） |
| request_id | char(36) | NO |  |  | 幂等请求 ID（客户端生成，防重复领取） |
| request_fingerprint | char(64) | NO |  |  | 请求指纹（userId + claimRef 的哈希，用于冲突检测） |
| pet_id | varchar(64) | NO |  | IDX | 领取时绑定的宠物 ID |
| result | varchar(32) | NO |  |  | 领取结果（CLAIMED / CLAIMED_BY_SELF / FAILED） |
| create_date | datetime(3) | NO |  |  | 创建时间 |

**索引**:
- `idx_pdc_nfc_claim_pet`（普通）: pet_id
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_claim_asset`（唯一）: asset_id
- `uk_pdc_nfc_claim_user_request`（唯一）: user_id,request_id

### 76. pdc_nfc_operation_log - NFC 操作日志表

> NFC 操作日志表，记录状态流转与操作审计。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| operator_user_id | bigint(20) | YES |  |  | 操作人用户 ID |
| request_id | char(36) | YES |  | IDX | 幂等请求 ID（关联到具体的幂等请求记录） |
| source | varchar(32) | NO |  |  | 日志来源（ADMIN_API / SYSTEM_TASK） |
| object_type | varchar(32) | NO |  | IDX | 对象类型（BATCH / ASSET / SCHEME_JOB / WRITE_JOB） |
| object_id | bigint(20) | YES |  |  | 对象 ID（对应具体实体的 ID） |
| operation_type | varchar(64) | NO |  |  | 操作类型（WRITE_RESULT_IMPORT / STOCK_IN / ACTIVATE / DISABLE / SCRAP 等） |
| before_status | varchar(32) | YES |  |  | 操作前状态 |
| after_status | varchar(32) | YES |  |  | 操作后状态 |
| quantity | int(11) | YES |  |  | 操作数量（批量操作时受影响资源数） |
| business_no | varchar(64) | YES |  |  | 业务单号 |
| result | varchar(32) | NO |  |  | 操作结果（SUCCESS / FAILED / PARTIAL） |
| error_code | varchar(64) | YES |  |  | 错误码（失败时记录） |
| detail_json | json | YES |  |  | 详情 JSON（操作结果的详细信息，经 allowlist 过滤） |
| create_date | datetime(3) | NO |  |  | 创建时间 |

**索引**:
- `idx_pdc_nfc_operation_object`（普通）: object_type,object_id,create_date
- `idx_pdc_nfc_operation_request`（普通）: request_id
- `PRIMARY`（唯一）: id

### 77. pdc_nfc_admin_request - NFC 管理端审批请求表

> NFC 管理端审批请求表，记录带 requestId 的操作幂等响应。

| 字段名 | 类型 | 允许NULL | 默认值 | 键 | 说明 |
|--------|------|----------|--------|-----|------|
| id | bigint(20) | NO |  | PK | 主键 ID（雪花算法生成） |
| operation_type | varchar(64) | NO |  | IDX | 操作类型（WRITE_RESULT_IMPORT / STOCK_IN / ACTIVATE / DISABLE / SCRAP） |
| request_id | char(36) | NO |  |  | 幂等请求 ID（UUID 字符串） |
| request_fingerprint | char(64) | NO |  |  | 请求指纹（规范化请求内容的哈希，用于冲突检测） |
| response_json | json | YES |  |  | 响应 JSON（操作成功后的完整响应序列化） |
| status | varchar(16) | NO |  |  | 处理状态（PENDING / SUCCESS / FAILED） |
| operator_user_id | bigint(20) | NO |  |  | 操作人用户 ID |
| create_date | datetime(3) | NO |  |  | 创建时间 |
| update_date | datetime(3) | YES |  |  | 更新时间 |

**索引**:
- `PRIMARY`（唯一）: id
- `uk_pdc_nfc_admin_request`（唯一）: operation_type,request_id


## 表关系图

### 核心关系

```
系统用户 (sys_user)
    ├── 1:N → 用户 Token (sys_user_token)
    ├── 1:N → 设备 (ai_device)
    ├── 1:N → 智能体 (ai_agent)
    ├── 1:N → 声音克隆 (ai_voice_clone)
    ├── 1:N → 知识库 (ai_rag_dataset)
    ├── 1:N → 星星罐账户 (ai_star_account)
    ├── 1:N → 星星罐流水 (ai_star_transaction)
    ├── 1:N → 用户道具库存 (ai_user_item)
    ├── 1:N → 用户订阅 (ai_user_subscription)
    ├── 1:N → 支付订单 (ai_payment_order)
    └── 1:1 → 微信绑定 (ai_wechat_user.user_id)

模型供应器 (ai_model_provider)
    ├── 1:N → 模型配置 (ai_model_config)
    └── 1:N → 插件映射 (ai_agent_plugin_mapping)

模型配置 (ai_model_config)
    ├── 1:N → 智能体 (ai_agent) [通过多个模型 ID 字段]
    └── 1:N → TTS 音色 (ai_tts_voice)

智能体 (ai_agent)
    ├── 1:N → 设备 (ai_device)
    ├── 1:N → 配置快照 (ai_agent_snapshot)
    ├── 1:N → 插件映射 (ai_agent_plugin_mapping)
    ├── 1:N → 标签关联 (ai_agent_tag_relation)
    ├── 1:1 → 上下文源 (ai_agent_context_provider)
    ├── 1:N → 替换词关联 (ai_agent_correct_word_mapping)
    ├── 1:N → 声纹 (ai_agent_voice_print)
    └── 1:N → 聊天记录 (ai_agent_chat_history)

智能体标签 (ai_agent_tag)
    └── 1:N → 标签关联 (ai_agent_tag_relation)

替换词文件 (ai_agent_correct_word_file)
    ├── 1:N → 替换词词条 (ai_agent_correct_word_item)
    └── 1:N → 替换词关联 (ai_agent_correct_word_mapping)

知识库 (ai_rag_dataset)
    └── 1:N → 文档 (ai_rag_knowledge_document)

宠物 (ai_pet)
    ├── N:1 → 用户 (ai_pet.user_id → sys_user.id)
    ├── 1:N → 孵化动作明细 (ai_pet_hatch_action)
    ├── 1:N → 收藏卡 (ai_pet_collection_card)
    └── 1:N → 明信片 (ai_pet_postcard)

故事引擎
    ai_story_big_scene
        └── 1:N → ai_story_small_scene (big_scene_id)
                       └── 1:N → ai_story_action (small_scene_id)
                                      └── 1:N → ai_story_action_image (action_id)

宠物故事状态 (ai_pet_story_state / ai_pet_story_history)
    → 引用 ai_story_big_scene / ai_story_small_scene / ai_story_action / ai_story_action_image

听你说题库
    ai_listen_group
        └── 1:N → ai_listen_question (group_id)
                       └── 1:N → ai_listen_answer (question_id)

邀请码
    ai_invite_code
        └── 1:N → ai_invite_usage (code_id)

支付
    ai_payment_order
        └── 1:N → ai_payment_refund (order_id)
    ai_user_subscription → ai_subscription_plan (plan_id)、ai_payment_order (order_id)

星星罐
    ai_star_account
        └── 1:N → ai_star_transaction
                       └── 1:N → ai_star_daily_reward (star_transaction_id)

道具
    ai_item_sku
        └── 1:N → ai_user_item
        └── 1:N → ai_item_grant_log / ai_item_consume_log

NFC
    pdc_nfc_product_type
        └── 1:N → pdc_nfc_batch (product_type_id)
                       ├── 1:N → pdc_nfc_asset (batch_id)
                       ├── 1:N → pdc_nfc_scheme_job (batch_id)
                       └── 1:N → pdc_nfc_write_job (batch_id)
    pdc_nfc_asset
        ├── 1:N → pdc_nfc_scheme_attempt (asset_id)
        ├── 1:N → pdc_nfc_write_job_item (asset_id)
        ├── 1:N → pdc_nfc_write_record (asset_id)
        └── 1:N → pdc_nfc_claim_record (asset_id)
    pdc_nfc_asset.pet_id → ai_pet.id
    pdc_nfc_asset.claimed_user_id → sys_user.id
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
├── ai_agent_snapshot (配置快照)
├── ai_agent_plugin_mapping (插件配置)
├── ai_agent_tag_relation (标签分类)
├── ai_agent_context_provider (上下文源)
├── ai_agent_correct_word_mapping (文本纠错)
└── ai_agent_voice_print (声纹)
```

#### 4. 宠物领养与孵化流程

```
sys_user ── invite code / NFC / device birth ──► ai_pet (hatch_status=EGG)
                                                     ↓
                                         ai_pet_hatch_action (修炼动作累计加速)
                                                     ↓
                                         hatch_status=HATCHED (avatar_url/scene_url 生成)
                                                     ↓
                                    ai_pet_collection_card / ai_pet_postcard
```

#### 5. NFC 生产与领取流程

```
pdc_nfc_product_type → pdc_nfc_batch → pdc_nfc_asset (逐件资产)
                                          ↓
                           pdc_nfc_scheme_job / pdc_nfc_scheme_attempt (调微信生成 Scheme)
                                          ↓
                           pdc_nfc_write_job / pdc_nfc_write_job_item (导出写卡文件)
                                          ↓
                           pdc_nfc_write_record (回传导入写卡结果)
                                          ↓
                           pdc_nfc_claim_record (用户触碰领取 → 建蛋)
```

#### 6. 星星罐与道具流程

```
ai_star_account ── ai_star_transaction (流水, ref_id 幂等) ── ai_star_daily_reward
        ↓
ai_item_sku ── ai_user_item (库存) ── ai_item_grant_log / ai_item_consume_log
        ↓
ai_payment_order ── ai_payment_refund
        ↓
ai_user_subscription ── ai_subscription_plan
```

#### 7. 知识库 RAG 流程

```
ai_rag_dataset (知识库)
        ↓
ai_rag_knowledge_document (文档)
        ↓
ai_agent_context_provider (配置到智能体)
```

---

## 数据字典

### 字段类型说明

| 类型 | 说明 | 示例 |
|------|------|------|
| varchar | 变长字符串 | 用户名、设备 ID |
| char | 定长字符串 | SHA256 摘要、UUID |
| text | 长文本 | Prompt、配置 JSON |
| mediumtext | 中等长度文本 | 缩略图 Base64 |
| longtext | 超长文本 | 声纹特征向量 |
| bigint | 大整数 | 用户 ID、主键 ID |
| int | 整数 | 排序、数量 |
| tinyint | 小整数 | 状态、开关 |
| datetime | 日期时间 | 创建时间、更新时间 |
| date | 日期 | 心情对应日期 |
| json | JSON 数据 | 配置参数、八字、五行 |
| varbinary / mediumblob | 二进制 | NFC 密文与 Scheme |
| longblob | 二进制大对象 | 音频数据、声音数据 |

### 常用字段命名规范

| 前缀 | 说明 | 示例 |
|------|------|------|
| id | 主键 ID | id |
| *_id | 外键 ID | user_id, agent_id, pet_id |
| *_code | 编码 | agent_code, sku_code, model_code |
| *_name | 名称 | agent_name, type_name |
| *_type | 类型 | model_type, dict_type |
| *_status | 状态 | status, train_status, hatch_status |
| *_at | 时间戳 | created_at, updated_at, hatched_at |
| *_date | 日期 | create_date, update_date |
| is_* | 布尔标识 | is_default, is_enabled |

### 状态值说明

#### 通用状态 (status)
- 0: 停用/禁用
- 1: 启用/正常

#### 孵化状态 (ai_pet.hatch_status)
- `EGG`: 孵化中
- `HATCHED`: 已破壳

#### 宠物创建来源 (ai_pet.source)
- `INVITE_CODE`: 邀请码（激活码）领养
- `NFC`: NFC 触碰领取
- `DEVICE_BIRTH`: 设备出生

#### 训练状态 (ai_voice_clone.train_status)
- 0: 待训练
- 1: 训练中
- 2: 训练成功
- 3: 训练失败

#### 消息类型 (ai_agent_chat_history.chat_type)
- 1: 用户消息
- 2: 智能体消息

#### 知识库文档运行状态 (ai_rag_knowledge_document.run)
- UNSTART: 未开始
- RUNNING: 运行中
- CANCEL: 已取消
- DONE: 已完成
- FAIL: 失败

#### NFC 写卡模式 (pdc_nfc_write_job.mode)
- `FACTORY_CSV`: 工厂 CSV 模式
- `MANUAL`: 手动模式

#### NFC 验证来源 (pdc_nfc_asset.verify_source)
- `TOUCH`: 触碰自验证
- `MANUAL`: 人工验证
- 空: 工厂 CSV 模式

---

## 附录

### 迁移文件命名规范

格式: `YYYYMMDDHHMM.sql`

示例:
- `202503141335.sql` - 2025 年 3 月 14 日 13:35 创建的迁移
- `202610301000.sql` - 2026 年 10 月 30 日 10:00 创建的迁移

### 最佳实践

1. **表设计原则**:
   - 所有表都有主键
   - 关联字段建立索引
   - 经常查询的字段建立索引
   - 使用外键约束保持数据完整性

2. **字段类型选择**:
   - ID 使用 varchar(32) 或 bigint
   - 状态使用 tinyint 或 varchar（枚举字符串）
   - 时间使用 datetime
   - JSON 配置使用 json 类型
   - 二进制数据使用 longblob / mediumblob

3. **索引策略**:
   - 主键自动创建索引
   - 外键字段创建索引
   - 唯一字段创建唯一索引
   - 经常查询的字段创建普通索引
   - 联合查询创建联合索引

4. **数据迁移**:
   - 每次 schema 变更都创建新的 changeset
   - 不要修改已有的 changeset
   - 使用 Liquibase 的 rollback 功能

---

**文档版本**: 2.0
**最后更新**: 2026-11-04
**维护者**: 蛋宝宝后端开发团队