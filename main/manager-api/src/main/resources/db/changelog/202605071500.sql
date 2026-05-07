CREATE TABLE `ai_pet` (
    `id` VARCHAR(32) NOT NULL COMMENT '宠物唯一标识',
    `user_id` BIGINT NOT NULL COMMENT '归属用户ID',
    `device_id` VARCHAR(32) NOT NULL COMMENT '关联设备ID',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `birth_date` DATETIME NOT NULL COMMENT '出生日期时间',
    `bazi` JSON COMMENT '八字',
    `wuxing` JSON COMMENT '五行',
    `zodiac` VARCHAR(20) COMMENT '星座英文编码',
    `mbti` VARCHAR(4) COMMENT 'MBTI人格',
    `creator` BIGINT COMMENT '创建者',
    `create_date` DATETIME COMMENT '创建时间',
    `updater` BIGINT COMMENT '更新者',
    `update_date` DATETIME COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_ai_pet_device_id` (`device_id`),
    INDEX `idx_ai_pet_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI宠物表';
