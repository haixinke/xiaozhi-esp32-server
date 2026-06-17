# ACK 部署：阿里云资源与存储

本文件说明 ACK 集群、ACR、RDS、Redis、NAS 等阿里云资源的选型、网络规划和存储配置。

## 1. 资源选型

| 资源 | 建议规格 | 用途 |
|------|---------|------|
| ACK 托管版 Pro | 3~6 节点，ecs.g7.xlarge 起步 | 运行业务 Pod |
| ACR 企业版 | 基础版即可 | 镜像托管与扫描 |
| RDS MySQL 8.0 | 2C4G 起步 | manager-api 数据库 |
| Redis 企业版/社区版 | 1G 起步 | 缓存、会话、限流 |
| NAS 通用型 | 100G 起步 | 模型文件、音频缓存 |
| ALB | 按量计费 | manager-api 七层入口 |
| NLB（可选） | 按量计费 | WebSocket 四层入口 |
| SLS Project / Logstore | 按量计费 | 日志收集 |
| ARMS Prometheus | 按量计费 | 指标监控 |

## 2. ACK 集群创建

```bash
aliyun cs POST /clusters \
  --body '{
    "name": "xiaozhi-prod",
    "cluster_type": "ManagedKubernetes",
    "region_id": "cn-hangzhou",
    "vpc_id": "your-vpc-id",
    "vswitch_ids": ["your-vsw-id"],
    "worker_instance_types": ["ecs.g7.xlarge"],
    "num_of_nodes": 3,
    "service_cidr": "172.21.0.0/20",
    "pod_cidr": "172.20.0.0/16"
  }'
```

## 3. 本地工具配置

```bash
# 安装阿里云 CLI
brew install aliyun-cli
aliyun configure

# 配置 kubectl
aliyun cs GET /k8s/[cluster-id]/user_config | jq -r '.config' > ~/.kube/config-ack
export KUBECONFIG=~/.kube/config-ack
```

## 4. 镜像仓库 ACR

1. 创建 ACR 企业版实例。
2. 创建命名空间 `your-namespace`。
3. 创建仓库 `xiaozhi-server`、`manager-api`。
4. 配置 ACK Worker 节点 RAM 角色，使其可拉取 ACR 镜像（或配置 imagePullSecret）。

## 5. 数据库 RDS MySQL

1. 创建数据库 `egg_database`，字符集 `utf8mb4_unicode_ci`。
2. 创建账号 `xiaozhi`，授权 `egg_database` 读写权限。
3. 在 RDS 白名单中添加 ACK 节点 VPC 网段。
4. manager-api 启动时 Liquibase 自动建表。

## 6. 缓存 Redis

1. 创建 Redis 实例，建议开启 **密码认证** 与 **私有网络访问**。
2. 白名单添加 ACK 节点 VPC 网段。
3. 配置连接池参数（参考 `application-dev.yml`）。

## 7. OceanBase 兼容说明

本地开发使用 OceanBase 容器。ACK 环境可直接使用 **RDS MySQL 8.0** 替代。若坚持使用 OceanBase，请部署 OB Cloud 或自建 OB 集群并配置对应连接串。

## 8. NAS 存储

### 8.1 StorageClass

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: alicloud-nas-subpath
provisioner: nasplugin.csi.alibabacloud.com
parameters:
  server: "your-nas-mount-target.nas.aliyuncs.com:/"
  path: "/xiaozhi"
reclaimPolicy: Retain
allowVolumeExpansion: true
```

### 8.2 PVC

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: xiaozhi-models-pvc
  namespace: xiaozhi
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: alicloud-nas-subpath
  resources:
    requests:
      storage: 20Gi
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: xiaozhi-data-pvc
  namespace: xiaozhi
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: alicloud-nas-subpath
  resources:
    requests:
      storage: 10Gi
```

### 8.3 模型文件准备

1. 创建 NAS 挂载点，目录结构：
   ```text
   /xiaozhi/models/SenseVoiceSmall/model.pt
   ```
2. 在 Pod 中通过 PVC 挂载到 `/app/models`。
3. 升级模型时，替换 NAS 上的文件并滚动重启 Pod。

## 9. 网络规划

- **VPC**：建议使用专有网络，与 RDS/Redis/NAS 同 VPC。
- **安全组**：
  - Worker 节点：允许 VPC 内访问 RDS/Redis/NAS。
  - ALB/NLB：对外暴露 443、8000、8003。
- **NAT 网关**：若 Pod 需访问公网（如下载模型、调用外部 API），配置 NAT 网关或 EIP。

## 10. 密钥管理

### 10.1 配置分层

| 层级 | 内容 | 方式 |
|------|------|------|
| 默认配置 | `config.yaml` 中通用参数 | 打包进镜像或 ConfigMap |
| 环境配置 | 数据库地址、Redis 地址、域名 | ConfigMap / 环境变量 |
| 敏感配置 | API Key、数据库密码、server.secret | Secret / KMS |
| 运行时配置 | 智控台参数管理中的模型密钥 | 通过 manager-api 下发 |

### 10.2 阿里云 KMS（推荐）

1. 开通 KMS 并创建密钥。
2. 在 ACK 集群安装 KMS Plugin。
3. 使用 `kms-secret` 类型创建 Secret，KMS 自动解密注入 Pod。

### 10.3 xiaozhi-server 与 manager-api 连接

1. 部署 manager-api 后，登录智控台，进入 **参数管理** → `server.secret`。
2. 复制该值到 `xiaozhi-server-secret` 的 `MANAGER_API_SECRET`。
3. 确保 `xiaozhi-server-config` 中的 `manager-api.url` 指向 K8s Service 地址。
