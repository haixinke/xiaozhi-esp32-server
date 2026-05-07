停止后端服务（main/manager-api/）。

执行步骤：
1. 查找后端服务进程。使用 `ps` 命令查找在 `main/manager-api/` 目录下运行 `mvn spring-boot:run` 或 `java -jar` 的进程：
   ```bash
   ps aux | grep -E 'main/manager-api.*(mvn|java)' | grep -v grep
   ```
2. 查找占用 8002 端口的进程：
   ```bash
   lsof -ti:8002
   ```
3. 如果找到进程，先尝试优雅关闭（`kill PID`），等待 3 秒（Spring Boot 需要时间优雅关闭），检查是否仍在运行，如果仍在运行则强制关闭（`kill -9 PID`）。
4. 确认所有相关进程已停止，再次检查端口占用情况。
5. 输出停止结果：如果找到并停止了进程，显示进程 PID；如果没有运行中的服务，提示"后端服务未在运行"。
