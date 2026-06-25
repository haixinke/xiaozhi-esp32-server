#!/bin/sh
# SAE startup wrapper: 支持 NAS 挂载和 ConfigMap 两种配置来源
set -e

CONFIG_TARGET="/app/data/.config.yaml"
NAS_MOUNT="${NAS_MOUNT_PATH:-}"

echo "=== xiaozhi-server startup ==="
echo "NAS_MOUNT_PATH=${NAS_MOUNT}"

# ─── 配置文件处理 ───────────────────────────────────────────
mkdir -p /app/data

if [ -n "${NAS_MOUNT}" ] && [ -d "${NAS_MOUNT}" ]; then
    echo "[config] NAS mount detected at ${NAS_MOUNT}"

    # 1. 配置文件: NAS -> /app/data/.config.yaml
    if [ -f "${NAS_MOUNT}/config/.config.yaml" ]; then
        echo "[config] linking ${NAS_MOUNT}/config/.config.yaml -> ${CONFIG_TARGET}"
        ln -sf "${NAS_MOUNT}/config/.config.yaml" "${CONFIG_TARGET}"
    elif [ -f "${NAS_MOUNT}/config/config.yaml" ]; then
        echo "[config] linking ${NAS_MOUNT}/config/config.yaml -> ${CONFIG_TARGET}"
        ln -sf "${NAS_MOUNT}/config/config.yaml" "${CONFIG_TARGET}"
    fi

    # 2. 模型文件: NAS -> /app/models (符号链接)
    if [ -d "${NAS_MOUNT}/models" ]; then
        echo "[models] linking ${NAS_MOUNT}/models contents -> /app/models/"
        for dir in "${NAS_MOUNT}/models/"*/; do
            if [ -d "$dir" ]; then
                base=$(basename "$dir")
                ln -sfn "$dir" "/app/models/${base}"
                echo "[models]   linked: ${base}"
            fi
        done
    fi

    # 3. 音乐文件: NAS -> /app/music
    if [ -d "${NAS_MOUNT}/music" ]; then
        echo "[music] linking ${NAS_MOUNT}/music -> /app/music"
        rm -rf /app/music
        ln -sfn "${NAS_MOUNT}/music" /app/music
    fi
else
    echo "[config] NAS not mounted, falling back to ConfigMap mode"

    # 兼容旧的 ConfigMap 挂载方式
    CONFIG_SOURCE="/tmp/xiaozhi-config"
    if [ -f "${CONFIG_SOURCE}" ]; then
        echo "[config] found ConfigMap file, copying..."
        cat "${CONFIG_SOURCE}" > "${CONFIG_TARGET}"
    elif [ -d "${CONFIG_SOURCE}" ]; then
        if [ -f "${CONFIG_SOURCE}/config.yaml" ]; then
            echo "[config] found ${CONFIG_SOURCE}/config.yaml, copying..."
            cat "${CONFIG_SOURCE}/config.yaml" > "${CONFIG_TARGET}"
        elif [ -f "${CONFIG_SOURCE}/.config.yaml" ]; then
            echo "[config] found ${CONFIG_SOURCE}/.config.yaml, copying..."
            cat "${CONFIG_SOURCE}/.config.yaml" > "${CONFIG_TARGET}"
        fi
    fi
fi

# ─── 容错检查 ─────────────────────────────────────────────
# 配置文件检查
if [ ! -e "${CONFIG_TARGET}" ]; then
    echo "WARN: ${CONFIG_TARGET} not found, app will use defaults from config.yaml"
    touch "${CONFIG_TARGET}"
fi

# 模型文件检查（非致命，打印警告）
MODEL_DIR="${MODEL_DIR:-/app/models}"
VAD_MODEL="${MODEL_DIR}/snakers4_silero-vad/src/silero_vad/data/silero_vad.onnx"
if [ ! -f "${VAD_MODEL}" ]; then
    echo "WARN: VAD model not found at ${VAD_MODEL}"
    echo "      Voice activity detection may fail at runtime."
    echo "      Please ensure NAS is mounted with models/snakers4_silero-vad/"
fi

echo "=== starting app ==="
exec python app.py
