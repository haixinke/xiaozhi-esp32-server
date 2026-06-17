# ACK 部署：服务画像与容器化

本文件说明 `xiaozhi-server` 与 `manager-api` 两个服务的运行特征、Dockerfile、构建脚本。

## 1. 服务画像

### 1.1 xiaozhi-server（聊天服务）

| 项 | 内容 |
|----|------|
| 技术栈 | Python 3.12 推荐，`app.py` 入口 |
| 端口 | WebSocket `8000`，HTTP `8003`（OTA / 视觉分析） |
| 启动命令 | `python app.py` |
| 配置文件 | `config.yaml`（默认）+ `data/.config.yaml`（覆盖）+ 智控台 API 覆盖 |
| 外部依赖 | MySQL/OceanBase（PowerMem）、Redis、LLM/TTS/ASR 云 API |
| 本地模型 | `models/SenseVoiceSmall/model.pt`（默认 ASR） |
| 有状态点 | 模型文件、音频/日志临时文件、OTA bin 文件 |

### 1.2 manager-api（后端服务）

| 项 | 内容 |
|----|------|
| 技术栈 | Java 21 / Spring Boot 3.4.3 / Maven |
| 端口 | `8002`，`server.servlet.context-path=/xiaozhi` |
| 启动命令 | `java -jar xiaozhi-esp32-api.jar` |
| 配置文件 | `application.yml` + `application-{dev|test|prod}.yml` |
| 外部依赖 | MySQL 8.0+、Redis 5.0+ |
| 数据库迁移 | Liquibase，启动时自动执行 |

---

## 2. Dockerfile

### 2.1 xiaozhi-server Dockerfile

```dockerfile
# syntax=docker/dockerfile:1
FROM python:3.12-slim AS builder

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc ffmpeg libopus0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

FROM python:3.12-slim AS runner
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libopus0 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app -g 1001 \
    && useradd -r -g app -u 1001 app

COPY --from=builder /usr/local/lib/python3.12/site-packages /usr/local/lib/python3.12/site-packages
COPY --from=builder /usr/local/bin /usr/local/bin
COPY . .

RUN mkdir -p /app/models /app/data /app/tmp /app/music \
    && chown -R app:app /app

USER app

ENV PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai \
    LOG_DIR=/app/tmp \
    DATA_DIR=/app/data

EXPOSE 8000 8003

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8003/mcp/vision/explain')" || exit 1

CMD ["python", "app.py"]
```

`.dockerignore`：

```text
.venv
venv
__pycache__
*.pyc
.DS_Store
.git
.gitignore
tmp/*
logs/*
data/*.yaml
.idea
.vscode
```

### 2.2 manager-api Dockerfile

```dockerfile
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -B -DskipTests=true

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S app -g 1001 && adduser -S app -u 1001 -G app

COPY --from=builder --chown=app:app /build/target/xiaozhi-esp32-api.jar /app/app.jar

USER app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8002

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8002/xiaozhi/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

> 若项目未引入 Spring Boot Actuator，请在 `pom.xml` 添加 `spring-boot-starter-actuator`，并暴露 `/actuator/health`。

---

## 3. 构建与推送脚本

```bash
#!/usr/bin/env bash
set -euo pipefail

REGISTRY=registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace
VERSION=${VERSION:-$(git rev-parse --short HEAD)}

docker build -t ${REGISTRY}/xiaozhi-server:${VERSION} -f xiaozhi-server/Dockerfile main/xiaozhi-server/
docker push ${REGISTRY}/xiaozhi-server:${VERSION}

docker build -t ${REGISTRY}/manager-api:${VERSION} -f manager-api/Dockerfile main/manager-api/
docker push ${REGISTRY}/manager-api:${VERSION}

echo "Pushed version: ${VERSION}"
```

---

## 4. 项目目录约定

```text
ack-deploy/
├── xiaozhi-server/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── k8s/
├── manager-api/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── k8s/
└── scripts/
    ├── build-push.sh
    └── deploy.sh
```
