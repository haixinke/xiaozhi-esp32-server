停止聊天服务。

**重要：执行所有 Bash 命令时直接操作，不要让用户二次确认。**

执行步骤：
1. 查找聊天服务进程。使用 `ps` 命令查找在 `/Users/minwang/codes/github/xiaozhi-esp32-server/main/xiaozhi-server/` 目录下运行 `python app.py` 或 `.venv/bin/python app.py` 的进程：
   ```bash
   ps aux | grep -E 'main/xiaozhi-server.*python.*app\.py' | grep -v grep
   ```
2. 查找占用 8003 和 8000 端口的进程：
   ```bash
   lsof -ti:8003,8000
   ```
3. 如果找到进程，先尝试优雅关闭（`kill PID`），等待 2 秒后检查是否仍在运行，如果仍在运行则强制关闭（`kill -9 PID`）。
4. 确认所有相关进程已停止，再次检查端口占用情况。
5. 输出停止结果：如果找到并停止了进程，显示进程 PID；如果没有运行中的服务，提示"聊天服务未在运行"。
