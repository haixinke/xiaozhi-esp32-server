-- 宠物逻辑删除：deleted_at 标记（0=未删除，epoch毫秒=删除时间），见 ADR 0006
-- 唯一索引改造为 (user_id, deleted_at)：删除保留旧行后仍可再领养；
-- 同一用户多次删除因删除时间戳不同不撞键。不用 NULL 标记——MySQL 唯一索引允许多个 NULL 会使活跃约束失效
ALTER TABLE `ai_pet` ADD COLUMN `deleted_at` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除时间戳(epoch毫秒): 0=未删除';

ALTER TABLE `ai_pet` DROP INDEX `uk_ai_pet_user_id`;
ALTER TABLE `ai_pet` ADD UNIQUE KEY `uk_ai_pet_user_deleted` (`user_id`, `deleted_at`);
