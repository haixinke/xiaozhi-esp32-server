#!/usr/bin/env bash
# FunASR 镜像转存到个人 ACR（解决 SAE 拉取公共镜像超时问题）
# 由 scripts/run-mirror-funasr.sh 调用
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

FUNASR_VERSION="funasr-runtime-sdk-online-cpu-0.1.13"
SOURCE_IMAGE="registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:${FUNASR_VERSION}"
TARGET_IMAGE="${REGISTRY}/funasr:${FUNASR_VERSION}"

init

echo "=========================================="
echo "源镜像: ${SOURCE_IMAGE}"
echo "目标镜像: ${TARGET_IMAGE}"
echo "=========================================="

echo "正在拉取源镜像..."
docker pull "${SOURCE_IMAGE}"

echo "正在重新打标签..."
docker tag "${SOURCE_IMAGE}" "${TARGET_IMAGE}"

echo "正在推送到目标 ACR..."
docker push "${TARGET_IMAGE}"

echo "=========================================="
echo "镜像转存成功"
echo "请在 SAE 中使用以下镜像地址:"
echo "${TARGET_IMAGE}"
echo "=========================================="
