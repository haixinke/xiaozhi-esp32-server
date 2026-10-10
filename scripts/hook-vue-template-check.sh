#!/bin/bash
# PostToolUse hook：Write/Edit 了 manager-web 下的 .vue 文件时，立即编译校验其模板。
# stdin 为工具调用的 JSON；非 .vue 或不在 manager-web 内直接放行。
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
case "$FILE" in
  */main/manager-web/*.vue) ;;
  *) exit 0 ;;
esac
cd "$(dirname "$0")/../main/manager-web" || { echo "warn: manager-web 目录不存在，跳过模板检查" >&2; exit 0; }
# 模板错误输出到 stderr 并以退出码 2 反馈给 agent（PostToolUse 的约定）
node scripts/check-vue-template.js "$FILE" 2>&1 || exit 2
