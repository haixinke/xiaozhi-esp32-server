---
kind: external_dependency
name: 阿里云 Serverless 应用引擎 (SAE)
slug: aliyun-sae
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### 阿里云 SAE 部署平台
- 角色：项目的主要云部署平台，支持 Python 聊天服务 (xiaozhi-server) 和 Java 管理 API (manager-api) 的无服务器部署
- 集成点：通过 `docs/aliyun/sae-deployment-handbook.md` 提供完整的部署手册，包含容器镜像构建、环境变量配置、弹性伸缩策略
- 使用模式：按 CU 消耗计费，适合中小规模场景和流量波动大的应用
- 关键约束：WebSocket 长连接需要特殊配置（TCP/WS 监听），模型文件需通过 NAS 挂载持久化
- 弹性策略：支持基于 CPU、内存、QPS、响应时间的自动扩缩容，推荐使用 TCP 活跃连接数作为核心指标
- 验证：参考官方文档确认具体的弹性策略配置方法和端口监听设置