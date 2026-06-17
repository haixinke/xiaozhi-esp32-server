# 小智 ESP32 Server 阿里云 ACK 部署手册

本手册说明如何将 **聊天服务（xiaozhi-server，Python）** 与 **后端服务（manager-api，Java Spring Boot）** 部署到 **阿里云容器服务 ACK（Kubernetes）**，实现可扩展、可运维的生产级部署。

> 前置知识：建议先阅读 `docs/Deployment_all.md` 了解单机构建与配置逻辑。ACK 部署是其云原生延伸。

---

## 目标架构

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                              阿里云 ACK 集群                                 │
│  ┌─────────────────────┐         ┌─────────────────────┐                    │
│  │  manager-api Pod     │◄───────►│  xiaozhi-server Pod  │                    │
│  │  Java 21 / 8002      │  HTTP   │  Python 3.12 / 8000  │                    │
│  │  context-path /xiaozhi        │  HTTP 8003 (OTA/视觉) │                    │
│  └──────────┬──────────┘         └──────────┬──────────┘                    │
│             │                                │                               │
│  ┌──────────▼──────────┐      ┌─────────────▼─────────────┐                 │
│  │  ALB Ingress (L7)   │      │  ALB Ingress / NLB (L4)   │                 │
│  │  https://console...  │      │  ws://...:8000/xiaozhi/v1 │                 │
│  └─────────────────────┘      └───────────────────────────┘                 │
└─────────────────────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
  ┌──────────────┐                    ┌──────────────┐
│  阿里云 RDS MySQL │                │  阿里云 Redis  │
│  egg_database    │                │   5.0+         │
└────────────────────┘               └────────────────┘
```

## 关键设计决策

| 决策 | 说明 |
|------|------|
| **ACK 托管版 Pro** | 免运维 Master 节点，支持 SLA 保障。 |
| **ALB Ingress** | manager-api 使用 ALB L7；xiaozhi-server WebSocket 使用 ALB L7 或 NLB L4。 |
| **镜像仓库** | 阿里云 ACR 企业版，与 ACK 同 VPC，支持镜像加速与安全扫描。 |
| **配置分离** | 非敏感配置走 ConfigMap，密钥走 Secret（或 KMS/OOS）。 |
| **模型文件** | `SenseVoiceSmall/model.pt` 建议放在共享 NAS 或预置到镜像中。 |
| **日志监控** | 日志接入 SLS，指标接入 ARMS Prometheus，告警接入 CloudMonitor。 |

## 文档导航

| 文档 | 内容 |
|------|------|
| [01-containerization.md](./ack-deployment/01-containerization.md) | 服务画像、Dockerfile、构建脚本 |
| [02-kubernetes.md](./ack-deployment/02-kubernetes.md) | Namespace、ConfigMap、Secret、Deployment、Service、Ingress、HPA |
| [03-cloud-resources.md](./ack-deployment/03-cloud-resources.md) | ACK、ACR、RDS、Redis、NAS、网络、KMS 密钥管理 |
| [04-operations.md](./ack-deployment/04-operations.md) | 部署步骤、可观测性、安全、回滚、运维 FAQ、成本估算 |
| [SAE 部署手册](./sae-deployment-handbook.md) | SAE Serverless 部署方案（替代 ACK 的轻量方案） |

## 快速开始

1. **准备阿里云资源**：按 `03-cloud-resources.md` 创建 ACK、ACR、RDS、Redis、NAS。
2. **容器化**：按 `01-containerization.md` 准备 Dockerfile 并构建镜像。
3. **部署 K8s**：按 `02-kubernetes.md` 应用 YAML 清单。
4. **运维**：按 `04-operations.md` 完成验证、监控、回滚配置。

## 服务端口速查

| 服务 | 端口 | 协议 | 用途 |
|------|------|------|------|
| manager-api | 8002 | HTTP | 智控台 REST API |
| xiaozhi-server | 8000 | WebSocket | 设备实时语音对话 |
| xiaozhi-server | 8003 | HTTP | OTA 固件 / 视觉分析 |

## 重要提示

- `xiaozhi-server` 启动后需从智控台获取 `server.secret` 并更新到 Secret。
- WebSocket 长连接对负载均衡超时敏感，建议使用 NLB 或 ALB WebSocket 监听。
- 生产环境务必将所有敏感信息迁移到 KMS/OOS，避免明文 Secret。
- Liquibase 数据库迁移需保持向后兼容，重大变更前创建 RDS 快照。

## 相关文档

- `docs/Deployment_all.md` — 单机 Docker 全模块部署
- `main/xiaozhi-server/CLAUDE.md` — Python 服务架构
- `main/manager-api/CLAUDE.md` — Java 服务架构
