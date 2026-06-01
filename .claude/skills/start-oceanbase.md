---
name: start-oceanbase
description: 启动 OceanBase 数据库容器
---

# 启动 OceanBase 数据库

执行以下操作：

1. 启动容器：`docker start egg-oceanbase`
2. 等待 60-90 秒让容器完成初始化（OceanBase 启动较慢）
3. 验证数据库连接：`docker exec egg-oceanbase obclient -h127.0.0.1 -P2881 -uroot@sys -p123456 -e "SELECT 1"`
4. 如果连接成功，显示 "✅ OceanBase 数据库已成功启动"
5. 如果连接失败，检查 observer 进程状态并给出故障排查建议

**重要提示**：
- 内存配置已通过 docker-compose-oceanbase.yml 环境变量设置（4G/5G/3G），无需手动修改
- 启动过程可能需要 60-90 秒，请耐心等待
