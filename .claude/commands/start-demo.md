重启演示服务（main/demo-web/）。

执行步骤：
1. 检查是否有正在运行的演示服务进程。使用 `ps` 命令查找在 `main/demo-web/` 目录下运行 `vite` 的进程，也可通过端口 8006 占用情况判断。
2. 如果有正在运行的进程，先杀掉该进程（使用 `kill` 或 `pkill`）。如果端口 8006 被占用，也杀掉占用该端口的进程。
3. 进入 `main/demo-web/` 目录，确保 `logs/` 目录存在（不存在则创建），然后使用 `nohup` 在后台启动演示服务：
   ```bash
   cd main/demo-web && mkdir -p logs && nohup npx vite --port 8006 > logs/demo.log 2>&1 &
   ```
4. 等待 2-3 秒，检查进程是否启动成功，并输出 `logs/demo.log` 最后 10 行确认服务已启动。如果日志中出现 `Local:` 和 `http://localhost:8006`，说明启动成功。
