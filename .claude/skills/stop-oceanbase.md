---
name: stop-oceanbase
description: 关闭 OceanBase 数据库容器
---

# 关闭 OceanBase 数据库

执行以下操作：

1. 停止 OceanBase 容器：`docker stop egg-oceanbase`
2. 等待 3 秒确保容器完全停止
3. 验证容器状态（应为 Exited 状态）
4. 提示用户数据已保存在 `./oceanbase/data/` 目录

**重要提示**：
- 停止容器不会删除数据
- 所有数据持久化在 `./oceanbase/data/` 目录
- 下次启动时数据会自动恢复
