-- 蛋宝宝孵化修炼动作明细表
-- 端点 POST /pet/{id}/hatch-action 落库的每一次修炼动作(NICKNAME/CUDDLE/WISH/LESSON/DOODLE)
-- 幂等: 一次性动作(NICKNAME/DOODLE)按 (pet_id, action_type) 唯一; 每日动作按 (pet_id, action_type, action_date) 唯一
CREATE TABLE `ai_pet_hatch_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `pet_id` VARCHAR(32) NOT NULL COMMENT '宠物ID',
    `action_type` VARCHAR(20) NOT NULL COMMENT 'NICKNAME/CUDDLE/WISH/LESSON/DOODLE',
    `payload` TEXT NULL COMMENT '动作载荷JSON',
    `action_date` DATE NOT NULL COMMENT '动作日期(Asia/Shanghai,幂等用)',
    `accelerated_minutes` INT NOT NULL DEFAULT 0 COMMENT '本次加速分钟数',
    `creator` BIGINT NULL,
    `create_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_pet_action_date` (`pet_id`, `action_type`, `action_date`),
    INDEX `idx_pet_id` (`pet_id`)
) COMMENT='蛋宝宝孵化修炼动作明细';
