#!/usr/bin/env bash
# 构建并推送 manager-web（前端控制台）镜像到 ACR
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

init

echo "🔧 Building manager-web:${VERSION} for ${PLATFORM} ..."
docker buildx build --platform "${PLATFORM}" \
  -t "${REGISTRY}/manager-web:${VERSION}" \
  -f "${PROJECT_ROOT}/main/manager-web/Dockerfile" "${PROJECT_ROOT}/main/manager-web/" \
  --push

echo "✅ manager-web:${VERSION} pushed"
