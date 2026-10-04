-- ai_pet 唯一索引改造：(user_id, deleted_at) -> (user_id, prototype, deleted_at)
-- 背景：NFC 渠道放开「同用户跨原型多只、同原型限一只」（CONTEXT.md「领养名额规则」，ADR 0007），
-- DB 兜底口径随之一人一只 -> 一人一原型一只。存量数据每用户至多一只宠物，天然兼容。
ALTER TABLE `ai_pet` DROP INDEX `uk_ai_pet_user_deleted`;
ALTER TABLE `ai_pet` ADD UNIQUE KEY `uk_ai_pet_user_prototype_deleted` (`user_id`, `prototype`, `deleted_at`);
