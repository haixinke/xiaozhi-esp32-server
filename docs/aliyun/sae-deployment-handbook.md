# 小智 ESP32 Server 阿里云 SAE 部署手册

本手册说明如何将 **聊天服务（xiaozhi-server，Python）** 与 **后端服务（manager-api，Java Spring Boot）** 部署到 **阿里云 Serverless 应用引擎（SAE）**。

> 与 ACK 不同，SAE 是 Serverless 应用托管平台：无需管理 K8s 节点，按实际 CU 消耗计费，适合快速上线、测试或中小规模场景。

---

## 1. 适用场景

| 场景 | 是否推荐 SAE |
|------|-------------|
| 快速验证/测试 | 推荐 |
| 中小规模、流量波动大 | 推荐 |
| 无专职运维团队 | 推荐 |
| 7×24 小时稳定生产 | 可考虑，但长期成本可能高于 ACK |
| 复杂微服务/自定义 K8s 资源 | 不推荐，建议 ACK |
| 强 WebSocket 长连接需求 | 需谨慎配置，ACK 更灵活 |

---

## 2. 目标架构

```text
┌─────────────────────────────────────────────────────────────┐
│                        阿里云 SAE                            │
│  ┌─────────────────────┐     ┌─────────────────────┐        │
│  │  manager-api 应用    │     │  xiaozhi-server 应用 │        │
│  │  Java 21 / 8002      │     │  Python 3.12        │        │
│  │  镜像/JAR 部署        │     │  镜像部署            │        │
│  └──────────┬──────────┘     └──────────┬──────────┘        │
│             │                            │                   │
│  ┌──────────▼──────────┐  ┌──────────────▼────────────┐     │
│  │   ALB / SLB 公网    │  │   SLB TCP/WS 公网         │     │
│  │   https://api...    │  │   ws://...:8000           │     │
│  └─────────────────────┘  └───────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
         │                            │
         ▼                            ▼
  ┌──────────────┐            ┌──────────────┐
│  阿里云 RDS    │            │  阿里云 Redis │
│  MySQL 8.0    │            │  社区版/企业版 │
└────────────────┘            └────────────────┘
         │
         ▼
  ┌──────────────┐
│  阿里云 NAS    │
│  模型文件 + 配置 │
└────────────────┘
```

---

## 3. 前置准备

### 3.1 阿里云资源清单

| 资源 | 建议规格 | 用途 |
|------|---------|------|
| SAE 命名空间 | `xiaozhi` | 应用隔离 |
| VPC + 交换机 | 与 RDS/Redis/NAS 同 VPC | 应用网络 |
| 安全组 | 允许 VPC 内互通 | 网络安全 |
| ACR 镜像仓库 | 个人版/企业版 | 镜像托管 |
| RDS MySQL 8.0 | 2C4G 起步 | manager-api 数据库 |
| Redis | 1G 起步 | 缓存、会话 |
| NAS | 50G 起步 | 模型文件、配置持久化 |
| ALB/SLB | 按量计费 | 公网访问入口 |

### 3.2 本地工具

```bash
# 安装阿里云 CLI
brew install aliyun-cli          # macOS
aliyun configure                 # 配置 AccessKey/Region

# 或安装 SAE 专用 CLI（如有）
pip install aliyun-sae-cli
```

### 3.3 项目目录约定

```text
sae-deployment/
├── scripts/
│   └── build-push.sh      ← 构建推送脚本
└── ../sae-deployment-handbook.md
```

脚本位于 `docs/aliyun/sae-deployment/scripts/build-push.sh`，已配置 ACR 地址与自动登录。

---

## 4. 构建镜像并推送 ACR

