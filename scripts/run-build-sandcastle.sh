#!/usr/bin/env bash
# ============================================================================
# sandcastle 镜像本地构建执行器
# ============================================================================
#
# 用途：从 .sandcastle/Dockerfile 构建本地 sandcastle:xiaozhi-esp32-server 镜像
#       （Java 21 + Maven + Python 3.12 + Node 22 + Claude CLI + skills）。
#
# 使用方法：
#   bash scripts/run-build-sandcastle.sh
#
# 说明：
#   - 必须先跑 .sandcastle/sync-skills.sh 把 skills 同步进构建上下文
#   - 必须 cd 到 .sandcastle/，构建上下文才包含 claude-skills/
#   - AGENT_UID/GID 须等于 host 用户，容器内文件权限才对
#   - 重建后建议跑 scripts/run-push-sandcastle.sh 推到 ACR 备份
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 同步 skills 到构建上下文（.sandcastle/claude-skills/）
bash "$PROJECT_ROOT/.sandcastle/sync-skills.sh"

cd "$PROJECT_ROOT/.sandcastle"

docker build -t sandcastle:xiaozhi-esp32-server \
  --build-arg AGENT_UID="$(id -u)" \
  --build-arg AGENT_GID="$(id -g)" \
  --add-host=host.docker.internal:host-gateway \
  .

echo "✅ sandcastle:xiaozhi-esp32-server 构建完成"
