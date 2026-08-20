-- liquibase formatted sql

-- changeset xiaozhi:202608171000
-- 故事引擎小场景权重时段调整：深夜 19:00~06:59(跨零点)、上午 07:00~11:59、下午 12:00~16:59、傍晚 17:00~18:59。
-- 仅更新列注释，不变更列类型与默认值
ALTER TABLE `ai_story_small_scene`
    MODIFY COLUMN `weight_night`     INT NOT NULL DEFAULT 0 COMMENT '深夜时段(19:00~06:59)权重百分比',
    MODIFY COLUMN `weight_morning`   INT NOT NULL DEFAULT 0 COMMENT '上午时段(07:00~11:59)权重百分比',
    MODIFY COLUMN `weight_afternoon` INT NOT NULL DEFAULT 0 COMMENT '下午时段(12:00~16:59)权重百分比',
    MODIFY COLUMN `weight_evening`   INT NOT NULL DEFAULT 0 COMMENT '傍晚时段(17:00~18:59)权重百分比';
