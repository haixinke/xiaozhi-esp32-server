#!/bin/bash
# PreToolUse hook（合并版）：一次注入完成两项检查，避免双 hook 双份输出。
# 1. graphify 项目内限定 guard：上游 search guard 只看命令里有无 grep/find token，
#    不看目标路径。这里先按目标路径过滤：命令不含项目内路径时跳过 graphify 检查。
# 2. dedicated-tools 检查：拦截 Bash 里裸用的 grep/cat/head/tail/sed/awk，
#    提示改用专用工具（Grep/Read/Edit）。
# 顺序：dedicated-tools 硬拦截（exit 2）优先——裸 grep 被拦时不必再跑 graphify。
INPUT=$(cat)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // .tool_input.pattern // empty')

# ---- 检查 2：Bash 裸用 grep/cat/head/tail/sed/awk（硬拦截） ----
if echo "$INPUT" | jq -e '.tool_input.command // empty' >/dev/null 2>&1; then
  # 豁免：node_modules / 日志文件，Grep 工具反而不合适（慢/不搜依赖目录）
  if ! echo "$CMD" | grep -qE 'node_modules/|\.log\b|/logs/'; then
    # 只拦命令开头的裸调用（^\s*cmd），管道/&& 之后的不拦；rg 豁免
    if echo "$CMD" | grep -qE '^\s*(/\S+/)?(grep|cat|head|tail|sed|awk)\s' && ! echo "$CMD" | grep -qE '^\s*(/\S+/)?rg\s'; then
      echo "优先用专用工具：Grep（搜索）、Read（读文件）、Edit（改文件），不要在 Bash 里直接 grep/cat/head/tail/sed/awk。确属合理用途（如处理命令输出）请加管道或注释说明。" >&2
      exit 2
    fi
  fi
fi

# ---- 检查 1：graphify 项目内路径过滤 ----
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
  # 用 jq 安全拼接：直接在字符串值内 sed 会留下未转义引号，产出非法 JSON
  echo "$RESULT" | jq -c '.hookSpecificOutput.additionalContext += " TIP: Use --context \"path/to/module\" to narrow results (e.g. --context \"main/manager-api\")."'
else
  echo "$RESULT"
fi
