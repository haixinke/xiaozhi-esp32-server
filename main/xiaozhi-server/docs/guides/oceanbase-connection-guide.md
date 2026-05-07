# OceanBase 本地连接配置指南

## Docker 容器配置

### 启动 OceanBase
```bash
cd main/xiaozhi-server
./oceanbase/init-powermem.sh
```

或手动启动：
```bash
docker-compose -f docker-compose-oceanbase.yml up -d
```

### 连接信息
- **Host**: `localhost` 或 `127.0.0.1`（容器内部）或 `172.19.0.2`（容器IP）
- **Port**: `2881`
- **User**: `root@test`
- **Password**: `123456`
- **Database**: `oceanbase` 或 `powermem`

## PowerMem 配置要点

### 1. 向量维度配置（关键！）

**常见错误**：向量维度不匹配导致保存失败

| Embedding 模型 | 输出维度 | embedding_model_dims |
|---------------|---------|---------------------|
| 阿里云 text-embedding-v4 | **1536** | 1536 |
| 阿里云 text-embedding-v3 | **1536** | 1536 |
| 智谱 embedding-3 | **2048** | 2048 |
| 智谱 embedding-2 | **1024** | 1024 |
| OpenAI text-embedding-3-small | **1536** | 1536 |
| OpenAI text-embedding-ada-002 | **1536** | 1536 |

### 2. 完整配置示例（data/.config.yaml）

```yaml
Memory:
  powermem:
    type: powermem
    enable_user_profile: true

    # LLM 配置（使用阿里云 qwen）
    llm:
      provider: openai  # 使用 openai 兼容接口
      config:
        api_key: sk-你的阿里云API密钥
        model: qwen3.6-flash
        openai_base_url: https://dashscope.aliyuncs.com/compatible-mode/v1/

    # Embedder 配置（使用阿里云 text-embedding-v4）
    embedder:
      provider: openai  # 使用 openai 兼容接口
      config:
        api_key: sk-你的阿里云API密钥
        model: text-embedding-v4
        openai_base_url: https://dashscope.aliyuncs.com/compatible-mode/v1

    # OceanBase 向量存储配置
    vector_store:
      provider: oceanbase
      config:
        host: localhost
        port: 2881
        user: root@test
        password: '123456'
        db_name: powermem
        collection_name: memories
        embedding_model_dims: 1536  # ⚠️ 必须与 embedding 模型输出维度一致

    # 图数据库配置（可选）
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
        embedding_model_dims: 1536  # ⚠️ 必须与 embedding 模型输出维度一致
```

### 3. 关键配置参数说明

#### LLM Provider
- **qwen**: 阿里云通义千问，使用 `dashscope_base_url`
- **openai**: OpenAI 兼容接口，使用 `openai_base_url`

#### Embedder Provider
- **qwen**: 阿里云 embedding，使用 `dashscope_base_url`
- **openai**: OpenAI 兼容接口，使用 `openai_base_url`（推荐）

⚠️ **重要**：PowerMem SDK 对参数名有严格要求，必须使用 `openai_base_url` 而不是 `dashscope_base_url`

## 数据库管理

### 查看表结构
```bash
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -Dpowermem -e "SHOW TABLES;"
```

### 查看记忆数量
```bash
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -Dpowermem -e "SELECT COUNT(*) FROM memories;"
```

### 删除表（维度错误时需要）
```bash
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -Dpowermem -e "DROP TABLE IF EXISTS memories, graph_entities, graph_relationships, user_profiles;"
```

### 查看表向量维度
```bash
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -Dpowermem -e "DESCRIBE memories;" | grep embedding
```

## 常见问题排查

### 问题 1: 记忆保存失败，日志显示 `action_counts: {"ADD": 0, "NONE": 1}`

**原因**：向量维度不匹配

**排查步骤**：
1. 检查配置文件中的 `embedding_model_dims`
2. 测试 embedding 模型的实际输出维度
3. 确保数据库表的向量维度与配置一致

**解决方案**：
- 修正 `embedding_model_dims` 配置
- 删除旧表，重启服务让其重新创建

### 问题 2: PowerMem 初始化失败，错误 `Extra inputs are not permitted [dashscope_base_url]`

**原因**：参数名错误

**解决方案**：将 `dashscope_base_url` 改为 `openai_base_url`

### 问题 3: 表已存在但维度错误

**原因**：之前创建了错误维度的表

**解决方案**：
```bash
# 删除所有相关表
docker exec xiaozhi-oceanbase obclient -h127.0.0.1 -P2881 -uroot@test -p123456 -Dpowermem -e "DROP TABLE IF EXISTS memories, graph_entities, graph_relationships, user_profiles;"

# 重启服务让其重新创建表
ps aux | grep "python.*app.py" | grep -v grep | awk '{print $2}' | xargs kill
cd main/xiaozhi-server
nohup .venv/bin/python app.py > logs/app.log 2>&1 &
```

## 测试 Embedding 模型输出维度

### 阿里云 text-embedding-v4
```python
import requests
import json

url = 'https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings'
headers = {
    'Authorization': 'Bearer sk-你的API密钥',
    'Content-Type': 'application/json'
}
data = {
    'model': 'text-embedding-v4',
    'input': '测试文本'
}

response = requests.post(url, headers=headers, json=data)
result = response.json()
embedding = result['data'][0]['embedding']
print(f'输出维度: {len(embedding)}')
```

### 智谱 embedding-3
```python
import requests
import json

url = 'https://open.bigmodel.cn/api/paas/v4/embeddings'
headers = {
    'Authorization': 'Bearer 你的API密钥',
    'Content-Type': 'application/json'
}
data = {
    'model': 'embedding-3',
    'input': '测试文本'
}

response = requests.post(url, headers=headers, json=data)
result = response.json()
embedding = result['data'][0]['embedding']
print(f'输出维度: {len(embedding)}')
```

## 验证配置是否正确

启动服务后，查看日志：
```bash
tail -50 logs/app.log | grep "PowerMem initialized successfully"
```

应该看到类似输出：
```
PowerMem initialized successfully: mode=UserMemory (用户画像模式), database=oceanbase, llm=openai, embedding=openai
```

如果看到错误信息，检查：
1. API Key 是否正确
2. 向量维度是否匹配
3. OceanBase 容器是否运行
4. 网络连接是否正常
