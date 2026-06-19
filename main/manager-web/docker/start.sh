#!/bin/sh
# 启动入口脚本：用环境变量替换nginx配置模板后启动nginx

# 设置默认值
export UPSTREAM_API_ADDR=${UPSTREAM_API_ADDR:-"127.0.0.1:8002"}

echo "📋 UPSTREAM_API_ADDR=${UPSTREAM_API_ADDR}"

# 从 UPSTREAM_API_ADDR 中提取主机名（去掉端口）
UPSTREAM_HOST=$(echo "${UPSTREAM_API_ADDR}" | sed 's/:.*//')

# 如果是IP地址则跳过DNS校验，否则校验域名是否可解析
if echo "${UPSTREAM_HOST}" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "⏭️  UPSTREAM_HOST 是IP地址，跳过DNS校验"
else
  echo "🔍 校验域名 ${UPSTREAM_HOST} 是否可解析..."
  if ! getent hosts "${UPSTREAM_HOST}" >/dev/null 2>&1; then
    echo "❌ DNS解析失败: ${UPSTREAM_HOST}"
    echo "   请检查 SAE 环境变量 UPSTREAM_API_ADDR 的值是否正确"
    echo "   当前值: ${UPSTREAM_API_ADDR}"
    echo "   格式应为: <应用名>.<命名空间>.svc.cluster.local:<端口>"
    echo "   可在 SAE 控制台 → manager-api 应用详情中查看内网访问地址"
    exit 1
  fi
  echo "✅ 域名 ${UPSTREAM_HOST} 解析成功"
fi

# 用envsubst将模板中的${UPSTREAM_API_ADDR}替换为实际环境变量值
envsubst '${UPSTREAM_API_ADDR}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

# 启动nginx
exec nginx -g 'daemon off;'
