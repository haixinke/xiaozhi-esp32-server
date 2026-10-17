-- ai_game.game_type 增加唯一索引：同一游戏类型只允许一条配置
-- 目的：杜绝同类型游戏被重复创建，违反"每种类型一份运营配置"的业务约束
--
-- 部署前置检查（重要）：本 changeset 在含重复 game_type 行的环境会失败并阻塞应用启动
-- 部署前先在目标库执行：
--   SELECT game_type, COUNT(*) FROM ai_game GROUP BY game_type HAVING COUNT(*) > 1;
-- 若返回非空，需人工保留一行、删除其余，再执行 Liquibase

ALTER TABLE `ai_game`
  ADD UNIQUE KEY `uk_ai_game_game_type` (`game_type`);
