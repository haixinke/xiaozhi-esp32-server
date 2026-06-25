FROM python:3.12-slim AS builder

WORKDIR /app
RUN set -e; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's|http://deb.debian.org|https://mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources; \
    elif [ -f /etc/apt/sources.list ]; then \
        sed -i 's|http://deb.debian.org|https://mirrors.aliyun.com|g' /etc/apt/sources.list; \
    fi \
    && apt-get update \
    && apt-get install -y --no-install-recommends gcc \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip \
    && pip install -r requirements.txt

FROM python:3.12-slim AS runner
WORKDIR /app

RUN set -e; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's|http://deb.debian.org|https://mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources; \
    elif [ -f /etc/apt/sources.list ]; then \
        sed -i 's|http://deb.debian.org|https://mirrors.aliyun.com|g' /etc/apt/sources.list; \
    fi \
    && apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libopus0 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app -g 1001 \
    && useradd -r -g app -u 1001 app

COPY --from=builder /usr/local/lib/python3.12/site-packages /usr/local/lib/python3.12/site-packages
COPY --from=builder /usr/local/bin /usr/local/bin
COPY --chown=app:app . .

# 创建运行时所需目录（NAS 挂载点）
RUN mkdir -p /app/models /app/data /app/tmp /app/music \
    && chown -R app:app /app \
    && chmod +x /app/start.sh

ENV PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai \
    LOG_DIR=/app/tmp \
    DATA_DIR=/app/data \
    NAS_MOUNT_PATH="" \
    MODEL_DIR=/app/models

EXPOSE 8000 8003

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD python -c "import socket,sys; s=socket.socket(); s.settimeout(3); s.connect(('127.0.0.1',8000)); s.close()" || exit 1

CMD ["/app/start.sh"]
