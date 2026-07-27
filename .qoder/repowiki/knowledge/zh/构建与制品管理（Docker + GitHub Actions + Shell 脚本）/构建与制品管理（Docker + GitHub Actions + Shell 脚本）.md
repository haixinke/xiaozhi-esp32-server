---
kind: build_system
name: 构建与制品管理（Docker + GitHub Actions + Shell 脚本）
category: build_system
scope:
    - '**'
source_files:
    - Dockerfile-server
    - Dockerfile-web
    - Dockerfile-server-base
    - main/xiaozhi-server/Dockerfile
    - main/manager-api/Dockerfile
    - main/manager-web/Dockerfile
    - .github/workflows/docker-image.yml
    - .github/workflows/build-base-image.yml
    - scripts/run-build-manager.sh
    - scripts/run-build-web.sh
    - scripts/run-build-xiaozhi.sh
    - docs/aliyun/sae-deployment/scripts/build-push-manager-api.sh
    - main/xiaozhi-server/requirements.txt
    - main/manager-api/pom.xml
    - main/xiaozhi-server/docker-compose.yml
    - docs/docker/start.sh
    - docs/docker/nginx.conf
    - docker-setup.sh
---

## 1. 使用的系统与工具
- **容器化**：多阶段 Dockerfile，Python 3.12-slim、Node 18、Maven/Java 21、Nginx 等基础镜像。
- **CI/CD**：GitHub Actions（`.github/workflows`），通过 `docker/setup-buildx-action` + `docker/build-push-action` 构建并推送镜像到 GHCR。
- **本地构建入口**：`scripts/run-build-*.sh` 三个脚本统一封装 ACR 登录、版本注入、调用 `docs/aliyun/sae-deployment/scripts/*` 实际执行 `docker buildx build --push`。
- **一键部署**：`docker-setup.sh` 在 Ubuntu/Debian 上自动安装 Docker、选择镜像源、下载配置与模型、拉起 `docker-compose_all.yml`。

## 2. 关键文件与位置
- 根级 Dockerfile：`Dockerfile-server`（仅拷贝 Python 应用代码）、`Dockerfile-web`（多阶段构建 Vue + Spring Boot 单体镜像）。
- 子服务独立镜像：`main/xiaozhi-server/Dockerfile`、`main/manager-api/Dockerfile`、`main/manager-web/Dockerfile`。
- CI 流水线：`.github/workflows/docker-image.yml`（tag v*.*.* 触发，构建 server/web 镜像并推送到 ghcr.io）、`.github/workflows/build-base-image.yml`（依赖变更时重建 base image）。
- 本地构建脚本：`scripts/run-build-manager.sh`、`run-build-web.sh`、`run-build-xiaozhi.sh`、`run-mirror-funasr.sh`。
- 阿里云 SAE 构建脚本：`docs/aliyun/sae-deployment/scripts/build-push-*.sh`，由 `scripts/run-build-*.sh` 委派执行。
- 依赖声明：`main/xiaozhi-server/requirements.txt`、`main/manager-api/pom.xml`、各前端 `package.json`。
- 编排与启动：`main/xiaozhi-server/docker-compose.yml`、`docs/docker/start.sh`、`docs/docker/nginx.conf`。

## 3. 架构与约定
- **分层镜像策略**：先构建 `server-base`（含 Python 运行时与系统依赖），再由 `Dockerfile-server` 基于其只拷贝业务代码，减小最终镜像体积。
- **多阶段构建**：
  - `Dockerfile-web`：第一阶段 Node 构建 Vue，第二阶段 Maven 构建 Spring Boot JAR，第三阶段以轻量 JRE+Nginx 运行。
  - `main/manager-web/Dockerfile`：Node 构建产物 + Nginx 运行时分离。
  - `main/manager-api/Dockerfile`：Maven 构建 + JRE 运行，支持 CCR CA 证书注入。
- **平台与缓存**：所有构建均使用 `--platform linux/amd64,linux/arm64` 双架构；启用 GitHub Actions cache（`type=gha`）加速依赖拉取。
- **版本标签**：Tag `vX.Y.Z` 时生成 `{repo}:server_{X.Y.Z}` / `web_{X.Y.Z}` 及 `_latest` 标签；非 tag 推送默认 `latest`。
- **健康检查**：Python 服务通过 TCP 8000 端口探测，Spring Boot 通过 Actuator `/xiaozhi/actuator/health` 探测。
- **环境变量驱动**：`TZ=Asia/Shanghai`、`JAVA_OPTS`、`PYTHONUNBUFFERED`、`LOG_DIR`、`DATA_DIR`、`MODEL_DIR` 等控制运行时行为。

## 4. 约定与约束
- **依赖锁定**：Python 使用 `requirements.txt` 固定版本（如 `onnxruntime>=1.16.1`、`websockets==14.2`）；Java 通过 `pom.xml` 的 `<properties>` 集中管理版本。
- **构建安全**：ACR 密码不硬编码，通过交互式输入或 `ACR_PASSWORD` 环境变量传入；CCR MITM CA 证书通过 `--mount=type=secret` 注入。
- **镜像最小化**：运行镜像仅包含必要运行时（JRE、Nginx、Python slim），删除包管理器缓存，创建非 root 用户运行。
- **部署一致性**：本地一键安装脚本与 CI 使用同一套 `docker-compose_all.yml` 编排，保证环境一致。
- **分支/路径触发**：base image 仅在 `main` 分支且 `requirements.txt` 或 `Dockerfile-server-base` 变更时重建，减少不必要构建。
- **端口暴露约定**：xiaozhi-server 暴露 8000（WebSocket）/8003（HTTP OTA/Vision），manager-web 暴露 8002，manager-web 前端 Nginx 暴露 80。

### 核心文件清单
- `Dockerfile-server`, `Dockerfile-web`, `Dockerfile-server-base`
- `main/xiaozhi-server/Dockerfile`, `main/manager-api/Dockerfile`, `main/manager-web/Dockerfile`
- `.github/workflows/docker-image.yml`, `.github/workflows/build-base-image.yml`
- `scripts/run-build-manager.sh`, `scripts/run-build-web.sh`, `scripts/run-build-xiaozhi.sh`, `scripts/run-mirror-funasr.sh`
- `docs/aliyun/sae-deployment/scripts/build-push-*.sh`
- `main/xiaozhi-server/requirements.txt`, `main/manager-api/pom.xml`
- `main/xiaozhi-server/docker-compose.yml`, `docs/docker/start.sh`, `docs/docker/nginx.conf`
- `docker-setup.sh`