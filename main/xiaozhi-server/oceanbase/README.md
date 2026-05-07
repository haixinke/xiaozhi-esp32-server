# OceanBase for PowerMem 使用指南

本目录包含 OceanBase 4.4.x Docker 配置，用于 xiaozhi-esp32-server 的 PowerMem 记忆存储。

## 功能特性

✅ **向量存储** - 支持 1536 维向量嵌入和相似度搜索
✅ **用户画像** - 自动提取和更新用户特征
✅ **知识图谱** - 存储实体关系和知识网络
✅ **高性能** - HNSW 向量索引（VSAG），毫秒级检索
✅ **全文检索** - 中文 ik 分词器支持
✅ **易部署** - 一键 Docker Compose 部署

## 快速开始

### 1. 启动 OceanBase

```bash
cd main/xiaozhi-server
./oceanbase/init-powermem.sh
```

脚本会自动：
- 拉取 OceanBase 4.4.0 Docker 镜像
- 启动容器（端口 2881/2882）
- 等待数据库就绪（约 60-90 秒）
- 初始化 PowerMem 数据库和表结构
- 创建向量索引和全文检索索引

### 2. 配置 PowerMem

编辑 `data/.config.yaml`，添加 PowerMem 配置：

```yaml
Memory:
  powermem:
    type: powermem
    
    # 是否启用用户画像功能
    enable_user_profile: true

    # ========== Database 配置 ==========
    vector_store:
      provider: oceanbase
      config:
        host: localhost
        port: 2881
        user: root@test
        password: '123456'
        db_name: powermem
        collection_name: memories  # 默认表名，如创建维度错误请删除此表或更改名称
        embedding_model_dims: 1536  # 实际测试PowerMem输出1536维，必须与表维度一致
    graph_store:
      enabled: true
      provider: oceanbase
      config:
        host: localhost
        port: 2881
        user: root@test
        password: '123456'
        db_name: powermem
        max_hops: 3
        embedding_model_dims: 1536
```

**注意**：
- OceanBase 默认密码为 `123456`
- 用户名格式为 `root@test`（用户@租户）
- 向量维度为 `1536`（text-embedding-v2 模型）

### 3. 重启服务

```bash
/start-ai
```

## 数据库结构

### 核心表

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `memories` | 对话记忆 | document, embedding(1536维), fulltext_content |
| `user_profiles` | 用户画像 | profile_content, topics |
| `graph_entities` | 知识图谱实体 | name, entity_type, embedding(1536维) |
| `graph_relationships` | 知识图谱关系 | source_entity_id, destination_entity_id, relationship_type |

### 表结构详情

#### memories 表
```sql
CREATE TABLE memories (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(128),
    agent_id VARCHAR(128),
    run_id VARCHAR(128),
    actor_id VARCHAR(128),
    hash VARCHAR(32),
    document LONGTEXT,              -- 记忆文档内容
    embedding VECTOR(1536),          -- 向量嵌入
    metadata JSON,
    category VARCHAR(64),
    fulltext_content LONGTEXT,       -- 全文检索内容
    created_at VARCHAR(128),
    updated_at VARCHAR(128),
    -- 向量索引（VSAG + HNSW）
    VECTOR KEY vidx (embedding),
    -- 全文索引（ik 中文分词）
    FULLTEXT KEY fulltext_index_for_col_text (fulltext_content)
);
```

#### user_profiles 表
```sql
CREATE TABLE user_profiles (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(128),
    profile_content LONGTEXT,        -- 画像内容
    topics JSON,                     -- 主题标签
    created_at VARCHAR(128),
    updated_at VARCHAR(128),
    KEY idx_user_id (user_id)
);
```

#### graph_entities 表
```sql
CREATE TABLE graph_entities (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(64),         -- person/place/concept/event
    embedding VECTOR(1536),          -- 实体向量
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    KEY idx_name (name),
    VECTOR KEY vidx (embedding)
);
```

#### graph_relationships 表
```sql
CREATE TABLE graph_relationships (
    id BIGINT PRIMARY KEY,
    source_entity_id BIGINT(20) NOT NULL,
    relationship_type VARCHAR(128) NOT NULL,
    destination_entity_id BIGINT(20) NOT NULL,
    user_id VARCHAR(128),
    agent_id VARCHAR(128),
    run_id VARCHAR(128),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    -- 复合索引优化查询性能
    KEY idx_r_covering (user_id, source_entity_id, destination_entity_id, relationship_type)
);
```

### 索引说明

所有索引在初始化时自动创建：

| 索引名 | 表名 | 类型 | 配置 |
|--------|------|------|------|
| vidx | memories | VECTOR | VSAG, L2距离, HNSW (M=16, EF_CONSTRUCTION=200) |
| vidx | graph_entities | VECTOR | VSAG, L2距离, HNSW (M=16, EF_CONSTRUCTION=200) |
| fulltext_index_for_col_text | memories | FULLTEXT | ik 中文分词器（智能模式） |
| idx_user_id | user_profiles | BTREE | 单列索引 |
| idx_name | graph_entities | BTREE | 单列索引 |
| idx_r_covering | graph_relationships | BTREE | 复合覆盖索引 |

## 常用命令

### 查看数据库状态

```bash
# 检查容器状态
docker ps | grep xiaozhi-oceanbase

# 查看实时日志
docker logs -f xiaozhi-oceanbase
```

### 进入数据库

```bash
# 方式1：进入交互式 shell
docker exec -it xiaozhi-oceanbase obclient \
  -h127.0.0.1 -P2881 -uroot@test -p123456 powermem

# 方式2：执行单条命令
docker exec xiaozhi-oceanbase obclient \
  -h127.0.0.1 -P2881 -uroot@test -p123456 powermem -e "SHOW TABLES;"
```

