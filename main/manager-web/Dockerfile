# 第一阶段：构建Vue前端
FROM node:18-alpine AS builder

WORKDIR /app

# 先复制依赖描述文件，利用Docker缓存加速构建
COPY package*.json .npmrc ./

RUN npm install

# 复制源码并构建
COPY . .

RUN npm run build

# 第二阶段：运行时镜像
FROM nginx:1.27-alpine

# nginx配置模板（启动时通过envsubst替换环境变量）
COPY docker/nginx.conf.template /etc/nginx/nginx.conf.template

# 启动脚本
COPY docker/start.sh /start.sh
RUN chmod +x /start.sh

# 复制前端构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制中文字体
COPY public/generator/static/fonts/*.ttf /usr/share/fonts/

# 更新字体缓存
RUN apk add --no-cache fontconfig && \
    fc-cache -f -v && \
    rm -rf /var/cache/apk/*

EXPOSE 80

CMD ["/start.sh"]
