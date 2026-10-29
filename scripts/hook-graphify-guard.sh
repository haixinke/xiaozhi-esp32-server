#!/bin/bash
# PreToolUse wrapper：graphify hook-guard 的项目内限定版。
# 上游 search guard 只看命令里有无 grep/find token，不看目标路径，
# 导致查日志、~/.claude、session 文件等无关路径也被强制插话。
# 这里先按目标路径过滤：命令不含项目内路径时直接放行。
INPUT=$(cat)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // .tool_input.pattern // empty')
# Grep 工具（pattern 无 command）：由上游自己判断，直接透传
if echo "$INPUT" | jq -e '.tool_input.command // empty' >/dev/null 2>&1; then
  # Bash 命令：不引用项目根或相对路径（本项目文件名）时放行。
  # 判定：含项目根绝对路径，或不含任何绝对路径（相对路径默认锚定 cwd=项目内）。
  if echo "$CMD" | grep -qE '/Users/|~/|\.claude|\.scratch' && ! echo "$CMD" | grep -qF '/Users/minwang/codes/github/xiaozhi-esp32-server'; then
    exit 0
  fi
fi
# 调用 graphify hook-guard，并追加项目特定的查询提示
RESULT=$(echo "$INPUT" | /Users/minwang/.local/bin/graphify hook-guard search 2>&1)

# 如果 graphify 有输出，追加 --context 提示
if echo "$RESULT" | grep -q "additionalContext"; then
  # 提取原始提示并增强
  ENHANCED=$(echo "$RESULT" | sed 's/Only grep after graphify has oriented you, or to modify\/debug specific lines\./Only grep after graphify has oriented you, or to modify\/debug specific lines. TIP: Use --context "path\/to\/module" to narrow results (e.g. --context "main\/manager-api")./')
  echo "$ENHANCED"
else
  echo "$RESULT"
fi