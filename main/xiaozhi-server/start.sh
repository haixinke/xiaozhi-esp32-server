#!/bin/sh
# SAE startup wrapper: copies ConfigMap-mounted config into /app/data/.config.yaml
# before starting xiaozhi-server.
set -e

CONFIG_SOURCE="/tmp/xiaozhi-config"
CONFIG_TARGET="/app/data/.config.yaml"

echo "=== checking config source ==="
ls -la "${CONFIG_SOURCE}" 2>/dev/null || true
ls -la /app/data/ 2>/dev/null || true

echo "=== copying config ==="
rm -rf "${CONFIG_TARGET}"
mkdir -p /app/data

if [ -f "${CONFIG_SOURCE}" ]; then
    echo "found ${CONFIG_SOURCE}, copying..."
    cat "${CONFIG_SOURCE}" > "${CONFIG_TARGET}"
elif [ -d "${CONFIG_SOURCE}" ]; then
    if [ -f "${CONFIG_SOURCE}/config.yaml" ]; then
        echo "found ${CONFIG_SOURCE}/config.yaml, copying..."
        cat "${CONFIG_SOURCE}/config.yaml" > "${CONFIG_TARGET}"
    elif [ -f "${CONFIG_SOURCE}/.config.yaml" ]; then
        echo "found ${CONFIG_SOURCE}/.config.yaml, copying..."
        cat "${CONFIG_SOURCE}/.config.yaml" > "${CONFIG_TARGET}"
    fi
fi

if [ ! -s "${CONFIG_TARGET}" ]; then
    echo "ERROR: ${CONFIG_TARGET} is missing or empty, config copy failed" >&2
    exit 1
fi

chmod 644 "${CONFIG_TARGET}"
ls -la "${CONFIG_TARGET}"

echo "=== starting app ==="
exec python app.py
