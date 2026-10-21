FROM node:22-bookworm

# Install system dependencies
# deb.debian.org 国内直连不稳，apt 走 host 代理（仅 build 期生效）
RUN echo 'Acquire::http::Proxy "http://host.docker.internal:7897";' > /etc/apt/apt.conf.d/99proxy \
 && echo 'Acquire::https::Proxy "http://host.docker.internal:7897";' >> /etc/apt/apt.conf.d/99proxy \
 && apt-get update && apt-get install -y \
  git \
  curl \
  jq \
 && rm -rf /var/lib/apt/lists/* /etc/apt/apt.conf.d/99proxy

# Issue tracker: .scratch/ markdown files via .sandcastle/bin/scratch-issues.sh
# No extra CLI needed — bash + jq already installed above.

# Build-args for UID/GID alignment: sandcastle docker build-image
# defaults these to the host user's UID/GID so image-built files
# and bind-mounted files share an owner without runtime chown.
ARG AGENT_UID=1000
ARG AGENT_GID=1000

# Rename the base image's "node" user to "agent" and align UID/GID.
RUN groupmod -o -g $AGENT_GID node && usermod -o -u $AGENT_UID -g $AGENT_GID -d /home/agent -m -l agent node

# Install Claude Code CLI via npm (以 root 身份跑，必须在 USER 切换之前)
# 官方 install.sh 从 downloads.claude.ai 拉 native binary，国内经代理也常被掐。
# npm 包 @anthropic-ai/claude-code 走 npm registry，可达性好。
# 全局装到 /usr/local，容器内所有用户都能跑。
# 显式装平台 optional dep：npm 10 在跨平台 build 时会漏 linux-arm64。
RUN npm install -g @anthropic-ai/claude-code \
 && npm install -g @anthropic-ai/claude-code-linux-arm64 \
 && claude --version

# 用户级 skills：由 sync-skills.sh 从本机插件目录同步到 claude-skills/。
# 必须在 usermod -m 之后 COPY，否则 /home/agent 已存在导致 usermod 失败。
# 用数字 UID:GID 而不是名字 —— 上一层 rename 后 /etc/passwd 里的用户名是 agent，
# 但 COPY --chown 在 buildkit 下对名字解析有时序问题，数字最稳。
COPY --chown=${AGENT_UID}:${AGENT_GID} claude-skills/ /home/agent/.claude/skills/

USER ${AGENT_UID}:${AGENT_GID}

# npm -g 装到 /usr/local/bin，已在 PATH 中，无需额外 ENV

WORKDIR /home/agent

# In worktree sandbox mode, Sandcastle bind-mounts the git worktree at /home/agent/workspace
# and overrides the working directory to /home/agent/workspace at container start.
# Structure your Dockerfile so that /home/agent/workspace can serve as the project root.
ENTRYPOINT ["sleep", "infinity"]
