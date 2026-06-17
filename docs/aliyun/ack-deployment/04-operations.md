# ACK 部署：操作与运维

本文件提供完整的部署步骤、可观测性配置、安全建议、回滚方案和运维 FAQ。

## 1. 部署步骤

### Step 1：创建 ACK 集群

参考 `03-cloud-resources.md` 创建 ACK 托管版 Pro 集群，并完成 kubectl 配置。

### Step 2：创建阿里云依赖

1. 创建 ACR 命名空间与仓库。
2. 创建 RDS MySQL 数据库并初始化账号。
3. 创建 Redis 实例。
4. 创建 NAS 文件系统并挂载到 ACK。
5. 上传 `SenseVoiceSmall/model.pt` 到 NAS。

### Step 3：构建并推送镜像

```bash
export VERSION=$(git rev-parse --short HEAD)
./scripts/build-push.sh
```

### Step 4：部署 K8s 资源

```bash
kubectl apply -f ack-deploy/shared/namespace.yaml
kubectl apply -f ack-deploy/shared/storageclass.yaml
kubectl apply -f ack-deploy/shared/pvc.yaml
kubectl apply -f ack-deploy/manager-api/secret.yaml
kubectl apply -f ack-deploy/manager-api/configmap.yaml
kubectl apply -f ack-deploy/manager-api/deployment.yaml
kubectl apply -f ack-deploy/manager-api/service.yaml
kubectl apply -f ack-deploy/manager-api/hpa.yaml
kubectl apply -f ack-deploy/xiaozhi-server/secret.yaml
kubectl apply -f ack-deploy/xiaozhi-server/configmap.yaml
kubectl apply -f ack-deploy/xiaozhi-server/deployment.yaml
kubectl apply -f ack-deploy/xiaozhi-server/service.yaml
kubectl apply -f ack-deploy/xiaozhi-server/hpa.yaml
kubectl apply -f ack-deploy/shared/ingress-alb.yaml
```

### Step 5：配置智控台

1. 浏览器打开 `https://api.your-domain.com/xiaozhi/doc.html` 确认 manager-api 启动。
2. 注册首个超级管理员。
3. 进入 **参数管理**，复制 `server.secret`。
4. 更新 `xiaozhi-server-config-secret` 中的 `.config.yaml`，填入从智控台复制的 `server.secret`：
   ```bash
   kubectl patch secret xiaozhi-server-config-secret -n xiaozhi --type merge \
     -p '{"stringData":{".config.yaml":"manager-api:\n  url: http://manager-api.xiaozhi.svc.cluster.local:8002/xiaozhi\n  secret: <secret>\n"}}'
   kubectl rollout restart deployment/xiaozhi-server -n xiaozhi
   ```
5. 配置 `server.websocket` 和 `server.ota` 为公网访问地址。

### Step 6：验证

```bash
# 查看 Pod 状态
kubectl get pods -n xiaozhi -o wide

# 查看日志
kubectl logs -f deployment/manager-api -n xiaozhi
kubectl logs -f deployment/xiaozhi-server -n xiaozhi

# 测试接口
curl https://api.your-domain.com/xiaozhi/actuator/health
wscat -c ws://your-domain:8000/xiaozhi/v1/
curl http://your-domain:8003/xiaozhi/ota/
```

---

## 2. 可观测性

### 2.1 日志（SLS）

为命名空间开启日志采集：

```bash
kubectl label namespace xiaozhi aliyun.log.app=xiaozhi
```

或在 Pod 中配置 Logtail sidecar。

### 2.2 监控（ARMS Prometheus）

1. 在 ACK 控制台开启 **ARMS Prometheus 监控**。
2. 为 Java 应用暴露 JMX / Micrometer 指标。
3. 配置告警规则：
   - Pod CPU > 80%
   - Pod Memory > 85%
   - 接口 5xx 错误率 > 1%
   - WebSocket 连接数异常下降

### 2.3 告警

通过 CloudMonitor 或 ARMS 告警中心配置短信/钉钉/邮件通知。

---

