-- 共享明信片表：AI 宠物主动寄给用户的明信片（照片 + 文字），
-- 支持分享小程序卡片给好友，好友免授权查看（公开只读）。
-- share_id 为对外随机串（不可枚举），防全量爬取；数据库主键仍用 ASSIGN_UUID。
CREATE TABLE `ai_pet_postcard` (
  `id` VARCHAR(32) NOT NULL COMMENT '明信片ID(内部UUID)',
  `share_id` VARCHAR(32) NOT NULL COMMENT '对外分享随机串(不可枚举)',
  `pet_id` VARCHAR(32) NOT NULL COMMENT '关联宠物ID',
  `user_id` BIGINT NOT NULL COMMENT '收件用户ID',
  `image_url` VARCHAR(512) NOT NULL COMMENT '明信片照片OSS URL(已过审)',
  `caption` VARCHAR(255) NOT NULL COMMENT '明信片文字(LLM生成)',
  `status` VARCHAR(16) NOT NULL DEFAULT 'SENT' COMMENT 'SENT-已送达/VIEWED-已被查看',
  `creator` BIGINT DEFAULT NULL COMMENT '创建者',
  `create_date` DATETIME DEFAULT NULL COMMENT '创建时间',
  `updater` BIGINT DEFAULT NULL COMMENT '更新者',
  `update_date` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_postcard_share_id` (`share_id`),
  KEY `idx_postcard_user_create` (`user_id`, `create_date`),
  KEY `idx_postcard_pet` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物明信片表';
