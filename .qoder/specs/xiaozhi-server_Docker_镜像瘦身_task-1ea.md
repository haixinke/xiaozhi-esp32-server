# xiaozhi-server Docker 镜像瘦身 + NAS 挂载优化

## Context

当前 `main/xiaozhi-server/` 的 Docker 镜像存在以下问题：
1. **无 `.dockerignore` 文件** — 整个目录（含 `.venv` 1.3GB）作为构建上下文发送
2. **`torch==2.2.2` + `torchaudio==2.2.2` (~850MB)** 在 requirements.txt 中，但生产 VAD 代码仅使用 `onnxruntime`，无任何 torch import
3. **`models/` (7.5MB)** 被 `COPY . .` 打入镜像
4. **`start.sh`** 仅支持 ConfigMap 方式，需适配 NAS 挂载

**预估优化效果**：镜像从 ~1.36GB 降至 ~486MB（减少约 65%）

---

## Task 1: 创建 `main/xiaozhi-server/.dockerignore`

**文件**: `main/xiaozhi-server/.dockerignore`

排除不需要打入镜像的文件：
```
# Python 缓存
__pycache__/
*.py[cod]
*$py.class

# 虚拟环境
.venv/
venv/
env/

# IDE
.idea/
.vscode/
.claude/

# 运行时数据（NAS 挂载）
models/
music/
data/
logs/
tmp/
oceanbase/

# 运行时生成文件
audit.log
*.log
config/assets/wakeup_words/
config/assets/bind_code/

# 测试与文档
performance_tester/
docs/
*.md

# 构建产物
Dockerfile
.dockerignore
docker-compose*.yml
.DS_Store
.git/
.gitignore
```

---

## Task 2: 优化 `requirements.txt`

**文件**: `main/xiaozhi-server/requirements.txt`

**移除**:
- `torch==2.2.2` (~800MB)
- `torchaudio==2.2.2` (~50MB)
- `silero_vad==6.1.0`（它会拉 torch 作为依赖）

**新增**:
- `onnxruntime>=1.16.1` (~50MB，生产 VAD 代码直接使用)

**依据**: `core/providers/vad/silero.py` 仅 `import onnxruntime`，全项目无 `import torch` / `from torch` 调用（已通过 grep 确认）。

---

## Task 3: 更新 `start.sh` — 适配 NAS 挂载

**文件**: `main/xiaozhi-server/start.sh`

核心改动：
- 通过环境变量 `NAS_MOUNT_PATH` 判断是否使用 NAS
- NAS 模式：使用符号链接指向 NAS 中的配置和模型
- 兼容旧 ConfigMap 模式（当 `NAS_MOUNT_PATH` 为空时回退）
- 添加模型文件存在性检查（WARN 级别，不阻止启动）

**NAS 挂载路径设计**:
```
${NAS_MOUNT_PATH}/
├── config/
│   └── .config.yaml       # 自定义配置
├── models/
│   └── snakers4_silero-vad/   # VAD 模型
└── music/                 # 音乐文件（可选）
```

---

## Task 4: 优化 Dockerfile

**文件**: `main/xiaozhi-server/Dockerfile`

改动点：
1. builder 阶段移除 `ffmpeg libopus0`（只在运行时需要）
2. 添加 `NAS_MOUNT_PATH` / `MODEL_DIR` 环境变量
3. `--start-period` 从 300s 降至 60s（无大模型加载后启动更快）
4. 恢复 `USER app` 降权运行（NAS 挂载文件对 app 用户只读即可）

---

## Task 5: config/assets/ 决策

**保留在镜像中**（不移到 NAS）。理由：
- 总大小仅 ~1.6MB
- 代码硬编码相对路径引用（如 `"config/assets/bind_code.wav"`）
- 属于系统内置提示音，非用户可定制内容

---

## Task 6: SAE 部署配置

在 SAE 控制台配置：
1. **NAS 存储卷**: 挂载到 `/mnt/nas/xiaozhi`
2. **环境变量**: `NAS_MOUNT_PATH=/mnt/nas/xiaozhi`
3. 删除旧 ConfigMap 挂载 (`/tmp/xiaozhi-config`)

---

## 关键文件清单

| 文件 | 操作 |
|------|------|
| `main/xiaozhi-server/.dockerignore` | 新建 |
| `main/xiaozhi-server/requirements.txt` | 修改（移除 torch，添加 onnxruntime） |
| `main/xiaozhi-server/start.sh` | 重写（NAS + ConfigMap 双模式） |
| `main/xiaozhi-server/Dockerfile` | 优化（精简 builder，添加环境变量） |

---

## 验证方式

1. **本地构建测试**: `docker build -f main/xiaozhi-server/Dockerfile main/xiaozhi-server/` 确认构建成功且镜像体积符合预期
2. **镜像大小**: `docker images` 检查输出约 500MB 以下
3. **启动测试**: 本地挂载模型目录运行容器，验证 VAD 初始化正常
4. **SAE 灰度部署**: 先单实例验证 NAS 挂载 + 服务启动 + 健康检查通过

---

## 风险与注意

| 风险 | 缓解 |
|------|------|
| `silero_vad` pip 包可能被其他依赖间接拉入 | 已确认无其他包依赖它 |
| NAS 首次访问延迟 | 模型启动时一次性加载到内存，运行时无 IO |
| NAS 不可用时模型文件缺失 | start.sh 打印 WARN + Python 层给出明确报错 |
| 向后兼容现有 ConfigMap 部署 | start.sh 支持双模式，通过 `NAS_MOUNT_PATH` 环境变量区分 |
