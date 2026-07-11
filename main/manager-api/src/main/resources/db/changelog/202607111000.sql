-- 蛋宝宝领养阶段：放宽 ai_pet.birth_date 为可空
-- 领养(adopt)只建 ai_pet(hatch_status=EGG, birth_date=NULL)；破壳(hatch)时才回填 birth_date=hatchedAt(=破壳时刻=生日)。
-- 原列定义为 DATETIME NOT NULL，会阻止领养阶段插入 NULL，故放宽（与 202607101500 放宽 device_id 同理）。
ALTER TABLE `ai_pet` MODIFY COLUMN `birth_date` DATETIME NULL COMMENT '出生日期时间(领养阶段为空,破壳时回填)';
