-- 游戏分类与外部游戏跳转（ticket 01）：为 ai_game 增加游戏分类与跳转链接
-- game_category 非空且默认 INTERNAL：存量游戏在升级时自动回填为内部游戏，无需逐条修改运营记录
-- jump_url 可空：仅外部游戏（EXTERNAL）使用，内部游戏恒为 NULL（由服务端在保存时清空）

ALTER TABLE `ai_game`
  ADD COLUMN `game_category` VARCHAR(16) NOT NULL DEFAULT 'INTERNAL' COMMENT '游戏分类:INTERNAL内部游戏(原生玩法) EXTERNAL外部游戏(H5跳转)' AFTER `game_type`,
  ADD COLUMN `jump_url` VARCHAR(1024) NULL COMMENT '跳转链接(仅外部游戏使用,内部游戏为NULL)' AFTER `description`;