## 3. 安全建议

| 领域 | 措施 |
|------|------|
| 镜像安全 | ACR 镜像扫描、禁止 `latest` Tag、签名镜像 |
| 网络安全 | 安全组仅开放 443/8000/8003，RDS/Redis 仅允许 VPC 内网访问 |
| 密钥安全 | KMS/OOS 加密，Secret 不提交 Git，定期轮换 API Key |
| 运行时安全 | Pod Security Standards `restricted`，非 root 用户，只读 rootfs |
| 访问控制 | RAM 角色最小权限，ACK RAM 授权只拉取所需镜像 |
| 应用安全 | 启用 xiaozhi-server `server.auth`，manager-api Shiro OAuth2 校验 |

---

## 4. 回滚方案

### 4.1 应用回滚

```bash
kubectl rollout history deployment/manager-api -n xiaozhi
kubectl rollout undo deployment/manager-api -n xiaozhi
kubectl rollout undo deployment/manager-api -n xiaozhi --to-revision=2
```

### 4.2 数据库回滚

- Liquibase changeset 必须保持**向后兼容**。
- 重大变更前，使用 RDS 快照备份。
- 回滚时恢复 RDS 快照并重新部署对应版本镜像。

### 4.3 配置回滚

```bash
kubectl rollout restart deployment/xiaozhi-server -n xiaozhi
```

---

## 5. 运维手册

### 5.1 常见问题

| 现象 | 排查 |
|------|------|
| Pod 启动失败 | `kubectl describe pod` 查看 Event；检查 Secret/ConfigMap 挂载 |
| manager-api 连不上数据库 | 检查 RDS 白名单、账号密码、Liquibase 锁表 |
| xiaozhi-server 无法注册到智控台 | 检查 `MANAGER_API_SECRET` 和 `manager-api.url` |
| WebSocket 连接中断 | 检查 ALB/NLB 空闲超时、HPA 缩容导致连接漂移 |
| OTA 下载失败 | 检查 `8003` 端口可达性、NAS 上 bin 文件权限 |

### 5.2 日常巡检

```bash
kubectl get pods -n xiaozhi
kubectl top nodes
kubectl top pods -n xiaozhi
kubectl get hpa -n xiaozhi
kubectl get ingress -n xiaozhi
```

### 5.3 升级流程

1. 在测试环境验证新镜像。
2. 更新 ConfigMap/Secret 中的新配置项。
3. 执行滚动更新：
   ```bash
   kubectl set image deployment/manager-api manager-api=registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/manager-api:NEW_VERSION -n xiaozhi
   kubectl set image deployment/xiaozhi-server xiaozhi-server=registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/xiaozhi-server:NEW_VERSION -n xiaozhi
   ```
4. 观察 Pod 滚动状态与错误率。
5. 失败则立即 `kubectl rollout undo`。

---

## 6. 成本估算（参考）

| 资源 | 月度参考成本（杭州） |
|------|---------------------|
| ACK Pro 托管版（3 节点 ecs.g7.xlarge） | 约 2,500~3,500 元 |
| RDS MySQL 2C4G | 约 500~800 元 |
| Redis 1G 社区版 | 约 200~400 元 |
| NAS 100G | 约 150~250 元 |
| ALB + NLB | 按量，约 100~300 元 |
| ACR 企业版基础版 | 约 780 元 |
| SLS + ARMS | 按量，约 200~500 元 |
| **总计** | **约 4,500~6,500 元/月** |

---

## 7. 后续优化建议

1. **GitOps**：将 ACK 清单提交到 Git，使用 ArgoCD / Flux 自动同步。
2. **IaC**：使用 Terraform 管理 ACK、RDS、Redis、NAS 等阿里云资源。
3. **CI/CD**：在 GitHub Actions / 云效中集成镜像构建、安全扫描、ACK 部署。
4. **服务网格**：高并发场景下引入 ASM（Istio）实现流量治理、熔断、灰度发布。
5. **多可用区**：生产环境建议 Worker 节点跨 3 个可用区部署。
