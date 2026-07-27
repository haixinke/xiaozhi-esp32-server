---
kind: dependency_management
name: 多语言依赖管理体系（Python/Java/Node.js）
category: dependency_management
scope:
    - '**'
source_files:
    - main/xiaozhi-server/requirements.txt
    - main/xiaozhi-server/Dockerfile
    - main/manager-api/pom.xml
    - main/manager-api/Dockerfile
    - main/manager-web/package.json
    - main/manager-web/package-lock.json
    - main/manager-web/.npmrc
    - main/manager-mobile/package.json
    - main/manager-mobile/pnpm-lock.yaml
    - main/manager-mobile/.npmrc
    - .github/dependabot.yml
---

该仓库是一个多语言单体项目，包含 Python 语音服务、Java 管理后端、Vue2/UniApp 前端与微信小程序等多个子项目，各子项目采用各自生态的标准依赖管理方式，并通过统一的容器化构建流程进行编排。

**1. Python 依赖管理（xiaozhi-server）**
- 使用 `requirements.txt` 声明所有 Python 依赖，版本采用精确锁定（如 `numpy==1.26.4`、`websockets==14.2`），部分依赖通过注释说明升级限制（如 cozepy0.20.0 要求 websockets<15.0.0）
- 可选依赖通过注释形式管理（如本地 ASR 的 funasr、sherpa_onnx，记忆模块的 mem0ai/vosk 等），按需取消注释启用
- Docker 构建使用多阶段镜像，通过 `--mount=type=cache,target=/root/.cache/pip` 缓存 pip 依赖加速构建
- 未使用 `pipenv`/`poetry`/`Pipfile`，仅依赖 `requirements.txt`

**2. Java 依赖管理（manager-api）**
- 基于 Maven (`pom.xml`) 管理依赖，通过 `<properties>` 集中定义版本号（如 `shiro.version=2.0.2`、`mybatisplus.version=3.5.17`），避免硬编码
- 父 POM 继承 `spring-boot-starter-parent:3.4.3`，统一 Spring Boot 生态依赖版本
- 配置阿里云 Maven 镜像 (`https://maven.aliyun.com/repository/public/`) 作为唯一仓库源
- 通过 `<exclusions>` 显式排除冲突依赖（如 Shiro 的 core/web 重复声明）
- Docker 构建使用 `mvn dependency:go-offline -B` 预下载依赖并缓存到 `/root/.m2/repository`
- 跳过测试 (`skipTests=true`) 以加速构建

**3. Node.js/前端依赖管理（manager-web & manager-mobile）**
- **manager-web (Vue2)**：使用 `package.json` + `package-lock.json` (lockfileVersion 3)，通过 `.npmrc` 配置淘宝镜像 (`registry=https://registry.npmmirror.com/`)
- **manager-mobile (UniApp)**：使用 `package.json` + `pnpm-lock.yaml` (lockfileVersion 9.0)，强制使用 pnpm (`preinstall: npx only-allow pnpm`)，支持 workspace 模式
- pnpm 项目通过 `overrides` 和 `patchedDependencies` 机制覆盖第三方包版本和应用补丁（如 `bin-wrapper: npm:bin-wrapper-china`、`@dcloudio/uni-h5` 补丁）
- 两个前端项目均配置淘宝镜像加速依赖安装

**4. 自动化更新与安全扫描**
- 通过 GitHub Dependabot (`.github/dependabot.yml`) 对 Python 依赖设置每周自动更新检查
- 未发现对 Java/Node.js 依赖的自动更新配置

**5. 容器化依赖策略**
- Python/Java 项目均采用多阶段 Docker 构建，将依赖安装与运行环境分离
- 通过 `--mount=type=cache` 挂载依赖缓存目录（pip/maven）提升 CI/CD 构建速度
- Java 镜像额外处理 CCR MITM 代理 CA 证书注入

**约束与约定：**
- Python 依赖版本严格锁定，禁止使用 `>=` 模糊版本（除个别可升级依赖外）
- Java 依赖版本统一在 `<properties>` 中管理，禁止在 `<dependencies>` 中直接写死版本
- 前端项目必须使用指定包管理器（manager-mobile 强制 pnpm）
- 所有项目默认使用国内镜像源加速依赖下载