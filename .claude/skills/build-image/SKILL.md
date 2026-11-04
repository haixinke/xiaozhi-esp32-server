---
name: build-image
description: 构建并推送本项目 Docker 镜像到阿里云 ACR（个人版仓库），覆盖三个模块——后端服务 manager-api（scripts/run-build-manager.sh）、智控台 manager-web（scripts/run-build-web.sh）、聊天服务 xiaozhi-server（scripts/run-build-xiaozhi.sh）。当用户说"打镜像"、"构建镜像"、"推送镜像"、"发布镜像"、"build image"、"docker push"、"打个 xxx 的版本"、"发版本"、"发镜像"、"build 镜像"、"出个生产镜像"，或提到给 manager-api / manager-web / 后端服务 / 智控台 / 聊天服务 / xiaozhi-server 打版本发布到生产时使用。流程：询问模块 → 从 .claude/skills/build-image/production-versions.md 读取并展示生产当前版本 → 询问新版本号 → 执行 VERSION=<版本> bash scripts/run-build-<模块>.sh 构建推送 → 成功后回写版本记录。
---

# 镜像构建与推送

为三个模块构建 Docker 镜像并推送到阿里云 ACR（上海，个人版仓库 `crpi-wvrvx27whligoca7.cn-shanghai.personal.cr.aliyuncs.com/companion-ai`），推送完成后维护本目录下的生产版本记录。

## 模块映射

| 模块 | 别名 | key（版本记录文件里用） | 构建脚本 | 镜像名 |
|---|---|---|---|---|
| 后端服务 | manager-api | manager-api | `scripts/run-build-manager.sh` | manager-api |
| 智控台 | manager-web | manager-web | `scripts/run-build-web.sh` | manager-web |
| 聊天服务 | xiaozhi-server | xiaozhi-server | `scripts/run-build-xiaozhi.sh` | xiaozhi-server |

## 版本号约定

- 镜像 tag 原样使用 `VERSION` 的值，项目惯例带 `egg-` 前缀（如 `egg-4.9`）。
- 如果 `VERSION` 未传给脚本，底层 `_common.sh` 会退化为 `git rev-parse --short HEAD` —— 这通常不是想要的，所以**必须显式传 VERSION**。
- ACR 凭据：构建脚本会交互式询问 ACR 密码（不回显），或读取环境变量 `ACR_PASSWORD`。凭据不在本 skill 的职责范围内，不要替用户处理密码。
- 平台默认 `linux/amd64`。

## 流程

### 1. 确定模块

如果用户已经指明了模块（如"给聊天服务打个镜像"），直接用它。否则用 AskUserQuestion 询问：

- 后端服务（manager-api）
- 智控台（manager-web）
- 聊天服务（xiaozhi-server）

### 2. 读取并展示当前生产版本

读取本 skill 目录下的 `production-versions.md`（与本 SKILL.md 同级），从中取该模块的当前版本，展示给用户，例如：

> 智控台（manager-web）当前生产版本：**egg-2.7**

如果文件缺失或读不到该模块的记录，如实告诉用户"本地没有版本记录"，继续下一步，不要中断。

### 3. 询问新版本号

询问用户要发布的新版本号（给出当前版本作为参照，并建议惯例格式如 `egg-4.9`）。用 AskUserQuestion 提供"基于当前版本 +1"的推荐项，但允许用户自由输入。

拿到版本号后做基本校验：

- 非空；
- 只允许字母、数字、点、下划线、连字符（Docker tag 合法字符：`[A-Za-z0-9_.-]`），不允许冒号、斜杠、空格 —— 这是防止拼出非法镜像名或意外注入 shell 的最小校验；
- 如果用户没写 `egg-` 前缀，**不要**自作主张加上 —— 先跟用户确认是"原样使用"还是"补前缀"，因为 tag 是原样推送的。

### 4. 构建并推送

拿到版本号后直接运行，不再要求用户确认（用户已在第 3 步显式给出新版本号，即视为已授权本次推送）：

```bash
VERSION=<用户输入的版本> bash scripts/run-build-<manager|web|xiaozhi>.sh
```

注意：

- 在仓库根目录下执行。
- 构建 + 推送可能耗时几分钟，给 Bash 工具设置足够的 timeout（建议 600000ms）。
- 脚本中途会交互式要求输入 ACR 密码（除非环境里已有 `ACR_PASSWORD`）。如果在非交互环境卡住或报密码相关错误，提醒用户：可以先在提示符输入 `! export ACR_PASSWORD=...`（`!` 前缀在本会话直接执行），或者用户自己在终端跑该命令。
- 脚本输出里有 🔧/✅ emoji 是正常的，照原样转述关键行即可。

### 5. 推送成功后回写版本记录

仅当上一步脚本退出码为 0 且输出包含 `pushed`（如 `✅ manager-web:egg-2.8 pushed`）时，用 Edit 更新 `production-versions.md` 中对应模块的行为新版本，并向用户确认"版本记录已更新为 egg-x.y"。

如果脚本失败或输出不含 `pushed`：

- **不要**改版本记录；
- 把失败输出的关键部分转述给用户；
- 常见失败原因：ACR 密码错误、Docker 未启动、网络问题、buildx 构建器异常（`sae-builder`）。

## 边界情况

- **用户一次想打多个模块**：按模块逐个走完整流程（每个模块有自己的版本号，不要假设它们共用）。也可以在收集齐三个版本号后依次执行。
- **版本号与当前生产相同**：提醒用户这会覆盖 ACR 上已有的同名 tag，让用户明确确认后再继续。
- **production-versions.md 与 ACR 实际不符**：这个文件只是本地记录，可能与线上真实运行的版本脱节（比如有人在别处发了版本没回写）。如果用户提到线上实际版本不同，以用户说的为准，并建议顺便修正记录文件。
