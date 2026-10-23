#!/usr/bin/env bash
# ============================================================================
# sandcastle 镜像推送执行器（本地运行）
# ============================================================================
#
# 用途：把本地已构建的 sandcastle:xiaozhi-esp32-server 镜像推送到阿里云 ACR
#       （仓库名 sandcastle），作为 Docker Desktop 故障/重装后的备份恢复源。
#
# 使用方法：
#   1. chmod +x scripts/run-push-sandcastle.sh
#   2. bash scripts/run-push-sandcastle.sh
#   3. 按提示输入 ACR 密码（用户名已预配置；也可通过环境变量 ACR_PASSWORD 传入密码）
#
# 恢复方法（Docker Desktop 重装后）：
#   docker login <REGISTRY> -u <用户名>
#   docker pull  <REGISTRY>/sandcastle:<VERSION>
#   docker tag   <REGISTRY>/sandcastle:<VERSION> sandcastle:xiaozhi-esp32-server
#
# 说明：脚本不含密码，密码在运行时输入或经环境变量传入，可安全提交到 git。
#       本脚本只做 tag + push，不做跨平台构建；镜像架构 = 本机构建时的架构。
# ============================================================================

set -euo pipefail

# 解析仓根并切过去，保证 git rev-parse 解析的是本仓而非调用方 cwd
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# ⬇️ 配置区
ACR_USERNAME="${ACR_USERNAME:-nick4941179123}"
# 与 _common.sh 同一 ACR 实例 + 命名空间，仓库名 sandcastle
REGISTRY="crpi-wvrvx27whligoca7.cn-shanghai.personal.cr.aliyuncs.com/companion-ai"
LOCAL_IMAGE="sandcastle:xiaozhi-esp32-server"
# 默认用当前 git 短哈希作为远端 tag；显式传 VERSION 则原样使用
VERSION="${VERSION:-$(git rev-parse --short HEAD)}"

# 密码通过交互式输入读取，避免硬编码在脚本中
if [ -n "${ACR_PASSWORD:-}" ]; then
  echo "检测到环境变量 ACR_PASSWORD，将使用该值"
else
  read -rsp "请输入 ACR 密码（输入不显示）: " ACR_PASSWORD
  echo
fi
# ⬆️ 配置区

if [ -z "${ACR_PASSWORD:-}" ]; then
  echo "错误: ACR_PASSWORD 不能为空"
  exit 1
fi

# 本地镜像必须已存在（由 .sandcastle/Dockerfile 构建产出）
if ! docker image inspect "${LOCAL_IMAGE}" >/dev/null 2>&1; then
  echo "错误: 本地镜像 ${LOCAL_IMAGE} 不存在，请先构建"
  exit 1
fi

REMOTE_IMAGE="${REGISTRY}/sandcastle:${VERSION}"

echo "$ACR_PASSWORD" | docker login "$REGISTRY" -u "$ACR_USERNAME" --password-stdin
unset ACR_PASSWORD

echo "📌 本地镜像: ${LOCAL_IMAGE}"
echo "📌 远端镜像: ${REMOTE_IMAGE}"

# tag 同时保留 latest，方便"直接拉最新"的恢复场景
docker tag "${LOCAL_IMAGE}" "${REMOTE_IMAGE}"
docker tag "${LOCAL_IMAGE}" "${REGISTRY}/sandcastle:latest"

docker push "${REMOTE_IMAGE}"
docker push "${REGISTRY}/sandcastle:latest"

echo "✅ sandcastle 镜像已推送: ${REMOTE_IMAGE} (+latest)"