### 查询数据

```sql
-- 查看记忆数量
SELECT COUNT(*) FROM memories;

-- 查看用户画像
SELECT * FROM user_profiles;

-- 查看知识图谱实体
SELECT * FROM graph_entities LIMIT 10;

-- 查看知识图谱关系
SELECT * FROM graph_relationships LIMIT 10;

-- 向量相似度搜索
SELECT document, created_at
FROM memories
ORDER BY embedding <-> '[0.1, 0.2, ...]'  -- 替换为实际 1536 维向量
LIMIT 10;

-- 全文检索
SELECT document, created_at
FROM memories
WHERE MATCH(fulltext_content) AGAINST('搜索关键词' IN NATURAL LANGUAGE MODE);
```

### 数据统计

```sql
-- 各表记录统计
SELECT 'memories' AS table_name, COUNT(*) AS count FROM memories
UNION ALL
SELECT 'user_profiles', COUNT(*) FROM user_profiles
UNION ALL
SELECT 'graph_entities', COUNT(*) FROM graph_entities
UNION ALL
SELECT 'graph_relationships', COUNT(*) FROM graph_relationships;

-- 按用户统计记忆数
SELECT user_id, COUNT(*) AS memory_count
FROM memories
GROUP BY user_id
ORDER BY memory_count DESC;

-- 知识图谱统计
SELECT entity_type, COUNT(*) AS count
FROM graph_entities
GROUP BY entity_type;
```

### 备份和恢复

```bash
# 备份单个表
docker exec xiaozhi-oceanbase obclient \
  -uroot@test -p123456 powermem \
  -e "SELECT * FROM memories" > backup_memories.sql

# 备份整个数据库
docker exec xiaozhi-oceanbase obclient \
  -uroot@test -p123456 powermem > backup_powermem.sql

# 恢复数据
docker exec -i xiaozhi-oceanbase obclient \
  -uroot@test -p123456 powermem < backup_powermem.sql
```

### 停止服务

```bash
# 停止容器（保留数据）
docker-compose -f docker-compose-oceanbase.yml down

# 停止并删除数据卷
docker-compose -f docker-compose-oceanbase.yml down -v

# 完全清理（包括数据文件）
docker-compose -f docker-compose-oceanbase.yml down -v
rm -rf ./oceanbase/data
```

## 性能优化

### 调整内存限制

编辑 `docker-compose-oceanbase.yml`：

```yaml
environment:
  - OB_MEMORY_LIMIT=8G     # 增加内存限制（默认 6G）
  - OB_DATAFILE_SIZE=20G   # 数据文件大小
  - OB_LOG_DISK_SIZE=10G   # 日志磁盘大小
```

### 向量索引参数

初始化脚本中已配置最优参数：

```sql
VECTOR KEY vidx (embedding)
WITH (
  LIB=VSAG,                   -- 使用 VSAG 库
  DISTANCE=L2,                -- L2 距离算法
  M=16,                       -- HNSW M 参数
  EF_CONSTRUCTION=200,        -- HNSW 构建参数
  TYPE=HNSW,                  -- HNSW 索引类型
  EF_SEARCH=64                -- 搜索参数
)
```

### 全文检索优化

```sql
-- ik 中文分词器（智能模式）
FULLTEXT KEY fulltext_index_for_col_text (fulltext_content)
WITH PARSER ik PARSER_PROPERTIES=(ik_mode="smart")
```

## 故障排查

### 容器启动失败

```bash
# 查看启动日志
docker logs xiaozhi-oceanbase

# 检查端口占用
lsof -ti:2881
lsof -ti:2882

# 重新初始化
docker-compose -f docker-compose-oceanbase.yml down -v
./oceanbase/init-powermem.sh
```

### 连接失败

```bash
# 测试连接（使用密码 123456）
docker exec xiaozhi-oceanbase obclient \
  -h127.0.0.1 -P2881 -uroot@test -p123456 -e"SELECT 1"

# 检查数据库是否存在
docker exec xiaozhi-oceanbase obclient \
  -h127.0.0.1 -P2881 -uroot@test -p123456 -e"SHOW DATABASES;"
```

### PowerMem 初始化失败

检查 `data/.config.yaml` 配置：
- 确认 `enable_user_profile: true` 是布尔值（不是字符串 `"true"`）
- 确认数据库密码为 `123456`
- 确认用户名格式为 `root@test`
- 确认向量维度配置为 `1536`
- 查看服务日志：`tail -f logs/app.log`

### 索引未生效

```sql
-- 检查索引状态
SHOW INDEX FROM memories;
SHOW INDEX FROM graph_entities;

-- 检查向量索引
SELECT * FROM information_schema.tables
WHERE table_schema = 'powermem'
AND table_name IN ('memories', 'graph_entities');
```

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| OceanBase | 4.4.0.0-1 | 分布式关系数据库 |
| PowerMem | 0.3.0+ | AI 记忆框架 |
| pyobvector | 0.2.26+ | OceanBase Vector SDK |
| VSAG | - | 向量索引库 |
| Docker Compose | 3.8+ | 容器编排 |

## 相关链接

- [PowerMem GitHub](https://github.com/oceanbase/powermem)
- [OceanBase 文档](https://www.oceanbase.com/docs)
- [pyobvector 文档](https://github.com/oceanbase/obvector-sdk)
- [项目文档](../../docs/powermem-integration.md)

## 许可证

本项目遵循 MIT 许可证。
