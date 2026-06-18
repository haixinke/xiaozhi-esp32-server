#!/usr/bin/env bash
# 构建并推送 manager-api（后端服务）镜像到 ACR
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

init

echo "🔧 Building manager-api:${VERSION} for ${PLATFORM} ..."
docker buildx build --platform "${PLATFORM}" \
  -t "${REGISTRY}/manager-api:${VERSION}" \
  -f "${PROJECT_ROOT}/main/manager-api/Dockerfile" "${PROJECT_ROOT}/main/manager-api/" \
  --push

echo "✅ manager-api:${VERSION} pushed"
