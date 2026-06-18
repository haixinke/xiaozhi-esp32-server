#!/usr/bin/env bash
# 共享函数：ACR 登录 + buildx 构建器准备
# 由 build-push-xiaozhi-server.sh 和 build-push-manager-api.sh source 引入
set -euo pipefail

# ====== 配置区 ======
REGISTRY=crpi-wvrvx27whligoca7.cn-shanghai.personal.cr.aliyuncs.com/companion-ai
VERSION=${VERSION:-$(git rev-parse --short HEAD)}
PLATFORM=${PLATFORM:-linux/amd64}
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"

# ====== 登录 ACR ======
_acr_login() {
  if [ -z "${ACR_USERNAME:-}" ] || [ -z "${ACR_PASSWORD:-}" ]; then
    echo "❌ 请先设置环境变量 ACR_USERNAME 和 ACR_PASSWORD"
    echo "   export ACR_USERNAME=<你的阿里云账号>"
    echo "   export ACR_PASSWORD=<你的密码或RAM子账号AccessKey>"
    exit 1
  fi
  echo "$ACR_PASSWORD" | docker login "$REGISTRY" -u "$ACR_USERNAME" --password-stdin
}

# ====== 准备 buildx 构建器 ======
_prepare_builder() {
  local BUILDER_NAME="sae-builder"
  local BUILDKITD_CONFIG="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/buildkitd.toml"

  if ! docker buildx inspect "${BUILDER_NAME}" >/dev/null 2>&1; then
    echo "🔧 创建 buildx 构建器: ${BUILDER_NAME} [config: ${BUILDKITD_CONFIG}]"
    docker buildx create --name "${BUILDER_NAME}" --use --bootstrap \
      --driver docker-container \
      --config "${BUILDKITD_CONFIG}"
  else
    docker buildx use "${BUILDER_NAME}"
    # 如果 builder 未运行，重建以加载新配置
    if ! docker buildx inspect "${BUILDER_NAME}" --bootstrap 2>/dev/null | grep -q "running"; then
      echo "🔄 重建 buildx 构建器以加载新配置..."
      docker buildx rm "${BUILDER_NAME}" 2>/dev/null || true
      docker buildx create --name "${BUILDER_NAME}" --use --bootstrap \
        --driver docker-container \
        --config "${BUILDKITD_CONFIG}"
    fi
  fi
}

# ====== 初始化 ======
init() {
  _acr_login
  _prepare_builder
  echo "📌 版本: ${VERSION}  平台: ${PLATFORM}  仓库: ${REGISTRY}"
}