```bash
完整脚本位于 `docs/aliyun/sae-deployment/scripts/build-push.sh`，使用方式：

```bash
# 在项目根目录执行
export ACR_USERNAME="<你的阿里云账号>"
export ACR_PASSWORD="<你的密码或RAM子账号AccessKey>"
bash docs/aliyun/sae-deployment/scripts/build-push.sh
```

也可手动指定版本号：

```bash
VERSION=v1.0 bash docs/aliyun/sae-deployment/scripts/build-push.sh
```

---

## 5. 部署 manager-api

### 5.1 控制台方式

1. 进入 [SAE 控制台](https://sae.console.aliyun.com/)。
2. 选择或创建命名空间，例如 `cn-hangzhou:xiaozhi`。
3. 点击 **创建应用** → **镜像部署**。
4. 填写基础信息：
   - **应用名称**：`manager-api`
   - **镜像类型**：容器镜像服务 ACR
   - **镜像地址**：`registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/manager-api:1.0`
   - **实例规格**：0.5 vCPU / 1 GB 起步
   - **实例数量**：1~2
   - **端口**：`8002`
5. 配置环境变量：
   ```text
   SPRING_PROFILES_ACTIVE=prod
   SERVER_PORT=8002
   SPRING_DATASOURCE_DRUID_URL=jdbc:mysql://<rds-host>:<port>/<db-name>?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true
   SPRING_DATASOURCE_DRUID_USERNAME=<your-db-username>
   SPRING_DATASOURCE_DRUID_PASSWORD=<your-db-password>
   SPRING_DATA_REDIS_HOST=<redis-host>
   SPRING_DATA_REDIS_PORT=6379
   SPRING_DATA_REDIS_PASSWORD=<your-redis-password>
   SPRING_DATA_REDIS_DATABASE=0
   WECHAT_MINIPROGRAM_APPID=<your-appid>
   WECHAT_MINIPROGRAM_SECRET=<your-secret>
   WECHAT_PAY_MCHID=<your-mchid>
   WECHAT_PAY_SERIAL_NO=<your-serial-no>
   WECHAT_PAY_PRIVATE_KEY=<your-private-key-pem>
   WECHAT_PAY_API_V3_KEY=<your-api-v3-key>
   WECHAT_PAY_PUB_KEY_ID=<your-pub-key-id>
   WECHAT_PAY_PUB_KEY=<your-pub-key-pem>
   WECHAT_PAY_NOTIFY_URL=<https://your-domain/payment/notify>
   KNIFE4J_ENABLE=false
   ```
   > `WECHAT_PAY_*` 敏感配置建议通过 SAE **保密字典（Secret）** 注入，不要直接写在普通环境变量里。
   > `KNIFE4J_ENABLE` 默认值为 `false`，未设置时生产环境不会暴露 Knife4j 接口文档；仅在需要临时调试时显式设为 `true`。
6. 开启 **访问日志** 和 **应用监控**。
7. 点击 **创建应用**。

### 5.2 CLI 方式

```bash
aliyun sae CreateApplication \
  --AppName manager-api \
  --NamespaceId cn-hangzhou:xiaozhi \
  --VpcConfig '{
    "vpcId":"your-vpc-id",
    "vSwitchId":"your-vsw-id",
    "securityGroupId":"your-sg-id"
  }' \
  --ImageUrl registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/manager-api:1.0 \
  --PackageType Image \
  --Cpu 500 \
  --Memory 1024 \
  --Replicas 1 \
  --Port 8002 \
  --Envs '[
    {"name":"SPRING_PROFILES_ACTIVE","value":"prod"},
    {"name":"SERVER_PORT","value":"8002"},
    {"name":"SPRING_DATASOURCE_DRUID_URL","value":"jdbc:mysql://<rds-host>:3306/egg_database?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true"},
    {"name":"SPRING_DATASOURCE_DRUID_USERNAME","value":"xiaozhi"},
    {"name":"SPRING_DATASOURCE_DRUID_PASSWORD","value":"<your-password>"},
    {"name":"SPRING_DATA_REDIS_HOST","value":"<redis-host>"},
    {"name":"SPRING_DATA_REDIS_PORT","value":"6379"},
    {"name":"SPRING_DATA_REDIS_PASSWORD","value":"<your-redis-password>"},
    {"name":"SPRING_DATA_REDIS_DATABASE","value":"0"},
    {"name":"WECHAT_MINIPROGRAM_APPID","value":"<your-appid>"},
    {"name":"WECHAT_MINIPROGRAM_SECRET","value":"<your-secret>"},
    {"name":"WECHAT_PAY_MCHID","value":"<your-mchid>"},
    {"name":"WECHAT_PAY_SERIAL_NO","value":"<your-serial-no>"},
    {"name":"WECHAT_PAY_PRIVATE_KEY","value":"<your-private-key-pem>"},
    {"name":"WECHAT_PAY_API_V3_KEY","value":"<your-api-v3-key>"},
    {"name":"WECHAT_PAY_PUB_KEY_ID","value":"<your-pub-key-id>"},
    {"name":"WECHAT_PAY_PUB_KEY","value":"<your-pub-key-pem>"},
    {"name":"WECHAT_PAY_NOTIFY_URL","value":"<https://your-domain/payment/notify>"},
    {"name":"KNIFE4J_ENABLE","value":"false"}
  ]'
