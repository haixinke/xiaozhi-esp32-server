启动 web 服务（main/manager-web/）。

执行步骤：
1. 检查是否有正在运行的 web 服务进程。使用 `ps` 命令查找在 `main/manager-web/` 目录下运行 `npm run serve` 的进程，也可通过端口 8001 占用情况判断。
2. 如果有正在运行的进程，先杀掉该进程（使用 `kill` 或 `pkill`）。如果端口 8001 被占用，也杀掉占用该端口的进程。
3. 进入 `main/manager-web/` 目录，确保 `logs/` 目录存在（不存在则创建），然后使用 `nohup` 在后台启动 web 服务：
   ```bash
   cd main/manager-web && mkdir -p logs && nohup npm run serve > logs/web.log 2>&1 &
   ```
4. 等待 5-8 秒（首次构建较慢），检查进程是否启动成功，并输出 `logs/web.log` 最后 20 行确认服务已启动。如果日志中出现 `App running at` 或 `Compiled successfully`，说明启动成功。