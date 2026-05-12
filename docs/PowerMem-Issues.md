# PowerMem 集成问题清单

## BUG-001: 用户画像因 OceanBase 连接断开被覆盖

**严重程度**: 严重
**发现日期**: 2026-05-08
**状态**: 待修复

### 问题描述

挂断通话触发用户画像存储时，如果 OceanBase 数据库连接已断开（长时间空闲后），旧的用户画像信息会被当前对话的内容完全覆盖，导致历史画像数据丢失。

### 根因分析

1. 挂断通话 → `save_memory()` → `UserMemory.add()` → `_extract_profile()`
2. `_get_existing_profile_data()` 尝试从 OceanBase 读取旧画像
3. **OceanBase 连接已断开**（`pymysql.err.OperationalError: (2013, 'Lost connection to MySQL server during query')`）
4. 异常被 `except` 静默捕获，返回 `None`
5. LLM prompt 中缺少 `[Current User Profile]` 段，只从当前对话提取
6. 新画像写入 DB，**旧的丰富画像被全量覆盖**

### 日志证据

```
Error retrieving existing profile_content: (pymysql.err.OperationalError)
(2013, 'Lost connection to MySQL server during query')
```

- 成功时画像包含 336+ 字符的丰富信息（昵称、年龄、学历、家庭、兴趣爱好等）
- 失败时画像仅包含当前对话内容（如"用户今日正在搬家，已迁居至苏州"）

### 修复方案

#### 方案 A（快速止血）— xiaozhi-server 侧

在 `core/providers/memory/powermem/powermem.py` 的 `save_memory` 中，`save_profile` 前检查旧画像是否读取成功，失败则跳过画像更新，保留旧数据。

#### 方案 B（根治）— PowerMem SDK 侧

1. 修复 `user_memory.py` 的 `_extract_profile`：旧画像读取失败时不覆盖，直接返回空字符串（`profile_extracted = false`）
2. 修复 OceanBase 连接池的 keepalive/reconnect 配置，避免长时间空闲后连接断开

#### 方案 C（增强）— 提升画像质量

1. 切换到 `profile_type="topics"` 结构化模式，LLM 更容易保留各字段
2. 放宽或取消 1000 字符限制，减少压缩导致的信息丢失

### 涉及文件

| 文件 | 说明 |
|------|------|
| `core/providers/memory/powermem/powermem.py` | xiaozhi-server 侧 PowerMem 集成 |
| `powermem/user_memory/user_memory.py` (SDK) | `_extract_profile` 和 `_get_existing_profile_data` |
| `powermem/user_memory/storage/user_profile.py` (SDK) | OceanBase 画像存储层 |
| `powermem/prompts/user_profile_prompts.py` (SDK) | 画像提取 prompt（含 1000 字符限制） |