```

### 5.3 开启公网访问

1. 进入应用详情页 → **访问配置**。
2. 开启 **公网访问**。
3. 选择 **ALB** 或 **SLB**。
4. 绑定域名：`api.your-domain.com`。
5. 配置 HTTPS 证书。
6. 访问测试：`https://api.your-domain.com/xiaozhi/actuator/health`

---

## 6. 部署 xiaozhi-server

### 6.1 关键注意点

- xiaozhi-server 需要暴露 **两个端口**：
  - `8000`：WebSocket（设备长连接）
  - `8003`：HTTP（OTA 固件、视觉分析）
- WebSocket 对长连接敏感，建议 8000 端口使用 **TCP/WS 监听**，避免默认 HTTP 短连接超时。
- 模型文件 `SenseVoiceSmall/model.pt` 必须通过 **NAS 挂载** 持久化。
- 配置文件 `config.yaml` 和 `data/.config.yaml` 建议放在 NAS，通过挂载方式读取。

### 6.2 控制台方式

1. 在 SAE 控制台点击 **创建应用** → **镜像部署**。
2. 填写基础信息：
   - **应用名称**：`xiaozhi-server`
   - **镜像地址**：`registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/xiaozhi-server:1.0`
   - **实例规格**：1 vCPU / 2 GB 起步
   - **实例数量**：1~2
   - **端口**：添加 `8000` 和 `8003`
3. 配置环境变量：
   ```text
   TZ=Asia/Shanghai
   ```
4. **NAS 挂载**：
   - 将 NAS 的 `/xiaozhi/models` 挂载到容器 `/app/models`
   - 将 NAS 的 `/xiaozhi/data` 挂载到容器 `/app/data`
5. 配置启动命令（默认即可）：
   ```text
   python app.py
   ```
6. 创建应用。

### 6.3 CLI 方式

```bash
aliyun sae CreateApplication \
  --AppName xiaozhi-server \
  --NamespaceId cn-hangzhou:xiaozhi \
  --VpcConfig '{
    "vpcId":"your-vpc-id",
    "vSwitchId":"your-vsw-id",
    "securityGroupId":"your-sg-id"
  }' \
  --ImageUrl registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/xiaozhi-server:1.0 \
  --PackageType Image \
  --Cpu 1000 \
  --Memory 2048 \
  --Replicas 1 \
  --Port 8000 \
  --Envs '[{"name":"TZ","value":"Asia/Shanghai"}]'
```

> 注意：CLI 创建后，需到控制台补充 8003 端口和 NAS 挂载。

### 6.4 访问 manager-api

在同一 SAE 命名空间内，应用可通过内网域名互相访问：

```yaml
manager-api:
  url: http://manager-api.xiaozhi.svc.cluster.local:8002/xiaozhi
  secret: "<从智控台 server.secret 获取>"
```

SAE 内网访问地址通常格式为：

```text
http://<app-name>.<namespace>.svc.cluster.local:<port>
```

---

## 7. NAS 与配置管理

### 7.1 NAS 目录结构

```text
/xiaozhi
├── models
│   └── SenseVoiceSmall
│       └── model.pt
├── data
│   └── .config.yaml
└── tmp
```

### 7.2 `.config.yaml` 示例

```yaml
server:
  websocket: ws://your-domain:8000/xiaozhi/v1/
  vision_explain: http://your-domain:8003/mcp/vision/explain
  auth:
    enabled: true

manager-api:
  url: http://manager-api.xiaozhi.svc.cluster.local:8002/xiaozhi
  secret: "<从智控台 server.secret 获取>"

selected_module:
  VAD: SileroVAD
  ASR: FunASR
  LLM: DoubaoLLM
  TTS: EdgeTTS
  Memory: nomem
  Intent: function_call

LLM:
  DoubaoLLM:
    api_key: "<your-llm-key>"
```

### 7.3 配置更新流程

1. 修改 NAS 上的 `data/.config.yaml`。
2. 在 SAE 控制台重启 `xiaozhi-server` 应用。
3. 观察日志确认配置生效。

---

## 8. 自动扩缩容

SAE 支持多种弹性策略：

