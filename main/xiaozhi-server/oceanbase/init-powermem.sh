#!/bin/bash
# OceanBase for PowerMem 初始化脚本

set -e

echo "=== OceanBase for PowerMem 初始化 ==="

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 配置
OB_CONTAINER="xiaozhi-oceanbase"
OB_HOST="127.0.0.1"
OB_PORT="2881"
OB_USER="root@test"
OB_PASSWORD="123456"
OB_DATABASE="powermem"
INIT_SCRIPT="./oceanbase/init/01-init-powermem.sql"

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}错误: Docker 未运行，请先启动 Docker${NC}"
    exit 1
fi

# 检查容器是否已存在
if docker ps -a --format '{{.Names}}' | grep -q "^${OB_CONTAINER}$"; then
    echo -e "${YELLOW}容器 ${OB_CONTAINER} 已存在${NC}"
    read -p "是否删除旧容器并重新创建？(y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}停止并删除旧容器...${NC}"
        docker stop ${OB_CONTAINER} 2>/dev/null || true
        docker rm ${OB_CONTAINER} 2>/dev/null || true
    else
        echo -e "${GREEN}启动现有容器...${NC}"
        docker start ${OB_CONTAINER}
        exit 0
    fi
fi

# 启动 OceanBase 容器
echo -e "${GREEN}启动 OceanBase 容器...${NC}"
docker-compose -f docker-compose-oceanbase.yml up -d

# 等待 OceanBase 启动
echo -e "${YELLOW}等待 OceanBase 初始化（约需 60-90 秒）...${NC}"
MAX_TRIES=60
TRY=0

while [ $TRY -lt $MAX_TRIES ]; do
    TRY=$((TRY + 1))
    echo -n "."

    # 检查容器是否健康
    if docker exec ${OB_CONTAINER} obclient -h${OB_HOST} -P${OB_PORT} -u${OB_USER} -p${OB_PASSWORD} -e "SELECT 1" &> /dev/null; then
        echo -e "\n${GREEN}OceanBase 已就绪！${NC}"
        break
    fi

    sleep 2
done

if [ $TRY -eq $MAX_TRIES ]; then
    echo -e "\n${RED}错误: OceanBase 启动超时${NC}"
    echo "请检查日志: docker logs ${OB_CONTAINER}"
    exit 1
fi

# 执行初始化 SQL
echo -e "${GREEN}执行 PowerMem 初始化脚本...${NC}"
docker exec -i ${OB_CONTAINER} obclient -h${OB_HOST} -P${OB_PORT} -u${OB_USER} -p${OB_PASSWORD} < ${INIT_SCRIPT}

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ PowerMem 数据库初始化成功！${NC}"
    echo ""
    echo "连接信息:"
    echo "  主机: ${OB_HOST}"
    echo "  端口: ${OB_PORT}"
    echo "  用户: ${OB_USER}"
    echo "  密码: ${OB_PASSWORD}"
    echo "  数据库: ${OB_DATABASE}"
    echo ""
    echo "常用命令:"
    echo "  查看日志: docker logs -f ${OB_CONTAINER}"
    echo "  进入数据库: docker exec -it ${OB_CONTAINER} obclient -h${OB_HOST} -P${OB_PORT} -u${OB_USER} -p${OB_PASSWORD} ${OB_DATABASE}"
    echo "  停止服务: docker-compose -f docker-compose-oceanbase.yml down"
    echo ""
    echo "下一步: 在 xiaozhi-server 的 data/.config.yaml 中配置 PowerMem 使用 OceanBase"
else
    echo -e "${RED}✗ 初始化失败${NC}"
    exit 1
fi
