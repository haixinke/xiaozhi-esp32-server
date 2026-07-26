#!/usr/bin/env bash
# ============================================================================
# FunASR 镜像转存到个人 ACR 的本地执行器（解决 SAE 拉取公共镜像超时问题）
# ============================================================================
#
# 使用方法：
#   1. chmod +x scripts/run-mirror-funasr.sh
#   2. bash scripts/run-mirror-funasr.sh
#   3. 按提示输入 ACR 密码
#
# 也可通过环境变量传入：
#   ACR_PASSWORD=你的密码 bash scripts/run-mirror-funasr.sh
#
# 说明：脚本不含密码，密码在运行时输入或经环境变量传入，可安全提交到 git。
# ============================================================================

set -euo pipefail

# ⬇️ 配置区
ACR_USERNAME="nick4941179123"
# ⬆️ 配置区

# 密码通过交互式输入读取，避免硬编码在脚本中
if [ -n "${ACR_PASSWORD:-}" ]; then
  echo "检测到环境变量 ACR_PASSWORD，将使用该值"
else
  read -rsp "请输入 ACR 密码（输入不显示）: " ACR_PASSWORD
  echo
fi

if [ -z "${ACR_PASSWORD:-}" ]; then
  echo "错误: ACR_PASSWORD 不能为空"
  exit 1
fi

export ACR_USERNAME ACR_PASSWORD

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$SCRIPT_DIR/../docs/aliyun/sae-deployment/scripts/mirror-funasr-image.sh"
