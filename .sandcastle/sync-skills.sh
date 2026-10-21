#!/usr/bin/env bash
# 从本机插件目录同步所需 skill 到 build context（claude-skills/）。
# claude-skills/ 是本地产物，已加入 .gitignore，不入库。
# 用法：./sync-skills.sh  （在 .sandcastle/ 下执行，build 镜像前跑）
set -euo pipefail

MP=~/.claude/plugins/marketplaces/mattpocock/skills/engineering
SP=~/.claude/plugins/cache/superpowers-marketplace/superpowers/7.0.0/skills
DST="$(dirname "$0")/claude-skills"

rm -rf "$DST"
mkdir -p "$DST"

# 目标 skill 列表 —— 增删改这里
SKILLS=(
  "$MP/tdd"
  "$MP/diagnosing-bugs"
  "$SP/systematic-debugging"
  "$SP/verification-before-completion"
)

for src in "${SKILLS[@]}"; do
  name="$(basename "$src")"
  if [[ -d "$src" ]]; then
    cp -r "$src" "$DST/$name"
    echo "synced: $name"
  else
    echo "MISSING: $src" >&2
    exit 1
  fi
done

echo "done -> $DST"
