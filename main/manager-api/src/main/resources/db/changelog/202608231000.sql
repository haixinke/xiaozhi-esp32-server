-- liquibase formatted sql

-- changeset minwang:202608231000
-- 用户反馈功能：新建反馈表 + 诉求类型字典初始数据
CREATE TABLE `ai_feedback` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '提交用户ID',
    `type` VARCHAR(50) NOT NULL COMMENT '诉求类型（字典 EGG_FEEDBACK_TYPE 的 dict_value）',
    `content` VARCHAR(500) NOT NULL COMMENT '反馈内容',
    `status` INT NOT NULL DEFAULT 0 COMMENT '处理状态：0-未处理 1-已处理',
    `remark` VARCHAR(500) NULL COMMENT '运营处理备注',
    `creator` BIGINT NULL,
    `create_date` DATETIME NULL,
    `updater` BIGINT NULL,
    `update_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `idx_feedback_status_created` (`status`, `create_date`),
    KEY `idx_feedback_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈';

-- 诉求类型字典（id 103 之前已被 101/102 占用）
delete from `sys_dict_type` where `id` = 103;
INSERT INTO `sys_dict_type` (`id`, `dict_type`, `dict_name`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(103, 'EGG_FEEDBACK_TYPE', '蛋宝宝诉求类型', '蛋宝宝小程序用户反馈的诉求类型字典', 0, 1, NOW(), 1, NOW());

delete from `sys_dict_data` where `dict_type_id` = 103;
INSERT INTO `sys_dict_data` (`id`, `dict_type_id`, `dict_label`, `dict_value`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(103001, 103, 'AI 虚拟宠物对话违规（涉政、色情、暴力、轻生、暧昧诱导）', 'AI_CONTENT_VIOLATION', '', 1, 1, NOW(), 1, NOW()),
(103002, 103, 'AI 误导或给出不实医疗、理财建议', 'AI_MISLEADING_ADVICE', '', 2, 1, NOW(), 1, NOW()),
(103003, 103, '未成年人使用相关问题（时长、模式、内容限制）', 'MINOR_USE', '', 3, 1, NOW(), 1, NOW()),
(103004, 103, '老年人操作、防诈骗或使用指引咨询', 'SENIOR_SUPPORT', '', 4, 1, NOW(), 1, NOW()),
(103005, 103, '账号或聊天记录隐私保护问题', 'PRIVACY', '', 5, 1, NOW(), 1, NOW()),
(103006, 103, '极端情绪干预失效（自杀、抑郁、家暴未安抚）', 'CRISIS_INTERVENTION', '', 6, 1, NOW(), 1, NOW()),
(103007, 103, '功能故障或无法打开宠物对话', 'FUNCTION_FAILURE', '', 7, 1, NOW(), 1, NOW()),
(103008, 103, '时长提醒或 AI 标识缺失', 'REMINDER_OR_AI_LABEL', '', 8, 1, NOW(), 1, NOW()),
(103009, 103, '其他申诉、意见建议', 'OTHER', '', 9, 1, NOW(), 1, NOW());
