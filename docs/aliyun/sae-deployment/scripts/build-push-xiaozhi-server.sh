#!/usr/bin/env bash
# 构建并推送 xiaozhi-server（聊天服务）镜像到 ACR
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

init

echo "🔧 Building xiaozhi-server:${VERSION} for ${PLATFORM} ..."
# 阿里云 ACR 不接受 provenance attestation 的 OCI empty manifest，必须关闭
docker buildx build --platform "${PLATFORM}" \
  --provenance=false \
  -t "${REGISTRY}/xiaozhi-server:${VERSION}" \
  -f "${PROJECT_ROOT}/main/xiaozhi-server/Dockerfile" "${PROJECT_ROOT}/main/xiaozhi-server/" \
  --push

echo "✅ xiaozhi-server:${VERSION} pushed"