| 策略 | 触发条件 |
|------|---------|
| CPU 使用率 | > 70% 扩容 |
| 内存使用率 | > 80% 扩容 |
| QPS | 超过阈值扩容 |
| 响应时间 | P99 超过阈值扩容 |
| 定时策略 | 高峰期自动扩容、夜间缩容 |

### 8.1 推荐配置

| 应用 | 最小实例 | 最大实例 | 触发条件 |
|------|---------|---------|---------|
| manager-api | 1 | 3 | CPU > 70% |
| xiaozhi-server | 1 | 5 | CPU > 60% |

### 8.2 CLI 配置示例

```bash
aliyun sae CreateApplicationScalingRule \
  --AppName manager-api \
  --ScalingRuleName cpu-scale \
  --ScalingRuleType CPU \
  --ScalingRuleMetric "cpu>70" \
  --MinReplicas 1 \
  --MaxReplicas 3
```

---

## 9. 智控台配置

部署 manager-api 后，必须完成以下配置：

1. 浏览器打开 `https://api.your-domain.com/xiaozhi/doc.html`。
2. 注册首个超级管理员账号。
3. 进入 **参数管理** → 找到 `server.secret`。
4. 复制该值，写入 NAS 上的 `data/.config.yaml` 的 `manager-api.secret`。
5. 重启 `xiaozhi-server` 应用。
6. 进入 **参数管理**，配置：
   - `server.websocket`：`ws://your-domain:8000/xiaozhi/v1/`
   - `server.ota`：`http://your-domain:8003/xiaozhi/ota/`

---

## 10. 验证

```bash
# 查看应用状态
aliyun sae DescribeApplicationConfig --AppName manager-api
aliyun sae DescribeApplicationConfig --AppName xiaozhi-server

# 查看日志
aliyun sae DescribeWebLog --AppName manager-api
aliyun sae DescribeWebLog --AppName xiaozhi-server

# 测试 manager-api
curl https://api.your-domain.com/xiaozhi/actuator/health

# 测试 WebSocket
wscat -c ws://your-domain:8000/xiaozhi/v1/

# 测试 OTA 接口
curl http://your-domain:8003/xiaozhi/ota/
```

---

## 11. 常见问题

| 问题 | 原因/解决 |
|------|----------|
| WebSocket 连接频繁断开 | SAE HTTP 监听空闲超时较短，8000 端口改用 TCP/WS 监听 |
| 模型文件丢失 | 未正确挂载 NAS，或 Pod 重启后未持久化 |
| xiaozhi-server 连不上 manager-api | 检查 `manager-api.url` 和 `server.secret` 是否正确 |
| 应用启动慢 | Python 依赖加载和模型初始化耗时，增加启动探针时间 |
| 费用超出预期 | SAE 按 CU 计费，长期满负载运行成本可能高于 ACK |

---

## 12. 安全建议

| 领域 | 措施 |
|------|------|
| 镜像安全 | 使用 ACR 镜像扫描，禁止 `latest` Tag |
| 网络安全 | 仅开放必要端口，RDS/Redis 只允许 VPC 内网访问 |
| 密钥安全 | 数据库密码、API Key 不要写入镜像，使用 SAE 环境变量或 NAS 加密配置 |
| 访问控制 | 配置 RAM 角色，最小权限原则 |
| 应用安全 | 开启 xiaozhi-server `server.auth`，manager-api 保持 Shiro OAuth2 认证 |

---

## 13. SAE vs ACK 选择参考

| 维度 | SAE | ACK |
|------|-----|-----|
| 部署复杂度 | 低 | 高 |
| 运维成本 | 低 | 高 |
| 灵活性 | 中 | 高 |
| 长期稳定成本 | 可能更高 | 可能更低 |
| WebSocket 长连接 | 需额外配置 | 更灵活 |
| 自动扩缩容 | 秒级 | 分钟级 |
| 适合场景 | 测试/中小规模 | 正式生产/复杂架构 |

---

## 14. 相关文档

- `docs/ack-deployment-handbook.md` — ACK 生产部署手册
- `docs/ack-deployment/01-containerization.md` — Dockerfile 与镜像构建
- `main/xiaozhi-server/CLAUDE.md` — Python 服务架构
- `main/manager-api/CLAUDE.md` — Java 服务架构
- `docs/Deployment_all.md` — 单机 Docker Compose 部署
