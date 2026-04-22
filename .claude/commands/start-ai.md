启动聊天服务（main/xiaozhi-server/）。

执行步骤：
1. 检查是否有进程占用 8003 端口。使用 `lsof -ti:8003` 或 `netstat` / `ss` 查找占用 8003 端口的进程 PID，如果有则杀掉该进程。
2. 检查是否有正在运行的聊天服务进程。使用 `ps` 命令查找在 `main/xiaozhi-server/` 目录下运行 `python app.py` 或 `.venv/bin/python app.py` 的进程。
3. 如果有正在运行的进程，先杀掉该进程（使用 `kill` 或 `pkill`）。
4. 进入 `main/xiaozhi-server/` 目录，确保 `logs/` 目录存在：
   ```bash
   cd main/xiaozhi-server && mkdir -p logs
   ```
5. 使用 `nohup` 在后台启动聊天服务：
   ```bash
   nohup python app.py > logs/app.log 2>&1 &
   ```
   如果存在虚拟环境 `.venv`，则使用 `.venv/bin/python app.py` 替代 `python app.py`。
6. 等待 2-3 秒，检查进程是否启动成功，并输出 `logs/app.log` 最后 15 行确认服务已启动。
