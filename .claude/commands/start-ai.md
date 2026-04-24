启动聊天服务（main/xiaozhi-server/）。

**前置条件：必须存在虚拟环境 `.venv`**

执行步骤：
1. 检查虚拟环境是否存在。虚拟环境路径：`/Users/minwang/codes/github/xiaozhi-esp32-server/main/xiaozhi-server/.venv/`
   - 如果不存在，输出错误提示并退出：`错误：虚拟环境不存在，请先创建虚拟环境：python -m venv .venv`
2. 检查是否有进程占用 8003 端口。使用 `lsof -ti:8003` 查找占用 8003 端口的进程 PID，如果有则杀掉该进程。
3. 检查是否有正在运行的聊天服务进程。使用 `ps` 命令查找在 `main/xiaozhi-server/` 目录下运行 `.venv/bin/python app.py` 的进程。
4. 如果有正在运行的进程，先杀掉该进程（使用 `kill` 或 `pkill`）。
5. 进入 `main/xiaozhi-server/` 目录，确保 `logs/` 目录存在：
   ```bash
   cd main/xiaozhi-server && mkdir -p logs
   ```
6. **使用虚拟环境中的 Python** 在后台启动聊天服务：
   ```bash
   nohup .venv/bin/python app.py > logs/app.log 2>&1 &
   ```
7. 等待 2-3 秒，检查进程是否启动成功，并输出 `logs/app.log` 最后 15 行确认服务已启动。
