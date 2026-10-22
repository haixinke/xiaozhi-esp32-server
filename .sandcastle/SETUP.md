# Sandcastle 安装与配置实战记录

> 本仓（xiaozhi-esp32-server）从零接入 [Sandcastle](https://github.com/mattpocock/sandcastle) 的完整过程，含所有踩坑与解决方案。
> 日期：2026-10-21 · 分支：`f-sandcastle`

## 目标

在本仓运行 AI coding agent（Claude Code），要求：

- 容器隔离执行，不污染 host 工作目录
- issue 来自本仓 `.scratch/` markdown 文件（非 GitHub Issues）
- LLM 流量走本机 CCR 代理（`127.0.0.1:3456`），不直连 Anthropic
- 容器内 agent 可使用指定的 4 个 skill

## 最终架构

```
host: npx tsx .sandcastle/main.mts
  │
  ├─ git worktree（每 issue 一个，从 HEAD 检出）
  │
  ├─ docker create（镜像 sandcastle:xiaozhi-esp32-server）
  │    ├─ worktree bind-mount → /home/agent/workspace
  │    ├─ host ~/.m2 bind-mount（读写）→ /home/agent/.m2
  │    ├─ JDK 21 + Maven（镜像内预装）
  │    ├─ Claude CLI → ANTHROPIC_BASE_URL=http://host.docker.internal:3456
  │    │    └─ host CCR（绑 0.0.0.0:3456）→ OpenRouter / 智谱 / Kimi
  │    └─ skills 预装在 /home/agent/.claude/skills/
  │
  └─ 分支策略：每 issue 独立 branch（sandcastle/sequential-reviewer/<ts>）
       └─ implement → Java 验证 → review → Java 再验证 → host 自动合并已验证 commit
```

## Java 验证与 Maven 缓存

`Dockerfile` 固定官方 Temurin JDK 21 和 Maven 版本，并在镜像构建时执行
`java -version`、`javac -version`、`mvn -version`。修改后必须重建镜像，旧镜像不会自动获得工具链。
`.dockerignore` 只允许 Dockerfile 和 skills 进入构建上下文，排除 `.env`、日志和 host 依赖。

`main.mts` 自动创建宿主机 `~/.m2`，以读写方式挂载到 `/home/agent/.m2`。
Maven 复用同一份 `repository/`，后续 iteration 不需要重新下载已有依赖；`clean` 只清理模块的构建产物，不清空依赖缓存。
该挂载也包含已有的 `settings.xml` 等 Maven 配置，不要在日志中输出其内容。
验证命令显式指定 `-Dmaven.repo.local=/home/agent/.m2/repository`，覆盖 settings 中可能存在的宿主机绝对路径。
镜像的 agent UID/GID 必须与宿主机匹配；启动预检会检查工具链和缓存目录可写性。

涉及 `main/manager-api/` 任意文件（包括删除、重命名）的变更，在 implement 和 review 之后分别执行：

```bash
mvn -B -ntp -f main/manager-api/pom.xml \
  -Dmaven.repo.local=/home/agent/.m2/repository \
  -DskipTests=false -Dmaven.test.skip=false -DfailIfNoTests=true clean verify
```

命令在容器的仓库根目录执行。项目没有 Maven Wrapper，且 POM 默认跳过测试，所以不能用
`./mvnw` 或省略显式开启测试的参数。非 manager-api 变更不触发 Java 门禁，仍由 agent 执行对应模块检查。
review 新引入的 Java 变更也会触发门禁。初始化 hooks 不能代替这两次修改后的验证。

验证命令由 host 编排程序决定并检查退出码；失败会停止运行，不自动 merge。
日志保存到 `.sandcastle/logs/<commit>-<phase>-<label>-verify.log`，包含提交、命令、退出码和过滤敏感行后的输出。
两个阶段都要求 worktree 没有未提交改动，因此 issue close 和 Comments 也需要提交。
合并使用验证过的 commit SHA；如果 host 分支或 HEAD 已变化，必须重新整理分支并验证。

### 多子项目门禁矩阵

本仓含四个子项目（manager-api / manager-web / egg-miniprogram / xiaozhi-server），
`main.mts` 的 `CHECKS` 数组按 changed paths 分发检查命令。变更触碰多端时**取并集全部执行**：

| Changed path 前缀 | 沙箱内执行的检查 |
|---|---|
| `main/manager-api/` | `mvn ... clean verify`（完整 Java 测试，排除 3 个 PDC NFC Testcontainers 测试） |
| `main/manager-web/` | `npm run test:unit && npm run check:i18n && npm run check:template` |
| `main/egg-miniprogram/` | `node scripts/verify-project.js` |
| `main/xiaozhi-server/` | `python3.12 -m compileall -q core/ config/ plugins_func/`（仅语法级） |

**关键陷阱：`sandbox.exec()` 非零 exitCode 是返回不抛出。** 每个 check 必须显式
`if (result.exitCode !== 0) throw`，否则测试失败也会被静默跳过、merge 照常发生。
README 示例里 `if (tests.exitCode !== 0) throw` 那行不是装饰，是门禁生效的必要条件。

`manager-web` 的 `node_modules` 通过 `sandbox.onSandboxReady` hook 在容器启动后
`npm ci --prefer-offline` 装入；host 的 `~/.npm` bind-mount 进容器当 tar 缓存。
`xiaozhi-server` 只装 Python 3.12 解释器，不装 onnxruntime/powermem 等重依赖 ——
门禁只做语法检查，需要跑 pytest 的 issue 在 prompt 里报告为 blocker，留 host 人工验证。

镜像里的工具链版本必须与 host 推荐版本对齐：
- JDK 21（manager-api）
- Node 22（manager-web、egg-miniprogram 检查脚本）
- Python **3.12**（xiaozhi-server；requirements.txt:2 与 CLAUDE.md 明确推荐）

不要用 3.10 / 3.11 顶替 —— 语法门禁版本不一致会让容器内通过的代码在 host 挂掉，反之亦然。

现有 Java 全量测试依赖 Testcontainers/Docker 和 OceanBase 等外部测试服务。
本配置没有挂载 Docker socket，3 个 PDC NFC 集成测试（`PdcNfcTrustReleaseIntegrationTest` /
`PdcNfcClaimInviteReplacementIntegrationTest` / `PdcNfcWriteResultImportIntegrationTest`）
在验证命令中显式排除；其余测试经 `DB_HOST` / `REDIS_HOST` 环境变量连 host 服务。
这表示测试环境需要准备，不应改成跳过测试来放行。

### 踩坑补充（2026-10-22 联调）

- **中文文件名**：POSIX locale 下 JVM 把国标号/中文文件名转成 `?`，javac 扫描源码目录报
  `Malformed input`。Dockerfile 需 `ENV LANG=C.utf8`。
- **测试数据污染**：`GameServiceTest` 每用例用同一 `gameType`，`ai_game.game_type`
  唯一约束下一旦某用例中断残留数据，后续全挂。`@AfterEach` 清理要包住所有路径；
  断言多条记录时 gameType 必须各不相同。
- **Liquibase 部分应用**：changeset 标记 `EXECUTED` 但表实际缺失（如 `ai_star_transaction`），
  验证报 `Table doesn't exist`。需手工补建并核对 `DATABASECHANGELOG` 一致性。
- **硬编码 `127.0.0.1`**：测试/配置里写死 loopback 的在容器内全失效。凡需跨容器访问的
  host/port 一律走 `${VAR:default}` 占位符，默认值保持 `127.0.0.1` 不影响 host 开发。
- **InviteCodeGeneratorTest** 随机生成 10000 个码有概率碰撞，属既有 flake，非链路问题。
- **README 列 `gh` 为镜像必需**，但本仓 tracker 走 `.scratch/`，当前镜像未装 `gh`；
  将来迁 GitHub Issues 时需 `apt-get install gh` 或走官方 install script。

运行编排器回归测试（使用临时 Git 仓库，替代外部 agent/容器，不调用真实模型）：

```bash
npm test --prefix .sandcastle
```

## 安装步骤（最终可用版本）

### 1. 初始化

```bash
npx -y @ai-hero/sandcastle init \
  --sandbox docker \
  --agent claude-code \
  --template sequential-reviewer \
  --issue-tracker custom
```

生成 `.sandcastle/`：Dockerfile、main.mts、implement-prompt.md、review-prompt.md、CODING_STANDARDS.md、.env.example、.gitignore。

**注意：** `--issue-tracker custom` 不自动 build 镜像（其他 tracker 会），并且留 `SETUP_ISSUE_TRACKER.md` 提示你手动接线。

### 2. 接入本仓 .scratch issue tracker

新建 `.sandcastle/bin/scratch-issues.sh`，实现三个子命令：

- `list` — 扫 `.scratch/*/issues/*.md`，输出 JSON `[{id, title, body, status}]`，过滤 `Status: resolved`
- `view <ID>` — 输出指定 issue 文件内容
- `close <ID>` — 把 `**Status:**` 改成 `resolved`

ID 格式：`<feature>/issues/<NN>-<slug>`。

在 `implement-prompt.md` 把 sentinel 命令替换为：

```markdown
!`bash .sandcastle/bin/scratch-issues.sh list`
```

close 指引写进 workflow 第 6 步。

### 3. 配置 .env

```bash
# .sandcastle/.env
ANTHROPIC_BASE_URL=http://host.docker.internal:3456
ANTHROPIC_API_KEY=<从 CCR sqlite api_keys 表读到的 key>
```

**为什么用 `host.docker.internal`：** 容器内 `127.0.0.1` 指容器自己，不是 host。Mac 上 Docker Desktop 提供 `host.docker.internal` DNS 指向 host。

**API key 从哪来：** CCR（Electron 版）的 key 存在 `~/.claude-code-router/config.sqlite` 的 `api_keys` 表，`encrypted_key` 字段 plaintext 存储。

### 4. 让 CCR 监听所有接口

默认 CCR 绑 `127.0.0.1`，容器访问不到。改 bind 地址：

```sql
sqlite3 ~/.claude-code-router/config.sqlite \
  "UPDATE app_config SET value_json = json_set(value_json, '$.HOST', '0.0.0.0') WHERE key='default';"
```

**改完必须重启 CCR 应用**（Electron 进程）才生效。验证：

```bash
lsof -iTCP:3456 -sTCP:LISTEN
# 期望看到 *:vat 或 *:3456，不是 localhost:vat
```

host 上 Claude Code 的 `~/.claude/settings.json` 里 `ANTHROPIC_BASE_URL=http://127.0.0.1:3456` **不用改** —— `0.0.0.0` 监听包含 loopback。

### 5. 内置 skills 到镜像

新建 `.sandcastle/sync-skills.sh`，从本机插件目录同步：

```bash
MP=~/.claude/plugins/marketplaces/mattpocock/skills/engineering
SP=~/.claude/plugins/cache/superpowers-marketplace/superpowers/7.0.0/skills

# 同步 4 个 skill 到 .sandcastle/claude-skills/：
#   tdd / diagnosing-bugs / systematic-debugging / verification-before-completion
```

`.sandcastle/.gitignore` 加 `claude-skills/`（本地产物不入库）。

Dockerfile 在 `usermod` 之后、`USER` 切换之前 COPY：

```dockerfile
RUN groupmod -o -g $AGENT_GID node && usermod -o -u $AGENT_UID -g $AGENT_GID -d /home/agent -m -l agent node

COPY --chown=${AGENT_UID}:${AGENT_GID} claude-skills/ /home/agent/.claude/skills/

USER ${AGENT_UID}:${AGENT_GID}
```

### 6. Claude CLI 安装：用 npm 而非官方 install.sh

```dockerfile
RUN npm install -g @anthropic-ai/claude-code \
 && npm install -g @anthropic-ai/claude-code-linux-arm64 \
 && claude --version
```

**为什么：** 官方 `install.sh` 从 `downloads.claude.ai` 拉 native binary，国内经代理也不稳。npm registry 通畅。

**必须显式装平台 optional dep**（`claude-code-linux-arm64`）：npm 10 在跨平台 / 非 TTY build 时会漏 optional deps，导致运行时报"Cannot find module"。

### 7. apt 走 host 代理

`deb.debian.org` 国内直连不稳，build 期走代理：

```dockerfile
RUN echo 'Acquire::http::Proxy "http://host.docker.internal:7897";' > /etc/apt/apt.conf.d/99proxy \
 && echo 'Acquire::https::Proxy "http://host.docker.internal:7897";' >> /etc/apt/apt.conf.d/99proxy \
 && apt-get update && apt-get install -y git curl jq \
 && rm -rf /var/lib/apt/lists/* /etc/apt/apt.conf.d/99proxy
```

**注意：** 代理配置在同一 RUN 里删掉，不进最终镜像层。

### 8. .scratch 纳入 git

默认 `.scratch/` 在仓根 `.gitignore` 里 —— worktree 是从 git HEAD 建的，gitignore 的目录不会出现在 worktree 里，tracker 就读不到 issue。

**解决：** 从 `.gitignore` 移除 `.scratch/`，提交进 git。

**取舍：**
- ✅ worktree 自带 `.scratch/`，不用 bind-mount
- ✅ agent 的 close 改动落 worktree 分支，merge 回主分支时同步
- ❌ 单人本地的 issue 笔记进了 git 历史

### 9. 调整 main.mts

本仓不是 Node 项目，删掉模板里的 `copyToWorktree: ["node_modules"]` 和 `onSandboxReady: npm install` —— 否则容器里 `npm install` 找不到 `package.json` 直接失败。

模型名改为 CCR 路由表内的：`anthropic/claude-sonnet-4.6`（不是 `claude-sonnet-4-6`）。

### 10. 构建镜像

```bash
./.sandcastle/sync-skills.sh   # 同步 skill 到 build context
cd .sandcastle
docker build -t sandcastle:xiaozhi-esp32-server \
  --build-arg AGENT_UID=$(id -u) \
  --build-arg AGENT_GID=$(id -g) \
  --add-host=host.docker.internal:host-gateway \
  .
```

**注意：** 从 `.sandcastle/` 目录跑，build context 里的 `claude-skills/` 才对得上 Dockerfile 的 COPY 路径。

### 11. 验证

```bash
# 镜像里有 claude 和 skills
docker run --rm --entrypoint sh sandcastle:xiaozhi-esp32-server \
  -c "claude --version && ls /home/agent/.claude/skills/"

# claude 在容器内能列到 skill
docker run --rm --entrypoint claude \
  -e ANTHROPIC_BASE_URL=http://host.docker.internal:3456 \
  -e ANTHROPIC_API_KEY=<key> \
  --add-host=host.docker.internal:host-gateway \
  sandcastle:xiaozhi-esp32-server \
  --model "anthropic/claude-sonnet-4.6" \
  --print "List the skills available to you."

# 端到端跑一次（空 backlog 应直接 COMPLETE）
npx tsx .sandcastle/main.mts
```

---

## 踩坑记录

### 坑 1：`curl ... | bash` 静默失败

**现象：** 镜像 build 成功，但容器里 `claude` 命令不存在。

**原因：** `RUN curl -fsSL https://claude.ai/install.sh | bash` —— curl 失败时 stdout 为空，pipe 把空输入喂给 bash，bash 成功退出（exit 0），Docker 认为该层成功。

**教训：** 任何 `curl | bash` 都要拆开，或加 `set -o pipefail`：

```dockerfile
# 错误
RUN curl -fsSL https://x.sh | bash

# 正确
RUN curl -fsSL -o /tmp/x.sh https://x.sh && bash /tmp/x.sh && rm /tmp/x.sh
```

### 坑 2：build 输出被 `| tail -N` 截断造成假象

**现象：** `docker build ... | tail -30` 退出码是 `tail` 的 0，build 实际失败但 shell 报 exit 0。

**教训：** 后台 build 时把**完整输出**重定向到文件，不要管道过滤：

```bash
docker build ... > /tmp/build.log 2>&1
echo "EXIT=$?"   # 真实退出码
```

### 坑 3：COPY 在 usermod 之前，usermod 失败

**现象：** `usermod: directory /home/agent exists`

**原因：** 把 `COPY claude-skills/ /home/agent/.claude/skills/` 放在 `usermod -d /home/agent -m` 之前。COPY 先创建了 `/home/agent`，usermod 想 `-m` 迁移 home 时冲突。

**教训：** 凡是要 `usermod -m` 移动 home 目录的，COPY 到 home 下的操作必须放在 usermod **之后**。

### 坑 4：build context 的 cwd

**现象：** `npx @ai-hero/sandcastle docker build-image` 报 `No .sandcastle/ found`。

**原因：** 在 `.sandcastle/` 目录内跑，CLI 找 `.sandcastle/.sandcastle/`。

**教训：** sandcastle CLI 一律从仓根跑。直接 `docker build` 则从 `.sandcastle/` 跑（context 是 `.sandcastle/`）。

### 坑 5：`.gitignore` 排除 `.scratch/` 导致 worktree 里没有

**现象：** 容器内 `bash .sandcastle/bin/scratch-issues.sh list` 报 `.scratch not found`。

**原因：** worktree 从 git HEAD 检出，gitignore 的目录不存在于 HEAD，worktree 里就没有。

**教训：** tracker 文件要给容器用，要么入 git，要么 `copyToWorktree` 拷过去，要么 bind-mount。选了入 git。

### 坑 6：容器访问 host 的 `127.0.0.1` 服务

**现象：** 容器调 `http://127.0.0.1:3456` 超时。

**原因：** 容器网络 namespace 独立，`127.0.0.1` 是容器自己。

**解决：** Mac 上用 `host.docker.internal`，并在 `docker build` / `docker run` 加 `--add-host=host.docker.internal:host-gateway`（Docker Desktop 新版默认有，显式加更稳）。

### 坑 7：CCR 只绑 127.0.0.1，容器访问不到

**现象：** 容器里 curl `http://host.docker.internal:3456` 拒绝连接。

**原因：** CCR 默认 `HOST: 127.0.0.1`，只接受 loopback。

**尝试过的方案：**
- ❌ socat 转发到 LAN IP —— 多一个进程要管，LAN IP 会变
- ❌ socat 绑 Docker 网关 `192.168.65.1` —— Mac 上这 IP 在 VM 里，host 不存在
- ✅ **改 CCR `HOST` 为 `0.0.0.0`，重启应用** —— 最干净

**风险：** 局域网可达 3456。可信网络可接受；不可信网络建议加 macOS 防火墙规则。

### 坑 8：docker build 重新拉 base 镜像 metadata

**现象：** 已有镜像但 build 卡在 `load metadata for node:22-bookworm`，aliyun mirror 403。

**原因：** buildkit 默认每次都验证 base 镜像 metadata。

**解决：** `~/.docker/daemon.json` 的 `registry-mirrors` 加多个备选：

```json
"registry-mirrors": [
  "https://f48b3qz8.mirror.aliyuncs.com",
  "https://docker.m.daocloud.io",
  "https://docker.1ms.run",
  "https://hub.rat.dev"
]
```

改完要**重启 Docker Desktop**（菜单退出 + 重开，不是只关窗口）。

### 坑 9：Claude CLI 下载源不可达

**现象：** `install.sh` 内 `Download failed`，无论直连还是代理都失败。

**原因：** `downloads.claude.ai` 对国内/某些代理不友好。

**解决：** 改用 npm 安装：

```dockerfile
RUN npm install -g @anthropic-ai/claude-code \
 && npm install -g @anthropic-ai/claude-code-linux-arm64 \
 && claude --version
```

第二个平台包必须显式装，否则运行时缺模块。

### 坑 10：`.sandcastle/.env` 不能提交

**现象：** 想把 token 也入 git 分享。

**原因：** `.sandcastle/.gitignore` 默认排除 `.env`（正确）。token 是机密，**永远不该入库**。

**做法：** `.env.example` 入库作模板，`.env` 本地填。团队成员各自 `claude setup-token` 或从 CCR 拿 key。

### 坑 11：并发 docker build 互相拖

**现象：** build 十几分钟不结束。

**原因：** 之前 background 任务没死透，又起了新 build，两个并发在 docker daemon 上抢资源。

**教训：** 起新 build 前 `ps aux | grep "docker build"` 确认没残留；有就 `pkill -f "docker build"` 清掉再起。

### 坑 12：模型名必须在 CCR 路由表内

**现象：** 容器内 claude 报 `400 All target providers failed`，`Model "claude-opus-4-8" is not configured for target provider`。

**原因：** sandcastle 模板默认 `claude-opus-4-8` 或 `claude-sonnet-4-6`，但 CCR 的 provider 配置了 `anthropic/claude-opus-4.6` 这种带前缀的模型名。

**解决：** `main.mts` 里改为 `anthropic/claude-sonnet-4.6`（或 CCR 路由表里实际存在的名字）。

---

## 常用命令速查

```bash
# 改完 Dockerfile 后重建
./.sandcastle/sync-skills.sh
cd .sandcastle && docker build -t sandcastle:xiaozhi-esp32-server \
  --build-arg AGENT_UID=$(id -u) --build-arg AGENT_GID=$(id -g) \
  --add-host=host.docker.internal:host-gateway .

# 跑 sequential-reviewer（从仓根）
npx tsx .sandcastle/main.mts

# 看实时日志
tail -f .sandcastle/logs/<latest>.log

# 清理 agent 留下的分支
git branch | grep "sandcastle/" | xargs git branch -D

# 手动测 tracker
bash .sandcastle/bin/scratch-issues.sh list
bash .sandcastle/bin/scratch-issues.sh view game-management/issues/01-admin-game-crud
bash .sandcastle/bin/scratch-issues.sh close game-management/issues/01-admin-game-crud
```

## 文件清单

```
.sandcastle/
├── .env                      # gitignored，含 CCR key
├── .env.example              # 模板
├── .gitignore                # .env / logs/ / worktrees/ / node_modules/ / claude-skills/
├── Dockerfile                # 镜像定义
├── main.mts                  # sequential-reviewer 入口
├── implement-prompt.md       # implementer prompt
├── review-prompt.md          # reviewer prompt
├── CODING_STANDARDS.md       # 审查标准（44 行精简版）
├── SETUP_ISSUE_TRACKER.md    # 模板自带，可删
├── SETUP.md                  # 本文档
├── sync-skills.sh            # 从本机同步 skill 到 build context
├── bin/scratch-issues.sh     # .scratch tracker 适配器
├── package.json              # host 依赖（@ai-hero/sandcastle + tsx）
└── claude-skills/            # gitignored，sync-skills.sh 生成
    ├── tdd/
    ├── diagnosing-bugs/
    ├── systematic-debugging/
    └── verification-before-completion/
```
