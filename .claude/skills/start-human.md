---
name: start-human
description: 启动 main/digital-human/ 数字人测试模块服务。当用户要求启动数字人、启动 digital-human、运行数字人测试模块、启动 main/digital-human 服务时使用。
---

# 启动 digital-human 数字人测试模块

执行以下操作启动 `main/digital-human/` 数字人测试模块服务：

1. 进入目录：`cd main/digital-human`
2. 检查服务是否已运行：访问 `http://127.0.0.1:8006/health`
3. 如果健康检查已返回 `{"status": "ok"}`，说明服务已在运行，提示用户无需重复启动
4. 激活虚拟环境：`source .venv/bin/activate`
5. 启动服务：`python start.py`
6. 等待 3-5 秒，服务会启动 HTTP 服务器和唤醒词运行时
7. 验证健康检查：`curl -s http://127.0.0.1:8006/health` 应返回 `{"status": "ok"}`
8. 成功后告知用户访问地址：
   - 测试页面：http://127.0.0.1:8006/index.html
   - 事件桥 WebSocket：`ws://127.0.0.1:8006/wakeword-ws`
   - 健康检查：http://127.0.0.1:8006/health

**环境要求**：
- 依赖已安装在 `main/digital-human/.venv/` 虚拟环境中
- 唤醒词模型文件应位于 `main/digital-human/wakeword_runtime/models/` 目录下（encoder.onnx、decoder.onnx、joiner.onnx、tokens.txt）
- 如模型缺失，服务会启动失败，需先下载并配置模型

**常见失败处理**：
- 若提示缺少 `numpy`，在虚拟环境中执行 `pip install numpy`
- 若提示缺少 `sherpa-onnx` 等依赖，执行 `pip install -r wakeword_runtime/requirements.txt`
- 若唤醒词服务初始化失败但测试页面仍可正常使用，会打印警告信息，不影响页面访问

**后台运行**：
如果用户需要长期在后台运行，可以使用 `nohup` 或 `screen`：

```bash
nohup python start.py > logs/digital-human.log 2>&1 &
```

服务启动后会持续占用终端，按 `Ctrl+C` 可停止。
