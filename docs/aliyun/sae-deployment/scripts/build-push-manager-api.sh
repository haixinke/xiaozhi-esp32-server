#!/usr/bin/env bash
# 构建并推送 manager-api（后端服务）镜像到 ACR
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

init

# 自动注入 CCR CA 证书（Docker Desktop VM 网络可能经过 CCR MITM 代理）
CCR_CA_CERT="${HOME}/.claude-code-router/app-data/certs/ca.pem"
BUILD_SECRET_ARGS=()
if [ -f "${CCR_CA_CERT}" ]; then
  BUILD_SECRET_ARGS+=("--secret" "id=ccr-ca,src=${CCR_CA_CERT}")
fi

echo "🔧 Building manager-api:${VERSION} for ${PLATFORM} ..."
docker buildx build --platform "${PLATFORM}" \
  -t "${REGISTRY}/manager-api:${VERSION}" \
  -f "${PROJECT_ROOT}/main/manager-api/Dockerfile" "${PROJECT_ROOT}/main/manager-api/" \
  ${BUILD_SECRET_ARGS[@]+"${BUILD_SECRET_ARGS[@]}"} \
  --push

echo "✅ manager-api:${VERSION} pushed"
