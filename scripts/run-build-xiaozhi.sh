#!/usr/bin/env bash
# ============================================================================
# xiaozhi-server（聊天服务）镜像构建 + 推送执行器（本地运行）
# ============================================================================
#
# 使用方法：
#   1. chmod +x scripts/run-build-xiaozhi.sh
#   2. bash scripts/run-build-xiaozhi.sh
#   3. 按提示输入 ACR 密码（用户名已预配置；也可通过环境变量 ACR_PASSWORD 传入密码）
#
# 说明：脚本不含密码，密码在运行时输入或经环境变量传入，可安全提交到 git。
# ============================================================================

set -euo pipefail

# ⬇️ 配置区
ACR_USERNAME="nick4941179123"
VERSION="${VERSION:-}"

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

export ACR_USERNAME ACR_PASSWORD
[ -n "$VERSION" ] && export VERSION

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$SCRIPT_DIR/../docs/aliyun/sae-deployment/scripts/build-push-xiaozhi-server.sh"
