-- liquibase formatted sql

-- changeset xiaozhi:202608061500
-- 部署前请先执行只读重复 user_id 预检：
-- SELECT user_id, COUNT(*) AS pet_count FROM ai_pet GROUP BY user_id HAVING COUNT(*) > 1;
ALTER TABLE `ai_pet` ADD UNIQUE INDEX `uk_ai_pet_user_id` (`user_id`);
