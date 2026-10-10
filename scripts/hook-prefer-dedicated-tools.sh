#!/bin/bash
# PreToolUse hook：拦截 Bash 里裸用的 grep/cat/head/tail/sed/awk，提示改用专用工具
# （Grep/Read/Edit）。依据：会话复盘发现 Bash grep/cat 单会话 35+ 次。
# 放行情形：管道右侧、复合命令中段（仍提示一次但不拦截，避免误伤合法组合）。
INPUT=$(cat)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
# 只拦命令开头的裸调用（^\s*cmd），管道/&& 之后的不拦
if echo "$CMD" | grep -qE '^\s*(/\S+/)?(grep|cat|head|tail|sed|awk)\s'; then
  echo "优先用专用工具：Grep（搜索）、Read（读文件）、Edit（改文件），不要在 Bash 里直接 grep/cat/head/tail/sed/awk。确属合理用途（如处理命令输出）请加管道或注释说明。" >&2
  exit 2
fi
exit 0
