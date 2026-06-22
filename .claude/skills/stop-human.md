---
name: stop-human
description: 关闭 main/digital-human/ 数字人测试模块服务。当用户要求停止数字人、关闭 digital-human、停止 main/digital-human 服务、结束数字人测试模块时使用。
---

# 停止 digital-human 数字人测试模块

执行以下操作停止 `main/digital-human/` 数字人测试模块服务：

1. 查找运行 `start.py` 的 Python 进程
2. 向该进程发送终止信号（优先使用 SIGTERM）
3. 等待 3-5 秒让服务完成关闭
4. 验证端口 `8006` 是否已释放：`lsof -i :8006` 或 `curl -s http://127.0.0.1:8006/health` 应无响应
5. 如果进程未退出，发送 SIGKILL 强制终止
6. 告知用户服务已停止

**常用命令**：

```bash
# 查找进程
ps aux | grep "python start.py" | grep -v grep

# 终止进程（将 <pid> 替换为实际进程号）
kill <pid>

# 强制终止（如果普通终止无效）
kill -9 <pid>
```

**一键停止脚本**：

```bash
pkill -f "python start.py" 2>/dev/null || true
sleep 2
if lsof -i :8006 >/dev/null 2>&1; then
  echo "服务仍在运行，尝试强制终止"
  pkill -9 -f "python start.py" 2>/dev/null || true
else
  echo "digital-human 服务已停止"
fi
```

**验证停止**：

```bash
curl -s http://127.0.0.1:8006/health
```

若返回连接失败或超时，说明服务已成功停止。

**注意事项**：
- 停止服务不会删除模型文件或日志
- 下次启动前无需重新下载模型
- 如果有多个 `start.py` 实例在运行，`pkill -f` 会全部终止
