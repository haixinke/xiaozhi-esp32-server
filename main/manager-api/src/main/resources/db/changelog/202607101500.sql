-- 蛋宝宝领养阶段：放宽 ai_pet.device_id 为可空
-- 领养(adopt)只建 ai_pet(hatch_status=EGG, device_id=NULL)；破壳(hatch)时才回填 device_id。
-- 原列定义为 VARCHAR(32) NOT NULL，会阻止领养阶段插入 NULL，故放宽。
-- uk_ai_pet_device_id 唯一索引保留：MySQL 唯一索引允许多个 NULL，破壳后仍保证"一设备一宠物"。
ALTER TABLE `ai_pet` MODIFY COLUMN `device_id` VARCHAR(32) NULL COMMENT '关联设备ID(领养阶段为空,破壳时回填)';
