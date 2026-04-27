# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

本项目是 **xiaozhi-esp32-server**，为 [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32) 开源智能硬件项目提供的后端服务。它为 ESP32 设备提供实时语音 AI 助手服务器，由 `main/` 下的四个子项目组成。

## 子项目

在本项目中，四个子项目有约定的简称：
- **聊天服务** → `main/xiaozhi-server/`
- **后端服务** → `main/manager-api/`
- **web服务** → `main/manager-web/`
- **移动服务** → `main/manager-mobile/`

| 子项目 | 语言 / 技术栈 | 端口 | 用途 |
|---|---|---|---|
| `main/xiaozhi-server/` | Python 3.10 | 8000 (WS), 8003 (HTTP) | AI 核心：语音流水线 (ASR → LLM → TTS)，WebSocket 设备连接 |
| `main/manager-api/` | Java 21 / Spring Boot 3.4.3 | 8002 (`/xiaozhi`) | 管理后台 REST API，设备注册，Python 服务端的配置来源 |
| `main/manager-web/` | Vue.js 2 / Vue CLI | 8001 (dev) | Web 管理控制台 ("智控台") |
| `main/manager-mobile/` | Uni-app / Vue 3 / Vite | — | 移动端管理后台 (H5、微信小程序、iOS、Android) |

每个子项目都有自己的 `CLAUDE.md`，包含详细的架构说明和常用命令。

## 常用命令

### Python 服务端 (`main/xiaozhi-server/`)
```bash
cd main/xiaozhi-server
pip install -r requirements.txt
python app.py                          # 启动服务
python performance_tester.py           # ASR/LLM/TTS 性能基准测试
```

### Java API (`main/manager-api/`)
```bash
cd main/manager-api
mvn spring-boot:run                    # 运行 (需要 MySQL + Redis)
mvn test -DskipTests=false             # 运行全部测试
mvn test -Dtest=DeviceTest -DskipTests=false            # 单个测试类
mvn test -Dtest=DeviceTest#testWriteDeviceInfo -DskipTests=false  # 单个测试方法
```

### Web 前端 (`main/manager-web/`)
```bash
cd main/manager-web
npm install
npm run serve                          # 开发服务器，端口 8001
npm run build                          # 生产构建
npm run analyze                        # 包体积分析
```

### 移动端前端 (`main/manager-mobile/`)
```bash
cd main/manager-mobile
pnpm install                           # 强制使用 pnpm
pnpm dev:h5                            # H5 开发
pnpm dev:mp-weixin                     # 微信小程序开发
pnpm build:h5                          # H5 构建
pnpm lint                            # 代码检查
```

### Docker (全栈)
```bash
# Ubuntu 一键部署
sudo bash -c "$(wget -qO- https://ghfast.top/https://raw.githubusercontent.com/xinnan-tech/xiaozhi-esp32-server/main/docker-setup.sh)"

# 手动 Docker Compose
# 最小化 (仅服务端):
docker compose -f main/xiaozhi-server/docker-compose.yml up -d
# 全模块 (MySQL + Redis + 服务端 + Web):
docker compose -f main/xiaozhi-server/docker-compose_all.yml up -d
# OceanBase (PowerMem 记忆存储):
cd main/xiaozhi-server
./oceanbase/init-powermem.sh
```

## 高层架构

```
┌──────────────┐     WebSocket      ┌─────────────────┐     HTTP      ┌─────────────────┐
│ ESP32 设备    │◄──────────────────►│ xiaozhi-server  │◄───────────►│ LLM / TTS / ASR │
│              │      端口 8000      │ (Python AI)     │   API       │   服务商         │
└──────────────┘                    └────────┬────────┘             └─────────────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  manager-api    │◄──── REST ────┐
                                    │  (Java Spring)  │               │
                                    │  端口 8002       │◄── MySQL + Redis
                                    └────────┬────────┘               │
                                             │                        │
                                   ┌─────────┴──────────┐             │
                                   │                    │             │
                             ┌─────▼─────┐      ┌──────▼──────┐      │
                             │manager-web│      │manager-     │      │
                             │(Vue.js)   │      │mobile      │      │
                             │端口 8001   │      │(Uni-app)   │      │
                             └───────────┘      └─────────────┘      │
                                                                      │
                                                          ┌───────────▼──────────┐
                                                          │  mqtt-gateway (可选) │
                                                          │  MQTT + UDP 桥接     │
                                                          └──────────────────────┘
```

### 数据流 (语音交互)

ESP32 设备 → WebSocket → `receiveAudioHandle` → ASR → 意图识别 → LLM → TTS → `sendAudioHandle` → ESP32 设备

Python 服务端对所有 AI 流水线组件 (ASR、TTS、LLM、VAD、意图识别、记忆) 采用 **Provider 模式**。Provider 位于 `core/providers/`，通过 `core/utils/` 中的工厂函数实例化。

### 配置加载流程

`xiaozhi-server` 从三层配置读取 (后层覆盖前层)：
1. `config.yaml` (已提交的默认配置)
2. `data/.config.yaml` (本地密钥和覆盖配置，已加入 gitignore)
3. 远程 `manager-api` 配置 (如果在 `data/.config.yaml` 中设置了 `manager-api.url`)

Java API 向 Python 服务端暴露运行时配置，使得管理控制台无需重启即可调整 AI 参数。

## 关键文件

| 文件 | 用途 |
|---|---|
| `main/xiaozhi-server/config.yaml` | 服务端基础配置 (已提交) |
| `main/xiaozhi-server/data/.config.yaml` | 本地覆盖配置和密钥 (gitignore，启动时必须存在) |
| `main/manager-api/src/main/resources/application-dev.yml` | Java 开发环境配置 (MySQL / Redis) |
| `main/manager-web/vue.config.js` | Vue 构建配置，代理 `/xiaozhi` 到 `localhost:8002` |
| `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase 迁移日志 |

## 部署模式

1. **最小化 (仅服务端)** — Docker 或本地 Python，配置存储在文件中，无需数据库。适合低资源配置环境。
2. **全模块** — Docker Compose 部署 MySQL + Redis + 服务端 + Web。通过管理控制台实现多用户、多智能体管理。

详细部署说明请参阅 `docs/Deployment.md` (最小化) 和 `docs/Deployment_all.md` (全模块)。
